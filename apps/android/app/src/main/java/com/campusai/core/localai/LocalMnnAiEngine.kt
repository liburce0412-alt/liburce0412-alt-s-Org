package com.campusai.core.localai

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Debug
import com.campusai.core.ai.AiEngine
import com.campusai.core.ai.AiEvent
import com.campusai.core.ai.AiRequest
import com.campusai.core.model.AiProvider
import com.campusai.core.model.LocalModelState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class LocalMnnAiEngine(
    context: Context,
    private val manager: LocalModelManager,
) : AiEngine, ComponentCallbacks2 {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()
    private val cancelled = AtomicBoolean(false)
    private val performance = LocalPerformanceRecorder(appContext)
    @Volatile private var nativePointer = 0L
    private var idleRelease: Job? = null

    init { appContext.registerComponentCallbacks(this) }

    override fun stream(request: AiRequest): Flow<AiEvent> = callbackFlow {
        if (manager.state.value != LocalModelState.Ready && nativePointer == 0L) {
            close(IllegalStateException("本地模型尚未完成下载与校验。"))
            return@callbackFlow
        }
        cancelled.set(false)
        idleRelease?.cancel()
        val job = scope.launch {
            var performanceMonitor: Job? = null
            try {
                trySend(AiEvent.Status("local_loading", 0))
                val peakPssKb = AtomicLong(Debug.getPss().toLong())
                val temperatureStart = batteryTemperatureC()
                performanceMonitor = scope.launch {
                    while (true) {
                        peakPssKb.accumulateAndGet(Debug.getPss().toLong()) { current, sample -> maxOf(current, sample) }
                        kotlinx.coroutines.delay(100)
                    }
                }
                var loadMs = 0L
                val metrics = mutex.withLock {
                    val loaded = ensureLoaded()
                    loadMs = loaded.loadMs
                    val messages = LocalPromptPolicy.prepare(request)
                    trySend(AiEvent.Meta("Qwen3.5-2B · MNN 4-bit", AiProvider.LOCAL))
                    trySend(AiEvent.Status("local_generating", 0))
                    MnnNativeBridge.generate(
                        pointer = loaded.pointer,
                        roles = messages.map { it.role }.toTypedArray(),
                        contents = messages.map { it.content }.toTypedArray(),
                        maxTokens = request.maxOutputTokens.coerceAtMost(manager.manifest.maxOutputTokens),
                        listener = MnnTokenListener { token ->
                            if (!cancelled.get() && token.isNotEmpty()) trySend(AiEvent.Delta(token))
                            cancelled.get()
                        },
                    )
                }
                performanceMonitor?.cancel()
                performance.record(LocalPerformanceSample(
                    recordedAt = System.currentTimeMillis(),
                    device = LocalPerformanceRecorder.deviceName(),
                    backend = "CPU",
                    threads = 4,
                    loadMs = loadMs,
                    firstTokenMs = metrics.firstTokenMicros / 1_000.0,
                    decodeTokensPerSecond = if (metrics.decodeMicros > 0) metrics.outputTokens * 1_000_000.0 / metrics.decodeMicros else 0.0,
                    peakPssKb = peakPssKb.get(),
                    outputTokens = metrics.outputTokens,
                    elapsedMs = metrics.elapsedMs,
                    batteryTemperatureStartC = temperatureStart,
                    batteryTemperatureEndC = batteryTemperatureC(),
                ))
                if (!cancelled.get()) trySend(AiEvent.Done(metrics.elapsedMs, metrics.inputTokens, metrics.outputTokens))
                manager.markReady()
            } catch (error: Throwable) {
                if (!cancelled.get()) {
                    runCatching { releaseAndWait() }
                    manager.refreshFromDisk()
                    trySend(AiEvent.Error("local_inference_failed", error.message ?: "本地推理失败，请重试。"))
                }
            } finally {
                performanceMonitor?.cancel()
                scheduleIdleRelease()
                close()
            }
        }
        awaitClose {
            cancelled.set(true)
            MnnNativeBridge.cancel(nativePointer)
            job.cancel()
        }
    }

    private data class LoadedEngine(val pointer: Long, val loadMs: Long)

    private fun ensureLoaded(): LoadedEngine {
        nativePointer.takeIf { it != 0L }?.let { return LoadedEngine(it, 0) }
        check(manager.storage.isReady()) { "本地模型文件未处于 Ready 状态。" }
        manager.markLoading()
        val cache = File(appContext.cacheDir, "mnn-local").apply { mkdirs() }
        val config = File(manager.storage.activeDirectory, "config.json")
        val started = System.nanoTime()
        val created = MnnNativeBridge.create(config.absolutePath, cache.absolutePath.replace('\\', '/'))
        check(created != 0L) { "MNN 模型加载失败。" }
        nativePointer = created
        manager.markReady()
        return LoadedEngine(created, (System.nanoTime() - started) / 1_000_000L)
    }

    override fun cancel() {
        cancelled.set(true)
        MnnNativeBridge.cancel(nativePointer)
    }

    fun release() {
        cancel()
        scope.launch { releaseAndWait() }
    }

    fun shutdown() {
        cancel()
        scope.launch {
            releaseAndWait()
            appContext.unregisterComponentCallbacks(this@LocalMnnAiEngine)
            scope.cancel()
        }
    }

    suspend fun releaseAndWait() = mutex.withLock {
        val pointer = nativePointer
        nativePointer = 0
        MnnNativeBridge.release(pointer)
        manager.refreshFromDisk()
    }

    private fun scheduleIdleRelease() {
        idleRelease?.cancel()
        idleRelease = scope.launch {
            kotlinx.coroutines.delay(IDLE_RELEASE_MS)
            releaseAndWait()
        }
    }

    override fun onTrimMemory(level: Int) {
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL) release()
    }
    override fun onLowMemory() = release()
    override fun onConfigurationChanged(newConfig: Configuration) = Unit

    private fun batteryTemperatureC(): Double? {
        val battery = appContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return null
        val tenths = battery.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
        return tenths.takeIf { it != Int.MIN_VALUE }?.div(10.0)
    }

    companion object { private const val IDLE_RELEASE_MS = 10 * 60 * 1_000L }
}
