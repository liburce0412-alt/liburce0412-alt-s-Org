package com.campusai.core.health.mifitness

import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MiFitnessStepsSyncWorkerTest {
    @Test
    fun `manual scheduler enqueues one unique immediate request with empty input`() {
        var name: String? = null
        var policy: ExistingWorkPolicy? = null
        var request: OneTimeWorkRequest? = null

        val enqueuedId = MiFitnessStepsSyncScheduler.enqueue(
            MiFitnessUniqueWorkEnqueuer { capturedName, capturedPolicy, capturedRequest ->
                name = capturedName
                policy = capturedPolicy
                request = capturedRequest
            },
        )

        val captured = checkNotNull(request)
        assertEquals(captured.id, enqueuedId)
        assertEquals(MiFitnessStepsSyncScheduler.UNIQUE_WORK, name)
        assertEquals(ExistingWorkPolicy.REPLACE, policy)
        assertEquals(NetworkType.NOT_REQUIRED, captured.workSpec.constraints.requiredNetworkType)
        assertEquals(0, captured.workSpec.input.size())
        assertTrue(captured.tags.contains(MiFitnessStepsSyncWorker::class.java.name))
    }

    @Test
    fun `worker failure data contains only allowlisted error code`() {
        val known = MiFitnessStepsSyncWorker.failureData(
            MiFitnessStepsSyncException("network_failed", "safe"),
        )
        val noData = MiFitnessStepsSyncWorker.failureData(
            MiFitnessStepsSyncException("no_cloud_data", "safe"),
        )
        val unknown = MiFitnessStepsSyncWorker.failureData(
            IllegalStateException("synthetic-pass-token raw-response"),
        )

        assertEquals(mapOf("error_code" to "network_failed"), known.keyValueMap)
        assertEquals(mapOf("error_code" to "no_cloud_data"), noData.keyValueMap)
        assertEquals(mapOf("error_code" to "sync_failed"), unknown.keyValueMap)
        assertFalse(unknown.toString().contains("synthetic-pass-token"))
        assertFalse(unknown.toString().contains("raw-response"))
    }
}
