package com.campusai.app

import com.campusai.core.localai.LocalModelMode
import com.campusai.core.model.LocalModelState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocalModelSettingsUiTest {
    @Test
    fun `local modes use fast 2b and deep 4b product names`() {
        assertEquals("FAST · 2B", localModelModeLabel(LocalModelMode.FAST))
        assertEquals("DEEP · 4B", localModelModeLabel(LocalModelMode.QUALITY))
    }

    @Test
    fun `each model card derives its action from its own state`() {
        val fastState = LocalModelState.Downloading(.25f, 250, 1_000)
        val deepState = LocalModelState.Ready

        assertEquals("暂停下载", localModelActionLabel(fastState))
        assertNull(localModelActionLabel(deepState))
        assertEquals("重试下载", localModelActionLabel(LocalModelState.Error("network", true, "网络中断")))
        assertEquals("下载中 25% · 0.2 KB / 1.0 KB", localModelStatusText(fastState))
        assertEquals("Ready · 可完全离线使用", localModelStatusText(deepState))
    }
}
