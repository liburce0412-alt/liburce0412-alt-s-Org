package com.campusai.caesar.bandbridge

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.campusai.caesar.bandcontract.BandBridgeContract
import com.campusai.caesar.bandcontract.BandBridgeSnapshot
import com.campusai.caesar.bandcontract.BridgeState

class BandBridgeService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var store: BandSnapshotStore
    private lateinit var adapter: BandAdapter
    private var running = false

    private val idleStop = Runnable { stopBridge("无活动十分钟，实时会话已停止") }

    override fun onCreate() {
        super.onCreate()
        store = BandSnapshotStore.get(this)
        adapter = GadgetbridgeIntentAdapter(this).takeIf(BandAdapter::isAvailable) ?: FakeBand9Adapter()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            BandBridgeContract.ACTION_STOP_LIVE -> stopBridge("用户已停止实时会话")
            BandBridgeContract.ACTION_TRIGGER_HISTORY_SYNC -> {
                if (ensureStarted()) {
                    adapter.triggerHistorySync()
                        .onSuccess(::publish)
                        .onFailure { publishError("历史同步请求失败：${it.message ?: "未知错误"}") }
                    scheduleIdleStop()
                }
            }
            BandBridgeContract.ACTION_START_LIVE, null -> ensureStarted()
            else -> publishError("拒绝未知 Service action")
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        handler.removeCallbacks(idleStop)
        adapter.stop()
        running = false
        super.onDestroy()
    }

    private fun ensureStarted(): Boolean {
        if (running) {
            scheduleIdleStop()
            return true
        }
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        ) {
            publishError("请先在 Caesar Band Bridge 诊断页授予附近设备权限")
            stopSelf()
            return false
        }
        startAsConnectedDeviceForeground(store.snapshot())
        val initial = adapter.initialSnapshot().copy(
            observedAt = System.currentTimeMillis(),
            bridgeState = if (adapter.isAvailable()) BridgeState.LISTENING else BridgeState.UNAVAILABLE,
        )
        publish(initial)
        adapter.start(::publish)
            .onSuccess { running = true }
            .onFailure {
                running = false
                publish(initial.copy(statusMessage = it.message ?: initial.statusMessage))
            }
        scheduleIdleStop()
        return true
    }

    private fun stopBridge(message: String) {
        handler.removeCallbacks(idleStop)
        adapter.stop()
        running = false
        val current = store.snapshot()
        publish(
            current.copy(
                observedAt = System.currentTimeMillis(),
                connected = null,
                bridgeState = if (adapter.isAvailable()) BridgeState.IDLE else BridgeState.UNAVAILABLE,
                statusMessage = message,
                // No real live protocol is active, so every live measurement is cleared.
                batteryPercent = null,
                charging = null,
                wearing = null,
                sleeping = null,
                heartRateBpm = null,
                stepDelta = null,
            ),
        )
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun publish(snapshot: BandBridgeSnapshot) {
        store.publish(snapshot)
        if (running) notificationManager().notify(NOTIFICATION_ID, notification(snapshot))
    }

    private fun publishError(message: String) {
        publish(
            store.snapshot().copy(
                observedAt = System.currentTimeMillis(),
                bridgeState = BridgeState.ERROR,
                statusMessage = message,
            ),
        )
    }

    private fun scheduleIdleStop() {
        handler.removeCallbacks(idleStop)
        handler.postDelayed(idleStop, IDLE_TIMEOUT_MILLIS)
    }

    private fun startAsConnectedDeviceForeground(snapshot: BandBridgeSnapshot) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification(snapshot),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification(snapshot))
        }
    }

    private fun notification(snapshot: BandBridgeSnapshot) = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
        .setContentTitle("Caesar Band Bridge")
        .setContentText(snapshot.statusMessage)
        .setStyle(NotificationCompat.BigTextStyle().bigText(snapshot.statusMessage))
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setContentIntent(
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, BandBridgeDiagnosticsActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        )
        .addAction(
            0,
            "停止",
            PendingIntent.getService(
                this,
                1,
                Intent(this, BandBridgeService::class.java).setAction(BandBridgeContract.ACTION_STOP_LIVE),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        )
        .build()

    private fun createNotificationChannel() {
        notificationManager().createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "手环实时会话", NotificationManager.IMPORTANCE_LOW).apply {
                description = "显示 Caesar 与手环伴侣的用户可见连接会话"
            },
        )
    }

    private fun notificationManager(): NotificationManager = getSystemService(NotificationManager::class.java)

    companion object {
        private const val CHANNEL_ID = "caesar_band_live"
        private const val NOTIFICATION_ID = 9109
        private const val IDLE_TIMEOUT_MILLIS = 10 * 60_000L
    }
}
