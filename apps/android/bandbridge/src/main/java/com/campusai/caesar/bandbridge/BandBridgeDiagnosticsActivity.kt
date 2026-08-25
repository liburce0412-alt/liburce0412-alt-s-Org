package com.campusai.caesar.bandbridge

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.campusai.caesar.bandcontract.BandBridgeContract

class BandBridgeDiagnosticsActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var store: BandSnapshotStore
    private lateinit var tokenVault: PairingTokenVault

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) = render()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = BandSnapshotStore.get(this)
        tokenVault = PairingTokenVault(this)
        setContentView(buildContent())
        requestForegroundPrerequisites()
        contentResolver.registerContentObserver(BandBridgeContract.SNAPSHOT_URI, false, observer)
        render()
    }

    override fun onDestroy() {
        contentResolver.unregisterContentObserver(observer)
        super.onDestroy()
    }

    private fun buildContent(): ScrollView = ScrollView(this).apply {
        addView(
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(24.dp, 28.dp, 24.dp, 28.dp)
                addView(TextView(context).apply {
                    text = "Caesar Band Bridge 诊断"
                    textSize = 24f
                })
                addView(TextView(context).apply {
                    text = "仅使用 Gadgetbridge 官方 Intent API；Band 9 私有实时协议当前明确为 Unavailable。"
                    textSize = 15f
                    setPadding(0, 12.dp, 0, 20.dp)
                })
                status = TextView(context).apply {
                    textSize = 14f
                    setTextIsSelectable(true)
                }
                addView(status, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                addView(actionButton("开始状态会话", BandBridgeContract.ACTION_START_LIVE))
                addView(actionButton("触发 Gadgetbridge 历史同步", BandBridgeContract.ACTION_TRIGGER_HISTORY_SYNC))
                addView(actionButton("停止状态会话", BandBridgeContract.ACTION_STOP_LIVE))
                addView(Button(context).apply {
                    text = "刷新"
                    setOnClickListener { render() }
                })
            },
        )
    }

    private fun actionButton(label: String, action: String) = Button(this).apply {
        text = label
        setOnClickListener {
            val intent = Intent(action).setComponent(
                ComponentName(this@BandBridgeDiagnosticsActivity, BandBridgeService::class.java),
            )
            if (action == BandBridgeContract.ACTION_START_LIVE || action == BandBridgeContract.ACTION_TRIGGER_HISTORY_SYNC) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        }
    }

    private fun render() {
        if (!::status.isInitialized) return
        val value = store.snapshot()
        status.text = buildString {
            appendLine("Schema: ${BandBridgeContract.SCHEMA_VERSION}")
            appendLine("Bridge state: ${value.bridgeState}")
            appendLine("Status: ${value.statusMessage}")
            appendLine("Source: ${value.source}")
            appendLine("Observed at: ${value.observedAt}")
            appendLine("Connected: ${value.connected ?: "Unknown"}")
            appendLine("History sync: ${value.historySyncState}")
            appendLine("Capabilities: 0x${value.capabilityBits.toString(16)}")
            appendLine("Live heart rate: ${value.heartRateBpm ?: "Unavailable"}")
            appendLine("Pairing token in Bridge vault: ${if (tokenVault.hasToken()) "Present (not readable here)" else "Absent"}")
            appendLine()
            append("Gadgetbridge pairing keys are never extracted. Health history is read by CampusAI from Health Connect.")
        }
    }

    private fun requestForegroundPrerequisites() {
        val permissions = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
            ) add(Manifest.permission.BLUETOOTH_CONNECT)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (permissions.isNotEmpty()) requestPermissions(permissions.toTypedArray(), REQUEST_PERMISSIONS)
    }

    private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()

    companion object {
        private const val REQUEST_PERMISSIONS = 9001
    }
}
