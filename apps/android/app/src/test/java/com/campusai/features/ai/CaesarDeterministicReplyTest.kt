package com.campusai.features.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CaesarDeterministicReplyTest {
    @Test fun `identity is a stable product fact`() {
        assertEquals(
            "我是 Caesar∞，运行在你设备上的私人 Agent。",
            CaesarDeterministicReply.forPrompt("你的名字是什么？", hasImages = false),
        )
        assertEquals(
            "我是 Caesar∞，运行在你设备上的私人 Agent。",
            CaesarDeterministicReply.forPrompt("你 是 谁", hasImages = false),
        )
    }

    @Test fun `capability reply names only current Caesar surfaces`() {
        val reply = CaesarDeterministicReply.forPrompt("说说你能干什么？", hasImages = false)
        requireNotNull(reply)
        assertTrue(reply.contains("心愿墙"))
        assertTrue(reply.contains("健康数据"))
        assertTrue(!reply.contains("订单"))
    }

    @Test fun `images and broader questions still reach the model`() {
        assertNull(CaesarDeterministicReply.forPrompt("你是谁", hasImages = true))
        assertNull(CaesarDeterministicReply.forPrompt("你是谁，为什么要使用工具？", hasImages = false))
    }
}
