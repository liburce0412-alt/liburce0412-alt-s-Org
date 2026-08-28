package com.campusai.features.ai

import com.campusai.core.ai.AiRequest
import com.campusai.core.model.AiConversationMessage
import com.campusai.core.model.AiMode
import org.json.JSONArray
import org.json.JSONObject

/** Pure request assembly used by text, speech transcripts and image turns. */
internal fun assembleCaesarTurnRequest(
    mode: AiMode,
    existingMessages: List<AiConversationMessage>,
    prompt: String,
    displayPrompt: String,
    structuredContext: JSONObject,
    attachments: List<CaesarImageAttachment>,
    sessionId: String,
    ownerUserId: String,
    localModelId: String,
    cloudOnce: Boolean = false,
): AiRequest {
    val context = JSONObject(structuredContext.toString()).put("locale", "zh-CN")
    val ocr = attachments.mapIndexedNotNull { index, image ->
        image.ocrText.takeIf(String::isNotBlank)?.let {
            JSONObject().put("imageIndex", index).put("text", it)
        }
    }
    if (ocr.isNotEmpty()) context.put("imageOcr", JSONArray(ocr))
    return AiRequest(
        mode = mode,
        messages = existingMessages + AiConversationMessage("user", prompt.trim()),
        structuredContextJson = context.toString(),
        maxOutputTokens = 512,
        sessionId = sessionId,
        ownerUserId = ownerUserId,
        userPrompt = displayPrompt.trim(),
        requiresLocal = !cloudOnce && CaesarLocalityPolicy.requiresLocal(
            prompt = displayPrompt,
            hasImages = attachments.isNotEmpty(),
        ),
        localModelId = localModelId,
        imagePaths = attachments.map(CaesarImageAttachment::localPath),
    )
}

internal object AiConversationCodec {
    fun encode(messages: List<AiConversationMessage>): String = JSONArray(messages.map { message ->
        JSONObject()
            .put("role", message.role)
            .put("content", message.content)
            .put("presentation", message.presentationJson ?: JSONObject.NULL)
            .put("attachments", JSONArray(message.attachmentPaths))
            .put("cloudHealthSensitive", message.cloudHealthSensitive)
    }).toString()

    fun decode(raw: String): List<AiConversationMessage> = runCatching {
        val rows = JSONArray(raw)
        buildList {
            repeat(rows.length()) { rowIndex ->
                rows.optJSONObject(rowIndex)?.let { row ->
                    val role = row.optString("role")
                    if (role !in ALLOWED_ROLES) return@let
                    add(
                        AiConversationMessage(
                            role = role,
                            content = row.optString("content"),
                            presentationJson = row.optString("presentation").takeUnless { it.isBlank() || it == "null" },
                            attachmentPaths = buildList {
                                val attachments = row.optJSONArray("attachments") ?: JSONArray()
                                repeat(attachments.length()) { attachmentIndex ->
                                    attachments.optString(attachmentIndex).takeIf(String::isNotBlank)?.let(::add)
                                }
                            },
                            cloudHealthSensitive = row.optBoolean("cloudHealthSensitive", false),
                        ),
                    )
                }
            }
        }
    }.getOrDefault(emptyList())

    private val ALLOWED_ROLES = setOf("system", "user", "assistant", "tool")
}
