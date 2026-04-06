package com.build.buddyai.domain.usecase.ondevice

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.android.apksig.ApkVerifier
import java.io.File
import java.util.zip.ZipFile

object BuiltApkInspector {

    fun verifyParseable(
        context: Context,
        apkFile: File,
        expectedPackageName: String,
        log: (String) -> Unit = {}
    ) {
        require(apkFile.exists() && apkFile.isFile) { "APK file does not exist: ${apkFile.absolutePath}" }

        ZipFile(apkFile).use { zip ->
            val entryNames = zip.entries().asSequence().map { it.name }.toSet()
            val required = setOf("AndroidManifest.xml", "resources.arsc", "classes.dex")
            val missing = required.filterNot { it in entryNames }
            if (missing.isNotEmpty()) {
                throw RuntimeException("Packaged APK is missing required entries: ${missing.joinToString()}")
            }
            log("[APK] Verification zip check passed: ${entryNames.size} entries present")
        }

        val verifyResult = ApkVerifier.Builder(apkFile).build().verify()
        if (!verifyResult.isVerified) {
            val issues = buildString {
                verifyResult.errors.take(10).forEach { appendLine(it.toString()) }
                verifyResult.warnings.take(10).forEach { appendLine(it.toString()) }
            }.trim()
            throw RuntimeException(
                "APK signature verification failed" + if (issues.isNotBlank()) ":\n$issues" else "."
            )
        }
        log("[APK] Signature verification passed")

        val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageArchiveInfo(
                apkFile.absolutePath,
                PackageManager.PackageInfoFlags.of(
                    (PackageManager.GET_ACTIVITIES or PackageManager.GET_SIGNING_CERTIFICATES).toLong()
                )
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageArchiveInfo(
                apkFile.absolutePath,
                PackageManager.GET_ACTIVITIES or PackageManager.GET_SIGNING_CERTIFICATES
            )
        }

        val parsedPackageName = packageInfo?.packageName
            ?: throw RuntimeException(
                "Android could not parse the generated APK. This usually means the APK structure, manifest, or signing metadata is invalid."
            )

        if (parsedPackageName != expectedPackageName) {
            throw RuntimeException(
                "Generated APK package mismatch. Expected $expectedPackageName but parser resolved $parsedPackageName"
            )
        }

        log("[APK] Package parser validation passed: $parsedPackageName")
    }
}
