package com.campusai

import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Explicit Xiaomi/Band acceptance probe. It is skipped in ordinary instrumentation runs and
 * never logs metric values or raw health samples. Run with `caesar.bandProbe=true` only after
 * the owner has granted the requested Health Connect permissions.
 */
@RunWith(AndroidJUnit4::class)
class HealthConnectBandProbeTest {
    @Test
    fun gadgetbridgeRecordsAreVisibleToCaesar() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assumeTrue(InstrumentationRegistry.getArguments().getString("caesar.bandProbe") == "true")
        val context = instrumentation.targetContext
        assumeTrue(HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE)
        val client = HealthConnectClient.getOrCreate(context)
        val end = Instant.now()
        val timeRange = TimeRangeFilter.between(end.minusSeconds(7 * 24 * 60 * 60), end)

        val summaries = listOf(
            readSummary<StepsRecord>(client, timeRange, "steps"),
            readSummary<DistanceRecord>(client, timeRange, "distance"),
            readSummary<ActiveCaloriesBurnedRecord>(client, timeRange, "activeCalories"),
            readSummary<HeartRateRecord>(client, timeRange, "heartRate"),
            readSummary<RestingHeartRateRecord>(client, timeRange, "restingHeartRate"),
            readSummary<OxygenSaturationRecord>(client, timeRange, "oxygenSaturation"),
            readSummary<SleepSessionRecord>(client, timeRange, "sleep"),
            readSummary<ExerciseSessionRecord>(client, timeRange, "exercise"),
        )
        summaries.forEach { Log.i(TAG, it.toLogLine()) }
        assertTrue(
            "Health Connect has no Gadgetbridge records in the last seven days: $summaries",
            summaries.any { summary -> summary.count > 0 && summary.origins.any(GADGETBRIDGE_PACKAGES::contains) },
        )
        val steps = summaries.single { it.type == "steps" }
        assertTrue(
            "Health Connect has no Gadgetbridge StepsRecord in the last seven days: $steps",
            steps.count > 0 && steps.origins.any(GADGETBRIDGE_PACKAGES::contains),
        )
    }

    private suspend inline fun <reified T : Record> readSummary(
        client: HealthConnectClient,
        timeRange: TimeRangeFilter,
        type: String,
    ): RecordSummary {
        val records = client.readRecords(
            ReadRecordsRequest(
                recordType = T::class,
                timeRangeFilter = timeRange,
                ascendingOrder = false,
                pageSize = 1_000,
            ),
        ).records
        return RecordSummary(
            type = type,
            count = records.size,
            origins = records.map { it.metadata.dataOrigin.packageName }.filter(String::isNotBlank).toSet(),
            lastModifiedAtMillis = records.maxOfOrNull { it.metadata.lastModifiedTime.toEpochMilli() },
        )
    }

    private data class RecordSummary(
        val type: String,
        val count: Int,
        val origins: Set<String>,
        val lastModifiedAtMillis: Long?,
    ) {
        fun toLogLine(): String =
            "type=$type count=$count origins=${origins.sorted()} lastModifiedAt=$lastModifiedAtMillis"
    }

    private companion object {
        const val TAG = "CaesarHealthProbe"
        val GADGETBRIDGE_PACKAGES = setOf(
            "nodomain.freeyourgadget.gadgetbridge",
            "nodomain.freeyourgadget.gadgetbridge.nightly",
            "nodomain.freeyourgadget.gadgetbridge.nightly_nopebble",
        )
    }
}
