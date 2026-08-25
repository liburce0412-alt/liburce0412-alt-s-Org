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
    private val storages = LocalModelMode.entries.associate { mode ->
        mode.modelId to LocalModelStorage(appContext, LocalModelManifest.load(appContext, mode.modelId))
    }
    private val modeStore = LocalModelModeStore(appContext)
    private val initialMode = modeStore.read()
    /** Kept for callers that still render the default QUALITY model card. */
    val storage = storages.getValue(LocalModelManifest.DEFAULT_MODEL_ID)
    val manifest: LocalModelManifest get() = storage.manifest
    private val workManager = WorkManager.getInstance(appContext)
    private val transferPreferences = storages.keys.associateWith { modelId ->
        appContext.getSharedPreferences(LocalModelDownloadWorker.transferPreferencesName(modelId), Context.MODE_PRIVATE)
    }
    private val _states = MutableStateFlow<Map<String, LocalModelState>>(
        storages.keys.associateWith { LocalModelState.Checking },
    )
    val states: StateFlow<Map<String, LocalModelState>> = _states.asStateFlow()
    private val _state = MutableStateFlow<LocalModelState>(LocalModelState.Checking)
    val state: StateFlow<LocalModelState> = _state.asStateFlow()
    private val _selection = MutableStateFlow(selectionFor(initialMode, LocalModelState.Checking))
    val selection: StateFlow<LocalModelSelection> = _selection.asStateFlow()
    private val workLiveData = storages.keys.associateWith { modelId ->
        workManager.getWorkInfosForUniqueWorkLiveData(LocalModelDownloadWorker.uniqueWorkName(modelId))
    }
    private val observers = storages.keys.associateWith { modelId ->
        Observer<List<WorkInfo>> { works -> refreshFromWork(modelId, currentWorkInfo(modelId, works)) }
    }

    init {
        refreshFromDisk()
        _selection.value = selectionFor(initialMode, stateFor(initialMode.modelId))
        workLiveData.forEach { (modelId, liveData) -> liveData.observeForever(observers.getValue(modelId)) }
    }

    fun download(wifiOnly: Boolean) = download(LocalModelManifest.DEFAULT_MODEL_ID, wifiOnly)

    fun download(mode: LocalModelMode, wifiOnly: Boolean) = download(mode.modelId, wifiOnly)

    fun download(modelId: String, wifiOnly: Boolean) {
        val selectedStorage = storageFor(modelId)
        selectedStorage.compatibilityIssue()?.let { updateState(modelId, LocalModelState.Incompatible(it)); return }
        if (selectedStorage.isReady()) {
            updateState(modelId, LocalModelState.Ready)
            return
        }
        if (!selectedStorage.hasEnoughSpace()) {
            updateState(modelId, LocalModelState.Error("insufficient_storage", true, "可用空间不足。需要剩余模型大小再加 512 MB 安全余量。"))
            return
        }
        LocalModelDownloadWorker.enqueue(appContext, modelId, wifiOnly)
        updateState(modelId, LocalModelState.Downloading(progress(selectedStorage), selectedStorage.downloadedBytes(), selectedStorage.manifest.totalBytes))
    }

    fun pause() = pause(LocalModelManifest.DEFAULT_MODEL_ID)

    fun pause(mode: LocalModelMode) = pause(mode.modelId)

    fun pause(modelId: String) {
        val selectedStorage = storageFor(modelId)
        preferencesFor(modelId).edit().putBoolean(LocalModelDownloadWorker.KEY_PAUSED, true).apply()
        workManager.cancelUniqueWork(LocalModelDownloadWorker.uniqueWorkName(modelId))
        updateState(modelId, LocalModelState.Paused(selectedStorage.downloadedBytes(), selectedStorage.manifest.totalBytes))
    }

    fun resume(wifiOnly: Boolean) = resume(LocalModelManifest.DEFAULT_MODEL_ID, wifiOnly)

    fun resume(mode: LocalModelMode, wifiOnly: Boolean) = resume(mode.modelId, wifiOnly)

    fun resume(modelId: String, wifiOnly: Boolean) = download(modelId, wifiOnly)

    suspend fun deleteModel(releaseEngine: suspend () -> Unit): Boolean =
        deleteModel(LocalModelManifest.DEFAULT_MODEL_ID, releaseEngine)

    suspend fun deleteModel(mode: LocalModelMode, releaseEngine: suspend () -> Unit): Boolean =
        deleteModel(mode.modelId, releaseEngine)

    suspend fun deleteModel(modelId: String, releaseEngine: suspend () -> Unit): Boolean {
        val selectedStorage = storageFor(modelId)
        val selectedPreferences = preferencesFor(modelId)
        selectedPreferences.edit().putBoolean(LocalModelDownloadWorker.KEY_PAUSED, true).apply()
        updateState(modelId, LocalModelState.Paused(selectedStorage.downloadedBytes(), selectedStorage.manifest.totalBytes))
        return try {
            withContext(Dispatchers.IO) {
                workManager.cancelUniqueWork(LocalModelDownloadWorker.uniqueWorkName(modelId)).result.get()
            }
            releaseEngine()
            val deleted = withContext(Dispatchers.IO) { selectedStorage.deleteSelected() }
            selectedPreferences.edit().clear().apply()
            updateState(modelId, if (deleted) LocalModelState.NotDownloaded
            else LocalModelState.Error("delete_failed", true, "模型仍被占用。请稍后重试或重启应用后删除。"))
            deleted
        } catch (error: Exception) {
            updateState(modelId, LocalModelState.Error("delete_failed", true, error.message ?: "无法安全停止模型任务，请重试。"))
            false
        }
    }

    fun refreshFromDisk() {
        storages.keys.forEach(::refreshFromDisk)
    }

    fun refreshFromDisk(modelId: String) {
        val selectedStorage = storageFor(modelId)
        updateState(modelId, diskState(modelId, selectedStorage))
    }

    fun selectMode(mode: LocalModelMode) {
        modeStore.write(mode)
        _selection.value = selectionFor(mode, stateFor(mode.modelId))
    }

    fun stateFor(modelId: String): LocalModelState {
        storageFor(modelId)
        return _states.value.getValue(modelId)
    }

    fun stateFor(mode: LocalModelMode): LocalModelState = stateFor(mode.modelId)

    fun runtimeFor(modelId: String = ""): LocalModelRuntime {
        val mode = if (modelId.isBlank()) _selection.value.mode else LocalModelMode.entries.firstOrNull { it.modelId == modelId }
            ?: throw IllegalArgumentException("未知的本地模型：$modelId")
        val selectedStorage = storageFor(mode.modelId)
        return LocalModelRuntime(selectionFor(mode, stateFor(mode.modelId)), selectedStorage)
    }

    fun manifestFor(modelId: String = ""): LocalModelManifest = runtimeFor(modelId).selection.manifest

    fun modelIdFromLabel(label: String): String? = LocalModelMode.entries.firstNotNullOfOrNull { mode ->
        storageFor(mode.modelId).manifest.id.takeIf { label.contains(storageFor(mode.modelId).manifest.displayName, ignoreCase = true) }
    }

    fun close() {
        workLiveData.forEach { (modelId, liveData) -> liveData.removeObserver(observers.getValue(modelId)) }
    }

    private fun refreshFromWork(modelId: String, info: WorkInfo?) {
        val selectedStorage = storageFor(modelId)
        val selectedPreferences = preferencesFor(modelId)
        if (info == null) {
            updateState(modelId, diskState(modelId, selectedStorage))
            return
        }
        val issue = selectedStorage.compatibilityIssue()
        val downloaded = info.progress.getLong(LocalModelDownloadWorker.KEY_DOWNLOADED, selectedStorage.downloadedBytes())
        val total = info.progress.getLong(LocalModelDownloadWorker.KEY_TOTAL, selectedStorage.manifest.totalBytes)
        val workState = when (info.state) {
            WorkInfo.State.ENQUEUED -> TransferWorkState.ENQUEUED
            WorkInfo.State.BLOCKED -> TransferWorkState.BLOCKED
            WorkInfo.State.RUNNING -> TransferWorkState.RUNNING
            WorkInfo.State.CANCELLED -> TransferWorkState.CANCELLED
            WorkInfo.State.FAILED -> TransferWorkState.FAILED
            WorkInfo.State.SUCCEEDED -> TransferWorkState.SUCCEEDED
        }
        updateState(modelId, reduceLocalModelState(LocalModelSnapshot(
            compatible = issue == null,
            incompatibilityReason = issue.orEmpty(),
            ready = selectedStorage.isReady(),
            paused = selectedPreferences.getBoolean(LocalModelDownloadWorker.KEY_PAUSED, false),
            workState = workState,
            stage = info.progress.getString(LocalModelDownloadWorker.KEY_STAGE).orEmpty(),
            downloadedBytes = downloaded,
            totalBytes = total,
            errorCode = info.outputData.getString(LocalModelDownloadWorker.KEY_ERROR_CODE)
                ?: selectedPreferences.getString(LocalModelDownloadWorker.KEY_ERROR_CODE, null) ?: "download_failed",
            errorMessage = info.outputData.getString(LocalModelDownloadWorker.KEY_ERROR_MESSAGE)
                ?: selectedPreferences.getString(LocalModelDownloadWorker.KEY_ERROR_MESSAGE, null) ?: "模型下载没有完成，请重试。",
        )))
    }

    private fun currentWorkInfo(modelId: String, works: List<WorkInfo>): WorkInfo? {
        val trackedId = preferencesFor(modelId).getString(LocalModelDownloadWorker.KEY_WORK_ID, null)
        if (trackedId != null) works.firstOrNull { it.id.toString() == trackedId }?.let { return it }
        return works.firstOrNull { !it.state.isFinished }
    }

    private fun selectionFor(mode: LocalModelMode, selectedState: LocalModelState): LocalModelSelection =
        LocalModelSelection(mode, storageFor(mode.modelId).manifest, selectedState)

    private fun diskState(modelId: String, selectedStorage: LocalModelStorage): LocalModelState =
        selectedStorage.compatibilityIssue()?.let(LocalModelState::Incompatible)
            ?: if (selectedStorage.isReady()) LocalModelState.Ready
            else if (preferencesFor(modelId).getBoolean(LocalModelDownloadWorker.KEY_PAUSED, false) && selectedStorage.downloadedBytes() > 0) {
                LocalModelState.Paused(selectedStorage.downloadedBytes(), selectedStorage.manifest.totalBytes)
            }
            else if (selectedStorage.downloadedBytes() > 0) LocalModelState.Error(
                "model_files_incomplete",
                true,
                "${selectedStorage.manifest.displayName} 文件不完整或未通过校验。",
            ) else LocalModelState.NotDownloaded

    private fun updateState(modelId: String, value: LocalModelState) {
        val mode = LocalModelMode.entries.firstOrNull { it.modelId == modelId }
            ?: throw IllegalArgumentException("未知的本地模型：$modelId")
        _states.value = _states.value + (modelId to value)
        if (modelId == LocalModelManifest.DEFAULT_MODEL_ID) _state.value = value
        if (_selection.value.mode == mode) {
            _selection.value = selectionFor(mode, value)
        }
    }

    private fun storageFor(modelId: String): LocalModelStorage = storages[modelId]
        ?: throw IllegalArgumentException("未知的本地模型：$modelId")

    private fun preferencesFor(modelId: String) = transferPreferences[modelId]
        ?: throw IllegalArgumentException("未知的本地模型：$modelId")

    private fun progress(selectedStorage: LocalModelStorage): Float =
        (selectedStorage.downloadedBytes().toFloat() / selectedStorage.manifest.totalBytes).coerceIn(0f, 1f)
}
