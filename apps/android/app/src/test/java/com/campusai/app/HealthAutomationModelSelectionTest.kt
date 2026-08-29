package com.campusai.app

import com.campusai.core.ai.CloudAiProvider
import com.campusai.core.ai.CloudProviderModel
import com.campusai.core.model.AiMode
import org.junit.Assert.assertEquals
import org.junit.Test

class HealthAutomationModelSelectionTest {
    @Test
    fun `keeps a previously locked draft while separately reporting catalog availability`() {
        val models = listOf(
            CloudProviderModel("deepseek-v4-flash"),
            CloudProviderModel("deepseek-v4-pro"),
        )

        assertEquals(
            "deepseek-v4-pro",
            selectHealthAutomationModel(CloudAiProvider.DEEPSEEK, "deepseek-v4-pro", models),
        )
        assertEquals(
            "deepseek-chat",
            selectHealthAutomationModel(CloudAiProvider.DEEPSEEK, "deepseek-chat", models),
        )
        assertEquals(
            false,
            isHealthAutomationModelAvailable(CloudAiProvider.DEEPSEEK, "deepseek-chat", models),
        )
    }

    @Test
    fun `normalizes Gemini model prefixes before selecting`() {
        val models = listOf(CloudProviderModel("models/gemini-2.5-flash"))

        assertEquals(
            "gemini-2.5-flash",
            selectHealthAutomationModel(
                CloudAiProvider.GOOGLE_GEMINI,
                "models/gemini-2.5-flash",
                models,
            ),
        )
    }

    @Test
    fun `uses the provider default only when no model was previously locked and it is live`() {
        val defaultModel = CloudAiProvider.DEEPSEEK.defaultModel(AiMode.FAST)
        val deepSeekModels = listOf(
            CloudProviderModel("deepseek-v4-pro"),
            CloudProviderModel(defaultModel),
        )

        assertEquals(
            defaultModel,
            selectHealthAutomationModel(CloudAiProvider.DEEPSEEK, "", deepSeekModels),
        )
        assertEquals(
            "",
            selectHealthAutomationModel(
                CloudAiProvider.GOOGLE_GEMINI,
                "",
                listOf(CloudProviderModel("gemini-2.5-pro")),
            ),
        )
    }
}
