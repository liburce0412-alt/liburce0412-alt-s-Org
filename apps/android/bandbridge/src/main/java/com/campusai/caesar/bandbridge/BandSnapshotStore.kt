package com.campusai.caesar.bandbridge

import android.content.Context
import android.content.SharedPreferences
import com.campusai.caesar.bandcontract.BandBridgeContract
import com.campusai.caesar.bandcontract.BandBridgeSnapshot
import com.campusai.caesar.bandcontract.BridgeState
import com.campusai.caesar.bandcontract.HistorySyncState

/** Persists only high-level state. Pairing material is owned by [PairingTokenVault]. */
internal class BandSnapshotStore private constructor(private val context: Context) {
    private val preferences: SharedPreferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun snapshot(): BandBridgeSnapshot = load()

    @Synchronized
    fun publish(snapshot: BandBridgeSnapshot) {
        persist(snapshot.sanitized())
        context.contentResolver.notifyChange(BandBridgeContract.SNAPSHOT_URI, null)
    }

    private fun BandBridgeSnapshot.sanitized(): BandBridgeSnapshot = copy(
        batteryPercent = batteryPercent?.coerceIn(0, 100),
        heartRateBpm = heartRateBpm?.takeIf { it in 20..260 },
        stepDelta = stepDelta?.takeIf { it >= 0 },
        statusMessage = statusMessage.take(MAX_STATUS_LENGTH),
    )

    private fun persist(value: BandBridgeSnapshot) {
        preferences.edit()
            .putLong(KEY_OBSERVED_AT, value.observedAt)
            .putNullableBoolean(KEY_CONNECTED, value.connected)
            .putNullableInt(KEY_BATTERY, value.batteryPercent)
            .putNullableBoolean(KEY_CHARGING, value.charging)
            .putNullableBoolean(KEY_WEARING, value.wearing)
            .putNullableBoolean(KEY_SLEEPING, value.sleeping)
            .putNullableInt(KEY_HEART_RATE, value.heartRateBpm)
            .putNullableLong(KEY_STEP_DELTA, value.stepDelta)
            .putLong(KEY_CAPABILITIES, value.capabilityBits)
            .putString(KEY_BRIDGE_STATE, value.bridgeState.name)
            .putString(KEY_STATUS, value.statusMessage)
            .putString(KEY_HISTORY_STATE, value.historySyncState.name)
            .putString(KEY_SOURCE, value.source)
            .apply()
    }

    private fun load(): BandBridgeSnapshot = BandBridgeSnapshot(
        observedAt = preferences.getLong(KEY_OBSERVED_AT, System.currentTimeMillis()),
        connected = preferences.nullableBoolean(KEY_CONNECTED),
        batteryPercent = preferences.nullableInt(KEY_BATTERY),
        charging = preferences.nullableBoolean(KEY_CHARGING),
        wearing = preferences.nullableBoolean(KEY_WEARING),
        sleeping = preferences.nullableBoolean(KEY_SLEEPING),
        heartRateBpm = preferences.nullableInt(KEY_HEART_RATE),
        stepDelta = preferences.nullableLong(KEY_STEP_DELTA),
        capabilityBits = preferences.getLong(KEY_CAPABILITIES, 0L),
        bridgeState = preferences.getString(KEY_BRIDGE_STATE, null)
            ?.let { runCatching { BridgeState.valueOf(it) }.getOrNull() }
            ?: BridgeState.UNAVAILABLE,
        statusMessage = preferences.getString(KEY_STATUS, null)
            ?: "Bridge 尚未启动；Band 9 实时协议为 Unavailable",
        historySyncState = preferences.getString(KEY_HISTORY_STATE, null)
            ?.let { runCatching { HistorySyncState.valueOf(it) }.getOrNull() }
            ?: HistorySyncState.UNAVAILABLE,
        source = preferences.getString(KEY_SOURCE, null) ?: "CaesarBandBridge",
    )

    private fun SharedPreferences.Editor.putNullableBoolean(key: String, value: Boolean?) = apply {
        if (value == null) remove(key) else putBoolean(key, value)
    }

    private fun SharedPreferences.Editor.putNullableInt(key: String, value: Int?) = apply {
        if (value == null) remove(key) else putInt(key, value)
    }

    private fun SharedPreferences.Editor.putNullableLong(key: String, value: Long?) = apply {
        if (value == null) remove(key) else putLong(key, value)
    }

    private fun SharedPreferences.nullableBoolean(key: String): Boolean? =
        if (contains(key)) getBoolean(key, false) else null

    private fun SharedPreferences.nullableInt(key: String): Int? =
        if (contains(key)) getInt(key, 0) else null

    private fun SharedPreferences.nullableLong(key: String): Long? =
        if (contains(key)) getLong(key, 0L) else null

    companion object {
        private const val PREFS = "band_snapshot_v1"
        private const val MAX_STATUS_LENGTH = 300
        private const val KEY_OBSERVED_AT = "observed_at"
        private const val KEY_CONNECTED = "connected"
        private const val KEY_BATTERY = "battery"
        private const val KEY_CHARGING = "charging"
        private const val KEY_WEARING = "wearing"
        private const val KEY_SLEEPING = "sleeping"
        private const val KEY_HEART_RATE = "heart_rate"
        private const val KEY_STEP_DELTA = "step_delta"
        private const val KEY_CAPABILITIES = "capabilities"
        private const val KEY_BRIDGE_STATE = "bridge_state"
        private const val KEY_STATUS = "status"
        private const val KEY_HISTORY_STATE = "history_state"
        private const val KEY_SOURCE = "source"

        fun get(context: Context): BandSnapshotStore = BandSnapshotStore(context.applicationContext)
    }
}
