package com.campusai.features.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiMarkdownTest {
    @Test
    fun `parser preserves headings and ordered and unordered list items`() {
        val blocks = parseAiMarkdown(
            """
            # 连接结果

            - 模型已加载
            2. 对话已验证
            """.trimIndent(),
        )

        assertEquals(
            listOf(
                AiMarkdownBlock.Heading(level = 1, text = "连接结果"),
                AiMarkdownBlock.ListItem(marker = "•", text = "模型已加载"),
                AiMarkdownBlock.ListItem(marker = "2.", text = "对话已验证"),
            ),
            blocks,
        )
    }

    @Test
    fun `parser keeps fenced code language and content separate`() {
        val blocks = parseAiMarkdown(
            """
            示例：

            ```kotlin
            val answer = "OK"
            println(answer)
            ```
            """.trimIndent(),
        )

        assertEquals(AiMarkdownBlock.Paragraph("示例："), blocks.first())
        assertEquals(
            AiMarkdownBlock.Code(
                language = "kotlin",
                code = "val answer = \"OK\"\nprintln(answer)",
            ),
            blocks.last(),
        )
    }

    @Test
    fun `unfinished streaming fence remains a code block through end of input`() {
        val blocks = parseAiMarkdown(
            """
            正在生成
            ```json
            {"status":"partial"}
            """.trimIndent(),
        )

        assertEquals(2, blocks.size)
        assertEquals(AiMarkdownBlock.Paragraph("正在生成"), blocks[0])
        val code = blocks[1]
        assertTrue(code is AiMarkdownBlock.Code)
        assertEquals("json", (code as AiMarkdownBlock.Code).language)
        assertEquals("{\"status\":\"partial\"}", code.code)
    }
}
