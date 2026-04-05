package com.build.buddyai.feature.agent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.build.buddyai.R
import com.build.buddyai.core.designsystem.component.NvBackButton
import com.build.buddyai.core.designsystem.component.NvEmptyState
import com.build.buddyai.core.designsystem.component.NvFilledButton
import com.build.buddyai.core.designsystem.component.NvTopBar
import com.build.buddyai.core.designsystem.theme.NvElevation
import com.build.buddyai.core.designsystem.theme.NvShapes
import com.build.buddyai.core.designsystem.theme.NvSpacing

@Composable
fun AgentScreen(
    projectId: String,
    onBack: () -> Unit,
    onNavigateToModels: () -> Unit
) {
    val viewModel: AgentViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    LaunchedEffect(projectId) { viewModel.initialize(projectId) }
    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) listState.animateScrollToItem(uiState.messages.size - 1)
    }

    Scaffold(
        topBar = {
            NvTopBar(
                title = "AI Agent",
                subtitle = "Autonomous build workflow",
                navigationIcon = { NvBackButton(onBack) },
                actions = {
                    IconButton(onClick = onNavigateToModels) {
                        Icon(Icons.Filled.Settings, contentDescription = "Configure AI Providers")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            AgentStatusHeader(
                modelName = uiState.modelName,
                providerName = uiState.providerName
            )

            if (!uiState.hasProvider) {
                NvEmptyState(
                    icon = Icons.Filled.Key,
                    title = stringResource(R.string.agent_no_provider),
                    subtitle = "Add an API key to start using AI features",
                    modifier = Modifier.weight(1f),
                    action = {
                        NvFilledButton(text = stringResource(R.string.agent_configure_provider), onClick = onNavigateToModels)
                    }
                )
            } else if (uiState.messages.isEmpty()) {
                NvEmptyState(
                    icon = Icons.Filled.Settings,
                    title = stringResource(R.string.agent_empty_title),
                    subtitle = stringResource(R.string.agent_empty_subtitle),
                    modifier = Modifier.weight(1f)
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = NvSpacing.Sm, vertical = NvSpacing.Xs),
                    verticalArrangement = Arrangement.spacedBy(NvSpacing.Xs)
                ) {
                    items(uiState.messages, key = { it.id }) { message ->
                        ChatMessageItem(message = message)
                    }
                    if (uiState.currentActions.isNotEmpty()) {
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(NvSpacing.Xxs)) {
                                uiState.currentActions.forEach { action ->
                                    ActionTimelineItem(action = action)
                                }
                            }
                        }
                    }
                    if (uiState.recentDiffs.isNotEmpty()) {
                        item { RecentChangesCard(diffs = uiState.recentDiffs) }
                    }
                    if (uiState.lastBuildStatus != null || uiState.lastBuildSummary != null) {
                        item {
                            BuildValidationCard(
                                status = uiState.lastBuildStatus,
                                summary = uiState.lastBuildSummary
                            )
                        }
                    }
                }
            }

            if (uiState.hasProvider) {
                Surface(
                    tonalElevation = NvElevation.Sm,
                    modifier = Modifier.fillMaxWidth().imePadding()
                ) {
                    Column(modifier = Modifier.padding(NvSpacing.Sm)) {
                        if (uiState.attachedFiles.isNotEmpty()) {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(NvSpacing.Xxs),
                                modifier = Modifier.padding(bottom = NvSpacing.Xxs)
                            ) {
                                items(uiState.attachedFiles) { file ->
                                    InputChip(
                                        selected = true,
                                        onClick = { viewModel.toggleFileAttachment(file) },
                                        label = { Text(file.substringAfterLast("/"), style = MaterialTheme.typography.labelSmall) },
                                        trailingIcon = { Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                    )
                                }
                            }
                        }
                        Row(verticalAlignment = Alignment.Bottom) {
                            OutlinedTextField(
                                value = uiState.currentInput,
                                onValueChange = viewModel::updateInput,
                                modifier = Modifier.weight(1f),
                                placeholder = { Text(stringResource(R.string.agent_input_hint), style = MaterialTheme.typography.bodySmall) },
                                maxLines = 5,
                                shape = NvShapes.small,
                                textStyle = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(Modifier.width(NvSpacing.Xs))
                            if (uiState.isStreaming) {
                                FilledIconButton(
                                    onClick = viewModel::cancelStream,
                                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.error)
                                ) { Icon(Icons.Filled.Stop, contentDescription = stringResource(R.string.agent_stop)) }
                            } else {
                                FilledIconButton(
                                    onClick = viewModel::sendMessage,
                                    enabled = uiState.currentInput.isNotBlank()
                                ) { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.agent_send)) }
                            }
                        }
                    }
                }
            }
        }
    }
}
