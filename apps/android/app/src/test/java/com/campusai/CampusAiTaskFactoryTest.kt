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

    @Test fun `ordinary chat payload does not contain learning analysis facts`() {
        val payload = CampusAiTaskFactory.create(
            CampusAiTask.CHAT,
            listOf(record("高数", "学习", 90)),
            emptyList(),
            userInput = "你好",
            nowMillis = now,
            zone = zone,
        )
        assertEquals("chat", payload.structuredContext.getString("task"))
        assertTrue(!payload.structuredContext.has("learningFacts"))
        assertEquals("你好", payload.prompt)
    }

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
        assertEquals(103L, facts.getLong("remainingTargetMinutes"))
        assertEquals(8_759L, facts.getJSONObject("categoryShareBasisPoints").getLong("学习"))
        assertEquals(1, facts.getInt("activeDays"))
        assertEquals(1, facts.getInt("currentStreakDays"))
        assertEquals(90L, facts.getLong("longestSessionMinutes"))
        assertEquals("insufficient_data", facts.getString("trendDirection"))
        val allowedSubjects = facts.getJSONArray("allowedActionSubjects")
        assertEquals(3, allowedSubjects.length())
        assertEquals("运动", allowedSubjects.getString(0))
        assertEquals("英语", allowedSubjects.getString(1))
        assertEquals("高数", allowedSubjects.getString(2))
        assertTrue(payload.prompt.contains("suggestedActionPlan"))
        val plan = facts.getJSONObject("suggestedActionPlan")
        assertEquals(2, plan.getJSONArray("blocks").length())
        assertEquals(50L, plan.getJSONArray("blocks").getJSONObject(0).getLong("durationMinutes"))
        assertEquals(10L, plan.getLong("breakMinutesBetweenBlocks"))
        assertEquals(100L, plan.getLong("coveredMinutes"))
        assertEquals(3L, plan.getLong("remainingAfterPlanMinutes"))
        val statements = facts.getJSONArray("analysisStatements")
        assertEquals("今日已记录137分钟，目标240分钟，剩余103分钟。", statements.getString(0))
        assertTrue(statements.toString().contains("投入最多的分类是学习"))
        assertTrue(statements.toString().contains("今日"))
        assertTrue(!statements.toString().contains("本周"))
        assertEquals("今日总结", payload.displayPrompt)
        assertTrue(!payload.displayPrompt.contains("analysisStatements"))
        assertEquals("今日目标还差 103 分钟", payload.presentation?.headline)
        assertEquals(2, payload.presentation?.actionBlocks?.size)
    }

    @Test fun `weekly trend streak peak and shares are computed before prompting`() {
        val records = listOf(
            recordAt(2026, 8, 17, "高数", "学习", 30),
            recordAt(2026, 8, 18, "英语", "学习", 30),
            recordAt(2026, 8, 20, "跑步", "运动", 60),
            recordAt(2026, 8, 21, "物理", "学习", 90),
            recordAt(2026, 8, 22, "项目", "学习", 120),
        )
        val payload = CampusAiTaskFactory.create(CampusAiTask.WEEK_SUMMARY, records, emptyList(), nowMillis = now, zone = zone)
        val facts = payload.structuredContext.getJSONObject("learningFacts")

        assertEquals(330L, facts.getLong("totalMinutes"))
        assertEquals(5, facts.getInt("activeDays"))
        assertEquals(3, facts.getInt("currentStreakDays"))
        assertEquals("2026-08-22", facts.getString("peakDate"))
        assertEquals(120L, facts.getLong("peakMinutes"))
        assertEquals("up", facts.getString("trendDirection"))
        assertEquals(70L, facts.getLong("trendDeltaAverageMinutes"))
        assertEquals(20L, facts.getLong("earlierAverageMinutes"))
        assertEquals(90L, facts.getLong("laterAverageMinutes"))
        assertEquals(8_181L, facts.getJSONObject("categoryShareBasisPoints").getLong("学习"))
        assertTrue(payload.prompt.contains("analysisStatements"))
        val allowedSubjects = facts.getJSONArray("allowedActionSubjects")
        assertEquals("项目", allowedSubjects.getString(0))
        assertEquals("物理", allowedSubjects.getString(1))
        val plan = facts.getJSONObject("suggestedActionPlan")
        assertEquals("项目", plan.getJSONArray("blocks").getJSONObject(0).getString("subject"))
        assertEquals("物理", plan.getJSONArray("blocks").getJSONObject(1).getString("subject"))
        assertEquals(1_250L, plan.getLong("remainingAfterPlanMinutes"))
        assertTrue(facts.getJSONArray("analysisStatements").toString().contains("后半段日均比前半段增加70分钟"))
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

    private fun recordAt(
        year: Int,
        month: Int,
        day: Int,
        title: String,
        category: String,
        minutes: Long,
    ): TimeRecord {
        val start = LocalDateTime.of(year, month, day, 10, 0).atZone(zone).toInstant().toEpochMilli()
        return TimeRecord(
            title = title,
            category = category,
            startTime = start,
            endTime = start + minutes * 60_000L,
            durationMinutes = minutes,
            remark = "",
        )
    }
}
