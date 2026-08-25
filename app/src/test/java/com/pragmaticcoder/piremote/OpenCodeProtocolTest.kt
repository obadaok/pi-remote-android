package com.pragmaticcoder.piremote

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenCodeProtocolTest {

    private fun event(type: String, props: String = "{}") =
        JSONObject().put("type", type).put("properties", JSONObject(props))

    @Test
    fun `text delta maps to assistant delta for active session`() {
        val e = event(
            "session.next.text.delta",
            """{"sessionID":"ses_1","delta":"Hel","textID":"text-0"}""",
        )
        val effects = parseOpenCodeEvent(e, "ses_1")
        assertEquals(listOf("Hel"), effects.assistantDeltas)
    }

    @Test
    fun `events from other sessions are ignored`() {
        val e = event(
            "session.next.text.delta",
            """{"sessionID":"ses_other","delta":"nope"}""",
        )
        val effects = parseOpenCodeEvent(e, "ses_mine")
        assertTrue(effects.assistantDeltas.isEmpty())
        assertNull(effects.working)
    }

    @Test
    fun `step lifecycle drives working state`() {
        val sid = "ses_1"
        assertTrue(parseOpenCodeEvent(event("session.next.step.started", """{"sessionID":"$sid"}"""), sid).working == true)
        // tool-calls finish means still working (another step follows)
        assertTrue(parseOpenCodeEvent(event("session.next.step.ended", """{"sessionID":"$sid","finish":"tool-calls"}"""), sid).working == true)
        // stop means idle
        assertTrue(parseOpenCodeEvent(event("session.next.step.ended", """{"sessionID":"$sid","finish":"stop"}"""), sid).working == false)
    }

    @Test
    fun `prompt admitted marks working`() {
        val e = event("session.next.prompt.admitted", """{"sessionID":"ses_1","prompt":{"text":"hi"}}""")
        val effects = parseOpenCodeEvent(e, "ses_1")
        assertEquals(true, effects.working)
    }

    @Test
    fun `tool start creates running tool card with callID`() {
        val e = event(
            "session.next.tool.input.started",
            """{"sessionID":"ses_1","callID":"bash:0","name":"bash"}""",
        )
        val effects = parseOpenCodeEvent(e, "ses_1")
        assertEquals(1, effects.toolUpdates.size)
        val t = effects.toolUpdates.first()
        assertEquals("bash:0", t.toolCallId)
        assertFalse(t.done)
        assertTrue(t.title.contains("running"))
    }

    @Test
    fun `tool input ended updates args text on same callID`() {
        val started = parseOpenCodeEvent(
            event("session.next.tool.input.started", """{"sessionID":"s","callID":"bash:0","name":"bash"}"""),
            "s",
        ).toolUpdates.single()
        val ended = parseOpenCodeEvent(
            event("session.next.tool.input.ended", """{"sessionID":"s","callID":"bash:0","text":"{\"command\":\"ls\"}"}"""),
            "s",
        ).toolUpdates.single()
        assertEquals(started.toolCallId, ended.toolCallId)
        assertEquals("{\"command\":\"ls\"}", ended.text)
        assertFalse(ended.done)
    }

    @Test
    fun `tool success closes tool card and unwraps content blocks`() {
        val e = event(
            "session.next.tool.success",
            """{"sessionID":"s","callID":"bash:0","structured":{"content":[{"type":"text","text":"hello\n"},{"type":"text","text":"exit 0"}]}}""",
        )
        val effects = parseOpenCodeEvent(e, "s")
        val t = effects.toolUpdates.single()
        assertEquals("bash:0", t.toolCallId)
        assertTrue(t.done)
        assertTrue(t.text.contains("hello"))
    }

    @Test
    fun `reasoning deltas produce no output`() {
        val e = event("session.next.reasoning.delta", """{"sessionID":"s","delta":"thinking"}""")
        val effects = parseOpenCodeEvent(e, "s")
        assertTrue(effects.assistantDeltas.isEmpty())
        assertTrue(effects.messages.isEmpty())
    }

    @Test
    fun `sse data extraction handles prefixes and done sentinel`() {
        assertEquals("""{"a":1}""", extractSseData("data: {\"a\":1}"))
        assertNull(extractSseData(": keep-alive comment"))
        assertNull(extractSseData("data: [DONE]"))
        assertNull(extractSseData(""))
    }

    @Test
    fun `session list parses newest first and skips non-sessions`() {
        val body = """
            {"data":[
              {"id":"ses_a","title":"Older","directory":"/b","time":{"updated":100}},
              {"id":"garbage","title":"x","time":{"updated":999}},
              {"id":"ses_b","title":"Newer","directory":"/a","time":{"updated":200}}
            ]}
        """.trimIndent()
        val sessions = parseOpenCodeSessions(body)
        assertEquals(listOf("ses_b", "ses_a"), sessions.map { it.id })
        assertEquals("Newer", sessions.first().title)
    }

    @Test
    fun `history maps user and assistant messages and drops reasoning`() {
        val body = """
            {"data":[
              {"id":"m1","type":"user","text":"hi there","time":{"created":1}},
              {"id":"m2","type":"assistant","model":{"providerID":"opencode","id":"m1"},"content":[
                 {"type":"reasoning","text":"secret thoughts"},
                 {"type":"text","text":"answer body"}
              ],"time":{"created":2,"completed":3}}
            ]}
        """.trimIndent()
        val effects = parseOpenCodeHistory(body)
        assertEquals(2, effects.messages.size)
        assertEquals(ChatKind.User, effects.messages[0].kind)
        assertEquals("hi there", effects.messages[0].text)
        assertEquals(ChatKind.Assistant, effects.messages[1].kind)
        assertEquals("answer body", effects.messages[1].text)
        assertFalse(effects.messages[1].text.contains("secret"))
    }

    @Test
    fun `oc-remote uri parsing extracts host port password`() {
        val parsed = parseOpenCodeUri(
            "oc-remote://192.168.1.129:4096?p=secret-pw",
            ConnectionSettings("", "4096", ""),
        )
        assertEquals("192.168.1.129", parsed?.host)
        assertEquals("4096", parsed?.port)
        assertEquals("secret-pw", parsed?.token)
    }

    @Test
    fun `pi-remote uri is not matched by opencode parser and vice versa`() {
        assertNull(parseOpenCodeUri("pi-remote://1.2.3.4:37891?token=x", ConnectionSettings("", "", "")))
        assertFalse(isBackendUri("https://example.com"))
        assertTrue(isBackendUri("oc-remote://1.2.3.4:1?p=a"))
        assertTrue(isBackendUri("pi-remote://1.2.3.4:1?token=b"))
    }
}
