package com.campusai.core.agent

import org.json.JSONArray
import org.json.JSONObject

data class CaesarSurface(
    val id: String,
    val title: String,
    val components: List<CaesarComponent>,
) {
    init {
        require(id.length in 1..80)
        require(title.length <= 120)
        require(components.size <= MAX_COMPONENTS)
    }

    fun toJson(): String = JSONObject()
        .put("schema", SCHEMA)
        .put("id", id)
        .put("title", title)
        .put("components", JSONArray(components.map(CaesarComponent::toJson)))
        .toString()

    companion object {
        const val SCHEMA = "caesar.surface.v1"
        private const val MAX_BYTES = 65_536
        private const val MAX_COMPONENTS = 32

        fun fromJson(raw: String?): CaesarSurface? {
            if (raw.isNullOrBlank() || raw.toByteArray().size > MAX_BYTES) return null
            return runCatching {
                val root = JSONObject(raw)
                if (root.optString("schema") != SCHEMA) return null
                val rows = root.optJSONArray("components") ?: JSONArray()
                if (rows.length() > MAX_COMPONENTS) return null
                CaesarSurface(
                    id = root.getString("id"),
                    title = root.optString("title").take(120),
                    components = buildList {
                        repeat(rows.length()) { index ->
                            add(CaesarComponent.fromJson(rows.getJSONObject(index)) ?: error("Unknown Caesar component"))
                        }
                    },
                )
            }.getOrNull()
        }
    }
}

sealed interface CaesarComponent {
    fun toJson(): JSONObject

    data class Text(val text: String, val emphasis: Boolean = false) : CaesarComponent {
        override fun toJson() = JSONObject().put("type", "text").put("text", text.take(2_000)).put("emphasis", emphasis)
    }

    data class Metric(val label: String, val value: String, val freshness: String = "") : CaesarComponent {
        override fun toJson() = JSONObject().put("type", "metric").put("label", label.take(80)).put("value", value.take(120)).put("freshness", freshness.take(80))
    }

    data class ListItems(val items: List<String>) : CaesarComponent {
        override fun toJson() = JSONObject().put("type", "list").put("items", JSONArray(items.take(20).map { it.take(300) }))
    }

    data class Button(val label: String, val actionId: String, val destructive: Boolean = false) : CaesarComponent {
        init { require(actionId.matches(Regex("[a-z0-9._:-]{1,120}"))) }
        override fun toJson() = JSONObject().put("type", "button").put("label", label.take(80)).put("actionId", actionId).put("destructive", destructive)
    }

    data class Progress(val label: String, val value: Float) : CaesarComponent {
        override fun toJson() = JSONObject().put("type", "progress").put("label", label.take(80)).put("value", value.coerceIn(0f, 1f))
    }

    companion object {
        fun fromJson(json: JSONObject): CaesarComponent? = when (json.optString("type")) {
            "text" -> Text(json.optString("text").take(2_000), json.optBoolean("emphasis"))
            "metric" -> Metric(json.optString("label").take(80), json.optString("value").take(120), json.optString("freshness").take(80))
            "list" -> ListItems(buildList {
                val rows = json.optJSONArray("items") ?: JSONArray()
                repeat(rows.length().coerceAtMost(20)) { add(rows.optString(it).take(300)) }
            })
            "button" -> runCatching { Button(json.getString("label"), json.getString("actionId"), json.optBoolean("destructive")) }.getOrNull()
            "progress" -> Progress(json.optString("label").take(80), json.optDouble("value").toFloat())
            else -> null
        }
    }
}
