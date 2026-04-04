package com.build.buddyai.core.install

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageInstaller
import android.net.Uri
import androidx.core.content.FileProvider
import com.build.buddyai.core.model.Artifact
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ApkInstaller @Inject constructor(
    @ApplicationContext
    private val context: Context,
) {
    fun packageInstallerIntentSender(): IntentSender {
        val intent = Intent("com.build.buddyai.INSTALL_COMMIT").setPackage(context.packageName)
        return PendingIntent.getBroadcast(
            context,
            41,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        ).intentSender
    }

    fun install(artifact: Artifact) {
        val packageInstaller = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        val sessionId = packageInstaller.createSession(params)
        val session = packageInstaller.openSession(sessionId)
        val apkFile = File(artifact.filePath)
        session.openWrite(apkFile.name, 0, apkFile.length()).use { out ->
            apkFile.inputStream().use { input -> input.copyTo(out) }
            session.fsync(out)
        }
        session.commit(packageInstallerIntentSender())
        session.close()
    }

    fun shareUri(filePath: String): Uri =
        FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            File(filePath),
        )
}
