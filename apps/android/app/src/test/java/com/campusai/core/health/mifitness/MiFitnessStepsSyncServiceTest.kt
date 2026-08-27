package com.campusai.core.health.mifitness

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest

@RunWith(RobolectricTestRunner::class)
class MiFitnessStepsSyncServiceTest {
    @Test
    fun `sync reads one CN day follows pages and stores only aggregate`() = runTest {
        val storage = RecordingSecretStorage()
        val credentialStore = MiFitnessCredentialStore(storage)
        assertTrue(credentialStore.save("12345", "synthetic-pass-token").isSuccess)
        storage.writes.clear()
        val start = LocalDate.of(2026, 8, 27).atStartOfDay().toEpochSecond(ZoneOffset.ofHours(8))
        val transport = FakeTransport(
            page(listOf(start to 100L), hasMore = true, nextKey = "cursor-one"),
            page(listOf(start + 60L to 250L), hasMore = false),
        )
        val cache = MiFitnessStepsCache(storage)
        val service = service(credentialStore, transport, cache)

        val summary = service.syncToday().getOrThrow()

        assertEquals(350L, summary.steps)
        assertEquals(2, summary.recordCount)
        assertEquals(LocalDate.of(2026, 8, 27), summary.localDate)
        assertEquals(start * 1_000L, summary.period.startEpochMillis)
        assertEquals((start + 86_400L) * 1_000L - 1L, summary.period.endEpochMillis)
        assertEquals(listOf("", "cursor-one"), transport.calls.map(FetchCall::nextKey))
        assertTrue(transport.calls.all { it.startEpochSeconds == start && it.endEpochSeconds == start + 86_399L })
        assertEquals(summary, cache.read(summary.period, summary.localDate, summary.accountScope))
        assertEquals(1, storage.writes.size)
        assertTrue(storage.writes.single().second.contains("\"steps\":350"))
        assertFalse(storage.writes.single().second.contains("data_list"))
        assertFalse(storage.writes.single().second.contains("synthetic-pass-token"))
    }

    @Test
    fun `successful sync safely rotates a refreshed pass token after caching`() = runTest {
        val storage = RecordingSecretStorage()
        val store = MiFitnessCredentialStore(storage)
        store.save("12345", "original-pass-token")
        val start = LocalDate.of(2026, 8, 27).atStartOfDay().toEpochSecond(ZoneOffset.ofHours(8))
        val transport = FakeTransport(page(listOf(start to 123L), hasMore = false)).apply {
            refreshedPassToken = "rotated-pass-token"
        }
        val cache = MiFitnessStepsCache(storage)
        val service = service(store, transport, cache)
        storage.writes.clear()

        val summary = service.syncToday().getOrThrow()

        assertEquals("rotated-pass-token", store.read()?.passToken)
        assertEquals(2, storage.writes.size)
        assertTrue(storage.writes[0].second.contains("\"steps\":123"))
        assertFalse(storage.writes[0].second.contains("rotated-pass-token"))
        assertTrue(storage.writes[1].second.contains("\"passToken\":\"rotated-pass-token\""))
        assertEquals(summary, cache.read(summary.period, summary.localDate, summary.accountScope))
    }

    @Test
    fun `failed refreshed credential write restores old visible state`() = runTest {
        val storage = RecordingSecretStorage()
        val store = MiFitnessCredentialStore(storage)
        store.save("12345", "original-pass-token")
        val oldCredential = checkNotNull(store.read())
        val start = LocalDate.of(2026, 8, 27).atStartOfDay().toEpochSecond(ZoneOffset.ofHours(8))
        val transport = FakeTransport(page(listOf(start to 999L), hasMore = false)).apply {
            refreshedPassToken = "rotated-pass-token"
        }
        val cache = MiFitnessStepsCache(storage)
        val service = service(store, transport, cache)
        val window = service.todayWindow()
        val oldSummary = MiFitnessStepsSummary(
            period = window.period,
            localDate = window.localDate,
            accountScope = oldCredential.accountScope,
            steps = 42L,
            recordCount = 1,
            observedAt = FIXED_CLOCK.millis(),
            lastSyncAt = FIXED_CLOCK.millis(),
        )
        cache.save(oldSummary)
        storage.writes.clear()
        storage.rejectCredentialWrites = true

        val result = service.syncToday()

        val error = result.exceptionOrNull() as MiFitnessStepsSyncException
        assertEquals("credential_write_failed", error.code)
        assertEquals("original-pass-token", store.read()?.passToken)
        assertEquals(oldSummary, cache.read(window.period, window.localDate, oldCredential.accountScope))
        assertFalse(error.toString().contains("rotated-pass-token"))
        assertTrue(
            storage.writes
                .filter { (_, value) -> value.contains("\"steps\"") }
                .none { (_, value) -> value.contains("rotated-pass-token") },
        )
    }

    @Test
    fun `repeated cursor fails closed without replacing cache`() = runTest {
        val fixture = fixture(
            page(emptyList(), hasMore = true, nextKey = "repeat"),
            page(emptyList(), hasMore = true, nextKey = "repeat"),
        )
        fixture.transport.refreshedPassToken = "rotated-pass-token"

        val result = fixture.service.syncToday()

        assertEquals("cursor_repeated", (result.exceptionOrNull() as MiFitnessStepsSyncException).code)
        assertEquals(2, fixture.transport.calls.size)
        assertEquals("synthetic-pass-token", fixture.store.read()?.passToken)
        assertTrue(fixture.storage.writes.isEmpty())
    }

    @Test
    fun `pagination stops at the hard page limit without caching partial data`() = runTest {
        val pages = Array(10) { index ->
            page(emptyList(), hasMore = true, nextKey = "cursor-${index + 1}")
        }
        val fixture = fixture(*pages)

        val result = fixture.service.syncToday()

        assertEquals("page_limit", (result.exceptionOrNull() as MiFitnessStepsSyncException).code)
        assertEquals(10, fixture.transport.calls.size)
        assertTrue(fixture.storage.writes.isEmpty())
    }

    @Test
    fun `empty cloud result persists a refreshed token without replacing the previous cache`() = runTest {
        val storage = RecordingSecretStorage()
        val store = MiFitnessCredentialStore(storage)
        store.save("12345", "synthetic-pass-token")
        val credential = checkNotNull(store.read())
        val cache = MiFitnessStepsCache(storage)
        val transport = FakeTransport(page(emptyList(), hasMore = false)).apply {
            refreshedPassToken = "rotated-pass-token"
        }
        val service = service(store, transport, cache)
        val window = service.todayWindow()
        val oldSummary = MiFitnessStepsSummary(
            period = window.period,
            localDate = window.localDate,
            accountScope = credential.accountScope,
            steps = 42L,
            recordCount = 1,
            observedAt = FIXED_CLOCK.millis() - 1_000L,
            lastSyncAt = FIXED_CLOCK.millis() - 1_000L,
        )
        cache.save(oldSummary)
        storage.writes.clear()

        val result = service.syncToday()

        assertEquals("no_cloud_data", (result.exceptionOrNull() as MiFitnessStepsSyncException).code)
        assertEquals("rotated-pass-token", store.read()?.passToken)
        assertEquals(oldSummary, cache.read(window.period, window.localDate, credential.accountScope))
        assertEquals(1, storage.writes.size)
        assertTrue(storage.writes.single().second.contains("\"passToken\":\"rotated-pass-token\""))
    }

    @Test
    fun `empty cloud result and rejected refreshed token leave credentials and cache untouched`() = runTest {
        val storage = RecordingSecretStorage()
        val store = MiFitnessCredentialStore(storage)
        store.save("12345", "synthetic-pass-token")
        val credential = checkNotNull(store.read())
        val cache = MiFitnessStepsCache(storage)
        val transport = FakeTransport(page(emptyList(), hasMore = false)).apply {
            refreshedPassToken = "rotated-pass-token"
        }
        val service = service(store, transport, cache)
        val window = service.todayWindow()
        val oldSummary = MiFitnessStepsSummary(
            period = window.period,
            localDate = window.localDate,
            accountScope = credential.accountScope,
            steps = 42L,
            recordCount = 1,
            observedAt = FIXED_CLOCK.millis() - 1_000L,
            lastSyncAt = FIXED_CLOCK.millis() - 1_000L,
        )
        cache.save(oldSummary)
        storage.writes.clear()
        storage.rejectCredentialWrites = true

        val result = service.syncToday()

        val error = result.exceptionOrNull() as MiFitnessStepsSyncException
        assertEquals("credential_write_failed", error.code)
        assertEquals("synthetic-pass-token", store.read()?.passToken)
        assertEquals(oldSummary, cache.read(window.period, window.localDate, credential.accountScope))
        assertFalse(error.toString().contains("rotated-pass-token"))
    }

    @Test
    fun `missing credentials and unexpected transport failures expose no secret`() = runTest {
        val emptyStorage = RecordingSecretStorage()
        val unusedTransport = FakeTransport()
        val emptyService = service(
            MiFitnessCredentialStore(emptyStorage),
            unusedTransport,
            MiFitnessStepsCache(emptyStorage),
        )
        val missing = emptyService.syncToday()
        assertEquals("credentials_missing", (missing.exceptionOrNull() as MiFitnessStepsSyncException).code)
        assertEquals(0, unusedTransport.exchangeCount)

        val storage = RecordingSecretStorage()
        val store = MiFitnessCredentialStore(storage)
        store.save("12345", "synthetic-pass-token")
        storage.writes.clear()
        val failingTransport = FakeTransport().apply {
            exchangeFailure = IllegalStateException("synthetic-pass-token raw-response")
        }
        val failed = service(store, failingTransport, MiFitnessStepsCache(storage)).syncToday()
        val error = failed.exceptionOrNull() as MiFitnessStepsSyncException
        assertEquals("sync_failed", error.code)
        assertFalse(error.message.orEmpty().contains("synthetic-pass-token"))
        assertFalse(error.toString().contains("raw-response"))
        assertTrue(storage.writes.isEmpty())
    }

    private fun fixture(vararg pages: String): Fixture {
        val storage = RecordingSecretStorage()
        val store = MiFitnessCredentialStore(storage)
        store.save("12345", "synthetic-pass-token")
        storage.writes.clear()
        val transport = FakeTransport(*pages)
        val service = service(store, transport, MiFitnessStepsCache(storage))
        return Fixture(storage, store, transport, service)
    }

    private fun service(
        store: MiFitnessCredentialStore,
        transport: MiFitnessStepsTransport,
        cache: MiFitnessStepsCache,
    ) = MiFitnessStepsSyncService(store, transport, cache, FIXED_CLOCK)

    private data class Fixture(
        val storage: RecordingSecretStorage,
        val store: MiFitnessCredentialStore,
        val transport: FakeTransport,
        val service: MiFitnessStepsSyncService,
    )

    private data class FetchCall(
        val startEpochSeconds: Long,
        val endEpochSeconds: Long,
        val nextKey: String,
    )

    private class FakeTransport(vararg responses: String) : MiFitnessStepsTransport {
        private val responses = ArrayDeque(responses.toList())
        val calls = mutableListOf<FetchCall>()
        var exchangeCount = 0
        var exchangeFailure: RuntimeException? = null
        var refreshedPassToken: String? = null

        override suspend fun exchangePassToken(credential: MiFitnessCredential): MiFitnessSession {
            exchangeCount += 1
            exchangeFailure?.let { throw it }
            return MiFitnessSession(
                userId = credential.userId,
                cUserId = "synthetic-c-user",
                serviceToken = "synthetic-service-token",
                ssecurityBase64 = "c3NlY3VyaXR5LXZlY3Rvcg==",
                deviceId = "synthetic-device",
                tokenRefreshed = refreshedPassToken != null,
                refreshedPassToken = refreshedPassToken,
            )
        }

        override suspend fun fetchSteps(
            session: MiFitnessSession,
            startEpochSeconds: Long,
            endEpochSeconds: Long,
            nextKey: String,
        ): String {
            calls += FetchCall(startEpochSeconds, endEpochSeconds, nextKey)
            return checkNotNull(responses.removeFirstOrNull()) { "Unexpected fetch" }
        }
    }

    private class RecordingSecretStorage : MiFitnessSecretStorage {
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

        fun page(
            records: List<Pair<Long, Long>>,
            hasMore: Boolean,
            nextKey: String = "",
        ): String {
            val items = records.joinToString(",") { (time, steps) ->
                """{"time":$time,"key":"steps","value":"{\"steps\":$steps}"}"""
            }
            return """{"code":0,"result":{"data_list":[$items],"has_more":$hasMore,"next_key":"$nextKey"}}"""
        }
    }
}
