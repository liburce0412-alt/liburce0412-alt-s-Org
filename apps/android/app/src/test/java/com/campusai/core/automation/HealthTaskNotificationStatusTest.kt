package com.campusai.core.automation

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class HealthTaskNotificationStatusTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val manager = context.getSystemService(NotificationManager::class.java)

    @After
    fun tearDown() {
        manager.deleteNotificationChannel(HealthTaskNotificationContract.CHANNEL_ID)
    }

    @Test
    fun mutedAutomationChannelIsReportedAsDisabled() {
        manager.createNotificationChannel(
            NotificationChannel(
                HealthTaskNotificationContract.CHANNEL_ID,
                "test",
                NotificationManager.IMPORTANCE_NONE,
            ),
        )

        assertFalse(healthTaskNotificationsEnabled(context))
    }

    @Test
    fun enabledAutomationChannelIsReportedAsEnabled() {
        manager.createNotificationChannel(
            NotificationChannel(
                HealthTaskNotificationContract.CHANNEL_ID,
                "test",
                NotificationManager.IMPORTANCE_HIGH,
            ),
        )

        assertTrue(healthTaskNotificationsEnabled(context))
    }
}
