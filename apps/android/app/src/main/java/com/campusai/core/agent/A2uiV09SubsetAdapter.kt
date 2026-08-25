package com.campusai.core.agent

import org.json.JSONObject

/**
 * Stateful, fail-closed adapter for the stable A2UI v0.9 basic catalog.
 *
 * Only static Text, layout containers, Card, List and pre-registered Button events are accepted.
 * Data bindings, functionCall, URLs and unknown components never reach the Compose renderer.
 */
class A2uiV09SubsetAdapter(private val allowedActionIds: () -> Set<String>) {
    private data class SurfaceState(
        val id: String,
        val components: MutableMap<String, JSONObject> = linkedMapOf(),
    )

    private val surfaces = mutableMapOf<String, SurfaceState>()

    fun apply(raw: String): CaesarSurface? {
        if (raw.isBlank() || raw.toByteArray().size > MAX_BYTES) return null
        return runCatching {
            val message = JSONObject(raw)
            check(message.optString("version") == VERSION)
            val messageKeys = ENVELOPES.filter(message::has)
            check(messageKeys.size == 1)
            when (messageKeys.single()) {
                "createSurface" -> create(message.getJSONObject("createSurface"))
                "updateComponents" -> update(message.getJSONObject("updateComponents"))
                "deleteSurface" -> delete(message.getJSONObject("deleteSurface"))
                else -> null // v0.9 data bindings are deliberately outside the V1 subset.
            }
        }.getOrNull()
    }

    private fun create(body: JSONObject): CaesarSurface? {
        val id = body.getString("surfaceId").validatedId()
        check(body.getString("catalogId") == BASIC_CATALOG)
        check(!body.optBoolean("sendDataModel"))
        check(id !in surfaces)
        surfaces[id] = SurfaceState(id)
        return null
    }

    private fun update(body: JSONObject): CaesarSurface {
        val id = body.getString("surfaceId").validatedId()
        val state = surfaces[id] ?: error("Surface must be created first")
        val rows = body.getJSONArray("components")
        check(rows.length() in 1..MAX_COMPONENTS)
        val updates = buildList {
            repeat(rows.length()) { index ->
            val component = rows.getJSONObject(index)
            val componentId = component.getString("id").validatedId()
            validateComponent(component)
                add(componentId to component)
            }
        }
        check((state.components.keys + updates.map { it.first }).size <= MAX_COMPONENTS)
        updates.forEach { (componentId, component) -> state.components[componentId] = component }
        return render(state)
    }

    private fun delete(body: JSONObject): CaesarSurface? {
        surfaces.remove(body.getString("surfaceId").validatedId())
        return null
    }

    private fun validateComponent(component: JSONObject) {
        when (component.getString("component")) {
            "Text" -> check(component.opt("text") is String)
            "Column", "Row", "List" -> {
                val children = component.getJSONArray("children")
                check(children.length() <= MAX_COMPONENTS)
                repeat(children.length()) { children.getString(it).validatedId() }
            }
            "Card" -> component.getString("child").validatedId()
            "Button" -> {
                component.getString("child").validatedId()
                val action = component.getJSONObject("action")
                check(action.length() == 1 && action.has("event"))
                val event = action.getJSONObject("event")
                val actionId = event.getString("name")
                check(actionId in allowedActionIds())
                check(!event.has("context") || event.getJSONObject("context").length() == 0)
            }
            else -> error("Unsupported A2UI component")
        }
        check(!component.has("url") && !component.has("functionCall"))
    }

    private fun render(state: SurfaceState): CaesarSurface {
        check("root" in state.components)
        val rendered = mutableListOf<CaesarComponent>()
        val active = mutableSetOf<String>()

        fun visit(id: String, depth: Int) {
            check(depth <= MAX_DEPTH && active.add(id))
            val component = state.components[id] ?: error("Missing child")
            when (component.getString("component")) {
                "Text" -> rendered += CaesarComponent.Text(
                    text = component.getString("text").take(2_000),
                    emphasis = component.optString("variant") in setOf("h1", "h2", "h3"),
                )
                "Column", "Row", "List" -> {
                    val children = component.getJSONArray("children")
                    repeat(children.length()) { visit(children.getString(it), depth + 1) }
                }
                "Card" -> visit(component.getString("child"), depth + 1)
                "Button" -> {
                    val label = state.components[component.getString("child")] ?: error("Missing button label")
                    check(label.getString("component") == "Text" && label.opt("text") is String)
                    rendered += CaesarComponent.Button(
                        label = label.getString("text").take(80),
                        actionId = component.getJSONObject("action").getJSONObject("event").getString("name"),
                    )
                }
            }
            active.remove(id)
        }

        visit("root", 0)
        check(rendered.size <= MAX_COMPONENTS)
        val title = rendered.filterIsInstance<CaesarComponent.Text>().firstOrNull()?.text.orEmpty().take(120)
        return CaesarSurface(state.id, title, rendered)
    }

    private fun String.validatedId(): String = apply { check(matches(ID_PATTERN)) }

    companion object {
        const val VERSION = "v0.9"
        const val BASIC_CATALOG = "https://a2ui.org/specification/v0_9/catalogs/basic/catalog.json"
        private const val MAX_BYTES = 65_536
        private const val MAX_COMPONENTS = 32
        private const val MAX_DEPTH = 8
        private val ID_PATTERN = Regex("[A-Za-z0-9._:-]{1,120}")
        private val ENVELOPES = listOf("createSurface", "updateComponents", "updateDataModel", "deleteSurface")
    }
}
