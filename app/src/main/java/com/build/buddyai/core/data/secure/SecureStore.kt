package com.build.buddyai.core.data.secure

import android.content.Context
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.build.buddyai.core.model.ProviderId
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecureStore @Inject constructor(
    @ApplicationContext
    context: Context,
) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "secret_store",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun setApiKey(providerId: ProviderId, value: String) {
        prefs.edit(commit = true) {
            putString(providerId.name, value)
        }
    }

    fun getApiKey(providerId: ProviderId): String? = prefs.getString(providerId.name, null)

    fun clearApiKey(providerId: ProviderId) {
        prefs.edit(commit = true) {
            remove(providerId.name)
        }
    }
}
