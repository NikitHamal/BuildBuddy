package com.build.buddyai.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.build.buddyai.core.designsystem.component.*
import com.build.buddyai.core.designsystem.theme.BuildBuddyTheme
import com.build.buddyai.core.designsystem.theme.NeoVedicSpacing
import com.build.buddyai.core.model.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(NeoVedicSpacing.LG),
            verticalArrangement = Arrangement.spacedBy(NeoVedicSpacing.SM)
        ) {
            // Appearance
            item { SectionHeader(title = "Appearance") }
            item {
                NeoVedicCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(NeoVedicSpacing.LG)) {
                        Text("Theme", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(NeoVedicSpacing.SM))
                        Row(horizontalArrangement = Arrangement.spacedBy(NeoVedicSpacing.SM)) {
                            AppTheme.entries.forEach { theme ->
                                NeoVedicChip(
                                    label = theme.displayName,
                                    selected = state.settings.theme == theme,
                                    onClick = { viewModel.updateTheme(theme) }
                                )
                            }
                        }
                    }
                }
            }

            // Editor
            item { SectionHeader(title = "Editor") }
            item {
                NeoVedicCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(NeoVedicSpacing.LG)) {
                        SettingsRow("Font Size", "${state.settings.editorSettings.fontSize}sp") {
                            Row(horizontalArrangement = Arrangement.spacedBy(NeoVedicSpacing.XS)) {
                                IconButton(onClick = { viewModel.updateEditorFontSize(state.settings.editorSettings.fontSize - 1) }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Default.Remove, "Decrease", modifier = Modifier.size(16.dp))
                                }
                                IconButton(onClick = { viewModel.updateEditorFontSize(state.settings.editorSettings.fontSize + 1) }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Default.Add, "Increase", modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                        NeoVedicDivider()
                        SettingsRow("Tab Width", "${state.settings.editorSettings.tabWidth} spaces") {
                            Row(horizontalArrangement = Arrangement.spacedBy(NeoVedicSpacing.XS)) {
                                IconButton(onClick = { viewModel.updateEditorTabWidth(state.settings.editorSettings.tabWidth - 1) }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Default.Remove, "Decrease", modifier = Modifier.size(16.dp))
                                }
                                IconButton(onClick = { viewModel.updateEditorTabWidth(state.settings.editorSettings.tabWidth + 1) }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Default.Add, "Increase", modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                        NeoVedicDivider()
                        ToggleRow("Soft Wrap", state.settings.editorSettings.softWrap, viewModel::toggleSoftWrap)
                        NeoVedicDivider()
                        ToggleRow("Line Numbers", state.settings.editorSettings.showLineNumbers, viewModel::toggleLineNumbers)
                        NeoVedicDivider()
                        ToggleRow("Auto Save", state.settings.editorSettings.autoSave, viewModel::toggleAutoSave)
                        NeoVedicDivider()
                        ToggleRow("Highlight Current Line", state.settings.editorSettings.highlightCurrentLine, viewModel::toggleHighlightLine)
                    }
                }
            }

            // AI / Models
            item { SectionHeader(title = "AI Providers") }
            items(state.providers) { providerState ->
                ProviderCard(
                    providerState = providerState,
                    isDefault = providerState.provider.id == state.settings.aiSettings.defaultProviderId,
                    onSetDefault = { viewModel.setDefaultProvider(providerState.provider.id) },
                    onEditKey = { viewModel.startEditingApiKey(providerState.provider.id) },
                    onRemoveKey = { viewModel.removeApiKey(providerState.provider.id) },
                    onTestConnection = { viewModel.testConnection(providerState.provider.id) },
                    onSelectModel = { viewModel.setDefaultModel(it) },
                    selectedModelId = state.settings.aiSettings.defaultModelId
                )
            }

            // AI Parameters
            item { SectionHeader(title = "AI Parameters") }
            item {
                NeoVedicCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(NeoVedicSpacing.LG)) {
                        Text("Temperature: ${"%.1f".format(state.settings.aiSettings.parameters.temperature)}", style = MaterialTheme.typography.bodySmall)
                        Slider(
                            value = state.settings.aiSettings.parameters.temperature,
                            onValueChange = { viewModel.updateTemperature(it) },
                            valueRange = 0f..2f,
                            steps = 19
                        )
                        Spacer(Modifier.height(NeoVedicSpacing.SM))
                        Text("Max Tokens: ${state.settings.aiSettings.parameters.maxTokens}", style = MaterialTheme.typography.bodySmall)
                        Slider(
                            value = state.settings.aiSettings.parameters.maxTokens.toFloat(),
                            onValueChange = { viewModel.updateMaxTokens(it.toInt()) },
                            valueRange = 256f..32768f
                        )
                        NeoVedicDivider()
                        ToggleRow("Stream Responses", state.settings.aiSettings.streamResponses, viewModel::toggleStreamResponses)
                    }
                }
            }

            // Build
            item { SectionHeader(title = "Build") }
            item {
                NeoVedicCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(NeoVedicSpacing.LG)) {
                        ToggleRow("Notify on Complete", state.settings.buildSettings.showNotificationOnComplete, viewModel::toggleBuildNotify)
                        NeoVedicDivider()
                        ToggleRow("Clean Before Build", state.settings.buildSettings.cleanBeforeBuild, viewModel::toggleCleanBeforeBuild)
                    }
                }
            }

            // Privacy
            item { SectionHeader(title = "Privacy") }
            item {
                NeoVedicCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(NeoVedicSpacing.LG)) {
                        ToggleRow("Analytics", state.settings.privacySettings.analyticsEnabled, viewModel::toggleAnalytics)
                    }
                }
            }

            // About
            item { SectionHeader(title = "About") }
            item {
                NeoVedicCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(NeoVedicSpacing.LG)) {
                        Text("BuildBuddy", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(NeoVedicSpacing.XS))
                        Text("Version 1.0.0", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(NeoVedicSpacing.XS))
                        Text("On-device Android development with AI-powered vibe coding.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            item { Spacer(Modifier.height(NeoVedicSpacing.XXXL)) }
        }

        // API Key Dialog
        if (state.editingApiKeyProvider != null) {
            val provider = DefaultProviders.ALL.find { it.id == state.editingApiKeyProvider }
            if (provider != null) {
                ApiKeyDialog(
                    providerName = provider.name,
                    apiKey = state.apiKeyInput,
                    onApiKeyChange = { viewModel.updateApiKeyInput(it) },
                    onSave = { viewModel.saveApiKey() },
                    onDismiss = { viewModel.cancelEditingApiKey() }
                )
            }
        }
    }
}

@Composable
private fun SettingsRow(
    label: String,
    value: String,
    trailing: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = NeoVedicSpacing.SM),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing()
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = NeoVedicSpacing.XS),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = { onToggle() })
    }
}

@Composable
private fun ProviderCard(
    providerState: ProviderState,
    isDefault: Boolean,
    onSetDefault: () -> Unit,
    onEditKey: () -> Unit,
    onRemoveKey: () -> Unit,
    onTestConnection: () -> Unit,
    onSelectModel: (String) -> Unit,
    selectedModelId: String?
) {
    NeoVedicCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(NeoVedicSpacing.LG)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(providerState.provider.name, style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(NeoVedicSpacing.XS)) {
                        if (providerState.hasApiKey) {
                            StatusBadge(label = "Connected", color = BuildBuddyTheme.extendedColors.statusSuccess)
                        } else {
                            StatusBadge(label = "No Key", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (isDefault) {
                            StatusBadge(label = "Default", color = MaterialTheme.colorScheme.primary)
                        }
                        providerState.testResult?.let { success ->
                            StatusBadge(
                                label = if (success) "Test OK" else "Test Failed",
                                color = if (success) BuildBuddyTheme.extendedColors.statusSuccess else BuildBuddyTheme.extendedColors.statusError
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(NeoVedicSpacing.SM))
            Row(horizontalArrangement = Arrangement.spacedBy(NeoVedicSpacing.SM)) {
                NeoVedicChip(label = if (providerState.hasApiKey) "Update Key" else "Add Key", onClick = onEditKey)
                if (providerState.hasApiKey) {
                    NeoVedicChip(label = "Test", onClick = onTestConnection)
                    if (!isDefault) {
                        NeoVedicChip(label = "Set Default", onClick = onSetDefault)
                    }
                    NeoVedicChip(label = "Remove", onClick = onRemoveKey)
                }
            }
            if (providerState.hasApiKey && isDefault) {
                Spacer(Modifier.height(NeoVedicSpacing.SM))
                Text("Model", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(NeoVedicSpacing.XS))
                Row(horizontalArrangement = Arrangement.spacedBy(NeoVedicSpacing.XS)) {
                    providerState.provider.models.forEach { model ->
                        NeoVedicChip(
                            label = model.name,
                            selected = model.id == selectedModelId,
                            onClick = { onSelectModel(model.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ApiKeyDialog(
    providerName: String,
    apiKey: String,
    onApiKeyChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    var showKey by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("$providerName API Key") },
        text = {
            Column {
                Text(
                    "Enter your API key for $providerName. Keys are stored securely on-device using encrypted storage.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(NeoVedicSpacing.MD))
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = onApiKeyChange,
                    label = { Text("API Key") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.small,
                    visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showKey = !showKey }) {
                            Icon(
                                if (showKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                "Toggle visibility"
                            )
                        }
                    }
                )
            }
        },
        confirmButton = {
            NeoVedicButton(text = "Save", onClick = onSave)
        },
        dismissButton = {
            NeoVedicTextButton(text = "Cancel", onClick = onDismiss)
        }
    )
}
