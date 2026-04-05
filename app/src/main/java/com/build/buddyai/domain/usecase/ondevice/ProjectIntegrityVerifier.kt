package com.build.buddyai.domain.usecase.ondevice

import com.build.buddyai.core.agent.ProjectSymbolIndex
import java.io.File
import java.util.Properties

private val PACKAGE_NAME_PATTERN = Regex("""^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)+$""")

enum class IntegrityLevel { ERROR, WARNING }

data class IntegrityIssue(
    val level: IntegrityLevel,
    val message: String,
    val filePath: String? = null
)

data class IntegrityReport(
    val issues: List<IntegrityIssue>,
    val symbolIndex: ProjectSymbolIndex,
    val preferredBuildEngine: String?
) {
    val errors: List<IntegrityIssue> get() = issues.filter { it.level == IntegrityLevel.ERROR }
    val warnings: List<IntegrityIssue> get() = issues.filter { it.level == IntegrityLevel.WARNING }
}

object ProjectIntegrityVerifier {
    fun verify(projectDir: File): IntegrityReport {
        val issues = mutableListOf<IntegrityIssue>()
        val index = ProjectSymbolIndex.build(projectDir)
        val manifest = File(projectDir, "app/src/main/AndroidManifest.xml")
        val appGradle = sequenceOf(
            File(projectDir, "app/build.gradle.kts"),
            File(projectDir, "app/build.gradle")
        ).firstOrNull { it.exists() }
        val settingsFile = sequenceOf(
            File(projectDir, "settings.gradle.kts"),
            File(projectDir, "settings.gradle")
        ).firstOrNull { it.exists() }

        if (!manifest.exists()) {
            issues += IntegrityIssue(IntegrityLevel.ERROR, "AndroidManifest.xml is missing", "app/src/main/AndroidManifest.xml")
        }

        val manifestPackage = index.manifestPackageName
        if (manifestPackage == null || !PACKAGE_NAME_PATTERN.matches(manifestPackage)) {
            issues += IntegrityIssue(IntegrityLevel.ERROR, "Manifest package name is missing or invalid", "app/src/main/AndroidManifest.xml")
        }

        if (appGradle == null) {
            issues += IntegrityIssue(IntegrityLevel.ERROR, "app/build.gradle(.kts) is missing", "app")
        }
        if (settingsFile == null) {
            issues += IntegrityIssue(IntegrityLevel.ERROR, "settings.gradle(.kts) is missing", ".")
        } else if (!settingsFile.readText().contains("include(\":app\")") && !settingsFile.readText().contains("include ':app'")) {
            issues += IntegrityIssue(IntegrityLevel.ERROR, "settings.gradle does not include :app", settingsFile.relativeTo(projectDir).invariantSeparatorsPath)
        }

        val namespace = index.gradleNamespace
        if (namespace == null) {
            issues += IntegrityIssue(IntegrityLevel.WARNING, "Gradle namespace is not declared in app/build.gradle(.kts)", appGradle?.relativeTo(projectDir)?.invariantSeparatorsPath)
        }
        if (manifestPackage != null && namespace != null && manifestPackage != namespace) {
            issues += IntegrityIssue(
                IntegrityLevel.WARNING,
                "Manifest package ($manifestPackage) and Gradle namespace ($namespace) differ. Keep them aligned unless you intentionally split namespace and applicationId.",
                appGradle?.relativeTo(projectDir)?.invariantSeparatorsPath
            )
        }
        if (index.applicationId != null && namespace != null && index.applicationId != namespace) {
            issues += IntegrityIssue(
                IntegrityLevel.WARNING,
                "applicationId (${index.applicationId}) differs from namespace ($namespace). Verify package/resource generation assumptions.",
                appGradle?.relativeTo(projectDir)?.invariantSeparatorsPath
            )
        }

        if (index.invalidResourceNames.isNotEmpty()) {
            issues += index.invalidResourceNames.map {
                IntegrityIssue(IntegrityLevel.ERROR, "Resource file names must be lowercase alphanumeric with underscores only", it)
            }
        }

        index.files.filter { it.path.startsWith("app/src/main/res/layout/") }.forEach { file ->
            if (file.hasConstraintLayout) {
                issues += IntegrityIssue(
                    IntegrityLevel.WARNING,
                    "ConstraintLayout resources require the Gradle build path. The legacy AAPT2 linker cannot resolve layout_constraint attributes without AndroidX resource dependencies.",
                    file.path
                )
            }
            if (file.tags.any { it.startsWith("androidx.") || it.startsWith("com.google.android.material") }) {
                issues += IntegrityIssue(
                    IntegrityLevel.WARNING,
                    "AndroidX/Material XML widgets require the Gradle build path with dependency resources available.",
                    file.path
                )
            }
        }

        if (index.launcherActivities.isEmpty()) {
            issues += IntegrityIssue(IntegrityLevel.ERROR, "No launcher activity is declared in AndroidManifest.xml", "app/src/main/AndroidManifest.xml")
        } else {
            index.launcherActivities.forEach { activityName ->
                val resolved = resolveActivityPath(index, activityName, manifestPackage)
                if (resolved == null) {
                    issues += IntegrityIssue(IntegrityLevel.WARNING, "Launcher activity $activityName could not be matched to a source file", "app/src/main/AndroidManifest.xml")
                }
            }
        }

        val preferredEngine = readPreferredBuildEngine(projectDir)
        if ((preferredEngine == "gradle" || index.hasKotlin || index.hasCompose) && !index.hasGradleWrapper) {
            issues += IntegrityIssue(
                IntegrityLevel.ERROR,
                "Gradle validation was selected but the Gradle wrapper is missing. Restore gradlew and gradle/wrapper before building."
            )
        }
        if (preferredEngine == "legacy" && index.hasKotlin) {
            issues += IntegrityIssue(
                IntegrityLevel.ERROR,
                "This project is marked for legacy validation but contains Kotlin sources. Switch the preferred build engine to gradle or remove Kotlin sources."
            )
        }

        return IntegrityReport(
            issues = issues.distinctBy { listOf(it.level.name, it.message, it.filePath).joinToString("|") },
            symbolIndex = index,
            preferredBuildEngine = preferredEngine
        )
    }

    private fun resolveActivityPath(index: ProjectSymbolIndex, activityName: String, manifestPackage: String?): String? {
        val normalizedClass = when {
            activityName.startsWith(".") && manifestPackage != null -> "$manifestPackage$activityName"
            '.' !in activityName && manifestPackage != null -> "$manifestPackage.$activityName"
            else -> activityName
        }
        val className = normalizedClass.substringAfterLast('.')
        return index.files.firstOrNull { file ->
            file.symbols.contains(className) && (file.packageName == normalizedClass.substringBeforeLast('.', "") || file.path.endsWith("/$className.${file.extension}"))
        }?.path
    }

    private fun readPreferredBuildEngine(projectDir: File): String? {
        val file = File(projectDir, "buildbuddy.properties")
        if (!file.exists()) return null
        return runCatching {
            Properties().apply { file.inputStream().use(::load) }
                .getProperty("preferredBuildEngine")
                ?.trim()
                ?.lowercase()
        }.getOrNull()
    }
}
