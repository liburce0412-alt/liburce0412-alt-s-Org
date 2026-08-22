package com.campusai

import com.campusai.core.model.CourseSchedule
import com.campusai.core.model.TimeRecord
import com.campusai.features.ai.CampusAiTask
import com.campusai.features.ai.CampusAiTaskFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDateTime
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
class CampusAiTaskFactoryTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val now = LocalDateTime.of(2026, 8, 22, 20, 0).atZone(zone).toInstant().toEpochMilli()

    @Test fun `Kotlin computes exact learning totals before model sees them`() {
        val records = listOf(
            record("高数", "学习", 90), record("英语", "学习", 30), record("运动", "运动", 17),
        )
        val payload = CampusAiTaskFactory.create(CampusAiTask.TODAY_SUMMARY, records, emptyList(), nowMillis = now, zone = zone)
        val facts = payload.structuredContext.getJSONObject("learningFacts")
        assertEquals(137L, facts.getLong("totalMinutes"))
        assertEquals(3, facts.getInt("recordCount"))
        assertEquals(120L, facts.getJSONObject("categoryMinutes").getLong("学习"))
        assertEquals(17L, facts.getJSONObject("categoryMinutes").getLong("运动"))
    }

    @Test fun `course conflicts and time duration are deterministic facts`() {
        val courses = listOf(
            CourseSchedule(name = "高数", weekday = 1, startMinute = 480, endMinute = 570, sourceHash = "a"),
            CourseSchedule(name = "英语", weekday = 1, startMinute = 540, endMinute = 600, sourceHash = "b"),
        )
        val schedule = CampusAiTaskFactory.create(CampusAiTask.SCHEDULE_CLEANUP, emptyList(), courses, nowMillis = now, zone = zone)
        assertEquals(1, schedule.structuredContext.getJSONObject("scheduleFacts").getJSONArray("computedConflicts").length())
        val parsed = CampusAiTaskFactory.create(CampusAiTask.TIME_PARSE, emptyList(), emptyList(), "刚才写了1小时15分钟报告", now, zone)
            .structuredContext.getJSONObject("timeParseFacts")
        assertEquals(75L, parsed.getLong("durationMinutes"))
        assertTrue(parsed.getBoolean("startsNow"))
        val chinese = CampusAiTaskFactory.create(CampusAiTask.TIME_PARSE, emptyList(), emptyList(), "刚才背了四十分钟单词", now, zone)
            .structuredContext.getJSONObject("timeParseFacts")
        assertEquals(40L, chinese.getLong("durationMinutes"))
    }

    private fun record(title: String, category: String, minutes: Long): TimeRecord = TimeRecord(
        title = title, category = category, startTime = now - minutes * 60_000L,
        endTime = now, durationMinutes = minutes, remark = "",
    )
}
