package com.campusai.debug

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.SystemClock
import android.view.Gravity
import android.widget.TextView
import androidx.work.Configuration
import androidx.work.WorkManager
import com.campusai.R
import com.campusai.core.ai.AiEvent
import com.campusai.core.ai.AiRequest
import com.campusai.core.localai.LocalMnnAiEngine
import com.campusai.core.localai.LocalModelManager
import com.campusai.core.localai.LocalPerformanceRecorder
import com.campusai.core.model.AiMode
import com.campusai.core.model.LocalModelState
import java.io.File
import java.time.Instant
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Debug-only, shell-gated real-model replay. It never projects or executes Caesar tools. */
class CaesarEvalActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var statusView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        statusView = TextView(this).apply {
            gravity = Gravity.CENTER
            textSize = 18f
            setPadding(48, 48, 48, 48)
            text = "Caesar Eval 等待显式授权"
        }
        setContentView(statusView)

        if (intent.action != ACTION_RUN || !intent.getBooleanExtra(EXTRA_EXPLICIT_OPT_IN, false)) {
            statusView.text = "Caesar Eval 已拒绝：缺少显式 opt-in"
            return
        }
        val modelId = intent.getStringExtra(EXTRA_MODEL_ID).orEmpty()
        val runId = intent.getStringExtra(EXTRA_RUN_ID).orEmpty()
        if (modelId !in ALLOWED_MODELS || !RUN_ID.matches(runId)) {
            statusView.text = "Caesar Eval 已拒绝：参数无效"
            return
        }

        val outputDirectory = File(filesDir, OUTPUT_DIRECTORY).apply { mkdirs() }
        val statusFile = File(outputDirectory, "$runId.status")
        statusFile.writeText("running", Charsets.UTF_8)
        statusView.text = "Caesar Eval 正在运行\n$modelId"
        scope.launch {
            val outcome = runCatching { runEvaluation(modelId, runId) }
            val report = outcome.getOrElse { error -> fatalReport(modelId, runId, error) }
            val reportFile = File(outputDirectory, "$runId.json")
            writeAtomically(reportFile, report.toString(2))
            statusFile.writeText(if (outcome.isSuccess) "complete" else "failed", Charsets.UTF_8)
            withContext(Dispatchers.Main) {
                statusView.text = if (outcome.isSuccess) {
                    "Caesar Eval 已完成\n${reportFile.name}"
                } else {
                    "Caesar Eval 失败\n${outcome.exceptionOrNull()?.message.orEmpty()}"
                }
                finish()
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun runEvaluation(modelId: String, runId: String): JSONObject {
        val manager = withContext(Dispatchers.Main) {
            ensureWorkManager()
            LocalModelManager(applicationContext)
        }
        val engine = LocalMnnAiEngine(applicationContext, manager)
        val startedAt = System.currentTimeMillis()
        try {
            manager.refreshFromDisk(modelId)
            check(manager.stateFor(modelId) == LocalModelState.Ready) {
                "$modelId 未处于 Ready；Eval 不会触发下载。"
            }
            val manifest = manager.manifestFor(modelId)
            val cases = CaesarEvalDataset.load(applicationContext)
            check(cases.size == EXPECTED_CASE_COUNT) { "Eval 数据集必须固定为 $EXPECTED_CASE_COUNT 条。" }
            check(cases.map(CaesarEvalCase::category).toSet() == CaesarEvalDataset.REQUIRED_CATEGORIES) {
                "Eval 数据集未覆盖全部四类任务。"
            }

            val results = JSONArray()
            cases.forEachIndexed { index, case ->
                withContext(Dispatchers.Main) {
                    statusView.text = "Caesar Eval 正在运行\n$modelId\n${index + 1}/${cases.size}  ${case.id}"
                }
                results.put(evaluateCase(engine, modelId, case, index))
            }
            val finishedAt = System.currentTimeMillis()
            return JSONObject()
                .put("schemaVersion", 1)
                .put("runId", runId)
                .put("dataset", "caesar_eval_v1")
                .put("syntheticDataOnly", true)
                .put("toolExecutionEnabled", false)
                .put("modelId", manifest.id)
                .put("model", manifest.displayName)
                .put("modelVersion", manifest.version)
                .put("quantization", manifest.quantization)
                .put("runtime", JSONObject().put("name", "MNN").put("version", manifest.runtime.version))
                .put("device", LocalPerformanceRecorder.deviceName())
                .put("startedAt", isoTime(startedAt))
                .put("finishedAt", isoTime(finishedAt))
                .put("configuration", JSONObject()
                    .put("maxOutputTokens", MAX_OUTPUT_TOKENS)
                    .put("caseOrder", JSONArray(cases.map(CaesarEvalCase::id))))
                .put("summary", CaesarEvalSummary.summarize(results))
                .put("results", results)
        } finally {
            try {
                engine.releaseAndWait()
            } catch (_: Throwable) {
                // The process is force-stopped after report extraction; release failure must not hide results.
            }
            withContext(Dispatchers.Main) { manager.close() }
        }
    }

    private suspend fun evaluateCase(
        engine: LocalMnnAiEngine,
        modelId: String,
        case: CaesarEvalCase,
        index: Int,
    ): JSONObject {
        val startedEpochMs = System.currentTimeMillis()
        val startedElapsed = SystemClock.elapsedRealtimeNanos()
        val performanceBefore = LocalPerformanceRecorder(applicationContext).latestJson()
        val output = StringBuilder()
        val toolCalls = mutableListOf<CaesarEvalToolCall>()
        var visibleFirstTokenMs: Double? = null
        var modelLabel: String? = null
        var done: AiEvent.Done? = null
        var errorCode: String? = null
        var errorMessage: String? = null

        try {
            engine.stream(
                AiRequest(
                    mode = if (modelId == MODEL_2B) AiMode.FAST else AiMode.DEEP,
                    messages = case.conversationMessages(),
                    structuredContextJson = case.structuredContextJson(),
                    maxOutputTokens = MAX_OUTPUT_TOKENS,
                    caesarToolsJson = case.tools.toString(),
                    requiresLocal = true,
                    localModelId = modelId,
                    sessionId = "caesar-eval-${case.id}-$index",
                    ownerUserId = "",
                    userPrompt = case.prompt,
                    imagePaths = case.imageResource?.let { listOf(materializeImage(it).absolutePath) }.orEmpty(),
                ),
            ).collect { event ->
                when (event) {
                    is AiEvent.Meta -> modelLabel = event.model
                    is AiEvent.Delta -> {
                        if (event.text.isNotEmpty() && visibleFirstTokenMs == null) {
                            visibleFirstTokenMs = elapsedMs(startedElapsed)
                        }
                        output.append(event.text)
                    }
                    is AiEvent.ToolCallRequested -> toolCalls += CaesarEvalToolCall(
                        event.name,
                        runCatching { JSONObject(event.argumentsJson) }.getOrDefault(JSONObject()),
                    )
                    is AiEvent.Done -> done = event
                    is AiEvent.Error -> {
                        errorCode = event.code
                        errorMessage = event.message
                    }
                    is AiEvent.Status,
                    is AiEvent.ToolStarted,
                    is AiEvent.ToolFinished,
                    is AiEvent.Surface,
                    is AiEvent.MemoryProposal -> Unit
                }
            }
        } catch (error: Throwable) {
            errorCode = "eval_exception"
            errorMessage = error.message ?: error.javaClass.simpleName
        }

        val wallElapsedMs = elapsedMs(startedElapsed)
        val performanceAfter = LocalPerformanceRecorder(applicationContext).latestJson()
        val native = performanceAfter
            ?.takeIf { it != performanceBefore }
            ?.let(::JSONObject)
            ?.takeIf { it.optLong("recordedAt", -1L) >= startedEpochMs }
        val nativeFirstTokenMs = native?.optDouble("firstTokenMs")?.takeIf(Double::isFinite)
        val firstTokenMs = nativeFirstTokenMs?.takeIf { it > 0.0 } ?: visibleFirstTokenMs
        val score = CaesarEvalScorer.score(case, output.toString(), done != null, errorCode, toolCalls)
        val unexpectedToolCalls = if (score.noUnexpectedToolCall) emptyList() else toolCalls.map(CaesarEvalToolCall::name)
        return JSONObject()
            .put("id", case.id)
            .put("category", case.category)
            .put("modelLabel", modelLabel ?: JSONObject.NULL)
            .put("success", score.passed)
            .put("error", errorCode?.let { JSONObject().put("code", it).put("message", errorMessage.orEmpty()) } ?: JSONObject.NULL)
            .put("firstTokenMs", firstTokenMs ?: JSONObject.NULL)
            .put("nativeFirstTokenMs", nativeFirstTokenMs ?: JSONObject.NULL)
            .put("visibleFirstTokenMs", visibleFirstTokenMs ?: JSONObject.NULL)
            .put("engineElapsedMs", done?.elapsedMs ?: native?.optLong("elapsedMs") ?: JSONObject.NULL)
            .put("wallElapsedMs", wallElapsedMs)
            .put("tokensPerSecond", native?.optDouble("decodeTokensPerSecond") ?: JSONObject.NULL)
            .put("outputTokens", done?.outputTokens ?: native?.optLong("outputTokens") ?: JSONObject.NULL)
            .put("loadMs", native?.optLong("loadMs") ?: JSONObject.NULL)
            .put("unexpectedToolCalls", JSONArray(unexpectedToolCalls))
            .put("toolCalls", JSONArray(toolCalls.map { call ->
                JSONObject().put("name", call.name).put("arguments", call.arguments)
            }))
            .put("response", output.toString())
            .put("staticScore", score.toJson())
            .put("nativeMetrics", native ?: JSONObject.NULL)
    }

    private fun fatalReport(modelId: String, runId: String, error: Throwable): JSONObject = JSONObject()
        .put("schemaVersion", 1)
        .put("runId", runId)
        .put("dataset", "caesar_eval_v1")
        .put("syntheticDataOnly", true)
        .put("toolExecutionEnabled", false)
        .put("modelId", modelId)
        .put("finishedAt", isoTime(System.currentTimeMillis()))
        .put("fatalError", JSONObject()
            .put("code", "eval_setup_failed")
            .put("message", error.message ?: error.javaClass.simpleName))
        .put("results", JSONArray())

    private fun ensureWorkManager() {
        runCatching { WorkManager.getInstance(applicationContext) }.getOrElse {
            WorkManager.initialize(applicationContext, Configuration.Builder().build())
        }
    }

    private fun writeAtomically(destination: File, content: String) {
        val temporary = File(destination.parentFile, ".${destination.name}.tmp")
        temporary.writeText(content, Charsets.UTF_8)
        if (destination.exists()) check(destination.delete()) { "无法覆盖旧 Eval 报告。" }
        check(temporary.renameTo(destination)) { "无法原子写入 Eval 报告。" }
    }

    private fun materializeImage(resourceName: String): File {
        check(resourceName == "campusai_infinity_icon") { "Eval 图片不在固定白名单。" }
        val directory = File(cacheDir, "caesar-eval-fixtures").apply { mkdirs() }
        val destination = File(directory, "$resourceName.png")
        if (!destination.exists() || destination.length() == 0L) {
            val bitmap = requireNotNull(BitmapFactory.decodeResource(resources, R.drawable.campusai_infinity_icon)) {
                "Eval 图片解码失败。"
            }
            try {
                destination.outputStream().use { output ->
                    check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) { "Eval 图片写入失败。" }
                }
            } finally {
                bitmap.recycle()
            }
        }
        return destination
    }

    private fun elapsedMs(startedNanos: Long): Double =
        (SystemClock.elapsedRealtimeNanos() - startedNanos) / 1_000_000.0

    private fun isoTime(epochMs: Long): String = DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(epochMs))

    companion object {
        const val ACTION_RUN = "com.campusai.debug.RUN_CAESAR_EVAL"
        const val EXTRA_EXPLICIT_OPT_IN = "explicit_opt_in"
        const val EXTRA_MODEL_ID = "model_id"
        const val EXTRA_RUN_ID = "run_id"
        const val OUTPUT_DIRECTORY = "caesar-eval"
        const val MODEL_2B = "qwen3.5-2b-mnn"
        const val MODEL_4B = "qwen3.5-4b-mnn"
        private val ALLOWED_MODELS = setOf(MODEL_2B, MODEL_4B)
        private val RUN_ID = Regex("[A-Za-z0-9._-]{1,96}")
        private const val EXPECTED_CASE_COUNT = 50
        private const val MAX_OUTPUT_TOKENS = 192
    }
}
