package com.campusai

import com.campusai.core.model.ContributionLevel
import com.campusai.core.model.DailyContributionCalculator
import com.campusai.core.model.TimeRecord
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class DailyContributionCalculatorTest {
    private val utc = ZoneId.of("UTC")

    @Test
    fun `levels use non-overlapping target boundaries`() {
        assertEquals(ContributionLevel.NONE, DailyContributionCalculator.contributionLevel(0, 100))
        assertEquals(ContributionLevel.LOW, DailyContributionCalculator.contributionLevel(24, 100))
        assertEquals(ContributionLevel.MEDIUM, DailyContributionCalculator.contributionLevel(25, 100))
        assertEquals(ContributionLevel.HIGH, DailyContributionCalculator.contributionLevel(50, 100))
        assertEquals(ContributionLevel.GOAL, DailyContributionCalculator.contributionLevel(100, 100))
    }

    @Test
    fun `records belong to completion day and use stable target snapshot`() {
        val completion = ZonedDateTime.of(2026, 1, 2, 0, 10, 0, 0, utc).toInstant().toEpochMilli()
        val records = listOf(
            record(start = completion - 30 * 60_000L, end = completion, minutes = 30),
            record(start = completion - 10 * 60_000L, end = completion, minutes = 10),
            record(start = completion, end = completion, minutes = 0),
        )
        val date = LocalDate.of(2026, 1, 2)

        val days = DailyContributionCalculator.calculate(
            year = 2026,
            records = records,
            targetSnapshots = mapOf(date to 40L),
            zoneId = utc,
            today = LocalDate.of(2026, 12, 31),
        )

        assertEquals(365, days.size)
        assertEquals(0, days.first().completedCount)
        assertEquals(2, days.first { it.date == date }.completedCount)
        assertEquals(40L, days.first { it.date == date }.durationMinutes)
        assertEquals(40L, days.first { it.date == date }.targetMinutes)
        assertEquals(ContributionLevel.GOAL, days.first { it.date == date }.level)
    }

    @Test
    fun `leap year includes February 29 and future records stay disabled`() {
        val leapDay = ZonedDateTime.of(2024, 2, 29, 12, 0, 0, 0, utc).toInstant().toEpochMilli()
        val future = ZonedDateTime.of(2024, 3, 2, 12, 0, 0, 0, utc).toInstant().toEpochMilli()
        val days = DailyContributionCalculator.calculate(
            year = 2024,
            records = listOf(
                record(leapDay - 60_000L, leapDay, 1),
                record(future - 60_000L, future, 1),
            ),
            zoneId = utc,
            today = LocalDate.of(2024, 3, 1),
        )

        assertEquals(366, days.size)
        assertEquals(1, days.first { it.date == LocalDate.of(2024, 2, 29) }.completedCount)
        assertEquals(0, days.first { it.date == LocalDate.of(2024, 3, 2) }.completedCount)
    }

    private fun record(start: Long, end: Long, minutes: Long) = TimeRecord(
        title = "专注",
        category = "学习",
        startTime = start,
        endTime = end,
        durationMinutes = minutes,
        remark = "",
    )
}
