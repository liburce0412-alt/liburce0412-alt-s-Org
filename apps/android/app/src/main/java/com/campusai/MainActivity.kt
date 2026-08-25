package com.campusai

import android.content.Intent
import android.net.Uri
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.campusai.app.CampusApp
import com.campusai.core.database.CampusDatabase
import com.campusai.core.sync.CampusSyncScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

class MainActivity : ComponentActivity() {
    private val sharedImage = MutableStateFlow<Uri?>(null)
    private var restoreExternalSurfacesOnResume = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        CampusSyncScheduler.schedule(applicationContext)
        val dao = CampusDatabase.getDatabase(applicationContext).campusDao()
        sharedImage.value = intent.sharedImage()
        setContent {
            val image by sharedImage.collectAsState()
            CampusApp(dao = dao, initialSharedImage = image)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        sharedImage.value = intent.sharedImage()
    }

    override fun onStop() {
        restoreExternalSurfacesOnResume = true
        super.onStop()
    }

    override fun onPostResume() {
        super.onPostResume()
        if (!restoreExternalSurfacesOnResume) return
        restoreExternalSurfacesOnResume = false

        val root = window.decorView
        root.post { refreshGlSurfaces(root, resume = true) }
        root.postOnAnimation { refreshGlSurfaces(root, resume = false) }
        root.postDelayed({ refreshGlSurfaces(root, resume = false) }, GL_SURFACE_SETTLE_MILLIS)
    }

    private fun refreshGlSurfaces(view: View, resume: Boolean) {
        if (view is GLSurfaceView && view.isAttachedToWindow) {
            if (resume) view.onResume()
            view.requestRender()
        }
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) refreshGlSurfaces(view.getChildAt(index), resume)
        }
    }

    @Suppress("DEPRECATION")
    private fun Intent.sharedImage(): Uri? = if (action == Intent.ACTION_SEND && type?.startsWith("image/") == true) {
        if (android.os.Build.VERSION.SDK_INT >= 33) getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        else getParcelableExtra(Intent.EXTRA_STREAM)
    } else null

    private companion object {
        const val GL_SURFACE_SETTLE_MILLIS = 96L
    }
}
