package com.campusai.caesar.bandbridge

import com.campusai.caesar.bandcontract.BandBridgeContract
import com.campusai.caesar.bandcontract.BridgeState
import com.campusai.caesar.bandcontract.HistorySyncState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class FakeBand9AdapterTest {
    @Test
    fun unavailableAdapterNeverClaimsBandData() {
        val snapshot = FakeBand9Adapter().initialSnapshot(42L)

        assertEquals(BridgeState.UNAVAILABLE, snapshot.bridgeState)
        assertNull(snapshot.connected)
        assertNull(snapshot.heartRateBpm)
        assertEquals(0L, snapshot.capabilityBits)
        assertFalse(snapshot.capabilityBits and BandBridgeContract.Capability.RAW_BAND9_PROTOCOL_VERIFIED != 0L)
    }

    @Test
    fun documentedIntentConstantsAreNarrowAndExplicit() {
        assertEquals(
            "nodomain.freeyourgadget.gadgetbridge.command.ACTIVITY_SYNC",
            GadgetbridgeIntentAdapter.ACTION_ACTIVITY_SYNC,
        )
        assertEquals(
            "nodomain.freeyourgadget.gadgetbridge.action.ACTIVITY_SYNC_FINISH",
            GadgetbridgeIntentAdapter.ACTION_ACTIVITY_SYNC_FINISH,
        )
        assertEquals(
            "nodomain.freeyourgadget.gadgetbridge.BLUETOOTH_CONNECT",
            GadgetbridgeIntentAdapter.ACTION_BLUETOOTH_CONNECT,
        )
        assertEquals("EXTRA_DEVICE_ADDRESS", GadgetbridgeIntentAdapter.EXTRA_DEVICE_ADDRESS)
    }

    @Test
    fun band9AddressDiscoveryIsNarrowAndRejectsMalformedAddresses() {
        assertEquals(true, isSupportedBand9Name("Xiaomi Smart Band 9"))
        assertEquals(true, isSupportedBand9Name("Mi Band 9 A1B2"))
        assertEquals(false, isSupportedBand9Name("Xiaomi Buds 5"))
        assertEquals(true, isBluetoothAddress("AA:BB:CC:DD:EE:FF"))
        assertEquals(false, isBluetoothAddress("AA:BB:CC:DD:EE"))
    }

    @Test
    fun completionAcceptsVerifiedInstalledSender() {
        assertEquals(
            GadgetbridgeBroadcastEvidence.VERIFIED_SENDER,
            gadgetbridgeBroadcastEvidence(
                isHistoryCompletion = true,
                senderIdentityAvailable = true,
                senderMatchesInstalledPackage = true,
                signaturePermissionGateActive = false,
                historySyncState = HistorySyncState.IDLE,
                lastHistoryRequestElapsedMillis = null,
                nowElapsedMillis = 10_000L,
            ),
        )
    }

    @Test
    fun completionUsesValidatedSignatureGateWhenSenderIdentityIsNotShared() {
        assertEquals(
            GadgetbridgeBroadcastEvidence.SIGNATURE_PERMISSION_GATED,
            gadgetbridgeBroadcastEvidence(
                isHistoryCompletion = true,
                senderIdentityAvailable = false,
                senderMatchesInstalledPackage = false,
                signaturePermissionGateActive = true,
                historySyncState = HistorySyncState.REQUESTED,
                lastHistoryRequestElapsedMillis = 10_000L,
                nowElapsedMillis = 11_000L,
            ),
        )
    }

    @Test
    fun completionCorrelatesMissingSenderIdentityOnlyToPendingRequestWithoutSignatureGate() {
        assertEquals(
            GadgetbridgeBroadcastEvidence.CORRELATED_REQUEST,
            gadgetbridgeBroadcastEvidence(
                isHistoryCompletion = true,
                senderIdentityAvailable = false,
                senderMatchesInstalledPackage = false,
                signaturePermissionGateActive = false,
                historySyncState = HistorySyncState.REQUESTED,
                lastHistoryRequestElapsedMillis = 10_000L,
                nowElapsedMillis = 11_000L,
            ),
        )
    }

    @Test
    fun unverifiedBroadcastCannotClaimConnectionOrUnrequestedCompletion() {
        assertNull(
            gadgetbridgeBroadcastEvidence(
                isHistoryCompletion = false,
                senderIdentityAvailable = false,
                senderMatchesInstalledPackage = false,
                signaturePermissionGateActive = false,
                historySyncState = HistorySyncState.REQUESTED,
                lastHistoryRequestElapsedMillis = 10_000L,
                nowElapsedMillis = 11_000L,
            ),
        )
        assertNull(
            gadgetbridgeBroadcastEvidence(
                isHistoryCompletion = true,
                senderIdentityAvailable = false,
                senderMatchesInstalledPackage = false,
                signaturePermissionGateActive = true,
                historySyncState = HistorySyncState.IDLE,
                lastHistoryRequestElapsedMillis = 10_000L,
                nowElapsedMillis = 11_000L,
            ),
        )
    }

    @Test
    fun unverifiedCompletionExpiresAndWrongKnownUidIsRejected() {
        assertNull(
            gadgetbridgeBroadcastEvidence(
                isHistoryCompletion = true,
                senderIdentityAvailable = false,
                senderMatchesInstalledPackage = false,
                signaturePermissionGateActive = true,
                historySyncState = HistorySyncState.REQUESTED,
                lastHistoryRequestElapsedMillis = 10_000L,
                nowElapsedMillis = 130_001L,
            ),
        )
        assertNull(
            gadgetbridgeBroadcastEvidence(
                isHistoryCompletion = true,
                senderIdentityAvailable = true,
                senderMatchesInstalledPackage = false,
                signaturePermissionGateActive = true,
                historySyncState = HistorySyncState.REQUESTED,
                lastHistoryRequestElapsedMillis = 10_000L,
                nowElapsedMillis = 11_000L,
            ),
        )
    }
}
