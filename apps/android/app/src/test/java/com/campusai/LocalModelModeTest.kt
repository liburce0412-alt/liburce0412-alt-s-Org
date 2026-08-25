package com.campusai

import androidx.test.core.app.ApplicationProvider
import com.campusai.core.localai.LocalModelManifest
import com.campusai.core.localai.LocalModelMode
import com.campusai.core.localai.LocalModelModeStore
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LocalModelModeTest {
    @Test fun `quality is default and modes map to pinned manifests`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context.getSharedPreferences("campusai_local_model_mode", android.content.Context.MODE_PRIVATE).edit().clear().commit()

        assertEquals(LocalModelMode.QUALITY, LocalModelModeStore(context).read())
        assertEquals(LocalModelManifest.DEFAULT_MODEL_ID, LocalModelMode.QUALITY.modelId)
        assertEquals("qwen3.5-2b-mnn", LocalModelMode.FAST.modelId)
    }

    @Test fun `fast selection survives a new store instance`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val store = LocalModelModeStore(context)
        try {
            store.write(LocalModelMode.FAST)
            assertEquals(LocalModelMode.FAST, LocalModelModeStore(context).read())
        } finally {
            store.write(LocalModelMode.QUALITY)
        }
    }
}
