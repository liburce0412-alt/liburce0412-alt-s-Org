package com.campusai.core.health.mifitness

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MiFitnessStepsParserTest {
    @Test
    fun `parser reads verified CloudStepReport daily total including distance calories and source count`() {
        val page = MiFitnessStepsParser.parse(
            aggregatePage(
                records = listOf(record(START, 3_210L, 1_926L, 123L, listOf("source-a", "source-b"))),
                hasMore = true,
                nextKey = "synthetic-next",
            ),
        ).getOrThrow()

        assertEquals(1, page.records.size)
        assertEquals(3_210L, page.records.single().steps)
        assertEquals(1_926L, page.records.single().distanceMeters)
        assertEquals(123L, page.records.single().caloriesKcal)
        assertEquals(2, page.records.single().sourceCount)
        assertTrue(page.hasMore)
        assertEquals("synthetic-next", page.nextKey)
    }

    @Test
    fun `parser requires exact aggregate tag key and CloudStepReport fields`() {
        val valid = record(START, 3_210L, 1_926L, 123L, emptyList())
        listOf(
            valid.replace("\"daily_report\"", "\"daily-mark\""),
            valid.replace("\"steps\",\"time\"", "\"distance\",\"time\""),
            valid.replace("\"zone_offset\":28800", "\"zone_offset\":999999"),
            valid.replace("\\\"distance\\\":1926,", ""),
            valid.replace("\\\"calories\\\":123", "\\\"calories\\\":1.5"),
        ).forEach { malformed ->
            assertTrue(MiFitnessStepsParser.parse(aggregatePage(listOf(malformed), false)).isFailure)
        }
    }

    @Test
    fun `parser accepts live aggregate records where Xiaomi omits zone offset`() {
        val withoutZone = record(START, 3_210L, 1_926L, 123L, emptyList())
            .replace("\"zone_offset\":28800,", "")

        val parsed = MiFitnessStepsParser.parse(aggregatePage(listOf(withoutZone), false)).getOrThrow()

        assertEquals(3_210L, parsed.records.single().steps)
    }

    @Test
    fun `vendor daily selector keeps one synthetic total and never sums time buckets`() {
        val official = MiFitnessStepsParser.parse(
            aggregatePage(listOf(record(START, 3_210L, 1_926L, 123L, listOf("source-a"))), false),
        ).getOrThrow().records
        val selected = MiFitnessStepsAggregator.selectVendorDaily(official, START, END).getOrThrow()

        assertEquals(3_210L, selected?.steps)
        assertEquals(1_926L, selected?.distanceMeters)
        assertEquals(1, selected?.recordCount)

        val conflicting = MiFitnessStepsParser.parse(
            aggregatePage(
                listOf(
                    record(START, 1_200L, 720L, 45L, listOf("source-a")),
                    record(START + 1L, 2_010L, 1_206L, 78L, listOf("source-a")),
                ),
                false,
            ),
        ).getOrThrow().records
        assertTrue(MiFitnessStepsAggregator.selectVendorDaily(conflicting, START, END).isFailure)
    }

    @Test
    fun `identical repeated page item is deduplicated rather than summed`() {
        val same = record(START, 3_210L, 1_926L, 123L, listOf("source-a"))
        val records = MiFitnessStepsParser.parse(
            aggregatePage(listOf(same, same), false),
        ).getOrThrow().records

        val selected = MiFitnessStepsAggregator.selectVendorDaily(records, START, END).getOrThrow()

        assertEquals(3_210L, selected?.steps)
        assertEquals(1, selected?.recordCount)
    }

    @Test
    fun `by time response remains a trend series and is not accepted as daily aggregate`() {
        val byTime = """
            {"code":0,"result":{"data_list":[
              {"time":$START,"key":"steps","value":"{\"steps\":1200}"},
              {"time":${START + 1},"key":"steps","value":"{\"steps\":2010}"}
            ],"has_more":false,"next_key":""}}
        """.trimIndent()

        val series = MiFitnessStepSeriesParser.parse(byTime).getOrThrow()

        assertEquals(listOf(1_200L, 2_010L), series.map(MiFitnessStepSeriesPoint::steps))
        assertTrue(MiFitnessStepsParser.parse(byTime).isFailure)
    }

    @Test
    fun `by time parser preserves pagination metadata and rejects missing continuation cursor`() {
        val first = """
            {"code":0,"result":{"data_list":[
              {"time":$START,"key":"steps","value":{"steps":1200}}
            ],"has_more":true,"next_key":"series-next"}}
        """.trimIndent()

        val page = MiFitnessStepSeriesParser.parsePage(first).getOrThrow()

        assertEquals(listOf(1_200L), page.points.map(MiFitnessStepSeriesPoint::steps))
        assertTrue(page.hasMore)
        assertEquals("series-next", page.nextKey)
        assertTrue(
            MiFitnessStepSeriesParser.parsePage(
                first.replace("\"series-next\"", "\"\""),
            ).isFailure,
        )
    }

    @Test
    fun `parser requires a continuation key when more pages exist`() {
        val raw = aggregatePage(emptyList(), hasMore = true, nextKey = "")

        assertTrue(MiFitnessStepsParser.parse(raw).isFailure)
    }

    private fun aggregatePage(
        records: List<String>,
        hasMore: Boolean,
        nextKey: String = "",
    ): String =
        """{"code":0,"result":{"data_list":[${records.joinToString(",")}],"has_more":$hasMore,"next_key":"$nextKey"}}"""

    private fun record(
        time: Long,
        steps: Long,
        distance: Long,
        calories: Long,
        sourceIds: List<String>,
    ): String {
        val sources = sourceIds.joinToString(",") { "\"$it\"" }
        return """{"tag":"daily_report","key":"steps","time":$time,"zone_offset":28800,"sid":"synthetic-aggregate","zone_name":"Asia/Shanghai","source_sid_list":[$sources],"value":"{\"steps\":$steps,\"distance\":$distance,\"calories\":$calories,\"goal\":6000}"}"""
    }

    private companion object {
        const val START = 1_777_305_600L
        const val END = START + 86_400L
    }
}
