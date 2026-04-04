package com.build.buddyai.core.data.datastore

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecureKeyStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "buildbuddy_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun getApiKey(providerId: String): String? = prefs.getString("api_key_$providerId", null)

    fun setApiKey(providerId: String, key: String) {
        prefs.edit().putString("api_key_$providerId", key).apply()
    }

    fun removeApiKey(providerId: String) {
        prefs.edit().remove("api_key_$providerId").apply()
    }

    fun hasApiKey(providerId: String): Boolean = prefs.contains("api_key_$providerId")

    fun getEnabledProviders(): Set<String> {
        return prefs.getStringSet("enabled_providers", emptySet()) ?: emptySet()
    }

    fun setProviderEnabled(providerId: String, enabled: Boolean) {
        val current = getEnabledProviders().toMutableSet()
        if (enabled) current.add(providerId) else current.remove(providerId)
        prefs.edit().putStringSet("enabled_providers", current).apply()
    }
}
