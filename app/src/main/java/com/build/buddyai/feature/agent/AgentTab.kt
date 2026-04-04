package com.build.buddyai.feature.agent

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.build.buddyai.R
import com.build.buddyai.core.designsystem.component.*
import com.build.buddyai.core.designsystem.theme.*
import com.build.buddyai.core.model.*

@Composable
fun AgentTab(
    projectId: String,
    onNavigateToModels: () -> Unit,
    viewModel: AgentViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    LaunchedEffect(projectId) { viewModel.initialize(projectId) }
    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) listState.animateScrollToItem(uiState.messages.size - 1)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Mode selector and provider info
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
                // Action timeline
                if (uiState.currentActions.isNotEmpty()) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(NvSpacing.Xxs)) {
                            uiState.currentActions.forEach { action ->
                                ActionTimelineItem(action = action)
                            }
                        }
                    }
                }
                // Pending diffs
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
                    // Attached files chips
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

@Composable
private fun ChatMessageItem(message: ChatMessage) {
    val isUser = message.role == MessageRole.USER
    val isError = message.status == MessageStatus.ERROR

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isUser) {
            Surface(
                modifier = Modifier.size(28.dp).clip(CircleShape),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Icon(
                    Icons.Filled.Psychology,
                    contentDescription = null,
                    modifier = Modifier.padding(4.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Spacer(Modifier.width(NvSpacing.Xs))
        }

        Surface(
            shape = NvShapes.medium,
            color = when {
                isUser -> MaterialTheme.colorScheme.primaryContainer
                isError -> MaterialTheme.colorScheme.errorContainer
                else -> MaterialTheme.colorScheme.surfaceContainerHigh
            },
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            Column(modifier = Modifier.padding(NvSpacing.Sm)) {
                if (message.status == MessageStatus.STREAMING) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        NvPulsingDot()
                        Spacer(Modifier.width(NvSpacing.Xs))
                        Text("Generating…", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(Modifier.height(NvSpacing.Xxs))
                }
                Text(
                    text = message.content,
                    style = if (message.content.contains("```")) {
                        MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                    } else {
                        MaterialTheme.typography.bodyMedium
                    },
                    color = when {
                        isUser -> MaterialTheme.colorScheme.onPrimaryContainer
                        isError -> MaterialTheme.colorScheme.onErrorContainer
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                )
                if (message.attachedFiles.isNotEmpty()) {
                    Spacer(Modifier.height(NvSpacing.Xxs))
                    message.attachedFiles.forEach { file ->
                        Text("📎 ${file.substringAfterLast("/")}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionTimelineItem(action: AgentAction) {
    Row(
        modifier = Modifier.padding(start = NvSpacing.Xxl),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (action.status == ActionStatus.IN_PROGRESS) {
            NvPulsingDot()
        } else {
            Surface(Modifier.size(8.dp), shape = CircleShape, color = BuildBuddyThemeExtended.colors.success) {}
        }
        Spacer(Modifier.width(NvSpacing.Xs))
        Text(action.description, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DiffReviewCard(
    diffs: List<FileDiff>,
    onApply: () -> Unit,
    onReject: () -> Unit
) {
    NvCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(NvSpacing.Sm)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.DifferenceOutlined, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(NvSpacing.Xs))
                Text("${diffs.size} file(s) to update", style = MaterialTheme.typography.titleSmall)
            }
            Spacer(Modifier.height(NvSpacing.Xs))
            diffs.forEach { diff ->
                Text(
                    text = "${if (diff.isNewFile) "+" else "~"} ${diff.filePath}",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = if (diff.isNewFile) BuildBuddyThemeExtended.colors.success else MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.height(NvSpacing.Sm))
            Row(horizontalArrangement = Arrangement.spacedBy(NvSpacing.Xs)) {
                NvFilledButton(text = "Apply Changes", onClick = onApply, icon = Icons.Filled.Check)
                NvOutlinedButton(text = "Reject", onClick = onReject)
            }
        }
    }
}
