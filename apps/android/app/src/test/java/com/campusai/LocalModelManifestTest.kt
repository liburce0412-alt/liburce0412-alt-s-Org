package com.campusai

import androidx.test.core.app.ApplicationProvider
import com.campusai.core.localai.LocalModelManifest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LocalModelManifestTest {
    @Test fun `default 4b manifest is pinned complete and trusted`() {
        val manifest = LocalModelManifest.load(ApplicationProvider.getApplicationContext())
        assertEquals("qwen3.5-4b-mnn", manifest.id)
        assertEquals("aa966261175a532d9906fa165c9d506d617320a9", manifest.revision)
        assertEquals("3.6.1", manifest.runtime.version)
        assertEquals("d407447ed56c4121a11ccbd266dc184ca1ead0c2", manifest.runtime.commit)
        assertEquals(2_845_943_054L, manifest.totalBytes)
        assertEquals(8, manifest.files.size)
        assertEquals(manifest.totalBytes, manifest.files.sumOf { it.size })
        assertEquals(manifest.files.size, manifest.files.map { it.path }.distinct().size)
        manifest.files.forEach {
            val url = manifest.downloadUrl(it)
            assertTrue(url.startsWith("https://huggingface.co/taobao-mnn/Qwen3.5-4B-MNN/resolve/${manifest.revision}/"))
            assertFalse(url.contains(".."))
        }
    }

    @Test fun `2b migration fallback remains selectable and isolated`() {
        val manifest = LocalModelManifest.load(ApplicationProvider.getApplicationContext(), "qwen3.5-2b-mnn")
        assertEquals("taobao-mnn/Qwen3.5-2B-MNN", manifest.repository)
        assertEquals(1_386_688_857L, manifest.totalBytes)
        assertTrue(manifest.downloadUrl(manifest.files.first()).contains("Qwen3.5-2B-MNN"))
    }

    @Test fun `unknown model id is rejected`() {
        assertThrows(IllegalStateException::class.java) {
            LocalModelManifest.load(ApplicationProvider.getApplicationContext(), "unknown-model")
        }
    }
}
