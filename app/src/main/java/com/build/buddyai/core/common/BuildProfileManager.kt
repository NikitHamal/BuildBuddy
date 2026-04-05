package com.build.buddyai.core.common

import android.content.Context
import android.net.Uri
import com.build.buddyai.core.model.BuildProfile
import com.build.buddyai.core.model.BuildVariant
import com.build.buddyai.core.model.SigningConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BuildProfileManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val secureKeyStore: SecureKeyStore,
    private val json: Json
) {
    fun loadProfile(projectId: String): BuildProfile {
        val file = profileFile(projectId)
        return if (!file.exists()) {
            BuildProfile()
        } else {
            runCatching { json.decodeFromString<StoredProfile>(file.readText()).toModel() }.getOrElse { BuildProfile() }
        }
    }

    fun saveProfile(projectId: String, profile: BuildProfile, storePassword: String? = null, keyPassword: String? = null) {
        profileFile(projectId).apply {
            parentFile?.mkdirs()
            writeText(json.encodeToString(StoredProfile.fromModel(profile)))
        }
        storePassword?.takeIf { it.isNotBlank() }?.let { secureKeyStore.storeApiKey("sign_store_$projectId", it) }
        keyPassword?.takeIf { it.isNotBlank() }?.let { secureKeyStore.storeApiKey("sign_key_$projectId", it) }
    }

    fun importKeystore(projectId: String, sourceUri: Uri, displayName: String? = null): SigningConfig {
        val keystoreDir = File(context.filesDir, "signing/$projectId").apply { mkdirs() }
        val extension = displayName?.substringAfterLast('.', "jks")?.ifBlank { "jks" } ?: "jks"
        val sanitized = (displayName?.substringBeforeLast('.', displayName) ?: UUID.randomUUID().toString())
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
        val target = File(keystoreDir, "$sanitized.$extension")
        context.contentResolver.openInputStream(sourceUri)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        } ?: error("Unable to open keystore")
        return SigningConfig(
            keystoreFileName = target.name,
            keystorePath = target.absolutePath,
            keyAlias = "",
            importedAt = System.currentTimeMillis()
        )
    }

    fun getSigningSecrets(projectId: String): SigningSecrets? {
        val storePassword = secureKeyStore.getApiKey("sign_store_$projectId")
        val keyPassword = secureKeyStore.getApiKey("sign_key_$projectId")
        return if (storePassword.isNullOrBlank() || keyPassword.isNullOrBlank()) null else SigningSecrets(storePassword, keyPassword)
    }

    fun clearSigning(projectId: String) {
        secureKeyStore.deleteApiKey("sign_store_$projectId")
        secureKeyStore.deleteApiKey("sign_key_$projectId")
        val current = loadProfile(projectId)
        saveProfile(projectId, current.copy(variant = BuildVariant.DEBUG, signing = null))
    }

    private fun profileFile(projectId: String) = File(context.filesDir, "build_profiles/$projectId.json")

    @Serializable
    private data class StoredProfile(
        val variant: BuildVariant = BuildVariant.DEBUG,
        val installAfterBuild: Boolean = true,
        val signing: SigningConfig? = null
    ) {
        fun toModel() = BuildProfile(variant = variant, installAfterBuild = installAfterBuild, signing = signing)

        companion object {
            fun fromModel(profile: BuildProfile) = StoredProfile(
                variant = profile.variant,
                installAfterBuild = profile.installAfterBuild,
                signing = profile.signing
            )
        }
    }

    data class SigningSecrets(
        val storePassword: String,
        val keyPassword: String
    )
}
