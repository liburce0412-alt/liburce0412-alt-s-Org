package com.campusai

import androidx.test.core.app.ApplicationProvider
import com.campusai.debug.CaesarEvalDataset
import com.campusai.debug.CaesarEvalScorer
import com.campusai.debug.CaesarEvalSummary
import com.campusai.debug.CaesarEvalToolCall
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CaesarEvalScorerTest {
    @Test fun `device eval keeps all thirty quality cases and adds twenty regressions`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val cases = CaesarEvalDataset.load(context)
        assertEquals(50, cases.size)
        assertEquals(50, cases.map { it.id }.distinct().size)
        assertEquals(CaesarEvalDataset.REQUIRED_CATEGORIES, cases.map { it.category }.toSet())

        val fullRaw = checkNotNull(javaClass.classLoader?.getResourceAsStream("localai/campusai_zh_quality.json"))
            .bufferedReader().use { it.readText() }
        val full = JSONArray(fullRaw)
        val fullById = buildMap<String, JSONObject> {
            repeat(full.length()) { index ->
                val item = full.getJSONObject(index)
                put(item.getString("id"), item)
            }
        }
        cases.take(30).forEach { case ->
            val source = checkNotNull(fullById[case.id])
            assertEquals(source.getString("category"), case.category)
            assertEquals(source.getString("prompt"), case.prompt)
            assertEquals(source.getJSONObject("context").toString(), case.context.toString())
            assertEquals(
                List(source.getJSONArray("mustPreserve").length(), source.getJSONArray("mustPreserve")::getString),
                case.mustPreserve,
            )
        }
        assertEquals(fullById.keys.toList(), cases.take(30).map { it.id })
        val followUp = cases.first { it.id == "followup-01" }
        assertEquals(listOf("user", "assistant", "user"), followUp.conversationMessages().map { it.role })
        assertTrue(cases.first { it.id == "utf8-name-01" }.mustNotContain.contains("�"))
        assertEquals("campusai_brand_mark", cases.first { it.id == "vision-01" }.imageResource)
        assertEquals("time.list_records", cases.first { it.id == "app-tool-01" }.expectedTool?.name)
        assertEquals("health.get_snapshot", cases.first { it.id == "health-tool-01" }.expectedTool?.name)
    }

    @Test fun `static score requires completion nonblank output and every preserved fact`() {
        val case = CaesarEvalDataset.parse(
            """[{"id":"one","category":"chat","prompt":"p","context":{},"mustPreserve":["60","复习"]}]""",
        ).single()
        val score = CaesarEvalScorer.score(case, "60 分钟：先复习再运动。", completed = true, errorCode = null, toolCalls = emptyList())
        assertTrue(score.passed)
        assertEquals(1.0, score.preserveRatio, 0.0)
        assertTrue(score.toJson().getBoolean("noUnexpectedToolCall"))

        val missing = CaesarEvalScorer.score(case, "先复习。", completed = true, errorCode = null, toolCalls = emptyList())
        assertFalse(missing.passed)
        assertEquals(listOf("60"), missing.missing)
        assertEquals(0.5, missing.toJson().getDouble("preserveRatio"), 0.0)
    }

    @Test fun `static score rejects tool calls errors and incomplete generations`() {
        val case = CaesarEvalDataset.parse(
            """[{"id":"one","category":"chat","prompt":"p","context":{},"mustPreserve":["60"]}]""",
        ).single()
        assertFalse(CaesarEvalScorer.score(case, "60", true, null, listOf(CaesarEvalToolCall("time.create", JSONObject()))).passed)
        assertFalse(CaesarEvalScorer.score(case, "60", true, "local_failure", emptyList()).passed)
        assertFalse(CaesarEvalScorer.score(case, "60", false, null, emptyList()).passed)
        assertFalse(CaesarEvalScorer.score(case, "", true, null, emptyList()).passed)
    }

    @Test fun `expected tool call requires exact name and canonical arguments without execution`() {
        val case = CaesarEvalDataset.parse(
            """[{"id":"tool","category":"app_tool","prompt":"p","context":{},"mustPreserve":[],"tools":[],"expectedTool":{"name":"time.list_records","arguments":{"limit":5}}}]""",
        ).single()
        val matching = CaesarEvalScorer.score(
            case,
            output = "",
            completed = false,
            errorCode = null,
            toolCalls = listOf(CaesarEvalToolCall("time.list_records", JSONObject("{\"limit\":5}"))),
        )
        assertTrue(matching.passed)
        assertTrue(matching.toolCallMatched)
        assertFalse(
            CaesarEvalScorer.score(
                case,
                "",
                false,
                null,
                listOf(CaesarEvalToolCall("time.list_records", JSONObject("{\"limit\":6}"))),
            ).passed,
        )
    }

    @Test fun `summary uses visible TTFT when native timing reports zero`() {
        val results = JSONArray()
            .put(JSONObject()
                .put("success", true)
                .put("firstTokenMs", 120.0)
                .put("nativeFirstTokenMs", 0.0)
                .put("visibleFirstTokenMs", 120.0)
                .put("wallElapsedMs", 1_000.0)
                .put("tokensPerSecond", 8.0))
            .put(JSONObject()
                .put("success", true)
                .put("firstTokenMs", 200.0)
                .put("nativeFirstTokenMs", 0.0)
                .put("visibleFirstTokenMs", 200.0)
                .put("wallElapsedMs", 2_000.0)
                .put("tokensPerSecond", 10.0))

        val summary = CaesarEvalSummary.summarize(results)
        assertEquals("visibleFirstTokenMs", summary.getString("ttftMetric"))
        assertEquals(160.0, summary.getDouble("visibleFirstTokenP50Ms"), 0.0)
        assertEquals(160.0, summary.getDouble("firstTokenP50Ms"), 0.0)
        assertEquals(0.0, summary.getDouble("nativeFirstTokenP50Ms"), 0.0)
    }

    @Test fun `must preserve remains strict literal matching`() {
        val case = CaesarEvalDataset.parse(
            """[{"id":"one","category":"study_summary","prompt":"p","context":{},"mustPreserve":["高数"]}]""",
        ).single()
        val score = CaesarEvalScorer.score(case, "今天重点是高等数学。", true, null, emptyList())
        assertFalse(score.passed)
        assertEquals(listOf("高数"), score.missing)
    }

    @Test fun `forbidden regressions fail even when required facts are preserved`() {
        val case = CaesarEvalDataset.parse(
            """[{"id":"utf8","category":"chat","prompt":"p","context":{},"mustPreserve":["Caesar"],"mustNotContain":["�"]}]""",
        ).single()

        val score = CaesarEvalScorer.score(case, "我是 Caesar�。", true, null, emptyList())

        assertFalse(score.passed)
        assertEquals(listOf("�"), score.forbiddenFound)
        assertEquals(listOf("�"), List(score.toJson().getJSONArray("forbiddenFound").length(), score.toJson().getJSONArray("forbiddenFound")::getString))
    }
}
