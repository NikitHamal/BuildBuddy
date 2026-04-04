package com.build.buddyai.feature.models

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import com.build.buddyai.core.designsystem.StatusBadge
import com.build.buddyai.core.model.ProviderSettings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelsScreen(
    viewModel: ModelsViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val spacing = NeoVedicTheme.spacing
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.models_title)) },
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
                Text(
                    stringResource(R.string.models_subtitle),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(state.providerSettings, key = { it.providerId.name }) { provider ->
                ProviderCard(
                    settings = provider,
                    keyPresent = state.secrets.firstOrNull { it.providerId == provider.providerId }?.apiKeyPresent == true,
                    connectionMessage = state.connectionResults[provider.providerId]?.message,
                    onSaveKey = { viewModel.saveApiKey(provider.providerId, it) },
                    onSettingsChange = viewModel::updateProviderSettings,
                    onTestConnection = { viewModel.testConnection(provider.providerId) },
                )
            }
        }
    }
}

@Composable
private fun ProviderCard(
    settings: ProviderSettings,
    keyPresent: Boolean,
    connectionMessage: String?,
    onSaveKey: (String) -> Unit,
    onSettingsChange: (ProviderSettings) -> Unit,
    onTestConnection: () -> Unit,
) {
    var apiKey by remember(settings.providerId) { mutableStateOf("") }
    var selectedModel by remember(settings.selectedModel) { mutableStateOf(settings.selectedModel) }
    var temperature by remember(settings.temperature) { mutableStateOf(settings.temperature.toString()) }
    var maxTokens by remember(settings.maxTokens) { mutableStateOf(settings.maxTokens.toString()) }
    var topP by remember(settings.topP) { mutableStateOf(settings.topP.toString()) }
    val spacing = NeoVedicTheme.spacing

    BuildBuddyCard {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(settings.providerId.name, style = MaterialTheme.typography.titleLarge)
                StatusBadge(
                    label = if (keyPresent) stringResource(R.string.provider_connection_ready) else "No key",
                    color = if (keyPresent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
            }
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text(stringResource(R.string.provider_api_key)) },
            )
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = selectedModel,
                onValueChange = {
                    selectedModel = it
                    onSettingsChange(settings.copy(selectedModel = it))
                },
                label = { Text(stringResource(R.string.provider_model)) },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                OutlinedTextField(
                    modifier = Modifier.weight(1f),
                    value = temperature,
                    onValueChange = {
                        temperature = it
                        it.toDoubleOrNull()?.let { parsed -> onSettingsChange(settings.copy(temperature = parsed)) }
                    },
                    label = { Text(stringResource(R.string.provider_temperature)) },
                )
                OutlinedTextField(
                    modifier = Modifier.weight(1f),
                    value = maxTokens,
                    onValueChange = {
                        maxTokens = it
                        it.toIntOrNull()?.let { parsed -> onSettingsChange(settings.copy(maxTokens = parsed)) }
                    },
                    label = { Text(stringResource(R.string.provider_max_tokens)) },
                )
                OutlinedTextField(
                    modifier = Modifier.weight(1f),
                    value = topP,
                    onValueChange = {
                        topP = it
                        it.toDoubleOrNull()?.let { parsed -> onSettingsChange(settings.copy(topP = parsed)) }
                    },
                    label = { Text(stringResource(R.string.provider_top_p)) },
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                Button(onClick = { onSaveKey(apiKey) }) {
                    Text(stringResource(R.string.action_add_key))
                }
                Button(onClick = onTestConnection) {
                    Text(stringResource(R.string.action_test_connection))
                }
            }
            connectionMessage?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
