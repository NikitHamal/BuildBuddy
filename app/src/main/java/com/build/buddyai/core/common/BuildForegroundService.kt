package com.build.buddyai.core.common

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.build.buddyai.MainActivity
import javax.inject.Inject
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class BuildForegroundService : Service() {

    @Inject
    lateinit var buildCancellationRegistry: BuildCancellationRegistry

    companion object {
        const val CHANNEL_ID = "build_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_STOP = "com.build.buddyai.STOP_BUILD"
        const val EXTRA_BUILD_ID = "build_id"
        const val EXTRA_PROJECT_NAME = "project_name"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            intent.getStringExtra(EXTRA_BUILD_ID)?.let(buildCancellationRegistry::cancelBuild)
            stopSelf()
            return START_NOT_STICKY
        }

        val projectName = intent?.getStringExtra(EXTRA_PROJECT_NAME) ?: "Project"
        val buildId = intent?.getStringExtra(EXTRA_BUILD_ID)
        val notification = createNotification(projectName, buildId, "Building…")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    fun updateNotification(projectName: String, buildId: String?, status: String) {
        val notification = createNotification(projectName, buildId, status)
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, notification)
    }

    private fun createNotification(projectName: String, buildId: String?, status: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, BuildForegroundService::class.java).apply {
                action = ACTION_STOP
                putExtra(EXTRA_BUILD_ID, buildId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("BuildBuddy: $projectName")
            .setContentText(status)
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", stopIntent)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Build Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows build progress for BuildBuddy projects"
        }
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)
    }
}
