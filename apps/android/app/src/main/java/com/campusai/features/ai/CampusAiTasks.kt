package com.campusai.features.ai

import com.campusai.core.model.CourseSchedule
import com.campusai.core.model.TimeRecord
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

enum class CampusAiTask { CHAT, HOME_INSIGHT, TODAY_SUMMARY, WEEK_SUMMARY, MONTH_SUMMARY, STRUCTURED_ADVICE, SCHEDULE_CLEANUP, TIME_PARSE }

data class CampusAiPayload(val prompt: String, val structuredContext: JSONObject)

object CampusAiTaskFactory {
    fun create(
        task: CampusAiTask,
        records: List<TimeRecord>,
        courses: List<CourseSchedule>,
        userInput: String = "",
        nowMillis: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): CampusAiPayload {
        val range = when (task) {
            CampusAiTask.MONTH_SUMMARY -> LearningRange.MONTH
            CampusAiTask.WEEK_SUMMARY -> LearningRange.WEEK
            else -> LearningRange.TODAY
        }
        val facts = learningFacts(records, range, nowMillis, zone)
        val prompt = when (task) {
            CampusAiTask.CHAT -> userInput
            CampusAiTask.HOME_INSIGHT -> "只根据已计算数据生成一句今日洞察和一个可执行动作。"
            CampusAiTask.TODAY_SUMMARY -> "总结今日学习数据：先结论，再给两条行动。不得重新计算数字。"
            CampusAiTask.WEEK_SUMMARY -> "总结本周学习数据：说明趋势，再给下周行动。不得重新计算数字。"
            CampusAiTask.MONTH_SUMMARY -> "总结本月学习数据：说明投入和目标完成情况。不得重新计算数字。"
            CampusAiTask.STRUCTURED_ADVICE -> "根据结构化学习数据给三条建议，所有数字原样保留。"
            CampusAiTask.SCHEDULE_CLEANUP -> "整理课程表字段并指出已计算出的时间冲突；缺失字段标记待确认，不得猜测。"
            CampusAiTask.TIME_PARSE -> "把自然语言记录整理成标题、分类、开始时间、结束时间和时长；只使用解析器已确定的字段，未知项标记待确认。"
        }
        val context = JSONObject().put("task", task.name.lowercase()).put("learningFacts", facts)
        if (task == CampusAiTask.SCHEDULE_CLEANUP) context.put("scheduleFacts", scheduleFacts(courses))
        if (task == CampusAiTask.TIME_PARSE) context.put("timeParseFacts", parseTimeFacts(userInput, nowMillis, zone))
        return CampusAiPayload(prompt, context)
    }

    private enum class LearningRange { TODAY, WEEK, MONTH }

    private fun learningFacts(records: List<TimeRecord>, range: LearningRange, nowMillis: Long, zone: ZoneId): JSONObject {
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        val startDate = when (range) {
            LearningRange.TODAY -> today
            LearningRange.WEEK -> today.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
            LearningRange.MONTH -> today.withDayOfMonth(1)
        }
        val start = startDate.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val selected = records.filter { it.startTime in start until end }
        val total = selected.sumOf(TimeRecord::durationMinutes)
        val days = generateSequence(startDate) { it.plusDays(1) }.takeWhile { !it.isAfter(today) }.toList()
        val daily = JSONArray(days.map { day ->
            val dayStart = day.atStartOfDay(zone).toInstant().toEpochMilli()
            val dayEnd = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            JSONObject().put("date", day.toString()).put("minutes", selected.filter { it.startTime in dayStart until dayEnd }.sumOf(TimeRecord::durationMinutes))
        })
        val categories = JSONObject()
        selected.groupBy(TimeRecord::category).toSortedMap().forEach { (category, values) -> categories.put(category, values.sumOf(TimeRecord::durationMinutes)) }
        val target = when (range) { LearningRange.TODAY -> 240L; LearningRange.WEEK -> 1_680L; LearningRange.MONTH -> 7_200L }
        val goalRateBasisPoints = if (target == 0L) 0L else total * 10_000L / target
        return JSONObject()
            .put("range", range.name.lowercase())
            .put("startDate", startDate.toString())
            .put("endDate", today.toString())
            .put("recordCount", selected.size)
            .put("totalMinutes", total)
            .put("targetMinutes", target)
            .put("goalRateBasisPoints", goalRateBasisPoints)
            .put("categoryMinutes", categories)
            .put("dailyMinutes", daily)
    }

    private fun scheduleFacts(courses: List<CourseSchedule>): JSONObject {
        val rows = JSONArray(courses.map { course -> JSONObject()
            .put("name", course.name).put("weekday", course.weekday)
            .put("startMinute", course.startMinute).put("endMinute", course.endMinute)
            .put("location", course.location).put("teacher", course.teacher).put("weeks", course.weeks)
        })
        val conflicts = JSONArray()
        courses.forEachIndexed { index, first -> courses.drop(index + 1).forEach { second ->
            if (first.weekday == second.weekday && first.startMinute < second.endMinute && second.startMinute < first.endMinute) {
                conflicts.put(JSONObject().put("first", first.name).put("second", second.name).put("weekday", first.weekday))
            }
        } }
        return JSONObject().put("courses", rows).put("computedConflicts", conflicts)
    }

    private fun parseTimeFacts(input: String, nowMillis: Long, zone: ZoneId): JSONObject {
        val number = "[零一二两三四五六七八九十百\\d]{1,4}"
        val hours = Regex("($number)\\s*(?:小时|h)").find(input)?.groupValues?.get(1)?.let(::parseChineseNumber) ?: 0L
        val minutes = Regex("($number)\\s*(?:分钟|分|min)").find(input)?.groupValues?.get(1)?.let(::parseChineseNumber) ?: 0L
        val duration = (hours * 60 + minutes).takeIf { it > 0 }
        val now = Instant.ofEpochMilli(nowMillis).atZone(zone)
        return JSONObject()
            .put("sourceText", input.take(500))
            .put("anchorTime", now.toOffsetDateTime().toString())
            .put("durationMinutes", duration ?: JSONObject.NULL)
            .put("startsNow", Regex("(?:从)?现在开始|刚才").containsMatchIn(input))
            .put("requiresConfirmation", duration == null)
    }

    private fun parseChineseNumber(raw: String): Long? {
        raw.toLongOrNull()?.let { return it }
        val digits = mapOf('零' to 0, '一' to 1, '二' to 2, '两' to 2, '三' to 3, '四' to 4, '五' to 5, '六' to 6, '七' to 7, '八' to 8, '九' to 9)
        var total = 0L
        var current = 0L
        raw.forEach { char -> when (char) {
            '十' -> { total += (if (current == 0L) 1L else current) * 10L; current = 0 }
            '百' -> { total += (if (current == 0L) 1L else current) * 100L; current = 0 }
            else -> current = digits[char]?.toLong() ?: return null
        } }
        return total + current
    }
}
