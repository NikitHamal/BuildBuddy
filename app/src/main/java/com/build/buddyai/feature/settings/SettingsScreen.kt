package com.build.buddyai.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.build.buddyai.R
import com.build.buddyai.core.designsystem.BuildBuddyCard
import com.build.buddyai.core.designsystem.NeoVedicTheme
import com.build.buddyai.core.model.ThemeMode

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var toolchainRoot by remember(state.toolchainRootOverride) { mutableStateOf(state.toolchainRootOverride.orEmpty()) }
    val spacing = NeoVedicTheme.spacing

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text(stringResource(R.string.action_back)) }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            item {
                BuildBuddyCard {
                    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        Text(stringResource(R.string.settings_appearance), style = MaterialTheme.typography.titleLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.sm), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                            ThemeMode.entries.forEach { mode ->
                                FilterChip(
                                    selected = state.themeMode == mode,
                                    onClick = { viewModel.setThemeMode(mode) },
                                    label = { Text(mode.name) },
                                )
                            }
                        }
                    }
                }
            }
            item {
                BuildBuddyCard {
                    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        Text(stringResource(R.string.settings_editor), style = MaterialTheme.typography.titleLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                            OutlinedTextField(
                                modifier = Modifier.weight(1f),
                                value = state.editorFontScaleSp.toString(),
                                onValueChange = { value ->
                                    value.toIntOrNull()?.let {
                                        viewModel.updateEditorSettings(it, state.tabWidth, state.softWrap, state.showLineNumbers, state.autosave)
                                    }
                                },
                                label = { Text("Font size") },
                            )
                            OutlinedTextField(
                                modifier = Modifier.weight(1f),
                                value = state.tabWidth.toString(),
                                onValueChange = { value ->
                                    value.toIntOrNull()?.let {
                                        viewModel.updateEditorSettings(state.editorFontScaleSp, it, state.softWrap, state.showLineNumbers, state.autosave)
                                    }
                                },
                                label = { Text("Tab width") },
                            )
                        }
                        ToggleRow(label = "Soft wrap", checked = state.softWrap) {
                            viewModel.updateEditorSettings(state.editorFontScaleSp, state.tabWidth, it, state.showLineNumbers, state.autosave)
                        }
                        ToggleRow(label = "Line numbers", checked = state.showLineNumbers) {
                            viewModel.updateEditorSettings(state.editorFontScaleSp, state.tabWidth, state.softWrap, it, state.autosave)
                        }
                        ToggleRow(label = "Autosave", checked = state.autosave) {
                            viewModel.updateEditorSettings(state.editorFontScaleSp, state.tabWidth, state.softWrap, state.showLineNumbers, it)
                        }
                    }
                }
            }
            item {
                BuildBuddyCard {
                    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        Text(stringResource(R.string.settings_build), style = MaterialTheme.typography.titleLarge)
                        OutlinedTextField(
                            modifier = Modifier.fillMaxWidth(),
                            value = toolchainRoot,
                            onValueChange = {
                                toolchainRoot = it
                                viewModel.setToolchainRoot(it)
                            },
                            label = { Text("Toolchain root") },
                        )
                        ToggleRow(label = "Build notifications", checked = state.notificationsEnabled) {
                            viewModel.setNotificationsEnabled(it)
                        }
                    }
                }
            }
            item {
                BuildBuddyCard {
                    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        Text(stringResource(R.string.settings_privacy), style = MaterialTheme.typography.titleLarge)
                        Text(
                            "Secrets stay local in encrypted storage. BuildBuddy does not silently install apps or send hidden telemetry.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item {
                BuildBuddyCard {
                    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        Text(stringResource(R.string.settings_about), style = MaterialTheme.typography.titleLarge)
                        Text("BuildBuddy 1.0.0", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            stringResource(R.string.settings_open_source_licenses),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
