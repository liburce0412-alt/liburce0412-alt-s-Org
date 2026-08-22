package com.campusai.core.localai

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.util.concurrent.TimeUnit

class LocalModelDownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    private val storage = LocalModelStorage(context)
    private val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    override suspend fun getForegroundInfo(): ForegroundInfo = foregroundInfo(storage.downloadedBytes())

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        storage.compatibilityIssue()?.let { return@withContext failure("incompatible", it) }
        if (!storage.hasEnoughSpace()) return@withContext failure("insufficient_storage", "可用空间不足。请至少保留模型剩余大小之外的 512 MB 安全余量。")
        storage.root.mkdirs()
        storage.stagingDirectory.mkdirs()
        preferences.edit().putBoolean(KEY_PAUSED, false).remove(KEY_ERROR_CODE).remove(KEY_ERROR_MESSAGE).apply()
        setForeground(foregroundInfo(storage.downloadedBytes()))
        try {
            for (expected in storage.manifest.files) {
                if (isPaused()) return@withContext failure("paused", "下载已暂停，可继续下载。")
                val complete = File(storage.stagingDirectory, expected.path)
                if (complete.exists() && !Sha256Verifier.verify(complete, expected.size, expected.sha256)) complete.delete()
                if (!complete.exists()) {
                    if (!storage.hasEnoughSpace()) return@withContext failure("insufficient_storage", "下载过程中可用空间不足。请清理空间后继续，已下载部分会保留。")
                    val result = downloadOne(expected)
                    if (result != null) return@withContext result
                }
            }
            setProgress(workDataOf(KEY_STAGE to STAGE_VERIFYING, KEY_DOWNLOADED to storage.manifest.totalBytes, KEY_TOTAL to storage.manifest.totalBytes))
            for (expected in storage.manifest.files) {
                val file = File(storage.stagingDirectory, expected.path)
                if (!Sha256Verifier.verify(file, expected.size, expected.sha256)) {
                    file.delete()
                    return@withContext failure("sha256_mismatch", "${expected.path} 校验失败，损坏文件已删除，请重新下载。")
                }
            }
            File(storage.stagingDirectory, LocalModelStorage.READY_MARKER).writeText(
                JSONObject().put("revision", storage.manifest.revision).put("verifiedAt", System.currentTimeMillis()).toString(),
            )
            if (storage.activeDirectory.exists() && !storage.isReady()) storage.activeDirectory.deleteRecursively()
            if (!storage.activeDirectory.exists() && !storage.stagingDirectory.renameTo(storage.activeDirectory)) {
                return@withContext failure("atomic_move_failed", "模型校验成功，但最终文件切换失败。请确认存储空间后重试。")
            }
            if (!storage.isReady()) return@withContext failure("ready_check_failed", "模型文件未能进入可用状态，请重新校验或下载。")
            preferences.edit().remove(KEY_ERROR_CODE).remove(KEY_ERROR_MESSAGE).apply()
            Result.success(workDataOf(KEY_STAGE to STAGE_READY, KEY_DOWNLOADED to storage.manifest.totalBytes, KEY_TOTAL to storage.manifest.totalBytes))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: IOException) {
            preferences.edit().putString(KEY_ERROR_CODE, "network_interrupted").putString(KEY_ERROR_MESSAGE, "下载连接中断，将在网络恢复后自动继续。").apply()
            Result.retry()
        } catch (error: Exception) {
            failure("download_failed", error.message ?: "模型下载未完成，请重试。")
        }
    }

    private suspend fun downloadOne(expected: LocalModelFile): Result? {
        val part = File(storage.stagingDirectory, "${expected.path}.part")
        if (part.length() > expected.size) part.delete()
        var offset = part.length()
        val request = Request.Builder()
            .url(storage.manifest.downloadUrl(expected))
            .header("Accept-Encoding", "identity")
            .apply { if (offset > 0) header("Range", "bytes=$offset-") }
            .build()
        val call = client.newCall(request)
        val cancellationHandle = currentCoroutineContext().job.invokeOnCompletion { cause ->
            if (cause != null) call.cancel()
        }
        try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    return if (response.code in 500..599 || response.code == 408 || response.code == 429) Result.retry()
                    else failure("download_http_${response.code}", "模型源返回 ${response.code}，请稍后重试。")
                }
                if (offset > 0 && response.code != 206) {
                    RandomAccessFile(part, "rw").use { it.setLength(0) }
                    offset = 0
                }
                val body = response.body ?: return failure("empty_download", "模型源没有返回文件内容，请重试。")
                RandomAccessFile(part, "rw").use { output ->
                    output.seek(offset)
                    body.byteStream().use { input ->
                        val buffer = ByteArray(256 * 1024)
                        var lastProgressAt = 0L
                        while (true) {
                            if (isStopped) throw CancellationException("worker stopped")
                            if (isPaused()) return failure("paused", "下载已暂停，可继续下载。")
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            val now = System.currentTimeMillis()
                            if (now - lastProgressAt >= 500) {
                                publishProgress()
                                lastProgressAt = now
                            }
                        }
                        output.fd.sync()
                    }
                }
            }
        } finally {
            cancellationHandle.dispose()
        }
        if (!Sha256Verifier.verify(part, expected.size, expected.sha256)) {
            part.delete()
            return failure("sha256_mismatch", "${expected.path} 校验失败，损坏的临时文件已删除。")
        }
        val finalFile = File(storage.stagingDirectory, expected.path)
        if (!part.renameTo(finalFile)) return failure("file_commit_failed", "${expected.path} 无法完成原子写入，请重试。")
        publishProgress()
        return null
    }

    private suspend fun publishProgress() {
        val downloaded = storage.downloadedBytes().coerceAtMost(storage.manifest.totalBytes)
        setProgress(workDataOf(KEY_STAGE to STAGE_DOWNLOADING, KEY_DOWNLOADED to downloaded, KEY_TOTAL to storage.manifest.totalBytes))
        setForeground(foregroundInfo(downloaded))
    }

    private fun failure(code: String, message: String): Result {
        preferences.edit().putString(KEY_ERROR_CODE, code).putString(KEY_ERROR_MESSAGE, message).apply()
        return Result.failure(workDataOf(KEY_ERROR_CODE to code, KEY_ERROR_MESSAGE to message, KEY_DOWNLOADED to storage.downloadedBytes(), KEY_TOTAL to storage.manifest.totalBytes))
    }

    private fun isPaused(): Boolean = preferences.getBoolean(KEY_PAUSED, false)

    private fun foregroundInfo(downloaded: Long): ForegroundInfo {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(NotificationChannel(CHANNEL, "离线模型下载", NotificationManager.IMPORTANCE_LOW))
        val progress = ((downloaded * 100L) / storage.manifest.totalBytes).toInt().coerceIn(0, 100)
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("正在下载 ${storage.manifest.displayName}")
            .setContentText("$progress% · 可暂停，关闭应用后仍可恢复")
            .setProgress(100, progress, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
        return if (Build.VERSION.SDK_INT >= 29) ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else ForegroundInfo(NOTIFICATION_ID, notification)
    }

    companion object {
        const val UNIQUE_WORK = "campusai-local-model-download"
        const val PREFS = "campusai_local_model_transfer"
        const val KEY_PAUSED = "paused"
        const val KEY_STAGE = "stage"
        const val KEY_DOWNLOADED = "downloaded"
        const val KEY_TOTAL = "total"
        const val KEY_ERROR_CODE = "error_code"
        const val KEY_ERROR_MESSAGE = "error_message"
        const val STAGE_DOWNLOADING = "downloading"
        const val STAGE_VERIFYING = "verifying"
        const val STAGE_READY = "ready"
        private const val CHANNEL = "local_model_download"
        private const val NOTIFICATION_ID = 43035

        fun enqueue(context: Context, wifiOnly: Boolean) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_PAUSED, false).apply()
            val request = OneTimeWorkRequestBuilder<LocalModelDownloadWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_WORK, ExistingWorkPolicy.REPLACE, request)
        }
    }
}
