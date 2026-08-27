package com.campusai.core.health.mifitness

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.campusai.core.health.CacheFirstHealthGateway
import com.campusai.core.health.HealthAvailability
import com.campusai.core.health.HealthFreshness
import com.campusai.core.health.HealthGatewayFactory
import com.campusai.core.health.HealthPeriod
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [25])
class MiFitnessSummaryHealthGatewayTest {
    @Test
    fun `API 25 reads matching Mi Fitness cache as a provisional steps-only snapshot`() = runTest {
        val fixture = fixture()
        val requested = fixture.period.copy(endEpochMillis = fixture.period.endEpochMillis + 5_000L)

        val snapshot = fixture.gateway.snapshot(requested).getOrThrow()

        assertEquals(setOf(MiFitnessSummaryHealthGateway.SOURCE_ID), snapshot.originPackages)
        assertEquals(fixture.period.startEpochMillis, snapshot.period.startEpochMillis)
        assertEquals("today", snapshot.period.key)
        assertEquals(3_210L, snapshot.metrics.steps)
        assertNull(snapshot.metrics.distanceMeters)
        assertEquals(HealthFreshness.FRESH, snapshot.freshness)
        assertEquals(MiFitnessSummaryHealthGateway.PROVISIONAL_CONFIDENCE, snapshot.confidence, 0.0)
        assertTrue("steps" !in snapshot.missingFields)
        assertTrue("sleep" in snapshot.missingFields)
    }

    @Test
    fun `cache fails closed for a different account or non-today period`() = runTest {
        val fixture = fixture()
        fixture.credentials.save("987654321", "unit-pass-token-two").getOrThrow()

        assertTrue(fixture.gateway.snapshot(fixture.period).isFailure)

        val weekly = fixture.period.copy(key = "week")
        assertTrue(fixture.gateway.snapshot(weekly).isFailure)
    }

    @Test
    fun `today request from another system zone resolves the canonical China cache day`() = runTest {
        val fixture = fixture()
        val systemZonePeriod = HealthPeriod(
            startEpochMillis = Instant.parse("2026-08-26T10:00:00Z").toEpochMilli(),
            endEpochMillis = fixture.period.endEpochMillis,
            key = "today",
        )

        val storage = MemorySecretStorage()
        val credentials = MiFitnessCredentialStore(storage)
        credentials.save("123456789", "unit-pass-token-one").getOrThrow()
        val accountScope = requireNotNull(credentials.read()).accountScope
        val cache = MiFitnessStepsCache(storage)
        val now = Instant.parse("2026-08-27T08:00:00Z").toEpochMilli()
        val chinaDate = LocalDate.of(2026, 8, 27)
        val chinaStart = chinaDate.atStartOfDay(ZoneOffset.ofHours(8)).toInstant().toEpochMilli()
        val chinaPeriod = HealthPeriod(chinaStart, chinaStart + 86_400_000L - 1L, "today")
        cache.save(
            MiFitnessStepsSummary(
                period = chinaPeriod,
                localDate = chinaDate,
                accountScope = accountScope,
                steps = 3_210L,
                recordCount = 4,
                observedAt = now - 1_000L,
                lastSyncAt = now - 60_000L,
            ),
        ).getOrThrow()
        val gateway = MiFitnessSummaryHealthGateway(
            credentials,
            cache,
            { now },
            ZoneOffset.ofHours(8),
            { ZoneOffset.ofHours(14) },
        )

        val snapshot = gateway.snapshot(systemZonePeriod).getOrThrow()

        assertEquals(chinaPeriod, snapshot.period)
        assertEquals(3_210L, snapshot.metrics.steps)
    }

    @Test
    fun `today label cannot relabel an arbitrary historical window`() = runTest {
        val fixture = fixture()
        val relabeled = fixture.period.copy(
            startEpochMillis = fixture.period.startEpochMillis - 86_400_000L,
        )

        assertTrue(fixture.gateway.snapshot(relabeled).isFailure)
    }

    @Test
    fun `credentials without a matching cache are not reported as available`() {
        val storage = MemorySecretStorage()
        val credentials = MiFitnessCredentialStore(storage)
        credentials.save("123456789", "unit-pass-token-one").getOrThrow()
        val now = Instant.parse("2026-08-27T08:00:00Z").toEpochMilli()
        val gateway = MiFitnessSummaryHealthGateway(
            credentials,
            MiFitnessStepsCache(storage),
            { now },
            ZoneOffset.UTC,
        )

        assertEquals(HealthAvailability.Unsupported, gateway.availability())
    }

    @Test
    fun `factory keeps cache-first gateway loadable while Health Connect is unsupported on API 25`() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val composite = HealthGatewayFactory.create(context)
        val healthConnect = HealthGatewayFactory.createHealthConnectOnly(context)

        assertTrue(composite is CacheFirstHealthGateway)
        assertEquals(HealthAvailability.Unsupported, composite.availability())
        assertEquals(HealthAvailability.Unsupported, healthConnect.availability())
    }

    private fun fixture(): Fixture {
        val storage = MemorySecretStorage()
        val credentials = MiFitnessCredentialStore(storage)
        credentials.save("123456789", "unit-pass-token-one").getOrThrow()
        val accountScope = requireNotNull(credentials.read()).accountScope
        val cache = MiFitnessStepsCache(storage)
        val date = LocalDate.of(2026, 8, 27)
        val start = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val now = Instant.parse("2026-08-27T08:00:00Z").toEpochMilli()
        val period = HealthPeriod(start, now, "today")
        cache.save(
            MiFitnessStepsSummary(
                period = period,
                localDate = date,
                accountScope = accountScope,
                steps = 3_210L,
                recordCount = 4,
                observedAt = now - 1_000L,
                lastSyncAt = now - 60_000L,
            ),
        ).getOrThrow()
        return Fixture(
            credentials = credentials,
            period = period,
            gateway = MiFitnessSummaryHealthGateway(
                credentials,
                cache,
                { now },
                ZoneOffset.UTC,
                { ZoneOffset.UTC },
            ),
        )
    }

    private data class Fixture(
        val credentials: MiFitnessCredentialStore,
        val period: HealthPeriod,
        val gateway: MiFitnessSummaryHealthGateway,
    )

    private class MemorySecretStorage : MiFitnessSecretStorage {
        private val values = mutableMapOf<String, String>()

        override fun read(key: String): String = values[key].orEmpty()

        override fun write(key: String, value: String): Boolean {
            if (value.isEmpty()) values.remove(key) else values[key] = value
            return true
        }
    }
}
