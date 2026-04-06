package com.build.buddyai.core.common

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BuildArtifactInstaller @Inject constructor(
    @ApplicationContext private val appContext: Context
) {
    fun install(context: Context = appContext, apkFile: File): InstallResult {
        if (!apkFile.exists()) return InstallResult.Error("APK file not found")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            return InstallResult.PermissionRequired("Allow BuildBuddy to install apps, then tap install again.")
        }

        return try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
            val intent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
                data = uri
                putExtra(Intent.EXTRA_RETURN_RESULT, false)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                clipData = ClipData.newUri(context.contentResolver, apkFile.name, uri)
            }
            context.startActivity(intent)
            InstallResult.Started
        } catch (e: Exception) {
            InstallResult.Error(e.message ?: "Unable to open the installer")
        }
    }

    fun share(context: Context = appContext, apkFile: File): Result<Unit> = runCatching {
        require(apkFile.exists()) { "APK file not found" }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/vnd.android.package-archive"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = ClipData.newUri(context.contentResolver, apkFile.name, uri)
        }
        context.startActivity(Intent.createChooser(intent, "Share APK").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    sealed interface InstallResult {
        data object Started : InstallResult
        data class PermissionRequired(val message: String) : InstallResult
        data class Error(val message: String) : InstallResult
    }
}
