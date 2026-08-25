package com.campusai.core.health

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.health.connect.client.PermissionController

/** Keeps Health Connect classes out of the API 24/25 main activity path. */
class HealthPermissionActivity : ComponentActivity() {
    private var requestedPermissions: Set<String> = emptySet()

    private val permissionLauncher = registerForActivityResult(
        PermissionController.createRequestPermissionResultContract(),
    ) { grantedPermissions ->
        val outcome = evaluateHealthPermissionOutcome(requestedPermissions, grantedPermissions)
        val result = Intent()
            .putStringArrayListExtra(EXTRA_GRANTED_PERMISSIONS, ArrayList(outcome.granted.sorted()))
            .putStringArrayListExtra(EXTRA_MISSING_PERMISSIONS, ArrayList(outcome.missing.sorted()))
        setResult(if (outcome.allGranted) RESULT_OK else RESULT_CANCELED, result)
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val gateway = HealthGatewayFactory.create(applicationContext)
        val availability = gateway.availability()
        if (
            gateway.readPermissions.isEmpty() ||
            availability == HealthAvailability.Unsupported ||
            availability == HealthAvailability.NeedsProvider
        ) {
            setResult(RESULT_CANCELED)
            finish()
            return
        }
        requestedPermissions = gateway.readPermissions
        permissionLauncher.launch(requestedPermissions)
    }

    companion object {
        const val EXTRA_GRANTED_PERMISSIONS = "com.campusai.extra.GRANTED_HEALTH_PERMISSIONS"
        const val EXTRA_MISSING_PERMISSIONS = "com.campusai.extra.MISSING_HEALTH_PERMISSIONS"
    }
}
