package com.campusai

import com.campusai.core.network.PersonalDeepSeekChunk
import com.campusai.core.network.PersonalDeepSeekStreamParser
import com.campusai.core.network.personalDeepSeekModel
import com.campusai.core.network.personalDeepSeekThinking
import com.campusai.core.security.PersonalDeepSeekKeyStore
import com.campusai.core.model.AiMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PersonalDeepSeekTest {
    @Test fun `parses content and usage without exposing request data`() {
        assertEquals(
            PersonalDeepSeekChunk("你好", null, null),
            PersonalDeepSeekStreamParser.parse("{\"choices\":[{\"delta\":{\"content\":\"你好\"}}]}")
        )
        assertEquals(
            PersonalDeepSeekChunk(null, 12, 8),
            PersonalDeepSeekStreamParser.parse("{\"choices\":[],\"usage\":{\"prompt_tokens\":12,\"completion_tokens\":8}}")
        )
        assertNull(PersonalDeepSeekStreamParser.parse("[DONE]"))
        assertNull(PersonalDeepSeekStreamParser.parse("not-json"))
    }

    @Test fun `personal key validation rejects blanks spaces and short values`() {
        assertFalse(PersonalDeepSeekKeyStore.isValid(""))
        assertFalse(PersonalDeepSeekKeyStore.isValid("unit-short"))
        assertFalse(PersonalDeepSeekKeyStore.isValid("unit-test-key with-space"))
        assertTrue(PersonalDeepSeekKeyStore.isValid("unit-test-key-1234567890"))
    }

    @Test fun `personal DeepSeek keeps frozen fast and deep mappings`() {
        assertEquals("deepseek-v4-flash", personalDeepSeekModel(AiMode.FAST))
        assertEquals("disabled", personalDeepSeekThinking(AiMode.FAST))
        assertEquals("deepseek-v4-pro", personalDeepSeekModel(AiMode.DEEP))
        assertEquals("enabled", personalDeepSeekThinking(AiMode.DEEP))
    }
}
