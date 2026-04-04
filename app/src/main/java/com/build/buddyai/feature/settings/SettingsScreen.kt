package com.build.buddyai.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.build.buddyai.R
import com.build.buddyai.core.designsystem.component.*
import com.build.buddyai.core.designsystem.theme.*
import com.build.buddyai.core.model.ThemeMode

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onNavigateToModels: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            NvTopBar(
                title = stringResource(R.string.settings_title),
                navigationIcon = { NvBackButton(onBack) }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(vertical = NvSpacing.Xs)
        ) {
            // Appearance
            item { SettingsSectionHeader(stringResource(R.string.settings_appearance)) }
            item {
                SettingsItem(
                    icon = Icons.Filled.Palette,
                    title = stringResource(R.string.settings_theme),
                    subtitle = uiState.settings.theme.displayName,
                    onClick = { viewModel.toggleThemeMenu() }
                )
                if (uiState.showThemeMenu) {
                    Row(
                        modifier = Modifier.padding(horizontal = NvSpacing.Xl),
                        horizontalArrangement = Arrangement.spacedBy(NvSpacing.Xs)
                    ) {
                        ThemeMode.entries.forEach { mode ->
                            NvFilterChip(
                                label = mode.displayName,
                                selected = uiState.settings.theme == mode,
                                onClick = { viewModel.updateTheme(mode) }
                            )
                        }
                    }
                }
            }

            // Editor
            item { SettingsSectionHeader(stringResource(R.string.settings_editor)) }
            item {
                SettingsSliderItem(
                    icon = Icons.Filled.TextFields,
                    title = stringResource(R.string.settings_font_size),
                    value = uiState.settings.editorFontSize.toFloat(),
                    range = 10f..24f,
                    steps = 13,
                    onValueChange = { viewModel.updateFontSize(it.toInt()) },
                    valueLabel = "${uiState.settings.editorFontSize}sp"
                )
            }
            item {
                SettingsSliderItem(
                    icon = Icons.Filled.SpaceBar,
                    title = stringResource(R.string.settings_tab_width),
                    value = uiState.settings.editorTabWidth.toFloat(),
                    range = 2f..8f,
                    steps = 5,
                    onValueChange = { viewModel.updateTabWidth(it.toInt()) },
                    valueLabel = "${uiState.settings.editorTabWidth}"
                )
            }
            item {
                SettingsToggleItem(
                    icon = Icons.Filled.WrapText,
                    title = stringResource(R.string.settings_soft_wrap),
                    checked = uiState.settings.editorSoftWrap,
                    onCheckedChange = viewModel::updateSoftWrap
                )
            }
            item {
                SettingsToggleItem(
                    icon = Icons.Filled.FormatListNumbered,
                    title = stringResource(R.string.settings_line_numbers),
                    checked = uiState.settings.editorLineNumbers,
                    onCheckedChange = viewModel::updateLineNumbers
                )
            }
            item {
                SettingsToggleItem(
                    icon = Icons.Filled.Save,
                    title = stringResource(R.string.settings_autosave),
                    checked = uiState.settings.editorAutosave,
                    onCheckedChange = viewModel::updateAutosave
                )
            }

            // AI
            item { SettingsSectionHeader(stringResource(R.string.settings_ai)) }
            item {
                SettingsItem(
                    icon = Icons.Filled.Psychology,
                    title = stringResource(R.string.settings_providers),
                    subtitle = "Manage AI model providers and API keys",
                    onClick = onNavigateToModels
                )
            }

            // Build
            item { SettingsSectionHeader(stringResource(R.string.settings_build)) }
            item {
                SettingsToggleItem(
                    icon = Icons.Filled.Cached,
                    title = stringResource(R.string.settings_build_cache),
                    checked = uiState.settings.buildCacheEnabled,
                    onCheckedChange = viewModel::updateBuildCache
                )
            }
            item {
                SettingsItem(
                    icon = Icons.Filled.DeleteSweep,
                    title = stringResource(R.string.settings_clear_cache),
                    subtitle = "Free up storage by clearing build cache",
                    onClick = { viewModel.clearCache() }
                )
            }

            // Notifications
            item { SettingsSectionHeader(stringResource(R.string.settings_notifications)) }
            item {
                SettingsToggleItem(
                    icon = Icons.Filled.Notifications,
                    title = stringResource(R.string.settings_build_notifications),
                    checked = uiState.settings.buildNotifications,
                    onCheckedChange = viewModel::updateBuildNotifications
                )
            }

            // Privacy
            item { SettingsSectionHeader(stringResource(R.string.settings_privacy)) }
            item {
                SettingsItem(
                    icon = Icons.Filled.BugReport,
                    title = stringResource(R.string.settings_export_logs),
                    subtitle = "Export debug logs for troubleshooting",
                    onClick = {}
                )
            }
            item {
                SettingsItem(
                    icon = Icons.Filled.DeleteForever,
                    title = stringResource(R.string.settings_clear_data),
                    subtitle = "Remove all app data and settings",
                    onClick = { viewModel.showClearDataDialog() }
                )
            }

            // About
            item { SettingsSectionHeader(stringResource(R.string.settings_about)) }
            item {
                SettingsItem(
                    icon = Icons.Filled.Info,
                    title = stringResource(R.string.settings_version),
                    subtitle = "1.0.0",
                    onClick = {}
                )
            }
            item {
                SettingsItem(
                    icon = Icons.Filled.Description,
                    title = stringResource(R.string.settings_licenses),
                    subtitle = "Open source software used in BuildBuddy",
                    onClick = {}
                )
            }

            item { Spacer(Modifier.height(NvSpacing.Xxl)) }
        }
    }

    if (uiState.showClearDataDialog) {
        NvAlertDialog(
            title = stringResource(R.string.settings_clear_data),
            message = "This will permanently delete all projects, settings, and data. This action cannot be undone.",
            confirmText = "Clear All Data",
            onConfirm = { viewModel.clearAllData() },
            onDismiss = { viewModel.dismissClearDataDialog() },
            isDestructive = true
        )
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
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = NvSpacing.Md, vertical = NvSpacing.Sm),
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
            .padding(horizontal = NvSpacing.Md, vertical = NvSpacing.Xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(NvSpacing.Md))
        Text(title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingsSliderItem(
    icon: ImageVector,
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit,
    valueLabel: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = NvSpacing.Md, vertical = NvSpacing.Xs)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(NvSpacing.Md))
            Text(title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(valueLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            steps = steps,
            modifier = Modifier.padding(start = 38.dp)
        )
    }
}
