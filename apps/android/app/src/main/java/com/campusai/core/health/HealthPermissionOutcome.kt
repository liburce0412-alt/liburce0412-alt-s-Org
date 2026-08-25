package com.campusai.core.health

internal data class HealthPermissionOutcome(
    val granted: Set<String>,
    val missing: Set<String>,
) {
    val allGranted: Boolean get() = missing.isEmpty()
}

internal fun evaluateHealthPermissionOutcome(
    requested: Set<String>,
    granted: Set<String>,
): HealthPermissionOutcome = HealthPermissionOutcome(
    granted = granted,
    missing = requested - granted,
)
