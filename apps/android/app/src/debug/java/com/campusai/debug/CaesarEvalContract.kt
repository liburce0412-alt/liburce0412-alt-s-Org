package com.campusai.debug

import android.content.Context
import com.campusai.core.agent.CaesarIntentEvidence
import com.campusai.core.model.AiConversationMessage
import org.json.JSONArray
import org.json.JSONObject

data class CaesarEvalCase(
    val id: String,
    val category: String,
    val prompt: String,
    val context: JSONObject,
    val mustPreserve: List<String>,
    val mustNotContain: List<String> = emptyList(),
    val history: List<AiConversationMessage> = emptyList(),
    val tools: JSONArray = JSONArray(),
    val expectedTool: CaesarEvalExpectedTool? = null,
    val imageResource: String? = null,
) {
    fun conversationMessages(): List<AiConversationMessage> =
        history + AiConversationMessage("user", prompt)

    fun structuredContextJson(): String = JSONObject(context.toString())
        .put("task", if (category == "chat") "chat" else category)
        .put("evalCaseId", id)
        .toString()
}

data class CaesarEvalExpectedTool(val name: String, val arguments: JSONObject)
data class CaesarEvalToolCall(val name: String, val arguments: JSONObject)

data class CaesarEvalScore(
    val nonBlank: Boolean,
    val completed: Boolean,
    val noUnexpectedToolCall: Boolean,
    val toolCallMatched: Boolean,
    val preserved: List<String>,
    val missing: List<String>,
    val forbiddenFound: List<String>,
    val preserveRatio: Double,
    val passed: Boolean,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("nonBlank", nonBlank)
        .put("completed", completed)
        .put("noUnexpectedToolCall", noUnexpectedToolCall)
        .put("toolCallMatched", toolCallMatched)
        .put("preserved", JSONArray(preserved))
        .put("missing", JSONArray(missing))
        .put("forbiddenFound", JSONArray(forbiddenFound))
        .put("preserveRatio", preserveRatio)
        .put("passed", passed)
}

object CaesarEvalDataset {
    const val ASSET_NAME = "caesar_eval_v1.json"
    val REQUIRED_CATEGORIES = setOf("chat", "study_summary", "time_parse", "schedule_cleanup", "vision", "app_tool", "health_context", "health_tool")

    fun load(context: Context): List<CaesarEvalCase> =
        context.assets.open(ASSET_NAME).bufferedReader().use { parse(it.readText()) }

    fun parse(raw: String): List<CaesarEvalCase> {
        val source = JSONArray(raw)
        return List(source.length()) { index ->
            val item = source.getJSONObject(index)
            val preserve = item.getJSONArray("mustPreserve")
            val forbidden = item.optJSONArray("mustNotContain") ?: JSONArray()
            val history = item.optJSONArray("history") ?: JSONArray()
            CaesarEvalCase(
                id = item.getString("id"),
                category = item.getString("category"),
                prompt = item.getString("prompt"),
                context = JSONObject(item.getJSONObject("context").toString()),
                mustPreserve = List(preserve.length(), preserve::getString),
                mustNotContain = List(forbidden.length(), forbidden::getString),
                history = List(history.length()) { historyIndex ->
                    val message = history.getJSONObject(historyIndex)
                    val role = message.getString("role")
                    require(role == "user" || role == "assistant") { "Eval history role is not allowed" }
                    AiConversationMessage(role, message.getString("content"))
                },
                tools = JSONArray(item.optJSONArray("tools")?.toString() ?: "[]"),
                expectedTool = item.optJSONObject("expectedTool")?.let { expected ->
                    CaesarEvalExpectedTool(expected.getString("name"), JSONObject(expected.getJSONObject("arguments").toString()))
                },
                imageResource = item.optString("imageResource").takeIf(String::isNotBlank),
            )
        }
    }
}

object CaesarEvalScorer {
    fun score(
        case: CaesarEvalCase,
        output: String,
        completed: Boolean,
        errorCode: String?,
        toolCalls: List<CaesarEvalToolCall>,
    ): CaesarEvalScore {
        val preserved = case.mustPreserve.filter(output::contains)
        val missing = case.mustPreserve - preserved.toSet()
        val forbiddenFound = case.mustNotContain.filter(output::contains)
        val preserveRatio = if (case.mustPreserve.isEmpty()) 1.0 else preserved.size.toDouble() / case.mustPreserve.size
        val toolCallMatched = case.expectedTool?.let { expected ->
            val actual = toolCalls.singleOrNull() ?: return@let false
            actual.name == expected.name &&
                CaesarIntentEvidence.canonicalArguments(actual.arguments) == CaesarIntentEvidence.canonicalArguments(expected.arguments)
        } ?: toolCalls.isEmpty()
        val noUnexpectedToolCall = toolCallMatched
        val nonBlank = case.expectedTool != null || output.isNotBlank()
        val effectivelyCompleted = if (case.expectedTool != null) toolCallMatched else completed
        return CaesarEvalScore(
            nonBlank = nonBlank,
            completed = effectivelyCompleted,
            noUnexpectedToolCall = noUnexpectedToolCall,
            toolCallMatched = toolCallMatched,
            preserved = preserved,
            missing = missing,
            forbiddenFound = forbiddenFound,
            preserveRatio = preserveRatio,
            passed = nonBlank && effectivelyCompleted && errorCode == null && noUnexpectedToolCall &&
                missing.isEmpty() && forbiddenFound.isEmpty(),
        )
    }
}

object CaesarEvalSummary {
    fun summarize(results: JSONArray): JSONObject {
        val firstTokens = mutableListOf<Double>()
        val nativeFirstTokens = mutableListOf<Double>()
        val visibleFirstTokens = mutableListOf<Double>()
        val wallTimes = mutableListOf<Double>()
        val speeds = mutableListOf<Double>()
        var passed = 0
        repeat(results.length()) { index ->
            val result = results.getJSONObject(index)
            if (result.optBoolean("success")) passed++
            result.optPositiveDoubleOrNull("firstTokenMs")?.let(firstTokens::add)
            result.optNonNegativeDoubleOrNull("nativeFirstTokenMs")?.let(nativeFirstTokens::add)
            result.optPositiveDoubleOrNull("visibleFirstTokenMs")?.let(visibleFirstTokens::add)
            result.optNonNegativeDoubleOrNull("wallElapsedMs")?.let(wallTimes::add)
            result.optNonNegativeDoubleOrNull("tokensPerSecond")?.let(speeds::add)
        }
        return JSONObject()
            .put("cases", results.length())
            .put("passed", passed)
            .put("failed", results.length() - passed)
            .put("passRate", if (results.length() == 0) 0.0 else passed.toDouble() / results.length())
            .put("ttftMetric", "visibleFirstTokenMs")
            .put("visibleFirstTokenP50Ms", median(visibleFirstTokens) ?: JSONObject.NULL)
            .put("firstTokenP50Ms", median(firstTokens) ?: JSONObject.NULL)
            .put("nativeFirstTokenP50Ms", median(nativeFirstTokens) ?: JSONObject.NULL)
            .put("wallElapsedP50Ms", median(wallTimes) ?: JSONObject.NULL)
            .put("tokensPerSecondP50", median(speeds) ?: JSONObject.NULL)
    }

    private fun JSONObject.optPositiveDoubleOrNull(name: String): Double? =
        optFiniteDoubleOrNull(name)?.takeIf { it > 0.0 }

    private fun JSONObject.optNonNegativeDoubleOrNull(name: String): Double? =
        optFiniteDoubleOrNull(name)?.takeIf { it >= 0.0 }

    private fun JSONObject.optFiniteDoubleOrNull(name: String): Double? =
        takeIf { has(name) && !isNull(name) }?.optDouble(name)?.takeIf(Double::isFinite)

    private fun median(values: List<Double>): Double? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[middle] else (sorted[middle - 1] + sorted[middle]) / 2.0
    }
}
