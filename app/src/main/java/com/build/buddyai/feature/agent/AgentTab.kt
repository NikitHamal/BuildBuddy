package com.build.buddyai.feature.agent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.build.buddyai.R
import com.build.buddyai.core.designsystem.component.NvCard
import com.build.buddyai.core.designsystem.component.NvEmptyState
import com.build.buddyai.core.designsystem.component.NvFilledButton
import com.build.buddyai.core.designsystem.component.NvPulsingDot
import com.build.buddyai.core.designsystem.theme.BuildBuddyThemeExtended
import com.build.buddyai.core.designsystem.theme.NvSpacing
import com.build.buddyai.core.model.ActionStatus
import com.build.buddyai.core.model.AgentAction
import com.build.buddyai.core.model.AgentActionType
import com.build.buddyai.core.model.AgentAutonomyMode
import com.build.buddyai.core.model.BuildStatus
import com.build.buddyai.core.model.ChatMessage
import com.build.buddyai.core.model.FileDiff
import com.build.buddyai.core.model.MessageRole
import com.build.buddyai.core.model.MessageStatus
import com.build.buddyai.feature.agent.components.AgentPromptBar

@Composable
fun AgentTab(
    projectId: String,
    onNavigateToModels: () -> Unit,
    viewModel: AgentViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    LaunchedEffect(projectId) { viewModel.initialize(projectId) }
    LaunchedEffect(uiState.messages.size, uiState.executionTimeline.size, uiState.currentActions.size) {
        val lastIndex = when {
            uiState.messages.isNotEmpty() -> uiState.messages.size
            uiState.executionTimeline.isNotEmpty() || uiState.currentActions.isNotEmpty() -> 1
            else -> 0
        }
        if (lastIndex > 0) listState.animateScrollToItem(lastIndex - 1)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (!uiState.hasProvider) {
            NvEmptyState(
                icon = Icons.Filled.Key,
                title = androidx.compose.ui.res.stringResource(R.string.agent_no_provider),
                subtitle = "Add an API key to start using AI features",
                modifier = Modifier.weight(1f),
                action = {
                    NvFilledButton(text = androidx.compose.ui.res.stringResource(R.string.agent_configure_provider), onClick = onNavigateToModels)
                }
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = NvSpacing.Sm, vertical = NvSpacing.Xs),
                verticalArrangement = Arrangement.spacedBy(NvSpacing.Xs)
            ) {
                if (uiState.messages.isEmpty()) {
                    item {
                        NvEmptyState(
                            icon = Icons.Filled.Psychology,
                            title = androidx.compose.ui.res.stringResource(R.string.agent_empty_title),
                            subtitle = androidx.compose.ui.res.stringResource(R.string.agent_empty_subtitle),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                } else {
                    items(uiState.messages, key = { it.id }) { message ->
                        ChatMessageItem(message = message)
                    }
                }
                if (uiState.executionTimeline.isNotEmpty() || uiState.currentActions.isNotEmpty()) {
                    item {
                        ExecutionTimelineCard(
                            events = uiState.executionTimeline,
                            liveActions = uiState.currentActions
                        )
                    }
                }
                if (uiState.recentDiffs.isNotEmpty()) {
                    item {
                        RecentChangesCard(diffs = uiState.recentDiffs)
                    }
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
            AgentPromptBar(
                currentInput = uiState.currentInput,
                attachedFiles = uiState.attachedFiles,
                autonomyMode = uiState.autonomyMode,
                modelName = uiState.modelName,
                providerName = uiState.providerName,
                allProviders = uiState.allProviders,
                supportsImageAttachments = uiState.supportsImageAttachments,
                isStreaming = uiState.isStreaming,
                onUpdateInput = viewModel::updateInput,
                onSendMessage = viewModel::sendMessage,
                onCancelStream = viewModel::cancelStream,
                onAttachImage = { },
                onRemoveAttachment = viewModel::toggleFileAttachment,
                onUpdateMode = viewModel::updateAutonomyMode,
                onSelectModel = viewModel::selectModel,
                placeholder = androidx.compose.ui.res.stringResource(R.string.agent_input_hint)
            )
        }
    }
}

@Composable
internal fun ChatMessageItem(message: ChatMessage) {
    val isUser = message.role == MessageRole.USER
    val isSystem = message.role == MessageRole.SYSTEM
    val isError = message.status == MessageStatus.ERROR

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = when {
                isUser -> MaterialTheme.colorScheme.primaryContainer
                isSystem -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                isError -> MaterialTheme.colorScheme.errorContainer
                else -> MaterialTheme.colorScheme.surfaceContainerHigh
            },
            modifier = Modifier.widthIn(max = 400.dp)
        ) {
            Column(modifier = Modifier.padding(NvSpacing.Sm), verticalArrangement = Arrangement.spacedBy(NvSpacing.Xxs)) {
                if (message.status == MessageStatus.STREAMING) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        NvPulsingDot()
                        Spacer(Modifier.width(NvSpacing.Xs))
                        Text("Working...", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
                val roleLabel = when (message.role) {
                    MessageRole.USER -> "You"
                    MessageRole.ASSISTANT -> "Assistant"
                    MessageRole.SYSTEM -> "System"
                }
                Text(
                    text = roleLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = message.content.ifBlank { if (message.status == MessageStatus.STREAMING) "" else "..." },
                    style = if (message.content.contains("```")) {
                        MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                    } else {
                        MaterialTheme.typography.bodyMedium
                    },
                    color = when {
                        isUser -> MaterialTheme.colorScheme.onPrimaryContainer
                        isSystem -> MaterialTheme.colorScheme.onSecondaryContainer
                        isError -> MaterialTheme.colorScheme.onErrorContainer
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                )
                if (message.attachedFiles.isNotEmpty()) {
                    message.attachedFiles.take(4).forEach { file ->
                        Text(
                            text = "File: ${file.substringAfterLast("/")}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun ExecutionTimelineCard(
    events: List<AgentExecutionEvent>,
    liveActions: List<AgentAction>
) {
    NvCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(NvSpacing.Sm),
            verticalArrangement = Arrangement.spacedBy(NvSpacing.Xs)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Build, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(NvSpacing.Xs))
                Text("Tool Timeline", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }

            if (liveActions.isNotEmpty()) {
                Text(
                    text = "Active now",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                liveActions.forEach { action ->
                    ActionTimelineItem(action = action)
                }
            }

            val recentEvents = events.takeLast(14).asReversed()
            if (recentEvents.isNotEmpty()) {
                Text(
                    text = "Recent calls",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                recentEvents.forEach { event ->
                    ExecutionEventRow(event = event)
                }
            }

            if (recentEvents.isEmpty() && liveActions.isEmpty()) {
                Text(
                    text = "No execution events yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ExecutionEventRow(event: AgentExecutionEvent) {
    Row(verticalAlignment = Alignment.Top) {
        val (statusIcon, statusTint) = when (event.status) {
            ActionStatus.IN_PROGRESS -> Icons.Filled.AutoAwesome to MaterialTheme.colorScheme.primary
            ActionStatus.COMPLETED -> Icons.Filled.Check to BuildBuddyThemeExtended.colors.success
            ActionStatus.FAILED -> Icons.Filled.Close to MaterialTheme.colorScheme.error
            ActionStatus.PENDING -> Icons.Filled.Description to MaterialTheme.colorScheme.onSurfaceVariant
        }
        Icon(statusIcon, contentDescription = null, tint = statusTint, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(NvSpacing.Xs))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = eventTypeIcon(event.type),
                    contentDescription = null,
                    modifier = Modifier.size(13.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            event.details?.takeIf { it.isNotBlank() }?.let { details ->
                Text(
                    text = details,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun eventTypeIcon(type: AgentActionType) = when (type) {
    AgentActionType.READING_FILE -> Icons.Filled.Description
    AgentActionType.SEARCHING -> Icons.Filled.Search
    AgentActionType.PLANNING -> Icons.Filled.AutoAwesome
    AgentActionType.EDITING_FILE -> Icons.Filled.Build
    AgentActionType.CREATING_FILE -> Icons.Filled.Build
    AgentActionType.DELETING_FILE -> Icons.Filled.Build
    AgentActionType.GENERATING_PATCH -> Icons.Filled.Build
    AgentActionType.BUILDING -> Icons.Filled.Build
    AgentActionType.ANALYZING_LOGS -> Icons.Filled.ErrorOutline
    AgentActionType.EXPLAINING -> Icons.Filled.AutoAwesome
    AgentActionType.VERIFYING -> Icons.Filled.Check
}

@Composable
internal fun ActionTimelineItem(action: AgentAction) {
    Row(
        modifier = Modifier.padding(start = NvSpacing.Xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        when (action.status) {
            ActionStatus.IN_PROGRESS -> NvPulsingDot()
            ActionStatus.FAILED -> Icon(
                Icons.Filled.ErrorOutline,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.error
            )
            else -> Surface(Modifier.size(8.dp), shape = CircleShape, color = BuildBuddyThemeExtended.colors.success) {}
        }
        Spacer(Modifier.width(NvSpacing.Xs))
        Text(
            text = action.description,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
internal fun RecentChangesCard(diffs: List<FileDiff>) {
    NvCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(NvSpacing.Sm)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Description, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(NvSpacing.Xs))
                Text("Applied changes", style = MaterialTheme.typography.titleSmall)
            }
            Spacer(Modifier.height(NvSpacing.Xs))
            diffs.forEach { diff ->
                val prefix = when {
                    diff.isDeleted -> "-"
                    diff.isNewFile -> "+"
                    else -> "~"
                }
                Text(
                    text = "$prefix ${diff.filePath}",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = when {
                        diff.isDeleted -> MaterialTheme.colorScheme.error
                        diff.isNewFile -> BuildBuddyThemeExtended.colors.success
                        else -> MaterialTheme.colorScheme.primary
                    }
                )
            }
        }
    }
}

@Composable
internal fun BuildValidationCard(
    status: BuildStatus?,
    summary: String?
) {
    val (icon, tint) = when (status) {
        BuildStatus.SUCCESS -> Icons.Filled.Check to BuildBuddyThemeExtended.colors.success
        BuildStatus.FAILED -> Icons.Filled.ErrorOutline to MaterialTheme.colorScheme.error
        BuildStatus.CANCELLED -> Icons.Filled.ErrorOutline to MaterialTheme.colorScheme.onSurfaceVariant
        else -> Icons.Filled.Build to MaterialTheme.colorScheme.primary
    }
    NvCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(NvSpacing.Sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = tint)
            Spacer(Modifier.width(NvSpacing.Sm))
            Column {
                Text("Validation", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(summary ?: "Waiting for build", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
