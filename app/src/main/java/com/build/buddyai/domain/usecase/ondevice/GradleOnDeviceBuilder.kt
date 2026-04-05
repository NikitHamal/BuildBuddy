package com.build.buddyai.domain.usecase.ondevice

import android.content.Context
import com.build.buddyai.core.common.FileUtils
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.Locale

class GradleOnDeviceBuilder(
    private val context: Context,
    private val projectDir: File,
    private val log: (String) -> Unit
) {
    data class Result(
        val apkFile: File,
        val engineDescription: String,
        val javaBinary: String
    )

    fun build(): Result {
        val gradlew = File(projectDir, "gradlew")
        require(gradlew.exists()) { "Gradle wrapper is missing from the project root" }
        gradlew.setExecutable(true)

        val javaBinary = resolveJavaBinary()
            ?: throw RuntimeException(
                "Kotlin/Gradle validation requires a Java 17 toolchain. BuildBuddy now supports a real Gradle build path, but no java executable was found. Expected one of: project-local .buildbuddy/toolchains/jdk/bin/java, app-local files/toolchains/jdk/bin/java, or a java binary on PATH."
            )

        val outputDir = File(projectDir, ".build/gradle-cache").apply { mkdirs() }
        val gradleUserHome = File(outputDir, "gradle-user-home").apply { mkdirs() }
        val javaHome = File(javaBinary).parentFile?.parentFile?.absolutePath ?: ""

        val shellBinary = if (File("/system/bin/sh").exists()) "/system/bin/sh" else "sh"
        val command = listOf(
            shellBinary,
            gradlew.absolutePath,
            ":app:assembleDebug",
            "--stacktrace",
            "--console=plain",
            "-Dorg.gradle.jvmargs=-Xmx1536m -Dfile.encoding=UTF-8",
            "-Djava.io.tmpdir=${File(outputDir, "tmp").apply { mkdirs() }.absolutePath}"
        )

        log("[GRADLE] Using java binary: $javaBinary")
        log("[GRADLE] Running: ${command.joinToString(" ")}")
        val process = ProcessBuilder(command)
            .directory(projectDir)
            .redirectErrorStream(true)
            .apply {
                environment()["JAVA_HOME"] = javaHome
                environment()["GRADLE_USER_HOME"] = gradleUserHome.absolutePath
                environment()["TERM"] = "dumb"
            }
            .start()

        BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
            var line = reader.readLine()
            while (line != null) {
                log("[GRADLE] $line")
                line = reader.readLine()
            }
        }

        val exitCode = process.waitFor()
        if (exitCode != 0) {
            throw RuntimeException("Gradle build failed with exit code $exitCode")
        }

        val apkFile = File(projectDir, "app/build/outputs/apk/debug")
            .walkTopDown()
            .firstOrNull { it.isFile && it.extension.lowercase(Locale.US) == "apk" }
            ?: throw RuntimeException("Gradle build completed but no debug APK was found")

        val artifactsDir = FileUtils.getArtifactsDir(context)
        val outputApk = File(artifactsDir, apkFile.nameWithoutExtension + "_gradle.apk")
        apkFile.copyTo(outputApk, overwrite = true)
        return Result(
            apkFile = outputApk,
            engineDescription = "Gradle wrapper + AGP",
            javaBinary = javaBinary
        )
    }

    private fun resolveJavaBinary(): String? {
        val candidates = buildList {
            add(File(projectDir, ".buildbuddy/toolchains/jdk/bin/java"))
            add(File(context.filesDir, "toolchains/jdk/bin/java"))
            add(File(context.filesDir, "build_tools/jdk/bin/java"))
            System.getenv("JAVA_HOME")?.let { add(File(it, "bin/java")) }
            System.getenv("PATH")?.split(":")?.forEach { add(File(it, "java")) }
        }
        return candidates.firstOrNull { it.exists() && it.canExecute() }?.absolutePath
    }
}
