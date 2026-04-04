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

    LaunchedEffect(uiState.lastOperationMessage) {
        uiState.lastOperationMessage?.let {
            viewModel.clearOperationMessage()
        }
    }

    Scaffold(
        topBar = {
            NvTopBar(
                title = stringResource(R.string.settings_title),
                navigationIcon = { NvBackButton(onBack) }
            )
        },
        snackbarHost = {
            SnackbarHost(
                modifier = Modifier.padding(NvSpacing.Md)
            ) {
                Snackbar(
                    modifier = Modifier.padding(bottom = NvSpacing.Md),
                    containerColor = MaterialTheme.colorScheme.inverseSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface
                ) {
                    Text(
                        text = uiState.lastOperationMessage.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(vertical = NvSpacing.Xs),
            verticalArrangement = Arrangement.spacedBy(NvSpacing.Xxs)
        ) {
            // Appearance Section
            item { SettingsSectionHeader(stringResource(R.string.settings_appearance)) }
            item {
                NvCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = NvSpacing.Md)
                ) {
                    Column(modifier = Modifier.padding(NvSpacing.Md)) {
                        SettingsItem(
                            icon = Icons.Filled.Palette,
                            title = stringResource(R.string.settings_theme),
                            subtitle = uiState.settings.theme.displayName,
                            onClick = { viewModel.toggleThemeMenu() }
                        )
                        if (uiState.showThemeMenu) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = NvSpacing.Xs),
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
                }
            }

            // Editor Section
            item { SettingsSectionHeader(stringResource(R.string.settings_editor)) }
            item {
                NvCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = NvSpacing.Md)
                ) {
                    Column(modifier = Modifier.padding(vertical = NvSpacing.Xs)) {
                        SettingsSliderItem(
                            icon = Icons.Filled.TextFields,
                            title = stringResource(R.string.settings_font_size),
                            value = uiState.settings.editorFontSize.toFloat(),
                            range = 10f..24f,
                            steps = 13,
                            onValueChange = { viewModel.updateFontSize(it.toInt()) },
                            valueLabel = "${uiState.settings.editorFontSize}sp"
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = NvSpacing.Md),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                        SettingsSliderItem(
                            icon = Icons.Filled.SpaceBar,
                            title = stringResource(R.string.settings_tab_width),
                            value = uiState.settings.editorTabWidth.toFloat(),
                            range = 2f..8f,
                            steps = 5,
                            onValueChange = { viewModel.updateTabWidth(it.toInt()) },
                            valueLabel = "${uiState.settings.editorTabWidth}"
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = NvSpacing.Md),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                        SettingsToggleItem(
                            icon = Icons.Filled.WrapText,
                            title = stringResource(R.string.settings_soft_wrap),
                            checked = uiState.settings.editorSoftWrap,
                            onCheckedChange = viewModel::updateSoftWrap
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = NvSpacing.Md),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                        SettingsToggleItem(
                            icon = Icons.Filled.FormatListNumbered,
                            title = stringResource(R.string.settings_line_numbers),
                            checked = uiState.settings.editorLineNumbers,
                            onCheckedChange = viewModel::updateLineNumbers
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = NvSpacing.Md),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                        SettingsToggleItem(
                            icon = Icons.Filled.Save,
                            title = stringResource(R.string.settings_autosave),
                            checked = uiState.settings.editorAutosave,
                            onCheckedChange = viewModel::updateAutosave
                        )
                    }
                }
            }

            // AI Section
            item { SettingsSectionHeader(stringResource(R.string.settings_ai)) }
            item {
                NvCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = NvSpacing.Md)
                ) {
                    Column(modifier = Modifier.padding(NvSpacing.Md)) {
                        SettingsItem(
                            icon = Icons.Filled.Psychology,
                            title = stringResource(R.string.settings_providers),
                            subtitle = "Manage AI model providers and API keys",
                            onClick = onNavigateToModels
                        )
                    }
                }
            }

            // Build Section
            item { SettingsSectionHeader(stringResource(R.string.settings_build)) }
            item {
                NvCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = NvSpacing.Md)
                ) {
                    Column(modifier = Modifier.padding(vertical = NvSpacing.Xs)) {
                        SettingsToggleItem(
                            icon = Icons.Filled.Cached,
                            title = stringResource(R.string.settings_build_cache),
                            checked = uiState.settings.buildCacheEnabled,
                            onCheckedChange = viewModel::updateBuildCache
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = NvSpacing.Md),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
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

            // Notifications Section
            item { SettingsSectionHeader(stringResource(R.string.settings_notifications)) }
            item {
                NvCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = NvSpacing.Md)
                ) {
                    Column(modifier = Modifier.padding(NvSpacing.Md)) {
                        SettingsToggleItem(
                            icon = Icons.Filled.Notifications,
                            title = stringResource(R.string.settings_build_notifications),
                            checked = uiState.settings.buildNotifications,
                            onCheckedChange = viewModel::updateBuildNotifications
                        )
                    }
                }
            }

            // Privacy Section
            item { SettingsSectionHeader(stringResource(R.string.settings_privacy)) }
            item {
                NvCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = NvSpacing.Md)
                ) {
                    Column(modifier = Modifier.padding(NvSpacing.Md)) {
                        SettingsItem(
                            icon = Icons.Filled.BugReport,
                            title = stringResource(R.string.settings_export_logs),
                            subtitle = if (uiState.isExportingLogs) "Exporting…" else "Export debug logs for troubleshooting",
                            onClick = { viewModel.exportLogs() },
                            enabled = !uiState.isExportingLogs
                        )
                    }
                }
            }

            // About Section
            item { SettingsSectionHeader(stringResource(R.string.settings_about)) }
            item {
                NvCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = NvSpacing.Md)
                ) {
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

            // Storage Info
            item {
                NvCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = NvSpacing.Md, vertical = NvSpacing.Md)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(NvSpacing.Md),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.Storage,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(NvSpacing.Sm))
                        Text(
                            text = stringResource(R.string.settings_storage_used),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = uiState.storageUsed,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(NvSpacing.Xxl)) }
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
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = NvSpacing.Md, vertical = NvSpacing.Sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(NvSpacing.Md))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyMedium)
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (enabled) {
                Icon(
                    Icons.Filled.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
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
            .padding(horizontal = NvSpacing.Md, vertical = NvSpacing.Sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(NvSpacing.Md))
        Text(
            title,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
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
            .padding(horizontal = NvSpacing.Md, vertical = NvSpacing.Sm)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(NvSpacing.Md))
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            Text(
                valueLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            steps = steps,
            modifier = Modifier.padding(start = 38.dp, top = NvSpacing.Xxs)
        )
    }
}
