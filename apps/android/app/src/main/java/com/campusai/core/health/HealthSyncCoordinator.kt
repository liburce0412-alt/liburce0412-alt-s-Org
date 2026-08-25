package com.campusai.core.health

import android.os.SystemClock
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class HealthSyncReason { USER, AGENT }

enum class HealthSyncStage {
    CONNECTING,
    PULLING_HISTORY,
    IMPORTING_HEALTH_CONNECT,
    COMPLETE,
    USING_CACHED_DATA,
    FAILED,
}

data class HealthSyncResult(
    val health: HealthSnapshot?,
    val band: BandLiveSnapshot?,
    val stage: HealthSyncStage,
    val message: String,
    val healthError: String? = null,
    val bandError: String? = null,
)

/** Coordinates one connect -> history sync -> Health Connect refresh chain for UI and Agent. */
class HealthSyncCoordinator(
    private val healthGateway: HealthGateway,
    private val bandGateway: BandLiveGateway,
    private val elapsedRealtime: () -> Long = SystemClock::elapsedRealtime,
    private val sleep: suspend (Long) -> Unit = { delay(it) },
) {
    private val mutex = Mutex()
    private var lastAttemptElapsedMillis: Long? = null

    suspend fun synchronize(
        period: HealthPeriod,
        reason: HealthSyncReason,
        onStage: (HealthSyncStage, String) -> Unit = { _, _ -> },
    ): HealthSyncResult = mutex.withLock {
        val baseline = healthGateway.snapshot(period).getOrNull()
        val now = elapsedRealtime()
        val recentAttempt = lastAttemptElapsedMillis?.let { now - it in 0 until AGENT_COOLDOWN_MILLIS } == true
        if (reason == HealthSyncReason.AGENT && recentAttempt) {
            val band = bandGateway.snapshot().getOrNull()
            val message = "刚刚已经尝试过手环同步，本轮直接读取现有 Health Connect 数据。"
            onStage(HealthSyncStage.USING_CACHED_DATA, message)
            return@withLock HealthSyncResult(
                health = baseline,
                band = band,
                stage = HealthSyncStage.USING_CACHED_DATA,
                message = message,
                healthError = if (baseline == null) "Health Connect 暂无可读取数据" else null,
            )
        }

        lastAttemptElapsedMillis = now
        val requestedAt = System.currentTimeMillis()
        onStage(HealthSyncStage.CONNECTING, "正在请求 Gadgetbridge 连接小米手环 9…")
        val trigger = bandGateway.triggerHistorySync()
        if (trigger.isFailure) {
            val error = trigger.exceptionOrNull()?.message ?: "CaesarBandBridge 无法启动"
            val band = bandGateway.snapshot().getOrNull()
            val message = "未能发起手环同步，继续显示现有健康数据。"
            onStage(HealthSyncStage.FAILED, message)
            return@withLock HealthSyncResult(
                health = baseline,
                band = band,
                stage = HealthSyncStage.FAILED,
                message = message,
                healthError = if (baseline == null) "Health Connect 暂无可读取数据" else null,
                bandError = error,
            )
        }

        onStage(HealthSyncStage.PULLING_HISTORY, "连接后将自动拉取手环历史数据…")
        val waitMillis = if (reason == HealthSyncReason.USER) USER_WAIT_MILLIS else AGENT_WAIT_MILLIS
        val band = awaitHistoryCompletion(requestedAt, waitMillis)
        if (band?.historySyncState != BandHistorySyncState.FINISHED) {
            val latest = healthGateway.snapshot(period).getOrNull() ?: baseline
            val failed = band?.historySyncState == BandHistorySyncState.ERROR
            val message = if (failed) {
                "手环同步没有完成，继续显示上次已写入的健康数据。"
            } else {
                "已在后台请求连接与同步；本轮先使用现有健康数据。"
            }
            val stage = if (failed) HealthSyncStage.FAILED else HealthSyncStage.USING_CACHED_DATA
            onStage(stage, message)
            return@withLock HealthSyncResult(
                health = latest,
                band = band,
                stage = stage,
                message = message,
                healthError = if (latest == null) "Health Connect 暂无可读取数据" else null,
                bandError = band?.statusMessage.takeIf { failed },
            )
        }

        onStage(HealthSyncStage.IMPORTING_HEALTH_CONNECT, "手环历史已拉取，正在等待 Health Connect 更新…")
        val refreshed = awaitHealthConnectUpdate(period, baseline)
        val changed = healthEvidenceChanged(baseline, refreshed)
        val message = if (changed) {
            "手环历史与 Health Connect 已更新。"
        } else {
            "手环历史同步已完成，本次没有发现新的 Health Connect 记录。"
        }
        onStage(HealthSyncStage.COMPLETE, message)
        HealthSyncResult(
            health = refreshed ?: baseline,
            band = bandGateway.snapshot().getOrNull() ?: band,
            stage = HealthSyncStage.COMPLETE,
            message = message,
            healthError = if (refreshed == null && baseline == null) "Health Connect 暂无可读取数据" else null,
        )
    }

    private suspend fun awaitHistoryCompletion(requestedAt: Long, timeoutMillis: Long): BandLiveSnapshot? {
        val deadline = elapsedRealtime() + timeoutMillis
        var latest: BandLiveSnapshot? = null
        while (elapsedRealtime() < deadline) {
            latest = bandGateway.snapshot().getOrNull() ?: latest
            if (latest != null && latest.observedAt >= requestedAt) {
                when (latest.historySyncState) {
                    BandHistorySyncState.FINISHED, BandHistorySyncState.ERROR -> return latest
                    else -> Unit
                }
            }
            sleep(POLL_MILLIS)
        }
        return latest
    }

    private suspend fun awaitHealthConnectUpdate(
        period: HealthPeriod,
        baseline: HealthSnapshot?,
    ): HealthSnapshot? {
        val deadline = elapsedRealtime() + HEALTH_CONNECT_WAIT_MILLIS
        var latest = baseline
        while (elapsedRealtime() < deadline) {
            latest = healthGateway.snapshot(period).getOrNull() ?: latest
            if (healthEvidenceChanged(baseline, latest)) return latest
            sleep(HEALTH_CONNECT_POLL_MILLIS)
        }
        return healthGateway.snapshot(period).getOrNull() ?: latest
    }

    private fun healthEvidenceChanged(before: HealthSnapshot?, after: HealthSnapshot?): Boolean {
        if (after == null) return false
        if (before == null) return true
        return after.lastSyncAt != before.lastSyncAt ||
            after.originPackages != before.originPackages ||
            after.metrics != before.metrics
    }

    companion object {
        private const val POLL_MILLIS = 500L
        private const val HEALTH_CONNECT_POLL_MILLIS = 1_000L
        private const val USER_WAIT_MILLIS = 90_000L
        private const val AGENT_WAIT_MILLIS = 25_000L
        private const val HEALTH_CONNECT_WAIT_MILLIS = 15_000L
        private const val AGENT_COOLDOWN_MILLIS = 2 * 60_000L
    }
}
