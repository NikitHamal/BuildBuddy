package com.build.buddyai.core.common

import android.content.Context
import android.net.Uri
import com.build.buddyai.core.model.BuildProfile
import com.build.buddyai.core.model.BuildVariant
import com.build.buddyai.core.model.SigningConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import java.io.File
import java.security.KeyStore
import java.security.PrivateKey
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BuildProfileManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val secureKeyStore: SecureKeyStore,
    private val json: Json,
    private val signingAuditStore: SigningAuditStore
) {
    fun loadProfile(projectId: String): BuildProfile {
        val file = profileFile(projectId)
        return if (!file.exists()) {
            BuildProfile()
        } else {
            runCatching { json.decodeFromString<StoredProfile>(file.readText()).toModel() }.getOrElse { BuildProfile() }
        }
    }

    fun saveProfile(projectId: String, profile: BuildProfile, storePassword: String? = null, keyPassword: String? = null, audit: Boolean = true) {
        validateSigningCredentials(projectId, profile, storePassword, keyPassword)
        val existingSigning = loadProfile(projectId).signing
        profileFile(projectId).apply {
            parentFile?.mkdirs()
            writeText(json.encodeToString<StoredProfile>(StoredProfile.fromModel(profile)))
        }
        val secretsSaved = !storePassword.isNullOrBlank() || !keyPassword.isNullOrBlank()
        storePassword?.takeIf { it.isNotBlank() }?.let { secureKeyStore.storeApiKey("sign_store_$projectId", it) }
        keyPassword?.takeIf { it.isNotBlank() }?.let { secureKeyStore.storeApiKey("sign_key_$projectId", it) }
        val signingChanged = existingSigning?.keystorePath != profile.signing?.keystorePath || existingSigning?.keyAlias != profile.signing?.keyAlias || (existingSigning == null) != (profile.signing == null)
        if (audit && (secretsSaved || signingChanged)) {
            signingAuditStore.record(
                SigningAuditEntry(
                    projectId = projectId,
                    eventType = if (secretsSaved) "SIGNING_SECRETS_UPDATED" else "SIGNING_PROFILE_UPDATED",
                    detail = buildString {
                        append("Updated signing profile")
                        profile.signing?.keyAlias?.takeIf { it.isNotBlank() }?.let { append(" for alias ").append(it) }
                    },
                    signerAlias = profile.signing?.keyAlias?.takeIf { it.isNotBlank() },
                    variant = profile.variant.name,
                    artifactFormat = profile.artifactFormat.name
                )
            )
        }
    }

    fun importKeystore(projectId: String, sourceUri: Uri, displayName: String? = null): SigningConfig {
        val keystoreDir = File(context.filesDir, "signing/$projectId").apply { mkdirs() }
        val hadExistingKeystore = keystoreDir.listFiles()?.any { it.isFile } == true
        keystoreDir.listFiles()?.forEach { existing -> if (existing.isFile) existing.delete() }
        val extension = displayName?.substringAfterLast('.', "jks")?.ifBlank { "jks" } ?: "jks"
        val sanitized = (displayName?.substringBeforeLast('.', displayName) ?: UUID.randomUUID().toString())
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
        val target = File(keystoreDir, "$sanitized.$extension")
        context.contentResolver.openInputStream(sourceUri)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        } ?: error("Unable to open keystore")
        signingAuditStore.record(
            SigningAuditEntry(
                projectId = projectId,
                eventType = if (hadExistingKeystore) "KEYSTORE_ROTATED" else "KEYSTORE_IMPORTED",
                detail = if (hadExistingKeystore) "Replaced the active keystore with ${target.name}" else "Imported ${target.name}",
                signerAlias = null
            )
        )
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
        File(context.filesDir, "signing/$projectId").deleteRecursively()
        val current = loadProfile(projectId)
        saveProfile(projectId, current.copy(variant = BuildVariant.DEBUG, signing = null), audit = false)
        signingAuditStore.record(
            SigningAuditEntry(
                projectId = projectId,
                eventType = "SIGNING_CLEARED",
                detail = "Deleted keystore material and reset the project to debug signing."
            )
        )
    }

    private fun profileFile(projectId: String) = File(context.filesDir, "build_profiles/$projectId.json")

    private fun validateSigningCredentials(projectId: String, profile: BuildProfile, storePassword: String?, keyPassword: String?) {
        val signing = profile.signing ?: return
        val hasIncomingSecrets = !storePassword.isNullOrBlank() || !keyPassword.isNullOrBlank()
        if (!hasIncomingSecrets) return
        val existing = getSigningSecrets(projectId)
        val resolvedStorePassword = storePassword?.takeIf { it.isNotBlank() } ?: existing?.storePassword
        val resolvedKeyPassword = keyPassword?.takeIf { it.isNotBlank() } ?: existing?.keyPassword
        if (resolvedStorePassword.isNullOrBlank() || resolvedKeyPassword.isNullOrBlank()) {
            throw IllegalArgumentException("Both store and key passwords are required for signing validation.")
        }
        if (signing.keystorePath.isBlank() || signing.keyAlias.isBlank()) {
            throw IllegalArgumentException("Keystore path and key alias are required before saving signing passwords.")
        }
        val keystoreFile = File(signing.keystorePath)
        require(keystoreFile.exists() && keystoreFile.isFile) { "Configured keystore file does not exist: ${keystoreFile.absolutePath}" }

        val keyStore = loadKeyStore(keystoreFile, resolvedStorePassword)
        require(keyStore.containsAlias(signing.keyAlias)) {
            "Keystore does not contain alias '${signing.keyAlias}'."
        }
        val privateKey = keyStore.getKey(signing.keyAlias, resolvedKeyPassword.toCharArray()) as? PrivateKey
        require(privateKey != null) {
            "Unable to unlock private key for alias '${signing.keyAlias}'. Check key password."
        }
    }

    private fun loadKeyStore(keystoreFile: File, password: String): KeyStore {
        val candidates = when (keystoreFile.extension.lowercase(Locale.US)) {
            "p12", "pfx" -> listOf("PKCS12", KeyStore.getDefaultType())
            else -> listOf(KeyStore.getDefaultType(), "JKS", "PKCS12")
        }.distinct()

        var lastError: Throwable? = null
        candidates.forEach { type ->
            try {
                return KeyStore.getInstance(type).apply {
                    keystoreFile.inputStream().use { load(it, password.toCharArray()) }
                }
            } catch (t: Throwable) {
                lastError = t
            }
        }
        throw IllegalArgumentException("Unable to open keystore '${keystoreFile.name}': ${lastError?.message}", lastError)
    }

    @Serializable
    private data class StoredProfile(
        val variant: BuildVariant = BuildVariant.DEBUG,
        val installAfterBuild: Boolean = true,
        val signing: SigningConfig? = null,
        val artifactFormat: com.build.buddyai.core.model.ArtifactFormat = com.build.buddyai.core.model.ArtifactFormat.APK,
        val flavorName: String = "main",
        val applicationIdSuffix: String = "",
        val versionNameSuffix: String = "",
        val versionCodeOverride: Int? = null,
        val versionNameOverride: String? = null,
        val manifestPlaceholders: Map<String, String> = emptyMap()
    ) {
        fun toModel() = BuildProfile(
            variant = variant,
            installAfterBuild = installAfterBuild,
            signing = signing,
            artifactFormat = artifactFormat,
            flavorName = flavorName,
            applicationIdSuffix = applicationIdSuffix,
            versionNameSuffix = versionNameSuffix,
            versionCodeOverride = versionCodeOverride,
            versionNameOverride = versionNameOverride,
            manifestPlaceholders = manifestPlaceholders
        )

        companion object {
            fun fromModel(profile: BuildProfile) = StoredProfile(
                variant = profile.variant,
                installAfterBuild = profile.installAfterBuild,
                signing = profile.signing,
                artifactFormat = profile.artifactFormat,
                flavorName = profile.flavorName,
                applicationIdSuffix = profile.applicationIdSuffix,
                versionNameSuffix = profile.versionNameSuffix,
                versionCodeOverride = profile.versionCodeOverride,
                versionNameOverride = profile.versionNameOverride,
                manifestPlaceholders = profile.manifestPlaceholders
            )
        }
    }

    data class SigningSecrets(
        val storePassword: String,
        val keyPassword: String
    )
}
