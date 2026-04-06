package com.build.buddyai.feature.models

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.build.buddyai.R
import com.build.buddyai.core.designsystem.component.*
import com.build.buddyai.core.designsystem.theme.*
import com.build.buddyai.core.model.AiModel
import com.build.buddyai.core.model.AiProvider
import com.build.buddyai.core.model.ProviderType
import java.util.concurrent.TimeUnit

@Composable
fun ModelsScreen(
    onBack: () -> Unit
) {
    val viewModel: ModelsViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            NvTopBar(
                title = stringResource(R.string.models_title),
                navigationIcon = { NvBackButton(onBack) }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(NvSpacing.Md),
            verticalArrangement = Arrangement.spacedBy(NvSpacing.Sm)
        ) {
            items(uiState.providers) { provider ->
                ProviderCard(
                    provider = provider,
                    apiKeyInput = uiState.apiKeyInputs[provider.type] ?: "",
                    isExpanded = uiState.expandedProvider == provider.type,
                    isTesting = uiState.testingProvider == provider.type,
                    testResult = uiState.testResults[provider.type],
                    isFetchingModels = uiState.fetchingModels.contains(provider.type),
                    modelFetchError = uiState.modelFetchErrors[provider.type],
                    onToggleExpand = { viewModel.toggleProviderExpand(provider.type) },
                    onApiKeyChange = { viewModel.updateApiKeyInput(provider.type, it) },
                    onSave = { viewModel.saveProvider(provider.type) },
                    onTest = { viewModel.testConnection(provider.type) },
                    onRefreshModels = { viewModel.refreshModels(provider.type) },
                    onSetDefault = { viewModel.setDefaultProvider(provider.type.name) },
                    onRemove = { viewModel.removeProvider(provider.type.name) },
                    onSelectModel = { viewModel.selectModel(provider.type.name, it) },
                    onUpdateTemperature = { viewModel.updateTemperature(provider.type.name, it) },
                    onUpdateMaxTokens = { viewModel.updateMaxTokens(provider.type.name, it) },
                    onUpdateTopP = { viewModel.updateTopP(provider.type.name, it) }
                )
            }
        }
    }
}

@Composable
private fun ProviderCard(
    provider: AiProvider,
    apiKeyInput: String,
    isExpanded: Boolean,
    isTesting: Boolean,
    testResult: String?,
    isFetchingModels: Boolean,
    modelFetchError: String?,
    onToggleExpand: () -> Unit,
    onApiKeyChange: (String) -> Unit,
    onSave: () -> Unit,
    onTest: () -> Unit,
    onRefreshModels: (ProviderType) -> Unit,
    onSetDefault: () -> Unit,
    onRemove: () -> Unit,
    onSelectModel: (String) -> Unit,
    onUpdateTemperature: (Float) -> Unit,
    onUpdateMaxTokens: (Int) -> Unit,
    onUpdateTopP: (Float) -> Unit
) {
    var showPassword by remember { mutableStateOf(false) }
    var showRemoveDialog by remember { mutableStateOf(false) }
    var showAdvanced by remember { mutableStateOf(false) }

    NvCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onToggleExpand
    ) {
        Column(modifier = Modifier.padding(NvSpacing.Md)) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    Modifier.size(36.dp),
                    shape = NvShapes.small,
                    color = if (provider.isConfigured) BuildBuddyThemeExtended.colors.successContainer
                    else MaterialTheme.colorScheme.surfaceContainerHighest
                ) {
                    Icon(
                        if (provider.isConfigured) Icons.Filled.CheckCircle else Icons.Filled.Key,
                        contentDescription = null,
                        modifier = Modifier.padding(NvSpacing.Xs),
                        tint = if (provider.isConfigured) BuildBuddyThemeExtended.colors.success
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(NvSpacing.Sm))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(provider.name, style = MaterialTheme.typography.titleSmall)
                        if (provider.isDefault) {
                            Spacer(Modifier.width(NvSpacing.Xs))
                            NvStatusChip(label = "Default", containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.primary)
                        }
                    }
                    if (provider.isConfigured && provider.selectedModelId != null) {
                        val modelName = provider.models.find { it.id == provider.selectedModelId }?.name ?: provider.selectedModelId
                        Text(modelName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Icon(
                    if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null
                )
            }

            // Expanded content
            if (isExpanded) {
                Spacer(Modifier.height(NvSpacing.Sm))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = NvBorder.Hairline)
                Spacer(Modifier.height(NvSpacing.Sm))

                // API Key input
                NvTextField(
                    value = apiKeyInput,
                    onValueChange = onApiKeyChange,
                    label = stringResource(R.string.models_api_key),
                    placeholder = stringResource(R.string.models_api_key_hint),
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = null
                            )
                        }
                    },
                    modifier = Modifier.imePadding()
                )

                Spacer(Modifier.height(NvSpacing.Xs))
                Row(horizontalArrangement = Arrangement.spacedBy(NvSpacing.Xs)) {
                    NvFilledButton(
                        text = stringResource(R.string.action_save),
                        onClick = onSave,
                        enabled = apiKeyInput.isNotBlank()
                    )
                    NvOutlinedButton(
                        text = if (isTesting) stringResource(R.string.models_testing)
                        else stringResource(R.string.models_test_connection),
                        onClick = onTest,
                        enabled = !isTesting && (apiKeyInput.isNotBlank() || provider.isConfigured)
                    )
                }

                testResult?.let { result ->
                    Spacer(Modifier.height(NvSpacing.Xs))
                    val isSuccess = result == stringResource(R.string.models_test_success)
                    Text(
                        result,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isSuccess) BuildBuddyThemeExtended.colors.success else MaterialTheme.colorScheme.error
                    )
                }

                // Model selection
                if (provider.isConfigured) {
                    Spacer(Modifier.height(NvSpacing.Sm))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(stringResource(R.string.models_select_model), style = MaterialTheme.typography.labelLarge)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (provider.cachedModels.isNotEmpty() && provider.lastModelFetchTime != null) {
                                val timeAgo = getTimeAgo(provider.lastModelFetchTime!!)
                                Text(timeAgo, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.width(NvSpacing.Xxs))
                            }
                            IconButton(onClick = { onRefreshModels(provider.type) }, enabled = !isFetchingModels) {
                                Icon(
                                    if (isFetchingModels) Icons.Filled.HourglassTop else Icons.Filled.Refresh,
                                    contentDescription = "Refresh models",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(NvSpacing.Xxs))

                    if (isFetchingModels) {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = NvSpacing.Md),
                            contentAlignment = androidx.compose.ui.Alignment.Center
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        }
                    } else if (modelFetchError != null) {
                        Text(
                            "Failed to load models: $modelFetchError",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else if (provider.cachedModels.isEmpty()) {
                        Text(
                            "No models found. Tap refresh to fetch.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        provider.cachedModels.forEach { model ->
                            ModelSelectionItem(
                                model = model,
                                isSelected = model.id == provider.selectedModelId,
                                onSelect = { onSelectModel(model.id) }
                            )
                        }
                    }

                    // Advanced parameters
                    Spacer(Modifier.height(NvSpacing.Sm))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(R.string.models_advanced), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                        IconButton(onClick = { showAdvanced = !showAdvanced }) {
                            Icon(if (showAdvanced) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null)
                        }
                    }

                    if (showAdvanced) {
                        Column(verticalArrangement = Arrangement.spacedBy(NvSpacing.Xs)) {
                            // Temperature
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.models_temperature), style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(100.dp))
                                Slider(
                                    value = provider.parameters.temperature,
                                    onValueChange = onUpdateTemperature,
                                    valueRange = 0f..2f,
                                    modifier = Modifier.weight(1f)
                                )
                                Text("%.1f".format(provider.parameters.temperature), style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(32.dp))
                            }
                            // Max tokens
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.models_max_tokens), style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(100.dp))
                                Slider(
                                    value = provider.parameters.maxTokens.toFloat(),
                                    onValueChange = { onUpdateMaxTokens(it.toInt()) },
                                    valueRange = 256f..16384f,
                                    modifier = Modifier.weight(1f)
                                )
                                Text("${provider.parameters.maxTokens}", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(48.dp))
                            }
                            // Top P
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.models_top_p), style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(100.dp))
                                Slider(
                                    value = provider.parameters.topP,
                                    onValueChange = onUpdateTopP,
                                    valueRange = 0f..1f,
                                    modifier = Modifier.weight(1f)
                                )
                                Text("%.2f".format(provider.parameters.topP), style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(32.dp))
                            }
                        }
                    }

                    // Actions
                    Spacer(Modifier.height(NvSpacing.Sm))
                    Row(horizontalArrangement = Arrangement.spacedBy(NvSpacing.Xs)) {
                        if (!provider.isDefault) {
                            NvTonalButton(text = stringResource(R.string.models_set_default), onClick = onSetDefault)
                        }
                        NvTextButton(
                            text = stringResource(R.string.models_remove_provider),
                            onClick = { showRemoveDialog = true }
                        )
                    }
                }
            }
        }
    }

    if (showRemoveDialog) {
        NvAlertDialog(
            title = stringResource(R.string.models_remove_provider),
            message = stringResource(R.string.models_remove_confirm),
            confirmText = "Remove",
            onConfirm = { onRemove(); showRemoveDialog = false },
            onDismiss = { showRemoveDialog = false },
            isDestructive = true
        )
    }
}

@Composable
private fun ModelSelectionItem(
    model: AiModel,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = NvSpacing.Xxs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = isSelected, onClick = onSelect)
        Spacer(Modifier.width(NvSpacing.Xs))
        Column(modifier = Modifier.weight(1f)) {
            Text(model.name, style = MaterialTheme.typography.bodySmall)
            if (model.description.isNotBlank()) {
                Text(model.description, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(NvSpacing.Sm)) {
                Text(
                    "Context: ${formatTokenCount(model.contextWindow)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Max: ${formatTokenCount(model.maxTokens)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Formats token counts in a human-readable way.
 * Shows "K" for thousands, "M" for millions.
 */
private fun formatTokenCount(tokens: Int): String = when {
    tokens >= 1_000_000 -> "${tokens / 1_000_000}M"
    tokens >= 1000 -> "${tokens / 1000}K"
    else -> tokens.toString()
}

private fun getTimeAgo(timestampMs: Long): String {
    val diff = System.currentTimeMillis() - timestampMs
    return when {
        diff < TimeUnit.MINUTES.toMillis(1) -> "Just now"
        diff < TimeUnit.HOURS.toMillis(1) -> "${TimeUnit.MILLISECONDS.toMinutes(diff)}m ago"
        diff < TimeUnit.DAYS.toMillis(1) -> "${TimeUnit.MILLISECONDS.toHours(diff)}h ago"
        else -> "${TimeUnit.MILLISECONDS.toDays(diff)}d ago"
    }
}
