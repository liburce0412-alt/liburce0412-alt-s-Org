package com.campusai

import androidx.test.core.app.ApplicationProvider
import com.campusai.core.localai.LocalModelManifest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LocalModelManifestTest {
    @Test fun `manifest is pinned complete and trusted`() {
        val manifest = LocalModelManifest.load(ApplicationProvider.getApplicationContext())
        assertEquals("f3307fcae4c41b63c9a924e0a3de17fd7ad09ae4", manifest.revision)
        assertEquals("3.6.1", manifest.runtime.version)
        assertEquals("d407447ed56c4121a11ccbd266dc184ca1ead0c2", manifest.runtime.commit)
        assertEquals(1_386_688_857L, manifest.totalBytes)
        assertEquals(8, manifest.files.size)
        assertEquals(manifest.totalBytes, manifest.files.sumOf { it.size })
        assertEquals(manifest.files.size, manifest.files.map { it.path }.distinct().size)
        manifest.files.forEach {
            val url = manifest.downloadUrl(it)
            assertTrue(url.startsWith("https://huggingface.co/taobao-mnn/Qwen3.5-2B-MNN/resolve/${manifest.revision}/"))
            assertFalse(url.contains(".."))
        }
    }
}
