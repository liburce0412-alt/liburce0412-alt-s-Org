package com.campusai.core.health.mifitness

import android.content.Context
import com.campusai.core.security.SecurePreferences

internal interface MiFitnessSecretStorage {
    fun read(key: String): String
    fun write(key: String, value: String): Boolean
}

internal class SecurePreferencesMiFitnessStorage(context: Context) : MiFitnessSecretStorage {
    private val appContext = context.applicationContext

    override fun read(key: String): String = SecurePreferences.decrypt(appContext, key)

    override fun write(key: String, value: String): Boolean =
        SecurePreferences.encrypt(appContext, key, value)
}
