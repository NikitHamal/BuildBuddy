package com.build.buddyai.core.common

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExecutionNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val CHANNEL_ID = "execution_updates"
    }

    fun notifyBuildOutcome(projectName: String, success: Boolean, detail: String) {
        ensureChannel()
        val title = if (success) "Build complete" else "Build failed"
        notify(
            id = (System.currentTimeMillis() % Int.MAX_VALUE).toInt(),
            title = "$title - $projectName",
            detail = detail,
            icon = if (success) android.R.drawable.stat_sys_download_done else android.R.drawable.stat_notify_error
        )
    }

    fun notifyAgentOutcome(projectName: String, success: Boolean, detail: String) {
        ensureChannel()
        val title = if (success) "Agent task complete" else "Agent task needs attention"
        notify(
            id = (System.currentTimeMillis() % Int.MAX_VALUE).toInt(),
            title = "$title - $projectName",
            detail = detail,
            icon = if (success) android.R.drawable.stat_notify_chat else android.R.drawable.stat_notify_error
        )
    }

    private fun notify(id: Int, title: String, detail: String, icon: Int) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(icon)
            .setContentTitle(title)
            .setContentText(detail)
            .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        nm.notify(id, notification)
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Execution updates",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Build and AI agent completion notifications"
            }
            nm.createNotificationChannel(channel)
        }
    }
}
