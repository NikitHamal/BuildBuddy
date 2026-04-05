package com.build.buddyai.feature.agent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.build.buddyai.R
import com.build.buddyai.core.common.ArtifactLauncher
import com.build.buddyai.core.designsystem.component.NvCard
import com.build.buddyai.core.designsystem.component.NvChip
import com.build.buddyai.core.designsystem.component.NvChipVariant
import com.build.buddyai.core.designsystem.component.NvEmptyState
import com.build.buddyai.core.designsystem.component.NvFilledButton
import com.build.buddyai.core.designsystem.component.NvPulsingDot
import com.build.buddyai.core.designsystem.theme.BuildBuddyThemeExtended
import com.build.buddyai.core.designsystem.theme.NvElevation
import com.build.buddyai.core.designsystem.theme.NvShapes
import com.build.buddyai.core.designsystem.theme.NvSpacing
import com.build.buddyai.core.model.ActionStatus
import com.build.buddyai.core.model.AgentAction
import com.build.buddyai.core.model.BuildArtifact
import com.build.buddyai.core.model.BuildStatus
import com.build.buddyai.core.model.ChatMessage
import com.build.buddyai.core.model.FileDiff
import com.build.buddyai.core.model.MessageRole
import com.build.buddyai.core.model.MessageStatus

@Composable
fun AgentTab(
    projectId: String,
    onNavigateToModels: () -> Unit,
    viewModel: AgentViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    LaunchedEffect(projectId) { viewModel.initialize(projectId) }
    LaunchedEffect(uiState.messages.size, uiState.currentActions.size, uiState.recentDiffs.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    AgentWorkspaceBody(
        uiState = uiState,
        listState = listState,
        onNavigateToModels = onNavigateToModels,
        onInputChanged = viewModel::updateInput,
        onSend = viewModel::sendMessage,
        onCancel = viewModel::cancelStream,
        onToggleAttachment = viewModel::toggleFileAttachment
    )
}

@Composable
internal fun AgentWorkspaceBody(
    uiState: AgentUiState,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onNavigateToModels: () -> Unit,
    onInputChanged: (String) -> Unit,
    onSend: () -> Unit,
    onCancel: () -> Unit,
    onToggleAttachment: (String) -> Unit
) {
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize()) {
        AgentStatusHeader(
            modelName = uiState.modelName,
            providerName = uiState.providerName,
            plannerSummary = uiState.plannerSummary
        )

        if (!uiState.hasProvider) {
            NvEmptyState(
                icon = Icons.Filled.Key,
                title = stringResource(R.string.agent_no_provider),
                subtitle = "Add an API key to start using the planner, executor, and build repair loop.",
                modifier = Modifier.weight(1f),
                action = { NvFilledButton(text = stringResource(R.string.agent_configure_provider), onClick = onNavigateToModels) }
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = NvSpacing.Sm, vertical = NvSpacing.Sm),
                verticalArrangement = Arrangement.spacedBy(NvSpacing.Sm)
            ) {
                if (uiState.messages.isEmpty()) {
                    item {
                        NvEmptyState(
                            icon = Icons.Filled.Psychology,
                            title = stringResource(R.string.agent_empty_title),
                            subtitle = "Describe a production change, bug, refactor, or feature. BuildBuddy will plan, edit, validate, and surface rollback points.",
                            modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp)
                        )
                    }
                } else {
                    items(uiState.messages, key = { it.id }) { message ->
                        ChatMessageItem(message = message)
                    }
                }
                if (uiState.currentActions.isNotEmpty()) {
                    item { ActionTimelineCard(actions = uiState.currentActions) }
                }
                if (uiState.recentDiffs.isNotEmpty()) {
                    item { RecentChangesCard(diffs = uiState.recentDiffs) }
                }
                if (uiState.lastBuildStatus != null || uiState.lastBuildSummary != null || uiState.latestArtifact != null) {
                    item {
                        BuildValidationCard(
                            status = uiState.lastBuildStatus,
                            summary = uiState.lastBuildSummary,
                            latestArtifact = uiState.latestArtifact,
                            onInstall = { uiState.latestArtifact?.let { ArtifactLauncher.install(context, it) } },
                            onShare = { uiState.latestArtifact?.let { ArtifactLauncher.share(context, it) } }
                        )
                    }
                }
                if (uiState.changeSets.isNotEmpty()) {
                    item { RestorePointsCard(count = uiState.changeSets.size) }
                }
            }
        }

        if (uiState.hasProvider) {
            Surface(
                tonalElevation = NvElevation.Sm,
                modifier = Modifier.fillMaxWidth().imePadding()
            ) {
                Column(modifier = Modifier.padding(NvSpacing.Sm), verticalArrangement = Arrangement.spacedBy(NvSpacing.Xs)) {
                    if (uiState.attachedFiles.isNotEmpty()) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(NvSpacing.Xxs)) {
                            items(uiState.attachedFiles) { file ->
                                InputChip(
                                    selected = true,
                                    onClick = { onToggleAttachment(file) },
                                    label = { Text(file.substringAfterLast("/"), style = MaterialTheme.typography.labelSmall) },
                                    trailingIcon = { Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                )
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.Bottom) {
                        OutlinedTextField(
                            value = uiState.currentInput,
                            onValueChange = onInputChanged,
                            modifier = Modifier.weight(1f),
                            placeholder = { Text(stringResource(R.string.agent_input_hint), style = MaterialTheme.typography.bodySmall) },
                            maxLines = 5,
                            shape = NvShapes.medium,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                            textStyle = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(Modifier.width(NvSpacing.Xs))
                        if (uiState.isStreaming) {
                            FilledIconButton(
                                onClick = onCancel,
                                colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Icon(Icons.Filled.Stop, contentDescription = stringResource(R.string.agent_stop))
                            }
                        } else {
                            FilledIconButton(onClick = onSend, enabled = uiState.currentInput.isNotBlank()) {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.agent_send))
                            }
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
    providerName: String?,
    plannerSummary: String?
) {
    NvCard(modifier = Modifier.fillMaxWidth().padding(horizontal = NvSpacing.Sm, vertical = NvSpacing.Xs)) {
        Column(modifier = Modifier.padding(NvSpacing.Sm), verticalArrangement = Arrangement.spacedBy(NvSpacing.Xs)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(NvSpacing.Xs)) {
                    NvChip(label = "Planner/executor", variant = NvChipVariant.PRIMARY, icon = Icons.Filled.AutoAwesome)
                    NvChip(label = "Rollback ready", variant = NvChipVariant.TERTIARY, icon = Icons.Filled.Build)
                }
                Text(
                    text = listOfNotNull(providerName, modelName).joinToString(" · ").ifBlank { "No model" },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            plannerSummary?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
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
            shape = NvShapes.large,
            color = when {
                isUser -> MaterialTheme.colorScheme.primaryContainer
                isSystem -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                isError -> MaterialTheme.colorScheme.errorContainer
                else -> MaterialTheme.colorScheme.surfaceContainerHigh
            },
            modifier = Modifier.widthIn(max = 360.dp)
        ) {
            Column(modifier = Modifier.padding(NvSpacing.Sm), verticalArrangement = Arrangement.spacedBy(NvSpacing.Xxs)) {
                Text(
                    text = when (message.role) {
                        MessageRole.USER -> "You"
                        MessageRole.SYSTEM -> "Build system"
                        MessageRole.ASSISTANT -> "BuildBuddy"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (message.status == MessageStatus.STREAMING) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        NvPulsingDot()
                        Spacer(Modifier.width(NvSpacing.Xs))
                        Text("Working…", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
                Text(
                    text = message.content.ifBlank { if (message.status == MessageStatus.STREAMING) "" else "…" },
                    style = if (message.content.contains("```")) MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace) else MaterialTheme.typography.bodyMedium,
                    color = when {
                        isUser -> MaterialTheme.colorScheme.onPrimaryContainer
                        isSystem -> MaterialTheme.colorScheme.onSecondaryContainer
                        isError -> MaterialTheme.colorScheme.onErrorContainer
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                )
                if (message.attachedFiles.isNotEmpty()) {
                    message.attachedFiles.forEach { file ->
                        Text("📎 ${file.substringAfterLast("/")}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionTimelineCard(actions: List<AgentAction>) {
    NvCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(NvSpacing.Sm), verticalArrangement = Arrangement.spacedBy(NvSpacing.Xs)) {
            Text("Execution timeline", style = MaterialTheme.typography.titleSmall)
            actions.forEach { action -> ActionTimelineItem(action) }
        }
    }
}

@Composable
internal fun ActionTimelineItem(action: AgentAction) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(NvSpacing.Xs)) {
        when (action.status) {
            ActionStatus.IN_PROGRESS -> NvPulsingDot()
            ActionStatus.FAILED -> Icon(Icons.Filled.ErrorOutline, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.error)
            else -> Box(
                modifier = Modifier.size(10.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(modifier = Modifier.size(8.dp), shape = CircleShape, color = BuildBuddyThemeExtended.colors.success) {}
            }
        }
        Text(action.description, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun RecentChangesCard(diffs: List<FileDiff>) {
    NvCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(NvSpacing.Sm), verticalArrangement = Arrangement.spacedBy(NvSpacing.Xs)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Description, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(NvSpacing.Xs))
                Text("Applied changes", style = MaterialTheme.typography.titleSmall)
            }
            diffs.forEach { diff ->
                val prefix = when {
                    diff.isDeleted -> "−"
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
    summary: String?,
    latestArtifact: BuildArtifact?,
    onInstall: () -> Unit,
    onShare: () -> Unit
) {
    val (icon, tint) = when (status) {
        BuildStatus.SUCCESS -> Icons.Filled.CheckCircle to BuildBuddyThemeExtended.colors.success
        BuildStatus.FAILED -> Icons.Filled.ErrorOutline to MaterialTheme.colorScheme.error
        BuildStatus.CANCELLED -> Icons.Filled.ErrorOutline to MaterialTheme.colorScheme.onSurfaceVariant
        else -> Icons.Filled.Build to MaterialTheme.colorScheme.primary
    }
    NvCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(NvSpacing.Sm), verticalArrangement = Arrangement.spacedBy(NvSpacing.Sm)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = tint)
                Spacer(Modifier.width(NvSpacing.Sm))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Validation", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(summary ?: "Waiting for build", style = MaterialTheme.typography.bodyMedium)
                }
            }
            latestArtifact?.let { artifact ->
                Surface(shape = NvShapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Column(modifier = Modifier.padding(NvSpacing.Sm), verticalArrangement = Arrangement.spacedBy(NvSpacing.Xs)) {
                        Text(artifact.fileName, style = MaterialTheme.typography.titleSmall)
                        Text(artifact.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(NvSpacing.Sm)) {
                            NvFilledButton(text = "Install", onClick = onInstall, icon = Icons.Filled.InstallMobile)
                            NvFilledButton(text = "Share", onClick = onShare, icon = Icons.Filled.Share)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RestorePointsCard(count: Int) {
    NvCard(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(NvSpacing.Sm), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Description, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(NvSpacing.Xs))
            Column {
                Text("Restore points ready", style = MaterialTheme.typography.titleSmall)
                Text("$count change-set rollback point(s) available in the Build tab.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
