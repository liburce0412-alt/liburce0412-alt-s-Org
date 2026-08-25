package com.campusai

import com.campusai.features.ai.CaesarLocalityPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CaesarLocalityPolicyTest {
    @Test fun `ordinary text follows the selected provider`() {
        assertFalse(CaesarLocalityPolicy.requiresLocal("帮我整理今天的学习计划", hasImages = false))
    }

    @Test fun `images always stay on device`() {
        assertTrue(CaesarLocalityPolicy.requiresLocal("看看这张图", hasImages = true))
    }

    @Test fun `health and band turns stay on device`() {
        assertTrue(CaesarLocalityPolicy.requiresLocal("我现在的心率怎么样", hasImages = false))
        assertTrue(CaesarLocalityPolicy.requiresLocal("小米手环昨晚睡眠数据", hasImages = false))
        assertTrue(CaesarLocalityPolicy.requiresLocal("检查 Health Connect 数据源", hasImages = false))
    }
}
