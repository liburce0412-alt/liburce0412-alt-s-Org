package com.campusai.caesar.bandbridge

import com.campusai.caesar.bandcontract.BandBridgeSnapshot
import com.campusai.caesar.bandcontract.BridgeState
import com.campusai.caesar.bandcontract.HistorySyncState

/**
 * Safe development fallback. It deliberately publishes no measurement and no capability bit.
 * A verified Band 9 protocol implementation must replace this adapter before live metrics exist.
 */
internal class FakeBand9Adapter : BandAdapter {
    override val id: String = "band9-protocol-unavailable"

    override fun isAvailable(): Boolean = false

    override fun initialSnapshot(nowMillis: Long): BandBridgeSnapshot = BandBridgeSnapshot(
        observedAt = nowMillis,
        connected = null,
        capabilityBits = 0L,
        bridgeState = BridgeState.UNAVAILABLE,
        statusMessage = "Band 9 实时协议尚未通过真机验证；未生成心率、步数或连接状态",
        historySyncState = HistorySyncState.UNAVAILABLE,
        source = "FakeBand9Adapter(Unavailable)",
    )

    override fun start(listener: (BandBridgeSnapshot) -> Unit): Result<Unit> =
        Result.failure(UnsupportedOperationException("Band 9 实时协议尚未验证"))

    override fun stop() = Unit

    override fun triggerHistorySync(): Result<BandBridgeSnapshot> =
        Result.failure(UnsupportedOperationException("未安装可用的 Gadgetbridge Intent API"))
}
