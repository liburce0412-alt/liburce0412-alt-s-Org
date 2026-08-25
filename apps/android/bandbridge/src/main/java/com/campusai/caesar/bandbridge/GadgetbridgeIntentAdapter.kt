package com.campusai.caesar.bandbridge

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.campusai.caesar.bandcontract.BandBridgeContract
import com.campusai.caesar.bandcontract.BandBridgeSnapshot
import com.campusai.caesar.bandcontract.BridgeState
import com.campusai.caesar.bandcontract.HistorySyncState

/**
 * Clean-room adapter for Gadgetbridge's documented public Intent API only.
 *
 * It can request an activity-history sync and observe completion/connected broadcasts. Modern
 * Gadgetbridge builds are permission-gated with their app-owned signature permission when that
 * permission can be validated; otherwise completion is explicitly treated as request-correlated,
 * not sender-authenticated. The API does not expose live heart rate, battery, wearing state, or an
 * initial connection query, so those values remain null and their capability bits stay clear.
 */
internal class GadgetbridgeIntentAdapter(private val context: Context) : BandAdapter {
    override val id: String = "gadgetbridge-documented-intents"

    private var listener: ((BandBridgeSnapshot) -> Unit)? = null
    private var registered = false
    private var current = initialSnapshot()
    private var lastHistoryRequestElapsedMillis: Long? = null
    private var signaturePermissionGateActive = false
    private var pendingHistorySync = false
    private var historySyncCommandSent = false
    private val handler = Handler(Looper.getMainLooper())
    private val deviceAddressStore = GadgetbridgeDeviceAddressStore(context)
    private val selectedPackage: String? get() = findInstalledPackage()

    private val connectFallback = Runnable {
        if (pendingHistorySync && !historySyncCommandSent) {
            sendHistorySyncCommand("连接状态尚未确认，已继续请求 Gadgetbridge 拉取历史数据")
        }
    }

    private val syncTimeout = Runnable {
        if (!pendingHistorySync || current.historySyncState != HistorySyncState.REQUESTED) return@Runnable
        pendingHistorySync = false
        historySyncCommandSent = false
        current = current.copy(
            observedAt = System.currentTimeMillis(),
            connected = null,
            historySyncState = HistorySyncState.ERROR,
            statusMessage = "等待 Gadgetbridge 同步完成超时；旧的 Health Connect 数据仍可读取",
        )
        listener?.invoke(current)
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val now = System.currentTimeMillis()
            val sourcePackage = selectedPackage ?: return
            val sentUid = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                sentFromUid
            } else {
                Process.INVALID_UID
            }
            val senderPackages = if (sentUid == Process.INVALID_UID) {
                emptySet()
            } else {
                context.packageManager.getPackagesForUid(sentUid).orEmpty().toSet()
            }
            val evidence = gadgetbridgeBroadcastEvidence(
                isHistoryCompletion = intent.action == ACTION_ACTIVITY_SYNC_FINISH,
                senderIdentityAvailable = sentUid != Process.INVALID_UID,
                senderMatchesInstalledPackage = sourcePackage in senderPackages,
                signaturePermissionGateActive = signaturePermissionGateActive,
                historySyncState = current.historySyncState,
                lastHistoryRequestElapsedMillis = lastHistoryRequestElapsedMillis,
                nowElapsedMillis = SystemClock.elapsedRealtime(),
            ) ?: return
            current = when (intent.action) {
                ACTION_BLUETOOTH_CONNECTED -> current.copy(
                    observedAt = now,
                    connected = true,
                    bridgeState = BridgeState.LISTENING,
                    statusMessage = when (evidence) {
                        GadgetbridgeBroadcastEvidence.VERIFIED_SENDER ->
                            "收到 $sourcePackage 的已连接广播；发送方身份已验证"
                        GadgetbridgeBroadcastEvidence.SIGNATURE_PERMISSION_GATED ->
                            "收到 Gadgetbridge 签名权限门控的已连接广播；断开状态仍不可查询"
                        GadgetbridgeBroadcastEvidence.CORRELATED_REQUEST -> return
                    },
                )
                ACTION_ACTIVITY_SYNC_FINISH -> current.copy(
                    observedAt = now,
                    historySyncState = HistorySyncState.FINISHED,
                    statusMessage = when (evidence) {
                        GadgetbridgeBroadcastEvidence.VERIFIED_SENDER ->
                            "Gadgetbridge 已广播历史活动同步完成；发送方身份已验证"
                        GadgetbridgeBroadcastEvidence.SIGNATURE_PERMISSION_GATED ->
                            "本次 Gadgetbridge 历史同步已完成；完成广播已由 Gadgetbridge 所有的签名权限门控"
                        GadgetbridgeBroadcastEvidence.CORRELATED_REQUEST ->
                            "本次 Gadgetbridge 历史同步已完成；公共广播未共享发送方身份，仅按请求时间窗匹配"
                    },
                )
                else -> return
            }
            listener?.invoke(current)
            when (intent.action) {
                ACTION_BLUETOOTH_CONNECTED -> {
                    intent.getStringExtra(EXTRA_DEVICE_ADDRESS)?.let(deviceAddressStore::remember)
                    if (pendingHistorySync && !historySyncCommandSent) {
                        handler.removeCallbacks(connectFallback)
                        sendHistorySyncCommand("手环已连接，正在请求 Gadgetbridge 拉取历史数据")
                    }
                }
                ACTION_ACTIVITY_SYNC_FINISH -> {
                    pendingHistorySync = false
                    historySyncCommandSent = false
                    handler.removeCallbacks(connectFallback)
                    handler.removeCallbacks(syncTimeout)
                }
            }
        }
    }

    override fun isAvailable(): Boolean = selectedPackage != null

    override fun initialSnapshot(nowMillis: Long): BandBridgeSnapshot {
        val installed = selectedPackage
        return if (installed == null) {
            FakeBand9Adapter().initialSnapshot(nowMillis).copy(
                statusMessage = "未检测到受支持的 Gadgetbridge 包；Band 9 实时协议仍为 Unavailable",
            )
        } else {
            BandBridgeSnapshot(
                observedAt = nowMillis,
                connected = null,
                capabilityBits = BandBridgeContract.Capability.GADGETBRIDGE_INSTALLED or
                    BandBridgeContract.Capability.GADGETBRIDGE_ACTIVITY_SYNC_TRIGGER or
                    BandBridgeContract.Capability.GADGETBRIDGE_CONNECTED_EVENT or
                    BandBridgeContract.Capability.GADGETBRIDGE_CONNECT_TRIGGER,
                bridgeState = BridgeState.IDLE,
                statusMessage = "已检测到 $installed；需在 Gadgetbridge 启用 Intent API，连接状态等待官方广播",
                historySyncState = HistorySyncState.IDLE,
                source = "Gadgetbridge documented Intent API",
            )
        }
    }

    override fun start(listener: (BandBridgeSnapshot) -> Unit): Result<Unit> = runCatching {
        check(isAvailable()) { "未安装受支持的 Gadgetbridge" }
        this.listener = listener
        current = initialSnapshot().copy(
            bridgeState = BridgeState.LISTENING,
            statusMessage = "正在监听 Gadgetbridge 官方连接/同步完成广播；实时心率不可用",
        )
        listener(current)
        if (!registered) {
            val filter = IntentFilter().apply {
                addAction(ACTION_BLUETOOTH_CONNECTED)
                addAction(ACTION_ACTIVITY_SYNC_FINISH)
            }
            // Modern Gadgetbridge builds own an AGP-generated signature permission. Requiring it
            // means only Gadgetbridge or another package signed by the same maintainer can reach
            // this receiver, even though the public broadcast does not share its precise UID.
            val signaturePermission = selectedPackage?.let(::validatedSignatureSenderPermission)
            signaturePermissionGateActive = signaturePermission != null
            val senderPermission = signaturePermission ?: if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Manifest.permission.BLUETOOTH_CONNECT
            } else null
            ContextCompat.registerReceiver(
                context,
                receiver,
                filter,
                senderPermission,
                null,
                ContextCompat.RECEIVER_EXPORTED,
            )
            registered = true
        }
    }

    override fun stop() {
        handler.removeCallbacks(connectFallback)
        handler.removeCallbacks(syncTimeout)
        if (registered) runCatching { context.unregisterReceiver(receiver) }
        registered = false
        listener = null
    }

    override fun triggerHistorySync(): Result<BandBridgeSnapshot> = runCatching {
        val packageName = selectedPackage ?: error("未安装受支持的 Gadgetbridge")
        val requestedAt = System.currentTimeMillis()
        lastHistoryRequestElapsedMillis = SystemClock.elapsedRealtime()
        pendingHistorySync = true
        historySyncCommandSent = false
        handler.removeCallbacks(connectFallback)
        handler.removeCallbacks(syncTimeout)

        val deviceAddress = deviceAddressStore.resolve()
        current = current.copy(
            observedAt = requestedAt,
            connected = null,
            historySyncState = if (deviceAddress == null) HistorySyncState.REQUESTED else HistorySyncState.CONNECTING,
            statusMessage = if (deviceAddress == null) {
                "尚未识别可安全连接的 Band 9 地址；已直接请求 Gadgetbridge 同步当前设备"
            } else {
                "已请求 Gadgetbridge 连接 Band 9；连接后将自动拉取历史数据"
            },
        )
        listener?.invoke(current)
        if (deviceAddress == null) {
            sendHistorySyncCommand(current.statusMessage)
        } else {
            context.sendBroadcast(
                Intent(ACTION_BLUETOOTH_CONNECT)
                    .setPackage(packageName)
                    .putExtra(EXTRA_DEVICE_ADDRESS, deviceAddress),
            )
            handler.postDelayed(connectFallback, CONNECT_GRACE_MILLIS)
        }
        current
    }

    private fun sendHistorySyncCommand(message: String) {
        if (!pendingHistorySync || historySyncCommandSent) return
        val packageName = selectedPackage ?: return
        historySyncCommandSent = true
        current = current.copy(
            observedAt = System.currentTimeMillis(),
            historySyncState = HistorySyncState.REQUESTED,
            statusMessage = message,
        )
        listener?.invoke(current)
        context.sendBroadcast(Intent(ACTION_ACTIVITY_SYNC).setPackage(packageName))
        handler.removeCallbacks(syncTimeout)
        handler.postDelayed(syncTimeout, HISTORY_COMPLETION_WINDOW_MILLIS)
    }

    @Suppress("DEPRECATION")
    private fun validatedSignatureSenderPermission(packageName: String): String? {
        val permissionName = "$packageName.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"
        val info = runCatching { context.packageManager.getPermissionInfo(permissionName, 0) }.getOrNull()
            ?: return null
        val baseProtection = info.protectionLevel and PermissionInfo.PROTECTION_MASK_BASE
        val ownedByPackage = info.packageName == packageName
        val grantedToPackage = context.packageManager.checkPermission(permissionName, packageName) ==
            PackageManager.PERMISSION_GRANTED
        return permissionName.takeIf {
            ownedByPackage && baseProtection == PermissionInfo.PROTECTION_SIGNATURE && grantedToPackage
        }
    }

    @Suppress("DEPRECATION")
    private fun findInstalledPackage(): String? = preferredGadgetbridgePackage { packageName ->
        runCatching { context.packageManager.getPackageInfo(packageName, 0) }.isSuccess
    }

    companion object {
        // Public action names documented at https://gadgetbridge.org/internals/automations/intents/
        const val ACTION_ACTIVITY_SYNC = "nodomain.freeyourgadget.gadgetbridge.command.ACTIVITY_SYNC"
        const val ACTION_ACTIVITY_SYNC_FINISH = "nodomain.freeyourgadget.gadgetbridge.action.ACTIVITY_SYNC_FINISH"
        const val ACTION_BLUETOOTH_CONNECT = "nodomain.freeyourgadget.gadgetbridge.BLUETOOTH_CONNECT"
        const val ACTION_BLUETOOTH_CONNECTED = "nodomain.freeyourgadget.gadgetbridge.BLUETOOTH_CONNECTED"
        const val EXTRA_DEVICE_ADDRESS = "EXTRA_DEVICE_ADDRESS"

        val SUPPORTED_PACKAGES = listOf(
            "nodomain.freeyourgadget.gadgetbridge.nightly",
            "nodomain.freeyourgadget.gadgetbridge.nightly_nopebble",
            "nodomain.freeyourgadget.gadgetbridge",
        )
    }
}

/** Keeps the Band address private to the Bridge process; it is never exposed through IPC. */
internal class GadgetbridgeDeviceAddressStore(private val context: Context) {
    private val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun remember(address: String) {
        if (isBluetoothAddress(address)) preferences.edit().putString(KEY_ADDRESS, address.uppercase()).apply()
    }

    fun resolve(): String? {
        preferences.getString(KEY_ADDRESS, null)?.takeIf(::isBluetoothAddress)?.let { return it }
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        ) return null
        val manager = context.getSystemService(BluetoothManager::class.java) ?: return null
        val candidates = runCatching {
            manager.adapter?.bondedDevices.orEmpty().mapNotNull { device ->
                val name = runCatching { device.name.orEmpty() }.getOrDefault("")
                device.address.takeIf { isSupportedBand9Name(name) && isBluetoothAddress(it) }
            }.distinct()
        }.getOrDefault(emptyList())
        return candidates.singleOrNull()?.also(::remember)
    }

    companion object {
        private const val PREFS = "gadgetbridge_device_v1"
        private const val KEY_ADDRESS = "band9_address"
    }
}

internal fun isSupportedBand9Name(name: String): Boolean {
    val normalized = name.lowercase().replace(Regex("[^a-z0-9]+"), " ").trim()
    return normalized.contains("xiaomi smart band 9") ||
        normalized.contains("mi smart band 9") ||
        normalized.contains("mi band 9")
}

internal fun isBluetoothAddress(value: String): Boolean =
    value.matches(Regex("(?i)^[0-9a-f]{2}(:[0-9a-f]{2}){5}$"))

/**
 * Prefer a co-installed Nightly package because Caesar's Band 9 compatibility patch is shipped
 * there. The official package remains the fallback for devices that do not install the bridge
 * companion build.
 */
internal fun preferredGadgetbridgePackage(isInstalled: (String) -> Boolean): String? =
    GadgetbridgeIntentAdapter.SUPPORTED_PACKAGES.firstOrNull(isInstalled)

internal enum class GadgetbridgeBroadcastEvidence {
    VERIFIED_SENDER,
    SIGNATURE_PERMISSION_GATED,
    CORRELATED_REQUEST,
}

/**
 * Gadgetbridge's public Intent API does not currently enable Android 14+'s sender identity
 * sharing. A completion with an unavailable sender identity is therefore accepted only while a
 * user/Caesar-triggered history request is pending and inside a short correlation window. This
 * evidence may update sync status only; it must never unlock live-measurement capability bits.
 */
internal fun gadgetbridgeBroadcastEvidence(
    isHistoryCompletion: Boolean,
    senderIdentityAvailable: Boolean,
    senderMatchesInstalledPackage: Boolean,
    signaturePermissionGateActive: Boolean,
    historySyncState: HistorySyncState,
    lastHistoryRequestElapsedMillis: Long?,
    nowElapsedMillis: Long,
): GadgetbridgeBroadcastEvidence? {
    if (senderIdentityAvailable) {
        return GadgetbridgeBroadcastEvidence.VERIFIED_SENDER.takeIf { senderMatchesInstalledPackage }
    }
    if (!isHistoryCompletion && signaturePermissionGateActive) {
        return GadgetbridgeBroadcastEvidence.SIGNATURE_PERMISSION_GATED
    }
    if (!isHistoryCompletion || historySyncState != HistorySyncState.REQUESTED) return null
    val requestedAt = lastHistoryRequestElapsedMillis ?: return null
    val elapsed = nowElapsedMillis - requestedAt
    if (elapsed !in 0..HISTORY_COMPLETION_WINDOW_MILLIS) return null
    return if (signaturePermissionGateActive) {
        GadgetbridgeBroadcastEvidence.SIGNATURE_PERMISSION_GATED
    } else {
        GadgetbridgeBroadcastEvidence.CORRELATED_REQUEST
    }
}

private const val CONNECT_GRACE_MILLIS = 12_000L
private const val HISTORY_COMPLETION_WINDOW_MILLIS = 2 * 60_000L
