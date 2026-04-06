package com.build.buddyai

import android.app.Activity
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.build.buddyai.core.designsystem.theme.*
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.*
import kotlin.system.exitProcess

object CrashHandler {
    var pendingCrash: CrashReport? = null

    fun init(context: Context) {
        val appContext = context.applicationContext
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val crashReport = CrashReport(
                timestamp = System.currentTimeMillis(),
                thread = thread.name,
                exception = throwable.javaClass.name,
                message = throwable.message ?: "No message",
                stackTrace = getStackTrace(throwable),
                deviceInfo = getDeviceInfo(appContext),
                appVersion = getAppVersion(appContext)
            )
            pendingCrash = crashReport

            val intent = Intent(appContext, CrashActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra("CRASH_REPORT", crashReport.toJson())
            }
            appContext.startActivity(intent)

            val pid = android.os.Process.myPid()
            android.os.Process.killProcess(pid)
            exitProcess(2)
        }
    }

    private fun getStackTrace(throwable: Throwable): String {
        val sw = StringWriter()
        val pw = PrintWriter(sw)
        throwable.printStackTrace(pw)
        return sw.toString()
    }

    private fun getDeviceInfo(context: Context): String {
        return """
            Device: ${Build.MANUFACTURER} ${Build.MODEL}
            Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})
            Brand: ${Build.BRAND}
            Hardware: ${Build.HARDWARE}
        """.trimIndent()
    }

    private fun getAppVersion(context: Context): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            "${packageInfo.versionName} (${packageInfo.versionCode})"
        } catch (e: Exception) {
            "Unknown"
        }
    }
}

data class CrashReport(
    val timestamp: Long,
    val thread: String,
    val exception: String,
    val message: String,
    val stackTrace: String,
    val deviceInfo: String,
    val appVersion: String
) {
    fun formatFullReport(): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return """
# BuildBuddy Crash Report
**Date:** ${dateFormat.format(Date(timestamp))}
**App Version:** $appVersion
**Device:** $deviceInfo

## Exception
```
$exception: $message
```

## Thread
`$thread`

## Stack Trace
```
$stackTrace
```
        """.trimIndent()
    }

    fun toJson(): String {
        return "$timestamp|$thread|$exception|$message|${stackTrace.replace("|", "\\|")}|$deviceInfo|$appVersion"
    }

    companion object {
        fun fromJson(json: String): CrashReport {
            val parts = json.split("|", limit = 7)
            return CrashReport(
                timestamp = parts[0].toLong(),
                thread = parts[1],
                exception = parts[2],
                message = parts[3],
                stackTrace = parts[4].replace("\\|", "|"),
                deviceInfo = parts[5],
                appVersion = parts[6]
            )
        }
    }
}

class CrashActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val crashJson = intent.getStringExtra("CRASH_REPORT")
        val crashReport = if (crashJson != null) CrashReport.fromJson(crashJson) else null

        setContent {
            BuildBuddyTheme {
                CrashScreen(crashReport = crashReport)
            }
        }
    }
}

@Composable
private fun CrashScreen(crashReport: CrashReport?) {
    val context = LocalContext.current
    var showDetails by remember { mutableStateOf(false) }

    val fullReport = crashReport?.formatFullReport() ?: "No crash report available"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f),
            contentPadding = PaddingValues(NvSpacing.Md),
            verticalArrangement = Arrangement.spacedBy(NvSpacing.Sm)
        ) {
            // Error summary
            item {
                SelectionContainer {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(NvSpacing.Md)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                Spacer(Modifier.width(NvSpacing.Xs))
                                Text("Error", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.error)
                            }
                            Spacer(Modifier.height(NvSpacing.Xs))
                            Text(
                                "${crashReport?.exception ?: "Unknown"}: ${crashReport?.message ?: "No details"}",
                                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }

            // Device info
            item {
                InfoCard("Device", crashReport?.deviceInfo ?: "Unknown")
            }

            // App version
            item {
                InfoCard("App Version", crashReport?.appVersion ?: "Unknown")
            }

            // Stack trace (expandable)
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = NvSpacing.Xs),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Stack Trace", style = MaterialTheme.typography.titleSmall)
                    IconButton(onClick = { showDetails = !showDetails }) {
                        Icon(if (showDetails) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null)
                    }
                }
            }

            if (showDetails && crashReport != null) {
                item {
                    SelectionContainer {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = NvShapes.small,
                            color = MaterialTheme.colorScheme.surfaceContainerHighest
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(NvSpacing.Md)
                                    .horizontalScroll(rememberScrollState())
                            ) {
                                crashReport.stackTrace.split("\n").forEach { line ->
                                    if (line.isNotBlank()) {
                                        Text(
                                            text = line,
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 11.sp,
                                                lineHeight = 16.sp
                                            ),
                                            color = when {
                                                line.contains("Caused by:") || line.contains("Exception") -> MaterialTheme.colorScheme.error
                                                line.contains("at com.build.buddyai") -> MaterialTheme.colorScheme.primary
                                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Action buttons
        Surface(
            modifier = Modifier.fillMaxWidth(),
            tonalElevation = NvElevation.Sm
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(NvSpacing.Md),
                horizontalArrangement = Arrangement.spacedBy(NvSpacing.Md)
            ) {
                // Copy button
                OutlinedButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Crash Report", fullReport))
                        Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Copy")
                }

                // Restart button
                Button(
                    onClick = {
                        val intent = Intent(context, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        }
                        context.startActivity(intent)
                        exitProcess(0)
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Restart")
                }
            }
        }
    }
}


@Composable
private fun InfoCard(title: String, value: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(NvSpacing.Md)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(NvSpacing.Xxs))
            Text(value, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
        }
    }
}
