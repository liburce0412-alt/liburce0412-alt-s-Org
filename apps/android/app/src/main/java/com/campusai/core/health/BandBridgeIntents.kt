package com.campusai.core.health

import android.content.ComponentName
import android.content.Intent

/** Stable, explicit entry points for deterministic Bridge status UI. */
object BandBridgeIntents {
    fun diagnostics(): Intent = Intent().setComponent(
        ComponentName(
            BandLiveProviderGateway.BRIDGE_PACKAGE,
            BandLiveProviderGateway.BRIDGE_DIAGNOSTICS_CLASS,
        ),
    )
}
