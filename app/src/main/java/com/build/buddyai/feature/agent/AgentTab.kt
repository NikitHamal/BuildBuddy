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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
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
import com.build.buddyai.core.designsystem.theme.NvElevation
import com.build.buddyai.core.designsystem.theme.NvShapes
import com.build.buddyai.core.designsystem.theme.NvSpacing
import com.build.buddyai.core.model.ActionStatus
import com.build.buddyai.core.model.AgentAction
import com.build.buddyai.core.model.BuildStatus
import com.build.buddyai.core.model.FileDiff
import com.build.buddyai.core.model.MessageRole
import com.build.buddyai.core.model.MessageStatus
import com.build.buddyai.core.model.ChatMessage

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
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
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
            Surface(
                tonalElevation = NvElevation.Sm,
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
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

@Composable
internal fun AgentStatusHeader(
    modelName: String?,
    providerName: String?
) {
    Surface(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = NvSpacing.Sm, vertical = NvSpacing.Xs),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            StatusPill(icon = Icons.Filled.AutoAwesome, label = "Safe apply")
            Text(
                text = listOfNotNull(providerName, modelName).joinToString(" · ").ifBlank { "No model selected" },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun StatusPill(icon: ImageVector, label: String) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = NvSpacing.Sm, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
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
            shape = NvShapes.medium,
            color = when {
                isUser -> MaterialTheme.colorScheme.primaryContainer
                isSystem -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                isError -> MaterialTheme.colorScheme.errorContainer
                else -> MaterialTheme.colorScheme.surfaceContainerHigh
            },
            modifier = Modifier.widthIn(max = 360.dp)
        ) {
            Column(modifier = Modifier.padding(NvSpacing.Sm)) {
                if (message.status == MessageStatus.STREAMING) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        NvPulsingDot()
                        Spacer(Modifier.width(NvSpacing.Xs))
                        Text("Working…", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(Modifier.height(NvSpacing.Xxs))
                }
                if (!isUser) {
                    Text(
                        text = if (isSystem) "System" else "Assistant",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(NvSpacing.Xxs))
                }
                Text(
                    text = message.content.ifBlank { if (message.status == MessageStatus.STREAMING) "" else "…" },
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
internal fun ActionTimelineItem(action: AgentAction) {
    Row(
        modifier = Modifier.padding(start = NvSpacing.Xxl),
        verticalAlignment = Alignment.CenterVertically
    ) {
        when (action.status) {
            ActionStatus.IN_PROGRESS -> NvPulsingDot()
            ActionStatus.FAILED -> Icon(Icons.Filled.ErrorOutline, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.error)
            else -> Surface(Modifier.size(8.dp), shape = CircleShape, color = BuildBuddyThemeExtended.colors.success) {}
        }
        Spacer(Modifier.width(NvSpacing.Xs))
        Text(action.description, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
