package com.build.buddyai.feature.agent

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.build.buddyai.R
import com.build.buddyai.core.designsystem.component.*
import com.build.buddyai.core.designsystem.theme.*
import com.build.buddyai.core.model.AgentMode

@Composable
fun AgentScreen(
    projectId: String,
    onBack: () -> Unit,
    onNavigateToModels: () -> Unit,
    viewModel: AgentViewModel = hiltViewModel()
) {
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
            // Mode selector
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = NvSpacing.Sm, vertical = NvSpacing.Xxs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(NvSpacing.Xxs)) {
                    items(AgentMode.entries) { mode ->
                        NvFilterChip(
                            label = mode.displayName,
                            selected = uiState.agentMode == mode,
                            onClick = { viewModel.updateAgentMode(mode) }
                        )
                    }
                }
                if (uiState.modelName != null) {
                    Text(uiState.modelName!!, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // Messages
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
                    icon = Icons.Filled.Psychology,
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
                    if (uiState.pendingDiffs.isNotEmpty()) {
                        item {
                            DiffReviewCard(
                                diffs = uiState.pendingDiffs,
                                onApply = viewModel::applyDiffs,
                                onReject = viewModel::rejectDiffs
                            )
                        }
                    }
                }
            }

            // Input area
            if (uiState.hasProvider) {
                Surface(
                    tonalElevation = NvElevation.Sm,
                    modifier = Modifier.fillMaxWidth()
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
                                        trailingIcon = { Icon(Icons.Filled.Close, contentDescription = null, Modifier.size(14.dp)) }
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
