package com.build.buddyai.core.agent

import com.build.buddyai.core.model.Project
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProjectIntegrityChecker @Inject constructor() {

    data class IntegrityReport(
        val errors: List<String> = emptyList(),
        val warnings: List<String> = emptyList()
    ) {
        val isValid: Boolean get() = errors.isEmpty()
        fun summary(): String = buildString {
            if (errors.isNotEmpty()) {
                appendLine("Integrity errors:")
                errors.forEach { appendLine("- $it") }
            }
            if (warnings.isNotEmpty()) {
                appendLine("Integrity warnings:")
                warnings.forEach { appendLine("- $it") }
            }
        }.trim()
    }

    fun validate(project: Project, projectDir: File): IntegrityReport {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        val manifest = File(projectDir, "app/src/main/AndroidManifest.xml")
        if (!manifest.exists()) {
            errors += "Missing manifest: app/src/main/AndroidManifest.xml"
        } else {
            val manifestText = manifest.readText()
            val manifestPackage = Regex("""package\s*=\s*\"([^\"]+)\"""")
                .find(manifestText)?.groupValues?.get(1)
            if (manifestPackage.isNullOrBlank()) {
                errors += "Manifest package is missing."
            } else if (manifestPackage != project.packageName) {
                warnings += "Manifest package ($manifestPackage) differs from project package (${project.packageName})."
            }
            if ("@style/Theme.App" in manifestText) {
                val themes = File(projectDir, "app/src/main/res/values/themes.xml")
                if (!themes.exists()) errors += "Manifest references @style/Theme.App but res/values/themes.xml is missing."
            }
        }

        val stringsFile = File(projectDir, "app/src/main/res/values/strings.xml")
        if (!stringsFile.exists()) warnings += "res/values/strings.xml is missing."

        val appBuild = sequenceOf(
            File(projectDir, "app/build.gradle.kts"),
            File(projectDir, "app/build.gradle")
        ).firstOrNull { it.exists() }
        val buildText = appBuild?.readText().orEmpty()
        val supportsConstraintLayout = buildText.contains("constraintlayout", ignoreCase = true)

        File(projectDir, "app/src/main/res").takeIf { it.exists() }?.walkTopDown()
            ?.filter { it.isFile && it.extension == "xml" }
            ?.forEach { xmlFile ->
                val relative = xmlFile.relativeTo(projectDir).invariantSeparatorsPath
                val xmlText = xmlFile.readText()
                if ("layout_constraint" in xmlText && !supportsConstraintLayout) {
                    errors += "$relative uses ConstraintLayout attributes but the template/build configuration does not guarantee ConstraintLayout resources for the on-device linker."
                }
                if (xmlText.contains("@string/app_name") && !stringsFile.exists()) {
                    errors += "$relative references @string/app_name but strings.xml is missing."
                }
            }

        val javaRoots = listOf(File(projectDir, "app/src/main/java"), File(projectDir, "app/src/main/kotlin"))
            .filter { it.exists() }
        javaRoots.forEach { root ->
            root.walkTopDown().filter { it.isFile && it.extension in setOf("java", "kt") }.forEach { src ->
                val text = src.readText()
                val declaredPackage = Regex("""package\s+([A-Za-z0-9_.]+)""").find(text)?.groupValues?.get(1)
                if (!declaredPackage.isNullOrBlank()) {
                    val expected = src.parentFile?.relativeTo(root)?.invariantSeparatorsPath?.replace('/', '.')?.trim('.')
                    if (!expected.isNullOrBlank() && declaredPackage != expected) {
                        warnings += "${src.relativeTo(projectDir).invariantSeparatorsPath} declares package $declaredPackage but lives under $expected."
                    }
                }
            }
        }

        return IntegrityReport(errors = errors.distinct(), warnings = warnings.distinct())
    }
}
