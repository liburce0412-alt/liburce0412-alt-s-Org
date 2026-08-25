package com.campusai.core.health

import android.content.Context
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build

class BandLiveProviderGateway(context: Context) : BandLiveGateway {
    private val appContext = context.applicationContext

    override suspend fun snapshot(): Result<BandLiveSnapshot> = runCatching {
        appContext.contentResolver.query(LIVE_URI, COLUMNS, null, null, null)?.use { cursor ->
            check(cursor.moveToFirst()) { "CaesarBandBridge 暂无实时数据" }
            BandLiveSnapshot(
                observedAt = cursor.getLong(cursor.getColumnIndexOrThrow("observed_at")),
                connected = cursor.optionalBoolean("connected"),
                batteryPercent = cursor.optionalInt("battery_percent"),
                charging = cursor.optionalBoolean("charging"),
                wearing = cursor.optionalBoolean("wearing"),
                sleeping = cursor.optionalBoolean("sleeping"),
                heartRateBpm = cursor.optionalInt("heart_rate_bpm"),
                stepDelta = cursor.optionalLong("step_delta"),
                capabilityBits = cursor.getLong(cursor.getColumnIndexOrThrow("capability_bits")),
                bridgeState = cursor.optionalString("bridge_state")
                    ?.let { runCatching { BandLiveState.valueOf(it) }.getOrNull() }
                    ?: BandLiveState.UNAVAILABLE,
                statusMessage = cursor.optionalString("status_message"),
                historySyncState = cursor.optionalString("history_sync_state")
                    ?.let { runCatching { BandHistorySyncState.valueOf(it) }.getOrNull() }
                    ?: BandHistorySyncState.UNAVAILABLE,
                source = cursor.optionalString("source") ?: "CaesarBandBridge",
            )
        } ?: error(
            if (isBridgeInstalled()) {
                "CaesarBandBridge 已安装；请先打开桥接诊断完成系统启动授权"
            } else {
                "尚未安装 CaesarBandBridge"
            },
        )
    }

    override fun startSession(): Result<Unit> = send(ACTION_START)
    override fun stopSession(): Result<Unit> = send(ACTION_STOP)
    override fun triggerHistorySync(): Result<Unit> = send(ACTION_TRIGGER_HISTORY_SYNC)

    private fun send(action: String): Result<Unit> = runCatching {
        val intent = Intent(action).setComponent(ComponentName(BRIDGE_PACKAGE, BRIDGE_SERVICE_CLASS))
        if (
            action in setOf(ACTION_START, ACTION_TRIGGER_HISTORY_SYNC) &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
        ) {
            appContext.startForegroundService(intent)
        } else {
            appContext.startService(intent)
        }
        Unit
    }

    private fun isBridgeInstalled(): Boolean = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appContext.packageManager.getPackageInfo(
                BRIDGE_PACKAGE,
                PackageManager.PackageInfoFlags.of(0),
            )
        } else {
            @Suppress("DEPRECATION")
            appContext.packageManager.getPackageInfo(BRIDGE_PACKAGE, 0)
        }
    }.isSuccess

    private fun android.database.Cursor.optionalInt(name: String): Int? {
        val index = getColumnIndex(name)
        return if (index < 0 || isNull(index)) null else getInt(index)
    }

    private fun android.database.Cursor.optionalLong(name: String): Long? {
        val index = getColumnIndex(name)
        return if (index < 0 || isNull(index)) null else getLong(index)
    }

    private fun android.database.Cursor.optionalBoolean(name: String): Boolean? = optionalInt(name)?.let { it != 0 }

    private fun android.database.Cursor.optionalString(name: String): String? {
        val index = getColumnIndex(name)
        return if (index < 0 || isNull(index)) null else getString(index)
    }

    companion object {
        const val BRIDGE_PACKAGE = "com.campusai.caesar.bandbridge"
        const val ACTION_START = "$BRIDGE_PACKAGE.action.START_LIVE"
        const val ACTION_STOP = "$BRIDGE_PACKAGE.action.STOP_LIVE"
        const val ACTION_TRIGGER_HISTORY_SYNC = "$BRIDGE_PACKAGE.action.TRIGGER_HISTORY_SYNC"
        const val BRIDGE_SERVICE_CLASS = "$BRIDGE_PACKAGE.BandBridgeService"
        const val BRIDGE_DIAGNOSTICS_CLASS = "$BRIDGE_PACKAGE.BandBridgeDiagnosticsActivity"
        val LIVE_URI: Uri = Uri.parse("content://$BRIDGE_PACKAGE.live/snapshot")
        val COLUMNS = arrayOf(
            "observed_at",
            "connected",
            "battery_percent",
            "charging",
            "wearing",
            "sleeping",
            "heart_rate_bpm",
            "step_delta",
            "capability_bits",
            "bridge_state",
            "status_message",
            "history_sync_state",
            "source",
        )
    }
}
