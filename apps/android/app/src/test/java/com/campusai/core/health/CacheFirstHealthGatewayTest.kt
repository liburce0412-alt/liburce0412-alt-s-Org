package com.campusai.core.health

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class CacheFirstHealthGatewayTest {
    @Test
    fun `cache hit returns one complete snapshot without consulting fallback`() = runTest {
        val cachedSnapshot = snapshot(steps = 100L, source = "cache")
        val fallbackSnapshot = snapshot(steps = 200L, source = "fallback")
        val cache = FakeGateway(Result.success(cachedSnapshot))
        val fallback = FakeGateway(Result.success(fallbackSnapshot))
        val gateway = CacheFirstHealthGateway(cache, fallback)

        val result = gateway.snapshot(PERIOD).getOrThrow()

        assertSame(cachedSnapshot, result)
        assertEquals(1, cache.snapshotCalls)
        assertEquals(0, fallback.snapshotCalls)
        assertEquals(100L, result.metrics.steps)
    }

    @Test
    fun `cache miss consults fallback exactly once`() = runTest {
        val fallbackSnapshot = snapshot(steps = 200L, source = "fallback")
        val cache = FakeGateway(Result.failure(IllegalStateException("cache empty")))
        val fallback = FakeGateway(Result.success(fallbackSnapshot))
        val gateway = CacheFirstHealthGateway(cache, fallback)

        val result = gateway.snapshot(PERIOD).getOrThrow()

        assertSame(fallbackSnapshot, result)
        assertEquals(1, cache.snapshotCalls)
        assertEquals(1, fallback.snapshotCalls)
        assertEquals(200L, result.metrics.steps)
    }

    @Test
    fun `permissions remain delegated to fallback without affecting cache reads`() = runTest {
        val cache = FakeGateway(Result.success(snapshot(10L, "cache")))
        val fallback = FakeGateway(
            snapshotResult = Result.success(snapshot(20L, "fallback")),
            permissions = setOf("health.steps"),
            availabilityValue = HealthAvailability.MissingPermissions(setOf("health.steps")),
        )
        val gateway = CacheFirstHealthGateway(cache, fallback)

        assertEquals(10L, gateway.snapshot(PERIOD).getOrThrow().metrics.steps)
        assertEquals(setOf("health.steps"), gateway.readPermissions)
        assertEquals(setOf("health.steps"), gateway.grantedPermissions())
        assertEquals(HealthAvailability.Available, gateway.availability())
        assertEquals(0, fallback.snapshotCalls)
    }

    @Test
    fun `configured cache miss fails closed without consulting fallback or its permissions`() = runTest {
        val cacheFailure = Result.failure<HealthSnapshot>(IllegalStateException("cache empty"))
        val cache = FakeGateway(cacheFailure, availabilityValue = HealthAvailability.Unsupported)
        val fallback = FakeGateway(
            snapshotResult = Result.success(snapshot(20L, "fallback")),
            permissions = setOf("health.steps"),
            availabilityValue = HealthAvailability.Available,
        )
        val gateway = CacheFirstHealthGateway(cache, fallback, fallbackEnabled = { false })

        val result = gateway.snapshot(PERIOD)

        assertSame(cacheFailure.exceptionOrNull(), result.exceptionOrNull())
        assertEquals(1, cache.snapshotCalls)
        assertEquals(0, fallback.snapshotCalls)
        assertEquals(emptySet<String>(), gateway.readPermissions)
        assertEquals(emptySet<String>(), gateway.grantedPermissions())
        assertEquals(HealthAvailability.Unsupported, gateway.availability())
    }

    private class FakeGateway(
        private val snapshotResult: Result<HealthSnapshot>,
        private val permissions: Set<String> = emptySet(),
        private val availabilityValue: HealthAvailability = HealthAvailability.Available,
    ) : HealthGateway {
        var snapshotCalls = 0

        override val readPermissions: Set<String> = permissions
        override fun availability(): HealthAvailability = availabilityValue
        override suspend fun grantedPermissions(): Set<String> = permissions
        override suspend fun snapshot(period: HealthPeriod): Result<HealthSnapshot> {
            snapshotCalls += 1
            return snapshotResult
        }
    }

    companion object {
        private val PERIOD = HealthPeriod(0L, 1_000L, "today")

        private fun snapshot(steps: Long, source: String) = HealthSnapshot(
            originPackages = setOf(source),
            period = PERIOD,
            observedAt = 900L,
            lastSyncAt = 900L,
            freshness = HealthFreshness.FRESH,
            metrics = HealthMetrics(steps = steps),
            missingFields = emptySet(),
            confidence = 1.0,
        )
    }
}
