package com.pragmaticcoder.piremote

import org.json.JSONArray
import org.json.JSONObject

/** Supported remote backends. Each keeps its own saved connection settings. */
enum class BackendKind(val label: String) {
    Pi("Pi"),
    OpenCode("OpenCode");
}

/**
 * Parses an `oc-remote://host:port?p=<password>` pairing URI produced by the
 * opencode-remote server script. Never logs the password.
 */
internal fun parseOpenCodeUri(
    uriText: String,
    current: ConnectionSettings,
): ConnectionSettings? {
    return runCatching {
        val uri = java.net.URI(uriText)
        if (uri.scheme != "oc-remote") return@runCatching null
        current.copy(
            host = uri.host?.takeIf { it.isNotBlank() } ?: current.host,
            port = uri.port.takeIf { it > 0 }?.toString() ?: current.port,
            token = queryParameter(uri.rawQuery, "p") ?: current.token,
        )
    }.getOrNull()
}

/** True when a scanned QR/URI belongs to either supported backend scheme. */
internal fun isBackendUri(uriText: String): Boolean =
    uriText.trimStart().startsWith("pi-remote://") || uriText.trimStart().startsWith("oc-remote://")

internal data class OpenCodeSessionEntry(
    val id: String,
    val title: String,
    val updated: Long,
    val directory: String,
)

/** GET /api/session → list, newest first. */
internal fun parseOpenCodeSessions(body: String): List<OpenCodeSessionEntry> {
    return runCatching {
        val root = JSONObject(body)
        val arr = root.optJSONArray("data") ?: root.optJSONArray("sessions") ?: JSONArray()
        val out = mutableListOf<OpenCodeSessionEntry>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optString("id")
            if (!id.startsWith("ses")) continue
            out += OpenCodeSessionEntry(
                id = id,
                title = o.optString("title").ifBlank { "Session" },
                updated = o.optJSONObject("time")?.optLong("updated") ?: 0L,
                directory = o.optString("directory").ifBlank { o.optString("path") },
            )
        }
        out.sortedByDescending { it.updated }
    }.getOrDefault(emptyList())
}

/**
 * Maps one OpenCode SSE event (`GET /event`, envelope: {"type": "...",
 * "properties": {...}}) into the same [IncomingEffects] shape the Pi protocol
 * produces, so the chat pipeline stays shared.
 *
 * Only events belonging to [activeSessionId] produce effects; other sessions'
 * traffic on the shared stream is ignored.
 */
internal fun parseOpenCodeEvent(
    obj: JSONObject,
    activeSessionId: String?,
): IncomingEffects {
    val type = obj.optString("type")
    val props = obj.optJSONObject("properties") ?: JSONObject()
    val eventSession = props.optString("sessionID")
    if (activeSessionId != null && eventSession.isNotBlank() && eventSession != activeSessionId) return IncomingEffects()
    return when {
        type == "session.next.prompt.admitted" -> IncomingEffects(working = true)
        type == "session.next.step.started" -> IncomingEffects(working = true)
        type == "session.next.step.failed" -> IncomingEffects(working = false)
        type == "session.next.step.ended" -> {
            // finish == "stop" means the whole run finished; "tool-calls" means
            // another step follows after tool execution.
            val finished = props.optString("finish", "stop") != "tool-calls"
            IncomingEffects(working = !finished)
        }
        type == "session.next.text.delta" ->
            IncomingEffects(assistantDeltas = listOf(props.optString("delta")))
        type.startsWith("session.next.reasoning.") -> IncomingEffects()
        type == "session.next.tool.input.started" -> {
            val callId = props.optString("callID").ifBlank { "oc-${System.nanoTime()}" }
            val name = props.optString("name").ifBlank { "tool" }
            IncomingEffects(toolUpdates = listOf(IncomingToolUpdate(callId, "$name running…", "Running…", false)))
        }
        type == "session.next.tool.input.delta" -> IncomingEffects()
        type == "session.next.tool.input.ended" -> {
            val callId = props.optString("callID").ifBlank { "oc-${System.nanoTime()}" }
            val name = callId.substringBefore(':').ifBlank { "tool" }
            val args = props.optString("text").takeIf { it.isNotBlank() } ?: "Running…"
            IncomingEffects(toolUpdates = listOf(IncomingToolUpdate(callId, "$name running…", args, false)))
        }
        type == "session.next.tool.success" -> {
            val callId = props.optString("callID").ifBlank { "oc-${System.nanoTime()}" }
            IncomingEffects(toolUpdates = listOf(IncomingToolUpdate(callId, "Tool finished", openCodeContentToText(props.opt("structured") ?: props.opt("content")), true)))
        }
        type == "session.next.tool.failed" || type == "session.error" || type == "session.next.step.failed" -> {
            val callId = props.optString("callID")
            if (callId.isNotBlank()) {
                IncomingEffects(toolUpdates = listOf(IncomingToolUpdate(callId, "Tool failed", openCodeContentToText(props.opt("error") ?: props.opt("content")).ifBlank { "Error" }, true)))
            } else {
                IncomingEffects(messages = listOf(IncomingChatMessage(ChatKind.Error, "OpenCode", openCodeContentToText(props.opt("error")).ifBlank { obj.toString().take(300) })))
            }
        }
        else -> IncomingEffects()
    }
}

/** SSE wire format: `data: {json}` lines separated by blank lines. */
internal fun extractSseData(line: String): String? {
    if (!line.startsWith("data:")) return null
    return line.removePrefix("data:").trim().takeIf { it.isNotEmpty() && it != "[DONE]" }
}

private fun openCodeContentToText(value: Any?): String = when (value) {
    null, JSONObject.NULL -> ""
    is String -> value
    is JSONObject -> openCodeBlocksToText(value.optJSONArray("content")) .ifBlank { value.toString() }
    is JSONArray -> openCodeBlocksToText(value)
    else -> value.toString()
}

/** Unwraps [{type:text,text:...}] content blocks used by tool results and messages. */
internal fun openCodeBlocksToText(blocks: JSONArray?): String {
    if (blocks == null) return ""
    val parts = mutableListOf<String>()
    for (i in 0 until blocks.length()) {
        val block = blocks.optJSONObject(i) ?: continue
        when (block.optString("type")) {
            "text" -> block.optString("text").takeIf { it.isNotBlank() }?.let { parts += it }
            "image" -> parts += "[image output]"
        }
    }
    return parts.joinToString("\n")
}

/** GET /api/session/{id}/message history → transcript effects. */
internal fun parseOpenCodeHistory(body: String): IncomingEffects {
    return runCatching {
        val root = JSONObject(body)
        val arr = root.optJSONArray("data") ?: root.optJSONArray("messages") ?: JSONArray()
        val messages = mutableListOf<IncomingChatMessage>()
        var lastActivity = 0L
        for (i in 0 until arr.length()) {
            val m = arr.optJSONObject(i) ?: continue
            val type = m.optString("type").ifBlank { m.optString("role") }
            when (type) {
                "user" -> {
                    val text = m.optString("text").ifBlank {
                        openCodeBlocksToText(m.optJSONArray("content"))
                    }
                    if (text.isNotBlank()) messages += IncomingChatMessage(ChatKind.User, "User", text)
                    lastActivity = maxOf(lastActivity, m.optJSONObject("time")?.optLong("created") ?: 0L)
                }
                "assistant" -> {
                    val text = m.optString("text").ifBlank {
                        openCodeBlocksToText(m.optJSONArray("content"))
                    }
                    if (text.isNotBlank()) messages += IncomingChatMessage(ChatKind.Assistant, "Assistant", text)
                    val time = m.optJSONObject("time")
                    lastActivity = maxOf(lastActivity, time?.optLong("completed") ?: time?.optLong("created") ?: 0L)
                }
            }
        }
        IncomingEffects(messages = messages, working = false)
    }.getOrDefault(IncomingEffects())
}

/**
 * Legacy `GET /session/{id}/message` shape: bare array of
 * `{info: {role, time}, parts: [{type: "text"|"reasoning"|...}]}`.
 * Used as fallback because the v2 endpoint may return an empty page for
 * sessions stored by older server instances.
 */
internal fun parseOpenCodeLegacyHistory(body: String): IncomingEffects {
    return runCatching {
        val arr = JSONArray(body.trim().let { if (it.startsWith("[")) it else "[]" })
        val messages = mutableListOf<IncomingChatMessage>()
        for (i in 0 until arr.length()) {
            val m = arr.optJSONObject(i) ?: continue
            val info = m.optJSONObject("info") ?: continue
            val role = info.optString("role")
            val text = openCodeBlocksToText(m.optJSONArray("parts"))
            when (role) {
                "user" -> if (text.isNotBlank()) messages += IncomingChatMessage(ChatKind.User, "User", text)
                "assistant" -> if (text.isNotBlank()) messages += IncomingChatMessage(ChatKind.Assistant, "Assistant", text)
            }
        }
        IncomingEffects(messages = messages, working = false)
    }.getOrDefault(IncomingEffects())
}

/** Basic auth header value for the OpenCode server password (RFC 7617). */
internal fun openCodeAuthHeader(password: String): String {
    val credentials = "opencode:$password"
    return "Basic " + android.util.Base64.encodeToString(credentials.toByteArray(), android.util.Base64.NO_WRAP)
}
