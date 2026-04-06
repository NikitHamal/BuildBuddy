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
import com.build.buddyai.core.agent.AgentBackgroundExecutionRegistry
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class AgentForegroundService : Service() {
    private var isForegroundStarted = false

    @Inject
    lateinit var backgroundExecutionRegistry: AgentBackgroundExecutionRegistry

    companion object {
        const val CHANNEL_ID = "agent_channel"
        const val NOTIFICATION_ID = 1101
        const val ACTION_STOP = "com.build.buddyai.STOP_AGENT"
        const val ACTION_UPDATE = "com.build.buddyai.UPDATE_AGENT"
        const val EXTRA_SESSION_ID = "session_id"
        const val EXTRA_PROJECT_NAME = "project_name"
        const val EXTRA_STATUS = "status"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        val projectName = intent?.getStringExtra(EXTRA_PROJECT_NAME) ?: "Project"
        val sessionId = intent?.getStringExtra(EXTRA_SESSION_ID)

        if (action == ACTION_STOP) {
            backgroundExecutionRegistry.cancel(sessionId)
            stopSelf()
            return START_NOT_STICKY
        }

        if (action == ACTION_UPDATE) {
            val status = intent?.getStringExtra(EXTRA_STATUS) ?: "Agent is working..."
            val notification = createNotification(projectName, sessionId, status)
            ensureForeground(notification)
            updateNotification(notification)
            return START_NOT_STICKY
        }

        val notification = createNotification(projectName, sessionId, "Agent is working...")
        ensureForeground(notification)
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun updateNotification(notification: Notification) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, notification)
    }

    private fun ensureForeground(notification: Notification) {
        if (isForegroundStarted) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        isForegroundStarted = true
    }

    private fun createNotification(projectName: String, sessionId: String?, status: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, AgentForegroundService::class.java).apply {
                action = ACTION_STOP
                putExtra(EXTRA_SESSION_ID, sessionId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("BuildBuddy Agent: $projectName")
            .setContentText(status)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setOngoing(true)
            .setContentIntent(openIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopIntent)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Agent execution",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows AI agent progress while tasks run in the background"
        }
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)
    }
}
