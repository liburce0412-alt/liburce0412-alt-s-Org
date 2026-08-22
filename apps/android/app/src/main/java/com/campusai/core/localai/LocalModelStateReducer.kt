package com.campusai.core.localai

import com.campusai.core.model.LocalModelState

enum class TransferWorkState { NONE, ENQUEUED, BLOCKED, RUNNING, CANCELLED, FAILED, SUCCEEDED }

data class LocalModelSnapshot(
    val compatible: Boolean = true,
    val incompatibilityReason: String = "",
    val ready: Boolean = false,
    val paused: Boolean = false,
    val workState: TransferWorkState = TransferWorkState.NONE,
    val stage: String = "",
    val downloadedBytes: Long = 0,
    val totalBytes: Long,
    val errorCode: String = "download_failed",
    val errorMessage: String = "模型下载没有完成，请重试。",
)

fun reduceLocalModelState(snapshot: LocalModelSnapshot): LocalModelState = when {
    !snapshot.compatible -> LocalModelState.Incompatible(snapshot.incompatibilityReason)
    snapshot.ready -> LocalModelState.Ready
    snapshot.workState == TransferWorkState.RUNNING && snapshot.stage == LocalModelDownloadWorker.STAGE_VERIFYING -> LocalModelState.Verifying
    snapshot.workState == TransferWorkState.RUNNING || snapshot.workState == TransferWorkState.ENQUEUED || snapshot.workState == TransferWorkState.BLOCKED -> {
        val progress = if (snapshot.totalBytes > 0) snapshot.downloadedBytes.toFloat() / snapshot.totalBytes else 0f
        LocalModelState.Downloading(progress.coerceIn(0f, 1f), snapshot.downloadedBytes, snapshot.totalBytes)
    }
    snapshot.paused || snapshot.workState == TransferWorkState.CANCELLED && snapshot.downloadedBytes > 0 -> LocalModelState.Paused(snapshot.downloadedBytes, snapshot.totalBytes)
    snapshot.workState == TransferWorkState.FAILED -> if (snapshot.errorCode == "paused") LocalModelState.Paused(snapshot.downloadedBytes, snapshot.totalBytes)
        else LocalModelState.Error(snapshot.errorCode, true, snapshot.errorMessage)
    snapshot.workState == TransferWorkState.SUCCEEDED -> LocalModelState.Error("ready_check_failed", true, "下载完成但模型未通过就绪检查。")
    else -> LocalModelState.NotDownloaded
}
