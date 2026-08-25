package com.campusai.caesar.bandbridge

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.content.pm.PackageManager
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Binder
import android.os.Process
import com.campusai.caesar.bandcontract.BandBridgeContract
import com.campusai.caesar.bandcontract.BandBridgeSnapshot

class BandSnapshotProvider : ContentProvider() {
    override fun onCreate(): Boolean = context != null

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        enforceSameSignatureCaller()
        require(MATCHER.match(uri) == MATCH_SNAPSHOT) { "Unsupported URI" }
        require(selection == null && selectionArgs == null && sortOrder == null) {
            "Selection and sorting are not supported"
        }
        val columns = projection?.map { it }?.toTypedArray() ?: BandBridgeContract.DEFAULT_PROJECTION
        require(columns.all(ALLOWED_COLUMNS::contains)) { "Unknown snapshot column" }
        val snapshot = BandSnapshotStore.get(providerContext()).snapshot()
        return MatrixCursor(columns, 1).apply {
            addRow(columns.map { snapshot.valueFor(it) })
            setNotificationUri(providerContext().contentResolver, BandBridgeContract.SNAPSHOT_URI)
        }
    }

    override fun getType(uri: Uri): String {
        enforceSameSignatureCaller()
        require(MATCHER.match(uri) == MATCH_SNAPSHOT) { "Unsupported URI" }
        return BandBridgeContract.SNAPSHOT_MIME_TYPE
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri = readOnly()
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = readOnly()
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = readOnly()

    private fun enforceSameSignatureCaller() {
        val callingUid = Binder.getCallingUid()
        if (callingUid == Process.myUid()) return
        val result = providerContext().packageManager.checkSignatures(callingUid, Process.myUid())
        if (result != PackageManager.SIGNATURE_MATCH) {
            throw SecurityException("CaesarBandBridge accepts only same-signature callers")
        }
    }

    private fun <T> readOnly(): T {
        enforceSameSignatureCaller()
        throw UnsupportedOperationException("Band snapshot provider is read-only")
    }

    private fun providerContext() = checkNotNull(context)

    private fun BandBridgeSnapshot.valueFor(column: String): Any? = when (column) {
        BandBridgeContract.COL_SCHEMA_VERSION -> BandBridgeContract.SCHEMA_VERSION
        BandBridgeContract.COL_OBSERVED_AT -> observedAt
        BandBridgeContract.COL_CONNECTED -> connected?.toInt()
        BandBridgeContract.COL_BATTERY_PERCENT -> batteryPercent
        BandBridgeContract.COL_CHARGING -> charging?.toInt()
        BandBridgeContract.COL_WEARING -> wearing?.toInt()
        BandBridgeContract.COL_SLEEPING -> sleeping?.toInt()
        BandBridgeContract.COL_HEART_RATE_BPM -> heartRateBpm
        BandBridgeContract.COL_STEP_DELTA -> stepDelta
        BandBridgeContract.COL_CAPABILITY_BITS -> capabilityBits
        BandBridgeContract.COL_BRIDGE_STATE -> bridgeState.name
        BandBridgeContract.COL_STATUS_MESSAGE -> statusMessage
        BandBridgeContract.COL_HISTORY_SYNC_STATE -> historySyncState.name
        BandBridgeContract.COL_SOURCE -> source
        else -> error("Unsupported column")
    }

    private fun Boolean.toInt(): Int = if (this) 1 else 0

    companion object {
        private const val MATCH_SNAPSHOT = 1
        private val MATCHER = UriMatcher(UriMatcher.NO_MATCH).apply {
            addURI(BandBridgeContract.PROVIDER_AUTHORITY, BandBridgeContract.SNAPSHOT_PATH, MATCH_SNAPSHOT)
        }
        private val ALLOWED_COLUMNS = BandBridgeContract.DEFAULT_PROJECTION.toSet()
    }
}
