package com.build.buddyai.core.agent

import com.build.buddyai.core.model.BuildProfile
import com.build.buddyai.core.model.Project
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ManifestPlaceholderResolver @Inject constructor() {

    data class ValidationResult(
        val resolvedManifest: String,
        val resolvedValues: Map<String, String>,
        val unresolvedKeys: List<String>,
        val warnings: List<String>
    ) {
        val isValid: Boolean get() = unresolvedKeys.isEmpty()
    }

    fun resolve(project: Project, manifestText: String, buildProfile: BuildProfile): ValidationResult {
        val resolvedValues = linkedMapOf<String, String>().apply {
            put("applicationId", project.packageName + buildProfile.applicationIdSuffix)
            put("packageName", project.packageName)
            put("flavorName", buildProfile.flavorName.ifBlank { "main" })
            put("versionName", buildProfile.versionNameOverride ?: "1.0.0${buildProfile.versionNameSuffix}")
            put("versionCode", (buildProfile.versionCodeOverride ?: 1).toString())
            putAll(buildProfile.manifestPlaceholders)
        }
        val placeholderPattern = Regex("""\$\{([A-Za-z0-9_.-]+)\}""")
        val unresolved = mutableSetOf<String>()
        val warnings = mutableListOf<String>()
        val resolvedManifest = placeholderPattern.replace(manifestText) { match ->
            val key = match.groupValues[1]
            resolvedValues[key] ?: run {
                unresolved += key
                match.value
            }
        }

        if (buildProfile.applicationIdSuffix.isNotBlank() && !manifestText.contains("\${applicationId}")) {
            warnings += "Application ID suffix is configured, but the manifest does not reference \${applicationId}. The on-device engine will preserve the base package for validation builds."
        }

        return ValidationResult(
            resolvedManifest = resolvedManifest,
            resolvedValues = resolvedValues,
            unresolvedKeys = unresolved.toList().sorted(),
            warnings = warnings
        )
    }

    fun stage(project: Project, projectDir: File, buildProfile: BuildProfile, outputDir: File): ValidationResult {
        val manifestFile = File(projectDir, "app/src/main/AndroidManifest.xml")
        val original = manifestFile.readText()
        val result = resolve(project, original, buildProfile)
        outputDir.mkdirs()
        File(outputDir, "AndroidManifest.xml").writeText(result.resolvedManifest)
        return result
    }
}
