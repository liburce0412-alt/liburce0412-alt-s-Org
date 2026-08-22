package com.campusai

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LocalQualityDatasetTest {
    @Test fun `contains thirty Chinese CampusAI prompts across required tasks`() {
        val raw = checkNotNull(javaClass.classLoader?.getResourceAsStream("localai/campusai_zh_quality.json"))
            .bufferedReader().use { it.readText() }
        val entries = JSONArray(raw)
        assertEquals(30, entries.length())
        val ids = mutableSetOf<String>()
        val categories = mutableSetOf<String>()
        repeat(entries.length()) { index ->
            val item = entries.getJSONObject(index)
            assertTrue(ids.add(item.getString("id")))
            categories += item.getString("category")
            assertTrue(item.getString("prompt").isNotBlank())
            assertTrue(item.getJSONArray("mustPreserve").length() > 0)
        }
        assertEquals(setOf("chat", "study_summary", "time_parse", "schedule_cleanup"), categories)
    }
}
