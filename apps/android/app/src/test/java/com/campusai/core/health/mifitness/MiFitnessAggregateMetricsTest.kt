package com.campusai.core.health.mifitness

import com.campusai.core.health.HealthMetricKey
import com.campusai.core.health.HealthMetricStatus
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MiFitnessAggregateMetricsTest {
    @Test
    fun `every semantically verified CloudKey has a typed parser and unit`() {
        val fixture = fixture()
        val responses = fixture.getJSONObject("responses")
        val fixtureKeys = responses.keys().asSequence().toSet()

        assertEquals(MiFitnessMetricRegistry.byRequestKey.keys, fixtureKeys)
        MiFitnessMetricRegistry.definitions.forEach { definition ->
            val page = MiFitnessAggregateParser.parse(
                responses.getJSONObject(definition.requestKey).toString(),
                definition.requestKey,
            ).getOrThrow()
            val metrics = MiFitnessAggregateParser.metricsFor(page.records.single()).getOrThrow()

            assertEquals(definition.outputs.keys, metrics.keys)
            metrics.forEach { (key, value) ->
                assertEquals(definition.outputs[key], value.unit)
                assertEquals(HealthMetricStatus.AVAILABLE, value.status)
                assertEquals(MiFitnessProtocol.AGGREGATE_FITNESS_PATH, value.provenance.endpoint)
                assertEquals(definition.requestKey, value.provenance.vendorKey)
                assertTrue(value.value != null)
            }
        }
    }

    @Test
    fun `synthetic step fixture exposes typed daily totals`() {
        val steps = fixture().getJSONObject("responses").getJSONObject("steps")
        val record = MiFitnessAggregateParser.parse(steps.toString(), "steps")
            .getOrThrow()
            .records
            .single()
        val metrics = MiFitnessAggregateParser.metricsFor(record).getOrThrow()

        assertEquals(3_210.0, metrics[HealthMetricKey.STEPS]?.value ?: -1.0, 0.0)
        assertEquals(1_926.0, metrics[HealthMetricKey.DISTANCE_METERS]?.value ?: -1.0, 0.0)
        assertEquals(2, metrics[HealthMetricKey.STEPS]?.provenance?.sourceCount)
    }

    @Test
    fun `optional report field becomes partial instead of zero`() {
        val response = fixture()
            .getJSONObject("responses")
            .getJSONObject("heart_rate")
        response.getJSONObject("result")
            .getJSONArray("data_list")
            .getJSONObject(0)
            .getJSONObject("value")
            .remove("avg_rhr")

        val metrics = MiFitnessAggregateParser.parse(response.toString(), "heart_rate")
            .mapCatching { MiFitnessAggregateParser.metricsFor(it.records.single()).getOrThrow() }
            .getOrThrow()
        val resting = checkNotNull(metrics[HealthMetricKey.RESTING_HEART_RATE_BPM])

        assertEquals(HealthMetricStatus.PARTIAL, resting.status)
        assertEquals(null, resting.value)
        assertEquals("field_missing", resting.reasonCode)
    }

    @Test
    fun `unknown aggregate key is rejected rather than silently discarded`() {
        assertTrue(MiFitnessAggregateParser.parse("{}", "unknown_vendor_key").isFailure)
    }

    @Test
    fun `sport parser counts only non deleted records without retaining source identifiers`() {
        val raw = """
            {"code":0,"result":{"sport_records":[
              {"sid":"synthetic-workout-a","key":"sport","time":1777305601,"category":1,"zone_offset":28800,"value":"{}","deleted":false},
              {"sid":"synthetic-workout-b","key":"sport","time":1777305602,"category":1,"zone_offset":28800,"value":"{}","deleted":true}
            ],"next_key":"","has_more":false}}
        """.trimIndent()

        val records = MiFitnessSportParser.parse(raw).getOrThrow().records

        assertEquals(2, records.size)
        assertEquals(1, records.count { !it.deleted })
        assertTrue(records.none { it.idDigest.contains("synthetic-workout") })
        val edited = MiFitnessSportParser.parse(raw.replaceFirst("\"value\":\"{}\"", "\"value\":\"{\\\"duration\\\":42}\""))
            .getOrThrow()
            .records
        assertTrue(records.first().revisionDigest != edited.first().revisionDigest)
        assertEquals(stableWorkoutRevision(records), stableWorkoutRevision(records + records.first()))
        assertTrue(stableWorkoutRevision(records) != stableWorkoutRevision(edited))
    }

    private fun fixture(): JSONObject {
        val stream = checkNotNull(javaClass.classLoader?.getResourceAsStream(FIXTURE))
        return stream.bufferedReader().use { JSONObject(it.readText()) }
    }

    private companion object {
        const val FIXTURE = "mi_fitness/daily_aggregate_metrics_synthetic.json"
    }
}
