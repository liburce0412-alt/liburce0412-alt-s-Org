package com.campusai.core.health.mifitness

import com.campusai.core.health.HealthPeriod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class MiFitnessSecureStorageTest {
    @Test
    fun `credential store writes one opaque payload and restores normalized values`() {
        val storage = MemorySecretStorage()
        val store = MiFitnessCredentialStore(storage)

        assertTrue(store.save("  123456789  ", "  unit-pass-token  ").isSuccess)

        assertEquals(1, storage.values.size)
        val restored = store.read()
        assertEquals("123456789", restored?.userId)
        assertEquals("unit-pass-token", restored?.passToken)
        assertEquals(32, restored?.accountScope?.length)
        assertFalse(restored.toString().contains("unit-pass-token"))
    }

    @Test
    fun `credential store rejects unsafe input without overwriting saved data`() {
        val storage = MemorySecretStorage()
        val store = MiFitnessCredentialStore(storage)
        assertTrue(store.save("123456789", "unit-pass-token").isSuccess)

        listOf("line-one\nline-two", "semi;colon", "comma,value", "quote\"value", "slash\\value").forEach { value ->
            assertTrue(store.save("123456789", value).isFailure)
        }
        assertTrue(store.save("not-digits", "unit-pass-token").isFailure)

        assertEquals("unit-pass-token", store.read()?.passToken)
    }

    @Test
    fun `credential store fails closed for corrupt unsupported and failed writes`() {
        val storage = MemorySecretStorage()
        val store = MiFitnessCredentialStore(storage)
        assertTrue(store.save("123456789", "unit-pass-token").isSuccess)
        storage.values[storage.onlyKey()] = "not-json"
        assertNull(store.read())

        storage.values[storage.onlyKey()] = "{\"version\":2}"
        assertNull(store.read())

        storage.allowWrites = false
        assertTrue(store.save("123456789", "unit-pass-token").isFailure)
    }

    @Test
    fun `account scope stays stable when pass token rotates`() {
        val storage = MemorySecretStorage()
        val store = MiFitnessCredentialStore(storage)
        assertTrue(store.save("123456789", "unit-pass-token-one").isSuccess)
        val originalScope = store.read()?.accountScope

        assertTrue(store.save("123456789", "unit-pass-token-two").isSuccess)

        assertEquals(originalScope, store.read()?.accountScope)
    }

    @Test
    fun `credential delete removes the encrypted payload`() {
        val storage = MemorySecretStorage()
        val store = MiFitnessCredentialStore(storage)
        assertTrue(store.save("123456789", "unit-pass-token").isSuccess)

        assertTrue(store.delete())

        assertFalse(store.hasCredentials())
        assertTrue(storage.values.isEmpty())
    }

    @Test
    fun `steps cache matches account date start and key while accepting a later window end`() {
        val storage = MemorySecretStorage()
        val cache = MiFitnessStepsCache(storage)
        val period = HealthPeriod(1_000L, 2_000L, "today")
        val localDate = LocalDate.of(2026, 8, 27)
        val accountScope = "0123456789abcdef0123456789abcdef"
        val summary = MiFitnessStepsSummary(
            period = period,
            localDate = localDate,
            accountScope = accountScope,
            steps = 3_210L,
            recordCount = 4,
            observedAt = 1_900L,
            lastSyncAt = 2_100L,
        )

        assertTrue(cache.save(summary).isSuccess)

        assertEquals(summary, cache.read(period, localDate, accountScope))
        assertEquals(summary.copy(period = period.copy(endEpochMillis = 2_001L)), cache.read(period.copy(endEpochMillis = 2_001L), localDate, accountScope))
        assertNull(cache.read(period.copy(startEpochMillis = 999L), localDate, accountScope))
        assertNull(cache.read(period, localDate.plusDays(1), accountScope))
        assertNull(cache.read(period, localDate, "fedcba9876543210fedcba9876543210"))
    }

    @Test
    fun `steps cache rejects impossible summaries and corrupt payloads`() {
        val storage = MemorySecretStorage()
        val cache = MiFitnessStepsCache(storage)
        val period = HealthPeriod(2_000L, 1_000L, "today")
        val summary = MiFitnessStepsSummary(
            period = period,
            localDate = LocalDate.of(2026, 8, 27),
            accountScope = "0123456789abcdef0123456789abcdef",
            steps = 1L,
            recordCount = 1,
            observedAt = 1L,
            lastSyncAt = 1L,
        )
        assertTrue(cache.save(summary).isFailure)
        assertTrue(storage.values.isEmpty())

        val validPeriod = HealthPeriod(1_000L, 2_000L, "today")
        assertTrue(cache.save(summary.copy(period = validPeriod)).isSuccess)
        storage.values[storage.onlyKey()] = "not-json"
        assertNull(
            cache.read(
                validPeriod,
                LocalDate.of(2026, 8, 27),
                "0123456789abcdef0123456789abcdef",
            ),
        )
    }

    private class MemorySecretStorage : MiFitnessSecretStorage {
        val values = linkedMapOf<String, String>()
        var allowWrites = true

        override fun read(key: String): String = values[key].orEmpty()

        override fun write(key: String, value: String): Boolean {
            if (!allowWrites) return false
            if (value.isEmpty()) values.remove(key) else values[key] = value
            return true
        }

        fun onlyKey(): String = values.keys.single()
    }
}
