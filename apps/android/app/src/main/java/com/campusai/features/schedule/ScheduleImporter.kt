package com.campusai.features.schedule

import android.content.Context
import android.graphics.Rect
import android.net.Uri
import com.campusai.core.model.CourseSchedule
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import java.security.MessageDigest
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.abs

data class CourseDraft(
    val name: String,
    val weekday: Int,
    val startMinute: Int,
    val endMinute: Int,
    val location: String = "",
    val teacher: String = "",
    val weeks: String = "",
) {
    fun toCourse() = CourseSchedule(
        name = name.trim(), weekday = weekday.coerceIn(1, 7), startMinute = startMinute, endMinute = endMinute,
        location = location.trim(), teacher = teacher.trim(), weeks = weeks.trim(),
        sourceHash = stableHash("${name.trim()}|$weekday|$startMinute|$endMinute|${location.trim()}|${weeks.trim()}"),
    )
}

object ScheduleImporter {
    suspend fun fromImage(context: Context, uri: Uri): List<CourseDraft> {
        val sourceBytes = context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
        require(sourceBytes < 0 || sourceBytes <= 25L * 1024 * 1024) { "图片超过 25 MB，请先截取课程表区域后再试" }
        val image = InputImage.fromFilePath(context, uri)
        val recognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
        return try {
            val result = suspendCancellableCoroutine<Text> { continuation ->
                val task = recognizer.process(image)
                task.addOnSuccessListener { if (continuation.isActive) continuation.resume(it) }
                task.addOnFailureListener { if (continuation.isActive) continuation.resumeWithException(it) }
            }
            parseRecognizedText(result, image.width, image.height)
        } finally { recognizer.close() }
    }

    fun fromIcs(context: Context, uri: Uri): List<CourseDraft> {
        val raw = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
            val result = StringBuilder()
            val buffer = CharArray(8192)
            while (true) {
                val count = reader.read(buffer)
                if (count < 0) break
                require(result.length + count <= 1_000_000) { "日历文件超过 1 MB" }
                result.append(buffer, 0, count)
            }
            result.toString()
        }
            ?: error("无法读取所选日历文件")
        return fromIcsText(raw)
    }

    internal fun fromIcsText(raw: String): List<CourseDraft> {
        val unfolded = raw.replace(Regex("\\r?\\n[ \\t]"), "")
        return Regex("BEGIN:VEVENT(.*?)END:VEVENT", RegexOption.DOT_MATCHES_ALL).findAll(unfolded).mapNotNull { event ->
            val body = event.groupValues[1]
            val summary = property(body, "SUMMARY")?.unescapeIcs()?.trim().orEmpty()
            val start = property(body, "DTSTART")?.let(::parseIcsDate) ?: return@mapNotNull null
            val end = property(body, "DTEND")?.let(::parseIcsDate) ?: start.plusMinutes(100)
            if (summary.isBlank()) return@mapNotNull null
            CourseDraft(
                name = summary,
                weekday = start.dayOfWeek.value,
                startMinute = start.hour * 60 + start.minute,
                endMinute = end.hour * 60 + end.minute,
                location = property(body, "LOCATION")?.unescapeIcs().orEmpty(),
                teacher = property(body, "DESCRIPTION")?.unescapeIcs()?.lineSequence()?.firstOrNull { it.contains("教师") || it.contains("老师") }?.substringAfter(':').orEmpty(),
                weeks = property(body, "RRULE")?.let { rule -> if ("WEEKLY" in rule) "每周" else rule }.orEmpty(),
            )
        }.distinctBy { it.toCourse().sourceHash }.toList()
    }

    private fun parseRecognizedText(text: Text, width: Int, height: Int): List<CourseDraft> {
        data class Line(val text: String, val box: Rect)
        val lines = text.textBlocks.flatMap { it.lines }.mapNotNull { line -> line.boundingBox?.let { Line(line.text.trim(), it) } }.filter { it.text.isNotBlank() }
        val dayPattern = Regex("(?:星期|周)?([一二三四五六日天])")
        val dayNumber = mapOf('一' to 1, '二' to 2, '三' to 3, '四' to 4, '五' to 5, '六' to 6, '日' to 7, '天' to 7)
        val anchors = lines.mapNotNull { line -> dayPattern.find(line.text)?.groupValues?.getOrNull(1)?.firstOrNull()?.let { dayNumber[it] }?.let { it to line.box.centerX() } }.distinctBy { it.first }
        val contentTop = (anchors.maxOfOrNull { pair -> lines.firstOrNull { dayPattern.containsMatchIn(it.text) && it.box.centerX() == pair.second }?.box?.bottom ?: 0 } ?: (height * .12f).toInt()).coerceAtLeast((height * .08f).toInt())
        val defaultSlots = listOf(8*60 to 9*60+40, 10*60 to 11*60+40, 14*60 to 15*60+40, 16*60 to 17*60+40, 19*60 to 20*60+40)
        val noise = Regex("^(星期|周)[一二三四五六日天]$|^第?\\d+[节周]?$|^\\d{1,2}[:：]\\d{2}.*|^上午$|^下午$|^晚上$")
        val candidates = lines.filter { it.box.centerY() > contentTop && it.text.length >= 2 && !noise.matches(it.text.replace(" ", "")) }
        val grouped = candidates.groupBy { line ->
            val day = if (anchors.isNotEmpty()) anchors.minBy { abs(it.second - line.box.centerX()) }.first else ((line.box.centerX().toFloat() / width * 7).toInt() + 1).coerceIn(1, 7)
            val normalized = ((line.box.centerY() - contentTop).toFloat() / (height - contentTop).coerceAtLeast(1)).coerceIn(0f, .999f)
            day to (normalized * defaultSlots.size).toInt().coerceIn(defaultSlots.indices)
        }
        return grouped.mapNotNull { (key, values) ->
            val ordered = values.sortedBy { it.box.top }.map { it.text }.distinct()
            val meaningful = ordered.filterNot { it.length == 1 || Regex("^[0-9-]+$").matches(it) }
            if (meaningful.isEmpty()) return@mapNotNull null
            val location = meaningful.firstOrNull { Regex("(楼|室|馆|教|苑|操场|中心)").containsMatchIn(it) }.orEmpty()
            val name = meaningful.firstOrNull { it != location } ?: meaningful.first()
            val slot = defaultSlots[key.second]
            CourseDraft(name=name, weekday=key.first, startMinute=slot.first, endMinute=slot.second, location=location)
        }.filter { it.name.length >= 2 }.distinctBy { it.toCourse().sourceHash }.sortedWith(compareBy({it.weekday},{it.startMinute}))
    }

    private fun property(body: String, name: String): String? = Regex("(?m)^$name(?:;[^:]*)?:(.*)$").find(body)?.groupValues?.get(1)?.trim()
    private fun parseIcsDate(raw: String): LocalDateTime = try {
        if (raw.endsWith('Z')) OffsetDateTime.parse(raw, DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmssX", Locale.ROOT)).toLocalDateTime()
        else LocalDateTime.parse(raw.take(15), DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss", Locale.ROOT))
    } catch (_: Exception) { LocalDateTime.parse(raw.take(13), DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmm", Locale.ROOT)) }
    private fun String.unescapeIcs() = replace("\\n", "\n").replace("\\,", ",").replace("\\;", ";").replace("\\\\", "\\")
}

private fun stableHash(value: String): String = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
