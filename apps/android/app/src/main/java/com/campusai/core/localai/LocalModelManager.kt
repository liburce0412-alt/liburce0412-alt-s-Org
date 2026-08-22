package com.campusai.core.localai

import android.content.Context
import androidx.lifecycle.Observer
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.campusai.core.model.LocalModelState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class LocalModelManager(context: Context) {
    private val appContext = context.applicationContext
    val storage = LocalModelStorage(appContext)
    val manifest: LocalModelManifest get() = storage.manifest
    private val workManager = WorkManager.getInstance(appContext)
    private val transferPreferences = appContext.getSharedPreferences(LocalModelDownloadWorker.PREFS, Context.MODE_PRIVATE)
    private val _state = MutableStateFlow<LocalModelState>(LocalModelState.Checking)
    val state: StateFlow<LocalModelState> = _state.asStateFlow()
    private val workLiveData = workManager.getWorkInfosForUniqueWorkLiveData(LocalModelDownloadWorker.UNIQUE_WORK)

    private val observer = Observer<List<WorkInfo>> { works -> refreshFromWork(works.firstOrNull()) }

    init {
        refreshFromDisk()
        workLiveData.observeForever(observer)
    }

    fun download(wifiOnly: Boolean) {
        storage.compatibilityIssue()?.let { _state.value = LocalModelState.Incompatible(it); return }
        if (!storage.hasEnoughSpace()) {
            _state.value = LocalModelState.Error("insufficient_storage", true, "可用空间不足。需要剩余模型大小再加 512 MB 安全余量。")
            return
        }
        LocalModelDownloadWorker.enqueue(appContext, wifiOnly)
        _state.value = LocalModelState.Downloading(progress(), storage.downloadedBytes(), manifest.totalBytes)
    }

    fun pause() {
        transferPreferences.edit().putBoolean(LocalModelDownloadWorker.KEY_PAUSED, true).apply()
        workManager.cancelUniqueWork(LocalModelDownloadWorker.UNIQUE_WORK)
        _state.value = LocalModelState.Paused(storage.downloadedBytes(), manifest.totalBytes)
    }

    fun resume(wifiOnly: Boolean) = download(wifiOnly)

    suspend fun deleteModel(releaseEngine: suspend () -> Unit): Boolean {
        transferPreferences.edit().putBoolean(LocalModelDownloadWorker.KEY_PAUSED, true).apply()
        _state.value = LocalModelState.Paused(storage.downloadedBytes(), manifest.totalBytes)
        return try {
            withContext(Dispatchers.IO) {
                workManager.cancelUniqueWork(LocalModelDownloadWorker.UNIQUE_WORK).result.get()
            }
            releaseEngine()
            val deleted = withContext(Dispatchers.IO) { storage.deleteAll() }
            transferPreferences.edit().clear().apply()
            _state.value = if (deleted) LocalModelState.NotDownloaded
            else LocalModelState.Error("delete_failed", true, "模型仍被占用。请稍后重试或重启应用后删除。")
            deleted
        } catch (error: Exception) {
            _state.value = LocalModelState.Error("delete_failed", true, error.message ?: "无法安全停止模型任务，请重试。")
            false
        }
    }

    fun refreshFromDisk() {
        _state.value = storage.compatibilityIssue()?.let(LocalModelState::Incompatible)
            ?: if (storage.isReady()) LocalModelState.Ready
            else if (transferPreferences.getBoolean(LocalModelDownloadWorker.KEY_PAUSED, false) && storage.downloadedBytes() > 0) {
                LocalModelState.Paused(storage.downloadedBytes(), manifest.totalBytes)
            } else LocalModelState.NotDownloaded
    }

    fun markLoading() { _state.value = LocalModelState.Loading }
    fun markReady() { if (storage.isReady()) _state.value = LocalModelState.Ready }
    fun markError(code: String, message: String) { _state.value = LocalModelState.Error(code, true, message) }
    fun close() { workLiveData.removeObserver(observer) }

    private fun refreshFromWork(info: WorkInfo?) {
        val issue = storage.compatibilityIssue()
        val downloaded = info?.progress?.getLong(LocalModelDownloadWorker.KEY_DOWNLOADED, storage.downloadedBytes()) ?: storage.downloadedBytes()
        val total = info?.progress?.getLong(LocalModelDownloadWorker.KEY_TOTAL, manifest.totalBytes) ?: manifest.totalBytes
        val workState = when (info?.state) {
            WorkInfo.State.ENQUEUED -> TransferWorkState.ENQUEUED
            WorkInfo.State.BLOCKED -> TransferWorkState.BLOCKED
            WorkInfo.State.RUNNING -> TransferWorkState.RUNNING
            WorkInfo.State.CANCELLED -> TransferWorkState.CANCELLED
            WorkInfo.State.FAILED -> TransferWorkState.FAILED
            WorkInfo.State.SUCCEEDED -> TransferWorkState.SUCCEEDED
            null -> TransferWorkState.NONE
        }
        _state.value = reduceLocalModelState(LocalModelSnapshot(
            compatible = issue == null,
            incompatibilityReason = issue.orEmpty(),
            ready = storage.isReady(),
            paused = transferPreferences.getBoolean(LocalModelDownloadWorker.KEY_PAUSED, false),
            workState = workState,
            stage = info?.progress?.getString(LocalModelDownloadWorker.KEY_STAGE).orEmpty(),
            downloadedBytes = downloaded,
            totalBytes = total,
            errorCode = info?.outputData?.getString(LocalModelDownloadWorker.KEY_ERROR_CODE)
                ?: transferPreferences.getString(LocalModelDownloadWorker.KEY_ERROR_CODE, null) ?: "download_failed",
            errorMessage = info?.outputData?.getString(LocalModelDownloadWorker.KEY_ERROR_MESSAGE)
                ?: transferPreferences.getString(LocalModelDownloadWorker.KEY_ERROR_MESSAGE, null) ?: "模型下载没有完成，请重试。",
        ))
    }

    private fun progress(): Float = (storage.downloadedBytes().toFloat() / manifest.totalBytes).coerceIn(0f, 1f)
}
