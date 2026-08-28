package com.campusai.core.health.mifitness

import com.campusai.core.health.HealthMetricKey
import com.campusai.core.health.HealthMetricStatus
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
class MiFitnessAccountServiceTest {
    @Test
    fun `validateAndSave caches verified summary before saving refreshed token`() = runTest {
        val fixture = fixture()
        fixture.transport.response = page(fixture.window.startEpochSeconds, 3_210L)
        fixture.storage.writes.clear()

        val summary = fixture.account.validateAndSave("  22222  ", "  candidate-token  ").getOrThrow()

        assertEquals(3_210L, summary.steps)
        assertEquals("22222", fixture.store.read()?.userId)
        assertEquals("refreshed-token", fixture.store.read()?.passToken)
        assertEquals(2, fixture.storage.writes.size)
        assertTrue(fixture.storage.writes[0].second.contains("\"steps\":3210"))
        assertTrue(fixture.storage.writes[1].second.contains("\"passToken\":\"refreshed-token\""))
        assertFalse(fixture.storage.writes[1].second.contains("candidate-token"))
        assertFalse(fixture.storage.writes.joinToString().contains("data_list"))
    }

    @Test
    fun `credential write failure restores old credentials and visible cache`() = runTest {
        val fixture = fixture(withOldState = true)
        fixture.transport.response = page(fixture.window.startEpochSeconds, 3_210L)
        fixture.storage.rejectCredentialWrites = true
        fixture.storage.writes.clear()

        val result = fixture.account.validateAndSave("22222", "candidate-token")

        assertTrue(result.isFailure)
        assertEquals("11111", fixture.store.read()?.userId)
        assertEquals("old-token", fixture.store.read()?.passToken)
        val restored = fixture.cache.read(
            fixture.window.period,
            fixture.window.localDate,
            checkNotNull(fixture.oldScope),
        )
        assertEquals(42L, restored?.steps)
        assertFalse(result.exceptionOrNull().toString().contains("candidate-token"))
    }

    @Test
    fun `valid credentials save an explicit empty metric snapshot without caching zero`() = runTest {
        val fixture = fixture()
        fixture.transport.response = emptyPage()
        fixture.storage.writes.clear()

        val summary = fixture.account.validateAndSave("22222", "candidate-token").getOrThrow()

        assertNull(summary.steps)
        assertEquals(HealthMetricStatus.EMPTY, summary.metricValues[HealthMetricKey.STEPS]?.status)
        val saved = checkNotNull(fixture.store.read())
        assertEquals("22222", saved.userId)
        assertEquals("refreshed-token", saved.passToken)
        assertEquals(summary, fixture.cache.read(fixture.window.period, fixture.window.localDate, saved.accountScope))
        assertEquals(2, fixture.storage.writes.size)
        assertTrue(fixture.storage.writes.last().second.contains("\"passToken\":\"refreshed-token\""))
    }

    @Test
    fun `empty cloud result and rejected credential write leave the previous cache untouched`() = runTest {
        val fixture = fixture(withOldState = true)
        fixture.transport.response = emptyPage()
        fixture.storage.rejectCredentialWrites = true
        fixture.storage.writes.clear()

        val result = fixture.account.validateAndSave("22222", "candidate-token")

        assertEquals("credential_write_failed", (result.exceptionOrNull() as MiFitnessAccountException).code)
        assertEquals("11111", fixture.store.read()?.userId)
        assertEquals(
            42L,
            fixture.cache.read(
                fixture.window.period,
                fixture.window.localDate,
                checkNotNull(fixture.oldScope),
            )?.steps,
        )
        assertEquals(3, fixture.storage.writes.size)
    }

    @Test
    fun `validation and network failure leave old state untouched`() = runTest {
        val fixture = fixture(withOldState = true)
        fixture.storage.writes.clear()

        val invalid = fixture.account.validateAndSave("not-an-id", "candidate-token")
        assertEquals("invalid_credentials", (invalid.exceptionOrNull() as MiFitnessAccountException).code)
        assertEquals(0, fixture.transport.exchangeCount)
        assertTrue(fixture.storage.writes.isEmpty())

        fixture.transport.failure = IllegalStateException("candidate-token raw-response")
        val failed = fixture.account.validateAndSave("22222", "candidate-token")
        assertTrue(failed.isFailure)
        assertEquals("11111", fixture.store.read()?.userId)
        assertEquals(
            42L,
            fixture.cache.read(
                fixture.window.period,
                fixture.window.localDate,
                checkNotNull(fixture.oldScope),
            )?.steps,
        )
        assertTrue(fixture.storage.writes.isEmpty())
        assertFalse(failed.exceptionOrNull().toString().contains("raw-response"))
    }

    @Test
    fun `delete clears credentials and aggregate cache`() = runTest {
        val fixture = fixture(withOldState = true)

        assertTrue(fixture.account.delete().isSuccess)

        assertNull(fixture.store.read())
        assertNull(
            fixture.cache.read(
                fixture.window.period,
                fixture.window.localDate,
                checkNotNull(fixture.oldScope),
            ),
        )
    }

    private fun fixture(withOldState: Boolean = false): Fixture {
        val storage = AccountStorage()
        val store = MiFitnessCredentialStore(storage)
        val cache = MiFitnessStepsCache(storage)
        val transport = AccountTransport()
        val sync = MiFitnessStepsSyncService(store, transport, cache, FIXED_CLOCK, ZoneOffset.ofHours(8))
        val account = MiFitnessAccountService(store, cache, sync)
        val window = sync.todayWindow()
        var oldScope: String? = null
        if (withOldState) {
            store.save("11111", "old-token")
            oldScope = checkNotNull(store.read()).accountScope
            cache.save(
                MiFitnessStepsSummary(
                    period = window.period,
                    localDate = window.localDate,
                    accountScope = checkNotNull(oldScope),
                    steps = 42L,
                    recordCount = 1,
                    observedAt = FIXED_CLOCK.millis(),
                    lastSyncAt = FIXED_CLOCK.millis(),
                ),
            )
        }
        return Fixture(storage, store, cache, transport, sync, account, window, oldScope)
    }

    private data class Fixture(
        val storage: AccountStorage,
        val store: MiFitnessCredentialStore,
        val cache: MiFitnessStepsCache,
        val transport: AccountTransport,
        val sync: MiFitnessStepsSyncService,
        val account: MiFitnessAccountService,
        val window: MiFitnessCnDayWindow,
        val oldScope: String?,
    )

    private class AccountTransport : MiFitnessStepsTransport {
        var exchangeCount = 0
        var response = ""
        var failure: RuntimeException? = null

        override suspend fun exchangePassToken(credential: MiFitnessCredential): MiFitnessSession {
            exchangeCount += 1
            failure?.let { throw it }
            return MiFitnessSession(
                userId = credential.userId,
                cUserId = "synthetic-c-user",
                serviceToken = "synthetic-service-token",
                ssecurityBase64 = "c3NlY3VyaXR5LXZlY3Rvcg==",
                deviceId = "synthetic-device",
                tokenRefreshed = true,
                refreshedPassToken = "refreshed-token",
            )
        }

        override suspend fun fetchSteps(
            session: MiFitnessSession,
            startEpochSeconds: Long,
            endEpochSecondsExclusive: Long,
            nextKey: String,
        ): String = response
    }

    private class AccountStorage : MiFitnessSecretStorage {
        val values = linkedMapOf<String, String>()
        val writes = mutableListOf<Pair<String, String>>()
        var rejectCredentialWrites = false

        override fun read(key: String): String = values[key].orEmpty()

        override fun write(key: String, value: String): Boolean {
            writes += key to value
            if (rejectCredentialWrites && value.contains("\"passToken\"")) return false
            if (value.isEmpty()) values.remove(key) else values[key] = value
            return true
        }
    }

    private companion object {
        val FIXED_CLOCK: Clock = Clock.fixed(Instant.parse("2026-08-27T04:00:00Z"), ZoneOffset.UTC)
        fun page(time: Long, steps: Long): String =
            """{"code":0,"result":{"data_list":[{"tag":"daily_report","key":"steps","time":$time,"zone_offset":28800,"sid":"synthetic-aggregate","zone_name":"Asia/Shanghai","source_sid_list":["synthetic-source"],"value":"{\"steps\":$steps,\"distance\":${steps / 2},\"calories\":${steps / 20}}"}],"has_more":false,"next_key":""}}"""

        fun emptyPage(): String =
            """{"code":0,"result":{"data_list":[],"has_more":false,"next_key":""}}"""
    }
}
