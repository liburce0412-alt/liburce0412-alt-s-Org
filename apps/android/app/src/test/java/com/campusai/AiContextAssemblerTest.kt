package com.campusai

import com.campusai.core.ai.AiSystemPolicy
import com.campusai.core.model.CourseSchedule
import com.campusai.core.model.TimeRecord
import com.campusai.features.ai.AiContextAssembler
import com.campusai.features.ai.AiContextSelection
import com.campusai.features.ai.AiContextSnapshot
import com.campusai.features.ai.AiPostContext
import com.campusai.features.ai.CampusAiTask
import com.campusai.features.ai.DailyGreetingPolicy
import java.time.LocalDateTime
import java.time.ZoneId
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AiContextAssemblerTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val now = LocalDateTime.of(2026, 8, 22, 15, 0).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun `range selection and statistics remain code computed`() {
        val records = listOf(
            record(2026, 8, 22, "编程", "项目", 50),
            record(2026, 8, 21, "高数", "学习", 40),
            record(2026, 8, 20, "英语", "学习", 30),
        )
        val context = AiContextAssembler.enrich(
            base = JSONObject().put("task", "chat"),
            task = CampusAiTask.CHAT,
            prompt = "分析我本周的时间记录",
            snapshot = AiContextSnapshot(records = records, displayName = "迟迟"),
            selection = AiContextSelection(),
            nowMillis = now,
            zone = zone,
        ).getJSONObject("personalContext")
        val learning = context.getJSONObject("learning")
        assertEquals("迟迟", context.getString("displayName"))
        assertEquals(50L, learning.getJSONObject("today").getLong("totalMinutes"))
        assertEquals(120L, learning.getJSONObject("week").getLong("totalMinutes"))
        assertEquals(3, learning.getJSONObject("week").getInt("activeDays"))
        assertEquals(3, learning.getJSONObject("week").getInt("currentStreakDays"))
        assertEquals(3, learning.getJSONArray("recentRelevantEntries").length())
    }

    @Test
    fun `only own related posts are included unless public posts are explicit`() {
        val longBody = "内容".repeat(200)
        val posts = listOf(
            AiPostContext("mine", "u1", longBody, "项目", 2, 1, "2026-08-22T10:00:00Z"),
            AiPostContext("other", "u2", "他人的公开动态", "校园", 3, 2, "2026-08-22T11:00:00Z"),
        )
        val snapshot = AiContextSnapshot(userId = "u1", visiblePosts = posts)
        val privateOnly = AiContextAssembler.enrich(
            JSONObject().put("task", "chat"), CampusAiTask.CHAT, "总结我的帖子", snapshot,
            AiContextSelection(), now, zone,
        ).getJSONObject("personalContext")
        assertEquals(1, privateOnly.getInt("ownPostCount"))
        assertEquals(1, privateOnly.getJSONArray("ownPosts").length())
        assertEquals(240, privateOnly.getJSONArray("ownPosts").getJSONObject(0).getString("body").length)
        assertFalse(privateOnly.has("selectedPublicPosts"))

        val explicitPublic = AiContextAssembler.enrich(
            JSONObject().put("task", "chat"), CampusAiTask.CHAT, "看看校园帖子", snapshot,
            AiContextSelection(publicPosts = true), now, zone,
        ).getJSONObject("personalContext")
        assertEquals("other", explicitPublic.getJSONArray("selectedPublicPosts").getJSONObject(0).getString("id"))
    }

    @Test
    fun `chat analysis and greeting use different behavior policies`() {
        val chat = AiSystemPolicy.instruction(JSONObject().put("task", "chat").toString())
        val analysis = AiSystemPolicy.instruction(JSONObject().put("task", "today_summary").toString())
        val greeting = AiSystemPolicy.instruction(JSONObject().put("task", "daily_greeting").toString())
        assertTrue(chat.contains("不要强制写学习分析"))
        assertTrue(analysis.contains("先给事实结论"))
        assertTrue(greeting.contains("12 至 20 个汉字"))
        assertFalse(chat.contains("只能按顺序复述"))
    }

    @Test
    fun `general chat does not inject unrelated learning or course facts`() {
        val context = AiContextAssembler.enrich(
            JSONObject().put("task", "chat"), CampusAiTask.CHAT, "Hello",
            AiContextSnapshot(
                displayName = "迟迟",
                records = listOf(record(2026, 8, 22, "编程", "项目", 50)),
                courses = listOf(CourseSchedule(name = "数据结构", weekday = 6, startMinute = 960, endMinute = 1050, sourceHash = "a")),
            ),
            AiContextSelection(), now, zone,
        ).getJSONObject("personalContext")
        assertEquals("迟迟", context.getString("displayName"))
        assertFalse(context.has("learning"))
        assertFalse(context.has("courses"))
    }

    @Test
    fun `daily greeting rejects facts that are absent and fallback is stable`() {
        val empty = AiContextSnapshot(displayName = "迟迟")
        assertFalse(DailyGreetingPolicy.isGrounded("今晚还有课，加油", empty, now, zone))
        assertFalse(DailyGreetingPolicy.isGrounded("今天已经投入了50分钟", empty, now, zone))
        assertFalse(DailyGreetingPolicy.isGrounded("今晚八点，校园静悄悄", empty, now, zone))
        assertFalse(DailyGreetingPolicy.isGrounded("今天是晴天，适合开始", empty, now, zone))
        assertTrue(DailyGreetingPolicy.isGrounded("先从眼前的一件小事开始", empty, now, zone))
        val date = LocalDateTime.of(2026, 8, 22, 0, 0).toLocalDate()
        assertEquals(DailyGreetingPolicy.fallback("迟迟", date), DailyGreetingPolicy.fallback("迟迟", date))
    }

    @Test
    fun `next course and conflicts are computed before prompting`() {
        val courses = listOf(
            CourseSchedule(name = "数据结构", weekday = 6, startMinute = 960, endMinute = 1050, sourceHash = "a"),
            CourseSchedule(name = "实验", weekday = 6, startMinute = 1020, endMinute = 1100, sourceHash = "b"),
        )
        val context = AiContextAssembler.enrich(
            JSONObject().put("task", "chat"), CampusAiTask.CHAT, "下一节课是什么",
            AiContextSnapshot(courses = courses), AiContextSelection(), now, zone,
        ).getJSONObject("personalContext").getJSONObject("courses")
        assertEquals("数据结构", context.getJSONObject("nextCourse").getString("name"))
        assertEquals(1, context.getJSONArray("computedConflicts").length())
    }

    private fun record(year: Int, month: Int, day: Int, title: String, category: String, minutes: Long): TimeRecord {
        val start = LocalDateTime.of(year, month, day, 10, 0).atZone(zone).toInstant().toEpochMilli()
        return TimeRecord(title = title, category = category, startTime = start, endTime = start + minutes * 60_000, durationMinutes = minutes, remark = "")
    }
}
