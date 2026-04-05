package com.build.buddyai.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SettingsBackupRestore
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WrapText
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.build.buddyai.R
import com.build.buddyai.core.common.AppDataManager
import com.build.buddyai.core.designsystem.component.NvBackButton
import com.build.buddyai.core.designsystem.component.NvCard
import com.build.buddyai.core.designsystem.component.NvTopBar
import com.build.buddyai.core.designsystem.theme.NvSpacing
import com.build.buddyai.core.model.AgentAutonomyMode
import com.build.buddyai.core.model.ThemeMode
import kotlinx.coroutines.flow.collect

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onNavigateToModels: () -> Unit
) {
    val viewModel: SettingsViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is SettingsEvent.LaunchIntent -> context.startActivity(event.intent)
            }
        }
    }

    LaunchedEffect(uiState.lastOperationMessage) {
        uiState.lastOperationMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearOperationMessage()
        }
    }

    Scaffold(
        topBar = {
            NvTopBar(
                title = stringResource(R.string.nav_settings),
                subtitle = "Storage, autonomy, editor, and provider controls",
                navigationIcon = { NvBackButton(onBack) }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            verticalArrangement = Arrangement.spacedBy(NvSpacing.Sm)
        ) {
            item {
                SettingsSectionHeader("Appearance")
                NvCard(modifier = Modifier.fillMaxWidth().padding(horizontal = NvSpacing.Md)) {
                    Column(modifier = Modifier.padding(NvSpacing.Md), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Theme", style = MaterialTheme.typography.titleSmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ThemeMode.entries.forEach { theme ->
                                FilterChip(
                                    selected = uiState.settings.theme == theme,
                                    onClick = { viewModel.updateTheme(theme) },
                                    label = { Text(theme.displayName) }
                                )
                            }
                        }
                    }
                }
            }

            item {
                SettingsSectionHeader("Editor")
                NvCard(modifier = Modifier.fillMaxWidth().padding(horizontal = NvSpacing.Md)) {
                    Column(modifier = Modifier.padding(vertical = NvSpacing.Xs)) {
                        SettingsToggleItem(
                            icon = Icons.Filled.WrapText,
                            title = stringResource(R.string.settings_soft_wrap),
                            checked = uiState.settings.editorSoftWrap,
                            onCheckedChange = viewModel::updateSoftWrap
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = NvSpacing.Md))
                        SettingsToggleItem(
                            icon = Icons.Filled.Save,
                            title = stringResource(R.string.settings_autosave),
                            checked = uiState.settings.editorAutosave,
                            onCheckedChange = viewModel::updateAutosave
                        )
                    }
                }
            }

            item {
                SettingsSectionHeader("AI")
                NvCard(modifier = Modifier.fillMaxWidth().padding(horizontal = NvSpacing.Md)) {
                    Column(modifier = Modifier.padding(NvSpacing.Md), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SettingsItem(
                            icon = Icons.Filled.Key,
                            title = stringResource(R.string.settings_providers),
                            subtitle = "Manage AI model providers, API keys, and model routing",
                            onClick = onNavigateToModels
                        )
                        HorizontalDivider()
                        Text("Autonomous mode", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Control when the agent can apply code changes automatically versus staging them for review.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        AgentAutonomyMode.entries.forEach { mode ->
                            Surface(
                                shape = MaterialTheme.shapes.medium,
                                color = if (uiState.settings.autonomyMode == mode) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.updateAutonomyMode(mode) }
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(mode.displayName, style = MaterialTheme.typography.bodyMedium)
                                    Text(mode.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }

            item {
                SettingsSectionHeader("Build")
                NvCard(modifier = Modifier.fillMaxWidth().padding(horizontal = NvSpacing.Md)) {
                    Column(modifier = Modifier.padding(vertical = NvSpacing.Xs)) {
                        SettingsToggleItem(
                            icon = Icons.Filled.SettingsBackupRestore,
                            title = stringResource(R.string.settings_build_cache),
                            checked = uiState.settings.buildCacheEnabled,
                            onCheckedChange = viewModel::updateBuildCache
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = NvSpacing.Md))
                        SettingsToggleItem(
                            icon = Icons.Filled.Notifications,
                            title = stringResource(R.string.settings_build_notifications),
                            checked = uiState.settings.buildNotifications,
                            onCheckedChange = viewModel::updateBuildNotifications
                        )
                    }
                }
            }

            item {
                SettingsSectionHeader("Storage and data")
                NvCard(modifier = Modifier.fillMaxWidth().padding(horizontal = NvSpacing.Md)) {
                    Column(modifier = Modifier.padding(NvSpacing.Md), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Storage, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.size(8.dp))
                            Text("App data in use", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            Text(uiState.storageUsed, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        }
                        Text(
                            "Select specific data scopes to delete, or wipe all local data. This includes project files, restore points, artifacts, chats, providers, signing material, cache, and settings.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        uiState.storageBuckets.forEach { bucket ->
                            Surface(
                                shape = MaterialTheme.shapes.medium,
                                color = if (bucket.scope in uiState.selectedScopes) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.toggleDataScope(bucket.scope) }
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(bucket.scope.displayName, style = MaterialTheme.typography.bodyMedium)
                                        Text(bucket.formattedSize, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    AssistChip(
                                        onClick = { viewModel.toggleDataScope(bucket.scope) },
                                        label = { Text(if (bucket.scope in uiState.selectedScopes) "Selected" else "Select") }
                                    )
                                }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = viewModel::selectAllDataScopes) { Text("Select all") }
                            TextButton(onClick = viewModel::refreshStorageInfo) { Text("Refresh") }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = viewModel::clearSelectedData, enabled = uiState.selectedScopes.isNotEmpty() && !uiState.isDeletingData) {
                                Text(if (uiState.isDeletingData) "Deleting…" else "Delete selected")
                            }
                            TextButton(onClick = viewModel::clearAllData, enabled = !uiState.isDeletingData) {
                                Text("Delete all app data")
                            }
                        }
                        HorizontalDivider()
                        SettingsItem(
                            icon = Icons.Filled.DeleteSweep,
                            title = stringResource(R.string.settings_clear_cache),
                            subtitle = if (uiState.isClearingCache) "Clearing…" else "Cache: ${uiState.cacheSize}",
                            onClick = { viewModel.clearCache() },
                            enabled = !uiState.isClearingCache
                        )
                    }
                }
            }

            item {
                SettingsSectionHeader("Privacy and diagnostics")
                NvCard(modifier = Modifier.fillMaxWidth().padding(horizontal = NvSpacing.Md)) {
                    Column(modifier = Modifier.padding(NvSpacing.Md), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SettingsItem(
                            icon = Icons.Filled.BugReport,
                            title = stringResource(R.string.settings_export_logs),
                            subtitle = if (uiState.isExportingLogs) "Exporting…" else "Export local logs for troubleshooting",
                            onClick = { viewModel.exportLogs() },
                            enabled = !uiState.isExportingLogs
                        )
                    }
                }
            }

            item {
                SettingsSectionHeader("About")
                NvCard(modifier = Modifier.fillMaxWidth().padding(horizontal = NvSpacing.Md)) {
                    Column(modifier = Modifier.padding(NvSpacing.Md)) {
                        SettingsItem(
                            icon = Icons.Filled.Info,
                            title = stringResource(R.string.settings_version),
                            subtitle = "1.0.0",
                            onClick = {}
                        )
                    }
                }
            }

            item { Spacer(Modifier.size(NvSpacing.Xl)) }
        }
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = NvSpacing.Md, top = NvSpacing.Md, bottom = NvSpacing.Xxs)
    )
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = NvSpacing.Sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(NvSpacing.Md))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Icon(Icons.Filled.ChevronRight, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SettingsToggleItem(
    icon: ImageVector,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = NvSpacing.Md, vertical = NvSpacing.Sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(NvSpacing.Md))
        Text(title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
