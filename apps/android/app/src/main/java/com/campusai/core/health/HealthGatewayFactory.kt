package com.campusai.core.health

import android.content.Context
import android.os.Build

object HealthGatewayFactory {
    fun create(context: Context): HealthGateway {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return UnsupportedHealthGateway
        return runCatching {
            Class.forName("com.campusai.core.health.HealthConnectGateway")
                .getConstructor(Context::class.java)
                .newInstance(context.applicationContext) as HealthGateway
        }.getOrElse { UnsupportedHealthGateway }
    }
}

private object UnsupportedHealthGateway : HealthGateway {
    override val readPermissions: Set<String> = emptySet()
    override fun availability(): HealthAvailability = HealthAvailability.Unsupported
    override suspend fun grantedPermissions(): Set<String> = emptySet()
    override suspend fun snapshot(period: HealthPeriod): Result<HealthSnapshot> =
        Result.failure(UnsupportedOperationException("此 Android 版本不支持 Health Connect"))
}
