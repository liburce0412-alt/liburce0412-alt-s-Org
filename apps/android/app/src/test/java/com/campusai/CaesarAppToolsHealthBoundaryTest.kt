package com.campusai

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.campusai.core.agent.AutonomyMode
import com.campusai.core.agent.CaesarAppTools
import com.campusai.core.agent.CaesarIdempotencyStore
import com.campusai.core.agent.CaesarMemoryStore
import com.campusai.core.agent.CaesarToolResult
import com.campusai.core.agent.ToolExecutionContext
import com.campusai.core.database.CampusDatabase
import com.campusai.core.health.HealthAvailability
import com.campusai.core.health.HealthFreshness
import com.campusai.core.health.HealthGateway
import com.campusai.core.health.HealthMetrics
import com.campusai.core.health.HealthPeriod
import com.campusai.core.health.HealthSnapshot
import com.campusai.core.profile.ProfileRepository
import com.campusai.features.community.CampusRepository
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CaesarAppToolsHealthBoundaryTest {
    private lateinit var database: CampusDatabase
    private lateinit var health: RecordingHealthGateway
    private lateinit var tools: CaesarAppTools

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            CampusDatabase::class.java,
        ).allowMainThreadQueries().build()
        health = RecordingHealthGateway()
        val dao = database.campusDao()
        tools = CaesarAppTools(
            dao = dao,
            campus = CampusRepository(),
            profile = ProfileRepository(),
            health = health,
            idempotency = CaesarIdempotencyStore(dao),
            memory = CaesarMemoryStore(dao),
        )
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun `health snapshot reads only the gateway and creates no persisted action`() = runTest {
        val result = tools.registry().execute(
            "health.get_snapshot",
            JSONObject().put("period", "today"),
            CONTEXT,
        )

        assertTrue(result is CaesarToolResult.Success)
        assertEquals(1, health.snapshotCalls)
        assertEquals(0, health.availabilityCalls)
        assertEquals(0, health.grantedPermissionCalls)
        assertEquals(0, health.readPermissionReads)
        assertEquals(0, rowCount("agent_actions"))
        assertEquals(0, rowCount("agent_memories"))
    }

    @Test
    fun `health sources uses gateway metadata and snapshot without side-effect tools`() = runTest {
        val registry = tools.registry()
        val result = registry.execute("health.get_sources", JSONObject(), CONTEXT)

        assertTrue(result is CaesarToolResult.Success)
        assertEquals(1, health.snapshotCalls)
        assertEquals(1, health.availabilityCalls)
        assertEquals(1, health.grantedPermissionCalls)
        assertEquals(1, health.readPermissionReads)
        assertEquals(0, rowCount("agent_actions"))
        assertEquals(0, rowCount("agent_memories"))

        val healthTools = registry.definitions.map { it.name }.filter { it.startsWith("health.") }.toSet()
        assertEquals(
            setOf(
                "health.get_snapshot",
                "health.get_activity",
                "health.get_sleep",
                "health.get_heart_rate",
                "health.get_workouts",
                "health.get_sources",
            ),
            healthTools,
        )
        assertFalse(healthTools.any { it.contains("sync") || it.contains("session") || it.contains("live") })
    }

    @Test
    fun `Caesar app tools constructor has no Band or HealthSync dependency`() {
        val dependencyNames = CaesarAppTools::class.java.declaredConstructors
            .flatMap { constructor -> constructor.parameterTypes.map { parameter -> parameter.name } }

        assertFalse(dependencyNames.any { it.contains("Band", ignoreCase = true) })
        assertFalse(dependencyNames.any { it.contains("HealthSync", ignoreCase = true) })
        assertTrue(dependencyNames.any { it == HealthGateway::class.java.name })
    }

    private fun rowCount(table: String): Int = database.openHelper.readableDatabase
        .query("SELECT COUNT(*) FROM $table")
        .use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }

    private class RecordingHealthGateway : HealthGateway {
        var snapshotCalls = 0
        var availabilityCalls = 0
        var grantedPermissionCalls = 0
        var readPermissionReads = 0

        override val readPermissions: Set<String>
            get() {
                readPermissionReads += 1
                return setOf("health.steps")
            }

        override fun availability(): HealthAvailability {
            availabilityCalls += 1
            return HealthAvailability.Available
        }

        override suspend fun grantedPermissions(): Set<String> {
            grantedPermissionCalls += 1
            return setOf("health.steps")
        }

        override suspend fun snapshot(period: HealthPeriod): Result<HealthSnapshot> {
            snapshotCalls += 1
            return Result.success(
                HealthSnapshot(
                    originPackages = setOf("mi_fitness_cloud_cn"),
                    period = period,
                    observedAt = 2_000L,
                    lastSyncAt = 1_900L,
                    freshness = HealthFreshness.FRESH,
                    metrics = HealthMetrics(steps = 3_210L),
                    missingFields = setOf("sleep"),
                    confidence = 0.65,
                ),
            )
        }
    }

    companion object {
        private val CONTEXT = ToolExecutionContext(
            sessionId = "health-boundary-test",
            ownerUserId = "local-user",
            userPrompt = "读取本地健康摘要",
            autonomyMode = AutonomyMode.READ_ONLY,
            explicitUserIntent = true,
            idempotencyKey = "health-boundary-test-key",
        )
    }
}
