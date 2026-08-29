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
import com.campusai.core.automation.HealthTaskNotificationContract
import com.campusai.core.database.CampusDatabase
import com.campusai.core.sync.CampusSyncScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

class MainActivity : ComponentActivity() {
    private val sharedImage = MutableStateFlow<Uri?>(null)
    private val automationConversation = MutableStateFlow<String?>(null)
    private lateinit var externalIntentConsumption: ExternalIntentConsumptionState
    private var restoreExternalSurfacesOnResume = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        CampusSyncScheduler.schedule(applicationContext)
        val dao = CampusDatabase.getDatabase(applicationContext).campusDao()
        externalIntentConsumption = ExternalIntentConsumptionState(savedInstanceState)
        sharedImage.value = intent.sharedImage().takeUnless { externalIntentConsumption.sharedImageConsumed }
        automationConversation.value = intent.automationConversationId()
            .takeUnless { externalIntentConsumption.automationConversationConsumed }
        setContent {
            val image by sharedImage.collectAsState()
            val conversationId by automationConversation.collectAsState()
            CampusApp(
                dao = dao,
                initialSharedImage = image,
                onSharedImageConsumed = ::consumeSharedImage,
                initialAutomationConversationId = conversationId,
                onAutomationConversationConsumed = ::consumeAutomationConversation,
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        externalIntentConsumption.resetForNewIntent()
        setIntent(intent)
        sharedImage.value = intent.sharedImage()
        automationConversation.value = intent.automationConversationId()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        externalIntentConsumption.save(outState)
        super.onSaveInstanceState(outState)
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

    private fun Intent.automationConversationId(): String? {
        if (action != HealthTaskNotificationContract.ACTION_OPEN_CONVERSATION) return null
        return getStringExtra(HealthTaskNotificationContract.EXTRA_CONVERSATION_ID)
            ?.takeIf(AUTOMATION_CONVERSATION_ID::matches)
    }

    private fun consumeSharedImage() {
        externalIntentConsumption.sharedImageConsumed = true
        sharedImage.value = null
        val current = intent
        if (current.action != Intent.ACTION_SEND) return
        current.action = null
        current.type = null
        current.clipData = null
        current.removeExtra(Intent.EXTRA_STREAM)
        setIntent(current)
    }

    private fun consumeAutomationConversation() {
        externalIntentConsumption.automationConversationConsumed = true
        automationConversation.value = null
        val current = intent
        if (current.action != HealthTaskNotificationContract.ACTION_OPEN_CONVERSATION) return
        current.action = null
        current.removeExtra(HealthTaskNotificationContract.EXTRA_CONVERSATION_ID)
        setIntent(current)
    }

    private companion object {
        const val GL_SURFACE_SETTLE_MILLIS = 96L
        val AUTOMATION_CONVERSATION_ID = Regex("automation-health-[A-Za-z0-9._-]{1,64}")
    }
}

internal class ExternalIntentConsumptionState(savedInstanceState: Bundle?) {
    var sharedImageConsumed: Boolean = savedInstanceState?.getBoolean(SHARED_IMAGE_CONSUMED) == true
    var automationConversationConsumed: Boolean =
        savedInstanceState?.getBoolean(AUTOMATION_CONVERSATION_CONSUMED) == true

    fun resetForNewIntent() {
        sharedImageConsumed = false
        automationConversationConsumed = false
    }

    fun save(outState: Bundle) {
        outState.putBoolean(SHARED_IMAGE_CONSUMED, sharedImageConsumed)
        outState.putBoolean(AUTOMATION_CONVERSATION_CONSUMED, automationConversationConsumed)
    }

    private companion object {
        const val SHARED_IMAGE_CONSUMED = "campusai.external.shared_image_consumed"
        const val AUTOMATION_CONVERSATION_CONSUMED = "campusai.external.automation_conversation_consumed"
    }
}
