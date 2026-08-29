package com.campusai

import com.campusai.core.localai.LocalOutputGuard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalOutputGuardTest {
    @Test fun `streams only a top level final envelope and strips its tags`() {
        val guard = LocalOutputGuard()
        assertTrue(guard.accept("<fi").isEmpty())
        assertEquals("今天先完成最重要的一项任务。", guard.accept("nal>**今天先完成最重要的一项任务。**").joinToString(""))
        assertEquals("然后休息十分钟。", guard.accept("然后休息十分钟。</final>").joinToString(""))
        assertTrue(guard.finish().isEmpty())
        assertTrue(guard.hasVisibleOutput)
        assertFalse(guard.blocked)
    }

    @Test fun `plain natural language remains buffered until generation finishes`() {
        val guard = LocalOutputGuard()
        assertTrue(guard.accept("今天先完成最重要的一项任务。然后休息十分钟。").isEmpty())
        assertEquals("今天先完成最重要的一项任务。然后休息十分钟。", guard.finish().joinToString(""))
        assertTrue(guard.hasVisibleOutput)
    }

    @Test fun `json and code fences never stream before finish`() {
        val jsonGuard = LocalOutputGuard()
        assertTrue(jsonGuard.accept("{\"summary\":\"ok\"}").isEmpty())
        assertEquals("{\"summary\":\"ok\"}", jsonGuard.finish().joinToString(""))

        val codeGuard = LocalOutputGuard()
        val code = "```kotlin\nval answer = 42\n```"
        assertTrue(codeGuard.accept(code).isEmpty())
        assertEquals(code, codeGuard.finish().joinToString(""))
    }

    @Test fun `json inside final is deferred until generation finishes`() {
        val guard = LocalOutputGuard()
        assertTrue(guard.accept("<final>{\"summary\":\"ok\"}</final>").isEmpty())
        assertEquals("{\"summary\":\"ok\"}", guard.finish().joinToString(""))
        assertTrue(guard.hasVisibleOutput)
    }

    @Test fun `tool call and an inner final in its parameter stay invisible`() {
        val guard = LocalOutputGuard()
        val raw = "<tool_call><function=memory.propose><parameter=content><final>secret\n${"x".repeat(200)}</final></parameter></function></tool_call>"
        raw.chunked(3).forEach { assertTrue(guard.accept(it).isEmpty()) }

        assertTrue(guard.finish().isEmpty())
        assertFalse(guard.hasVisibleOutput)
        assertTrue(guard.blocked)
    }

    @Test fun `private json inside a code fence remains blocked`() {
        val guard = LocalOutputGuard()
        assertTrue(guard.accept("```json\n{\"learningFacts\":{}}\n```").isEmpty())
        assertTrue(guard.finish().isEmpty())
        assertTrue(guard.blocked)
        assertFalse(guard.hasVisibleOutput)
    }

    @Test fun `think content and its inner final never stream`() {
        val guard = LocalOutputGuard()
        val early = guard.accept("<think>private <final>do not show</final></think>普通回答。")
        assertTrue(early.isEmpty())
        assertEquals("普通回答。", guard.finish().joinToString(""))
        assertFalse(guard.blocked)
    }

    @Test fun `think close requires a new top level final before streaming`() {
        val guard = LocalOutputGuard()
        assertTrue(guard.accept("<think>private</think><fi").isEmpty())
        assertEquals("安全回答。", guard.accept("nal>安全回答。</final>").joinToString(""))
        assertTrue(guard.finish().isEmpty())
    }

    @Test fun `a top level template think close is discarded before plain fallback`() {
        val guard = LocalOutputGuard()
        assertTrue(guard.accept("</thi").isEmpty())
        assertTrue(guard.accept("nk>普通回答。").isEmpty())
        assertEquals("普通回答。", guard.finish().joinToString(""))
        assertFalse(guard.blocked)
    }

    @Test fun `safe final content can close implicitly at generation end`() {
        val guard = LocalOutputGuard()
        assertEquals("第一句，", guard.accept("<final>第一句，").joinToString(""))
        assertTrue(guard.accept("第二句").isEmpty())
        assertEquals("第二句", guard.finish().joinToString(""))
        assertTrue(guard.hasVisibleOutput)
        assertFalse(guard.blocked)
    }

    @Test fun `late private marker keeps prior safe sentences and leaks nothing after marker`() {
        val guard = LocalOutputGuard()
        val output = buildList {
            addAll(guard.accept("<final>第一句安全回答。"))
            addAll(guard.accept("第二句也安全。<thi"))
            addAll(guard.accept("nk>绝密推理"))
            addAll(guard.finish())
        }.joinToString("")

        assertEquals("第一句安全回答。第二句也安全。", output)
        assertFalse(output.contains("绝密"))
        assertTrue(guard.blocked)
    }

    @Test fun `late private json key is blocked before any json delta`() {
        val guard = LocalOutputGuard()
        assertEquals("安全结论。", guard.accept("<final>安全结论。").joinToString(""))
        assertTrue(guard.accept("{\"learningFacts\":{\"private\":true}}").isEmpty())
        assertTrue(guard.blocked)
    }

    @Test fun `non whitespace after final close is blocked and never shown`() {
        val guard = LocalOutputGuard()
        val output = guard.accept("<final>安全回答。</final>unexpected suffix").joinToString("")
        assertEquals("安全回答。", output)
        assertFalse(output.contains("suffix"))
        assertTrue(guard.blocked)
    }

    @Test fun `private plain text and private json remain blocked`() {
        val reasoning = LocalOutputGuard()
        assertTrue(reasoning.accept("Thinking Process: internal prompt and private context").isEmpty())
        assertTrue(reasoning.finish().isEmpty())
        assertTrue(reasoning.blocked)

        val json = LocalOutputGuard()
        assertTrue(json.accept("{\"learningFacts\":{}}").isEmpty())
        assertTrue(json.finish().isEmpty())
        assertTrue(json.blocked)
    }

    @Test fun `replacement characters and punctuation floods are rejected`() {
        val invalidUtf8 = LocalOutputGuard()
        assertTrue(invalidUtf8.accept("正常开头\uFFFD损坏").isEmpty())
        assertTrue(invalidUtf8.blocked)
        assertFalse(invalidUtf8.hasVisibleOutput)

        val symbolFlood = LocalOutputGuard()
        assertTrue(symbolFlood.accept("!@#%&~".repeat(40)).isEmpty())
        assertTrue(symbolFlood.finish().isEmpty())
        assertTrue(symbolFlood.blocked)
        assertFalse(symbolFlood.hasVisibleOutput)
    }

    @Test fun `marker guard covers every current private and transport marker`() {
        assertTrue(LocalOutputGuard.MARKER_GUARD_CHARS >= LocalOutputGuard.longestControlMarkerChars)
    }
}
