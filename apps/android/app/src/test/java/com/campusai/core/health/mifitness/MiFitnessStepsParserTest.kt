package com.campusai.core.health.mifitness

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MiFitnessStepsParserTest {
    @Test
    fun `parser extracts only verified steps records and pagination`() {
        val raw = """
            {
              "code": 0,
              "result": {
                "data_list": [
                  {"time": 1770000000, "key": "steps", "value": "{\"steps\":3210}"},
                  {"time": 1770000001, "key": "distance", "value": "{\"distance\":99}"},
                  {"time": 1770000002, "key": "steps", "value": {"steps":"12"}}
                ],
                "has_more": true,
                "next_key": "unit-next"
              }
            }
        """.trimIndent()

        val page = MiFitnessStepsParser.parse(raw).getOrThrow()

        assertEquals(
            listOf(MiFitnessStepRecord(1_770_000_000L, 3_210L), MiFitnessStepRecord(1_770_000_002L, 12L)),
            page.records,
        )
        assertTrue(page.hasMore)
        assertEquals("unit-next", page.nextKey)
    }

    @Test
    fun `parser rejects nonintegral negative oversized and malformed step values`() {
        listOf("1.5", "-1", "1000001", "null").forEach { stepsLiteral ->
            val raw = """{"code":200,"result":{"data_list":[{"time":1770000000,"key":"steps","value":"{\"steps\":$stepsLiteral}"}],"has_more":false}}"""
            assertTrue("value=$stepsLiteral", MiFitnessStepsParser.parse(raw).isFailure)
        }
    }

    @Test
    fun `parser requires a continuation key when more pages exist`() {
        val raw = """{"code":0,"result":{"data_list":[],"has_more":true,"next_key":""}}"""

        assertTrue(MiFitnessStepsParser.parse(raw).isFailure)
    }

    @Test
    fun `provisional aggregator sums incremental buckets`() {
        val aggregate = MiFitnessStepsAggregator.sumIncremental(
            listOf(MiFitnessStepRecord(10L, 100L), MiFitnessStepRecord(20L, 250L)),
        ).getOrThrow()

        assertEquals(350L, aggregate.steps)
        assertEquals(2, aggregate.recordCount)
        assertEquals(20L, aggregate.latestRecordEpochSeconds)
        assertTrue(aggregate.aggregationProvisional)
    }

    @Test
    fun `provisional aggregator rejects invalid records and total cap`() {
        assertTrue(MiFitnessStepsAggregator.sumIncremental(listOf(MiFitnessStepRecord(10L, -1L))).isFailure)
        val overCap = List(11) { MiFitnessStepRecord(it.toLong(), 1_000_000L) }
        assertTrue(MiFitnessStepsAggregator.sumIncremental(overCap).isFailure)
        assertFalse(MiFitnessStepsAggregator.sumIncremental(emptyList()).isFailure)
    }
}
