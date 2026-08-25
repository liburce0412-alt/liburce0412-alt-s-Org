package com.campusai.caesar.bandbridge

import com.campusai.caesar.bandcontract.BandBridgeSnapshot

/** High-level adapter boundary. Raw Bluetooth commands and pairing secrets never cross it. */
internal interface BandAdapter {
    val id: String
    fun isAvailable(): Boolean
    fun initialSnapshot(nowMillis: Long = System.currentTimeMillis()): BandBridgeSnapshot
    fun start(listener: (BandBridgeSnapshot) -> Unit): Result<Unit>
    fun stop()
    fun triggerHistorySync(): Result<BandBridgeSnapshot>
}
