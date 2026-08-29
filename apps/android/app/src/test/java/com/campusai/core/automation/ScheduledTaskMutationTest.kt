package com.campusai.core.automation

import com.campusai.core.ai.CloudAiProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ScheduledTaskMutationTest {
    @Test
    fun settingsWriterRereadsAfterCasConflictAndKeepsLatestRunnerFields() = runTest {
        val initial = task().copy(lastStatus = ScheduledTaskRunStatus.RUNNING)
        val latest = initial.copy(
            lastAttemptAt = 2_000L,
            lastSourceRevision = "b".repeat(64),
            lastStatus = ScheduledTaskRunStatus.UPDATED,
        )
        val store = RacingStore(initial, latest)

        val mutation = store.mutateWithRetry(HealthTaskDefaults.TASK_ID) { current ->
            mergeEnabledHealthTaskConfig(
                current = current,
                provider = CloudAiProvider.GOOGLE_GEMINI,
                modelId = "gemini-2.5-flash",
                intervalMinutes = 10,
                includeHealthSummary = true,
            )
        }.getOrThrow()

        assertEquals(latest, mutation.previous)
        assertEquals(10, store.config.intervalMinutes)
        assertEquals(2_000L, store.config.lastAttemptAt)
        assertEquals("b".repeat(64), store.config.lastSourceRevision)
        assertEquals(ScheduledTaskRunStatus.UPDATED, store.config.lastStatus)
    }

    @Test
    fun disableWriterRereadsAfterCasConflictWithoutRestoringStaleRevision() = runTest {
        val initial = task().copy(lastStatus = ScheduledTaskRunStatus.RUNNING)
        val latest = initial.copy(
            lastAttemptAt = 2_000L,
            lastSourceRevision = "c".repeat(64),
            lastStatus = ScheduledTaskRunStatus.UPDATED,
        )
        val store = RacingStore(initial, latest)

        store.mutateWithRetry(
            HealthTaskDefaults.TASK_ID,
            transform = ::mergeDisabledHealthTaskConfig,
        ).getOrThrow()

        assertFalse(store.config.enabled)
        assertEquals(2_000L, store.config.lastAttemptAt)
        assertEquals("c".repeat(64), store.config.lastSourceRevision)
        assertEquals(ScheduledTaskRunStatus.IDLE, store.config.lastStatus)
    }

    private fun task() = ScheduledTaskConfig(
        id = HealthTaskDefaults.TASK_ID,
        type = ScheduledTaskType.HEALTH_CLOUD_STATUS,
        enabled = true,
        intervalMinutes = 5,
        provider = CloudAiProvider.GOOGLE_GEMINI,
        modelId = "gemini-2.5-flash",
        includeHealthSummary = true,
        lastAttemptAt = 1_000L,
        lastSourceRevision = "a".repeat(64),
    )

    private class RacingStore(
        initial: ScheduledTaskConfig,
        private val concurrentUpdate: ScheduledTaskConfig,
    ) : ScheduledTaskStore {
        var config = initial
        private var injectConflict = true

        override suspend fun read(taskId: String): ScheduledTaskConfig? = config.takeIf { it.id == taskId }

        override suspend fun readAll(): List<ScheduledTaskConfig> = listOf(config)

        override suspend fun save(config: ScheduledTaskConfig): Result<Unit> {
            this.config = config
            return Result.success(Unit)
        }

        override suspend fun saveIfCurrent(
            expected: ScheduledTaskConfig?,
            updated: ScheduledTaskConfig,
        ): Result<Boolean> {
            if (injectConflict) {
                injectConflict = false
                config = concurrentUpdate
                return Result.success(false)
            }
            if (config != expected) return Result.success(false)
            config = updated
            return Result.success(true)
        }

        override suspend fun delete(taskId: String): Result<Unit> = Result.success(Unit)
    }
}
