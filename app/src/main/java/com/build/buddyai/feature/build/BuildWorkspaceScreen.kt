package com.build.buddyai.feature.build

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.build.buddyai.R
import com.build.buddyai.core.designsystem.component.*
import com.build.buddyai.core.designsystem.theme.*
import com.build.buddyai.core.model.BuildArtifact
import com.build.buddyai.core.model.BuildStatus
import com.build.buddyai.core.model.LogLevel
import com.build.buddyai.feature.artifacts.ArtifactsViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun BuildWorkspaceScreen(
    projectId: String,
    onNavigateToAgent: () -> Unit,
    buildViewModel: BuildViewModel = hiltViewModel(),
    artifactsViewModel: ArtifactsViewModel = hiltViewModel(key = "artifacts")
) {
    val buildUiState by buildViewModel.uiState.collectAsStateWithLifecycle()
    val artifactsUiState by artifactsViewModel.uiState.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    var showDeleteDialog by remember { mutableStateOf<BuildArtifact?>(null) }
    var showLogs by remember { mutableStateOf(true) }
    var showHistory by remember { mutableStateOf(true) }
    var showArtifacts by remember { mutableStateOf(true) }
    val dateFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val historyDateFormat = remember { SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()) }

    LaunchedEffect(projectId) {
        buildViewModel.initialize(projectId)
        artifactsViewModel.loadArtifacts(projectId)
    }
    LaunchedEffect(buildUiState.buildStatus) {
        if (buildUiState.buildStatus == BuildStatus.SUCCESS) {
            artifactsViewModel.loadArtifacts(projectId)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(NvSpacing.Md),
        verticalArrangement = Arrangement.spacedBy(NvSpacing.Sm)
    ) {
        // Build Status Card
        item {
            NvCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(NvSpacing.Md)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val statusIcon = when (buildUiState.buildStatus) {
                            BuildStatus.SUCCESS -> Icons.Filled.CheckCircle
                            BuildStatus.FAILED -> Icons.Filled.Error
                            BuildStatus.BUILDING -> Icons.Filled.HourglassTop
                            BuildStatus.CANCELLED -> Icons.Filled.Cancel
                            else -> Icons.Filled.PlayCircle
                        }
                        val statusColor = when (buildUiState.buildStatus) {
                            BuildStatus.SUCCESS -> BuildBuddyThemeExtended.colors.success
                            BuildStatus.FAILED -> MaterialTheme.colorScheme.error
                            BuildStatus.BUILDING -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        Icon(statusIcon, contentDescription = null, tint = statusColor, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(NvSpacing.Xs))
                        Text(buildUiState.statusMessage.ifEmpty { stringResource(R.string.build_status_idle) }, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        if (buildUiState.buildStatus == BuildStatus.FAILED) {
                            TextButton(onClick = onNavigateToAgent) {
                                Icon(Icons.Filled.Psychology, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Ask AI", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    if (buildUiState.isBuilding) {
                        Spacer(Modifier.height(NvSpacing.Sm))
                        NvLinearProgress(progress = buildUiState.buildProgress)
                    }
                }
            }
        }

        // Build Controls
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(NvSpacing.Xs)) {
                if (buildUiState.isBuilding) {
                    NvOutlinedButton(
                        text = stringResource(R.string.build_cancel),
                        onClick = { buildViewModel.cancelBuild() },
                        icon = Icons.Filled.Stop,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    NvFilledButton(
                        text = stringResource(R.string.build_start),
                        onClick = { buildViewModel.startBuild() },
                        icon = Icons.Filled.PlayArrow,
                        modifier = Modifier.weight(1f)
                    )
                    NvOutlinedButton(
                        text = stringResource(R.string.build_clean),
                        onClick = { buildViewModel.cleanBuild() },
                        icon = Icons.Filled.CleaningServices,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Error summary
        if (buildUiState.errorSummary != null) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Column(modifier = Modifier.padding(NvSpacing.Md)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(NvSpacing.Xs))
                            Text("Build Errors", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.error)
                        }
                        Spacer(Modifier.height(NvSpacing.Xs))
                        Text(
                            buildUiState.errorSummary!!,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }

        // Compatibility warnings
        if (buildUiState.compatibilityWarnings.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = BuildBuddyThemeExtended.colors.warningContainer)
                ) {
                    Column(modifier = Modifier.padding(NvSpacing.Md)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Warning, contentDescription = null, tint = BuildBuddyThemeExtended.colors.warning, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(NvSpacing.Xs))
                            Text("Compatibility Warnings", style = MaterialTheme.typography.titleSmall)
                        }
                        Spacer(Modifier.height(NvSpacing.Xs))
                        buildUiState.compatibilityWarnings.forEach { warning ->
                            Text("• $warning", style = MaterialTheme.typography.bodySmall, color = BuildBuddyThemeExtended.colors.warning)
                        }
                    }
                }
            }
        }

        // Build Logs Section
        if (buildUiState.logEntries.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = NvSpacing.Sm),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Build Logs", style = MaterialTheme.typography.titleSmall)
                    IconButton(onClick = { showLogs = !showLogs }) {
                        Icon(if (showLogs) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null)
                    }
                }
            }
            item {
                AnimatedVisibility(
                    visible = showLogs,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Surface(
                        shape = NvShapes.small,
                        color = BuildBuddyThemeExtended.colors.editorBackground,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(NvSpacing.Xs)
                                .heightIn(max = 300.dp)
                                .horizontalScroll(rememberScrollState())
                        ) {
                            buildUiState.logEntries.forEach { entry ->
                                val color = when (entry.level) {
                                    LogLevel.ERROR -> MaterialTheme.colorScheme.error
                                    LogLevel.WARNING -> BuildBuddyThemeExtended.colors.warning
                                    LogLevel.INFO -> MaterialTheme.colorScheme.onSurface
                                    LogLevel.DEBUG -> MaterialTheme.colorScheme.onSurfaceVariant
                                    LogLevel.VERBOSE -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                }
                                Text(
                                    text = "[${dateFormat.format(Date(entry.timestamp))}] ${entry.level.name.first()} ${entry.message}",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        lineHeight = 16.sp
                                    ),
                                    color = color
                                )
                            }
                        }
                    }
                }
            }
        }

        // Build History Section
        if (buildUiState.buildHistory.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = NvSpacing.Sm),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Build History", style = MaterialTheme.typography.titleSmall)
                    IconButton(onClick = { showHistory = !showHistory }) {
                        Icon(if (showHistory) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null)
                    }
                }
            }
            item {
                AnimatedVisibility(
                    visible = showHistory,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(NvSpacing.Xxs)) {
                        buildUiState.buildHistory.take(10).forEach { record ->
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.padding(NvSpacing.Sm),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val statusColor = when (record.status) {
                                        BuildStatus.SUCCESS -> BuildBuddyThemeExtended.colors.success
                                        BuildStatus.FAILED -> MaterialTheme.colorScheme.error
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                    Surface(
                                        Modifier.size(8.dp),
                                        shape = MaterialTheme.shapes.extraLarge,
                                        color = statusColor
                                    ) {}
                                    Spacer(Modifier.width(NvSpacing.Xs))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(record.status.displayName, style = MaterialTheme.typography.labelMedium, color = statusColor)
                                        Text(historyDateFormat.format(Date(record.startedAt)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    if (record.durationMs != null) {
                                        Text(
                                            formatDuration(record.durationMs),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Artifacts Section
        if (artifactsUiState.artifacts.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = NvSpacing.Sm),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("APK Artifacts", style = MaterialTheme.typography.titleSmall)
                    IconButton(onClick = { showArtifacts = !showArtifacts }) {
                        Icon(if (showArtifacts) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null)
                    }
                }
            }
            item {
                AnimatedVisibility(
                    visible = showArtifacts,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(NvSpacing.Sm)) {
                        items(artifactsUiState.artifacts, key = { it.id }) { artifact ->
                            ArtifactCard(
                                artifact = artifact,
                                onInstall = { artifactsViewModel.installArtifact(context, artifact) },
                                onShare = { artifactsViewModel.shareArtifact(context, artifact) },
                                onDelete = { showDeleteDialog = artifact }
                            )
                        }
                    }
                }
            }
        }

        // Empty state
        if (buildUiState.buildHistory.isEmpty() && artifactsUiState.artifacts.isEmpty() && !buildUiState.isBuilding) {
            item {
                NvEmptyState(
                    icon = Icons.Filled.Build,
                    title = stringResource(R.string.build_no_builds),
                    subtitle = stringResource(R.string.build_no_builds_subtitle),
                    modifier = Modifier.padding(vertical = NvSpacing.Xxl)
                )
            }
        }
    }

    showDeleteDialog?.let { artifact ->
        NvAlertDialog(
            title = "Delete Artifact",
            message = "Delete ${artifact.fileName}?",
            confirmText = stringResource(R.string.action_delete),
            onConfirm = { artifactsViewModel.deleteArtifact(artifact); showDeleteDialog = null },
            onDismiss = { showDeleteDialog = null },
            isDestructive = true
        )
    }
}

@Composable
private fun ArtifactCard(
    artifact: BuildArtifact,
    onInstall: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }

    Card(
        modifier = Modifier.width(220.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(modifier = Modifier.padding(NvSpacing.Md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Android, contentDescription = null, tint = BuildBuddyThemeExtended.colors.success, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(NvSpacing.Xs))
                Text(artifact.fileName, style = MaterialTheme.typography.titleSmall, maxLines = 1)
            }
            Spacer(Modifier.height(NvSpacing.Xxs))
            Text(artifact.packageName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            Spacer(Modifier.height(NvSpacing.Xxs))
            Row(horizontalArrangement = Arrangement.spacedBy(NvSpacing.Md)) {
                Text(com.build.buddyai.core.common.FileUtils.formatFileSize(artifact.sizeBytes), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(artifact.versionName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(NvSpacing.Sm))
            Row(horizontalArrangement = Arrangement.spacedBy(NvSpacing.Xxs)) {
                TextButton(onClick = onInstall, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.InstallMobile, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Install", style = MaterialTheme.typography.labelSmall)
                }
                TextButton(onClick = onShare, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Share", style = MaterialTheme.typography.labelSmall)
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    val seconds = ms / 1000
    return when {
        seconds < 60 -> "${seconds}s"
        seconds < 3600 -> "${seconds / 60}m ${seconds % 60}s"
        else -> "${seconds / 3600}h ${(seconds % 3600) / 60}m"
    }
}
