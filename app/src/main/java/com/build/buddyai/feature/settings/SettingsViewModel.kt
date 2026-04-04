package com.build.buddyai.feature.settings

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.build.buddyai.core.common.FileUtils
import com.build.buddyai.core.data.datastore.SettingsDataStore
import com.build.buddyai.core.model.AppSettings
import com.build.buddyai.core.model.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

sealed class SettingsEvent {
    data class LaunchIntent(val intent: Intent) : SettingsEvent()
}

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val showThemeMenu: Boolean = false,
    val storageUsed: String = "Calculating…",
    val cacheSize: String = "0 B",
    val isClearingCache: Boolean = false,
    val isExportingLogs: Boolean = false,
    val lastOperationMessage: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<SettingsEvent>()
    val events: SharedFlow<SettingsEvent> = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            settingsDataStore.settings.collect { settings ->
                _uiState.update { it.copy(settings = settings) }
            }
        }
        updateStorageInfo()
    }

    private fun updateStorageInfo() {
        viewModelScope.launch {
            val total = FileUtils.calculateDirectorySize(context.filesDir) + FileUtils.calculateDirectorySize(context.cacheDir)
            val cacheDir = FileUtils.getCacheDir(context)
            val cacheSize = if (cacheDir.exists()) FileUtils.calculateDirectorySize(cacheDir) else 0L
            _uiState.update {
                it.copy(
                    storageUsed = FileUtils.formatFileSize(total),
                    cacheSize = FileUtils.formatFileSize(cacheSize)
                )
            }
        }
    }

    fun toggleThemeMenu() = _uiState.update { it.copy(showThemeMenu = !it.showThemeMenu) }
    fun updateTheme(theme: ThemeMode) { viewModelScope.launch { settingsDataStore.updateTheme(theme) } }
    fun updateFontSize(size: Int) { viewModelScope.launch { settingsDataStore.updateEditorFontSize(size) } }
    fun updateTabWidth(width: Int) { viewModelScope.launch { settingsDataStore.updateEditorTabWidth(width) } }
    fun updateSoftWrap(enabled: Boolean) { viewModelScope.launch { settingsDataStore.updateEditorSoftWrap(enabled) } }
    fun updateLineNumbers(enabled: Boolean) { viewModelScope.launch { settingsDataStore.updateEditorLineNumbers(enabled) } }
    fun updateAutosave(enabled: Boolean) { viewModelScope.launch { settingsDataStore.updateEditorAutosave(enabled) } }
    fun updateBuildCache(enabled: Boolean) { viewModelScope.launch { settingsDataStore.updateBuildCacheEnabled(enabled) } }

    fun updateBuildNotifications(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.updateBuildNotifications(enabled)
            if (enabled) createNotificationChannel()
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            _uiState.update { it.copy(isClearingCache = true) }
            val cacheDir = FileUtils.getCacheDir(context)
            val sizeBefore = if (cacheDir.exists()) FileUtils.calculateDirectorySize(cacheDir) else 0L
            cacheDir.deleteRecursively()
            cacheDir.mkdirs()
            updateStorageInfo()
            _uiState.update {
                it.copy(
                    isClearingCache = false,
                    lastOperationMessage = "Cache cleared (${FileUtils.formatFileSize(sizeBefore)} freed)"
                )
            }
        }
    }

    fun exportLogs() {
        viewModelScope.launch {
            _uiState.update { it.copy(isExportingLogs = true) }
            try {
                val logsDir = File(context.filesDir, "logs")
                val logFiles = if (logsDir.exists()) logsDir.listFiles()?.filter { it.isFile } ?: emptyList() else emptyList()
                val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
                val outputFile = File(exportDir, "buildbuddy_logs_${System.currentTimeMillis()}.txt")
                outputFile.bufferedWriter().use { writer ->
                    writer.appendLine("=== BuildBuddy Log Export ===")
                    writer.appendLine("Date: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}")
                    writer.appendLine()
                    if (logFiles.isEmpty()) {
                        writer.appendLine("No log files found.")
                    } else {
                        logFiles.forEach { logFile ->
                            writer.appendLine("=== ${logFile.name} ===")
                            writer.appendLine(logFile.readText())
                            writer.appendLine()
                        }
                    }
                }

                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", outputFile)
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "BuildBuddy Logs")
                    putExtra(Intent.EXTRA_TEXT, "BuildBuddy log export from ${SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())}")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                val chooser = Intent.createChooser(shareIntent, "Share BuildBuddy logs").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                _events.emit(SettingsEvent.LaunchIntent(chooser))

                _uiState.update {
                    it.copy(isExportingLogs = false, lastOperationMessage = "Logs exported to ${outputFile.name}")
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isExportingLogs = false, lastOperationMessage = "Failed to export logs: ${e.message}")
                }
            }
        }
    }

    fun clearOperationMessage() {
        _uiState.update { it.copy(lastOperationMessage = null) }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "build_notifications",
                "Build Notifications",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications for build completion and failures"
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun showBuildNotification(success: Boolean, buildDuration: String? = null) {
        if (!_uiState.value.settings.buildNotifications) return

        val title = if (success) "Build Successful" else "Build Failed"
        val message = if (success) {
            buildDuration?.let { "APK generated in $it" } ?: "Your APK has been built successfully"
        } else {
            "Check the build logs for details"
        }

        val notification = NotificationCompat.Builder(context, "build_notifications")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }
}
