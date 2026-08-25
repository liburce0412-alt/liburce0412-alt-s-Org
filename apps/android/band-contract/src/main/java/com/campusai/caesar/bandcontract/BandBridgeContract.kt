package com.campusai.caesar.bandcontract

import android.net.Uri

/**
 * Versioned, high-level IPC contract between CampusAI and CaesarBandBridge.
 *
 * This contract intentionally contains no Bluetooth address, pairing key, raw packet, or
 * Gadgetbridge database API. Nullable measurements mean "not observed" and must never be
 * converted to zero by a consumer.
 */
object BandBridgeContract {
    const val SCHEMA_VERSION = 1

    const val BRIDGE_PACKAGE = "com.campusai.caesar.bandbridge"
    const val READ_PERMISSION = "$BRIDGE_PACKAGE.permission.READ_LIVE"
    const val PROVIDER_AUTHORITY = "$BRIDGE_PACKAGE.live"
    const val SNAPSHOT_PATH = "snapshot"
    val SNAPSHOT_URI: Uri = Uri.parse("content://$PROVIDER_AUTHORITY/$SNAPSHOT_PATH")
    const val SNAPSHOT_MIME_TYPE = "vnd.android.cursor.item/vnd.$PROVIDER_AUTHORITY.snapshot"

    const val SERVICE_CLASS = "$BRIDGE_PACKAGE.BandBridgeService"
    const val ACTION_START_LIVE = "$BRIDGE_PACKAGE.action.START_LIVE"
    const val ACTION_STOP_LIVE = "$BRIDGE_PACKAGE.action.STOP_LIVE"
    const val ACTION_TRIGGER_HISTORY_SYNC = "$BRIDGE_PACKAGE.action.TRIGGER_HISTORY_SYNC"

    const val COL_SCHEMA_VERSION = "schema_version"
    const val COL_OBSERVED_AT = "observed_at"
    const val COL_CONNECTED = "connected"
    const val COL_BATTERY_PERCENT = "battery_percent"
    const val COL_CHARGING = "charging"
    const val COL_WEARING = "wearing"
    const val COL_SLEEPING = "sleeping"
    const val COL_HEART_RATE_BPM = "heart_rate_bpm"
    const val COL_STEP_DELTA = "step_delta"
    const val COL_CAPABILITY_BITS = "capability_bits"
    const val COL_BRIDGE_STATE = "bridge_state"
    const val COL_STATUS_MESSAGE = "status_message"
    const val COL_HISTORY_SYNC_STATE = "history_sync_state"
    const val COL_SOURCE = "source"

    val DEFAULT_PROJECTION = arrayOf(
        COL_SCHEMA_VERSION,
        COL_OBSERVED_AT,
        COL_CONNECTED,
        COL_BATTERY_PERCENT,
        COL_CHARGING,
        COL_WEARING,
        COL_SLEEPING,
        COL_HEART_RATE_BPM,
        COL_STEP_DELTA,
        COL_CAPABILITY_BITS,
        COL_BRIDGE_STATE,
        COL_STATUS_MESSAGE,
        COL_HISTORY_SYNC_STATE,
        COL_SOURCE,
    )

    object Capability {
        const val GADGETBRIDGE_INSTALLED = 1L shl 0
        const val GADGETBRIDGE_ACTIVITY_SYNC_TRIGGER = 1L shl 1
        const val GADGETBRIDGE_CONNECTED_EVENT = 1L shl 2
        const val GADGETBRIDGE_CONNECT_TRIGGER = 1L shl 3

        // Reserved for a future, separately verified Band 9 protocol adapter. Never set by the
        // Gadgetbridge Intent adapter because its public API does not expose these measurements.
        const val REALTIME_HEART_RATE = 1L shl 16
        const val REALTIME_STEPS = 1L shl 17
        const val BATTERY = 1L shl 18
        const val WEARING_STATE = 1L shl 19
        const val RAW_BAND9_PROTOCOL_VERIFIED = 1L shl 20
    }
}

enum class BridgeState {
    UNAVAILABLE,
    IDLE,
    LISTENING,
    ERROR,
}

enum class HistorySyncState {
    UNAVAILABLE,
    IDLE,
    CONNECTING,
    REQUESTED,
    FINISHED,
    ERROR,
}

data class BandBridgeSnapshot(
    val observedAt: Long,
    val connected: Boolean? = null,
    val batteryPercent: Int? = null,
    val charging: Boolean? = null,
    val wearing: Boolean? = null,
    val sleeping: Boolean? = null,
    val heartRateBpm: Int? = null,
    val stepDelta: Long? = null,
    val capabilityBits: Long = 0,
    val bridgeState: BridgeState = BridgeState.UNAVAILABLE,
    val statusMessage: String = "Band 9 实时协议尚未验证",
    val historySyncState: HistorySyncState = HistorySyncState.UNAVAILABLE,
    val source: String = "CaesarBandBridge",
)
