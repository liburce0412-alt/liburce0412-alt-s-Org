package com.campusai.core.automation

import android.content.Context
import com.campusai.core.database.CampusDao
import com.campusai.core.network.PersonalCloudHealthAutoMessageClient
import com.campusai.core.security.PersonalAiProviderStore

/** App-scoped foreground runtime. It owns no service, worker, or background lifecycle. */
class ForegroundHealthTaskRuntime(
    context: Context,
    dao: CampusDao,
) {
    val store: ScheduledTaskStore = NoBackupScheduledTaskStore(context.applicationContext)
    val aiClient: HealthAutoMessageClient = PersonalCloudHealthAutoMessageClient(
        PersonalAiProviderStore(context.applicationContext),
    )
    val runner: ForegroundHealthTaskRunner = DefaultForegroundHealthTaskRunner(
        store = store,
        source = MiFitnessForegroundHealthCloudSource(context.applicationContext),
        aiClient = aiClient,
        conversationWriter = RoomHealthTaskConversationWriter(dao),
        notificationPublisher = AndroidHealthTaskNotificationPublisher(context.applicationContext),
    )

    companion object {
        @Volatile
        private var instance: ForegroundHealthTaskRuntime? = null

        fun get(context: Context, dao: CampusDao): ForegroundHealthTaskRuntime =
            instance ?: synchronized(this) {
                instance ?: ForegroundHealthTaskRuntime(context.applicationContext, dao).also { instance = it }
            }
    }
}
