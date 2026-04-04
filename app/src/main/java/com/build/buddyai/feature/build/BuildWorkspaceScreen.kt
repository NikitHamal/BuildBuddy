package com.build.buddyai.feature.build

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
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
    var showHistory by remember { mutableStateOf(false) }
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
        verticalArrangement = Arrangement.spacedBy(NvSpacing.Md)
    ) {
        // Build Status Card
        item {
            NvCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(NvSpacing.Md)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val (statusIcon, statusColor) = when (buildUiState.buildStatus) {
                            BuildStatus.SUCCESS -> Icons.Filled.CheckCircle to BuildBuddyThemeExtended.colors.success
                            BuildStatus.FAILED -> Icons.Filled.Error to MaterialTheme.colorScheme.error
                            BuildStatus.BUILDING -> Icons.Filled.HourglassTop to MaterialTheme.colorScheme.primary
                            BuildStatus.CANCELLED -> Icons.Filled.Cancel to MaterialTheme.colorScheme.onSurfaceVariant
                            else -> Icons.Filled.PlayCircle to MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        Icon(statusIcon, contentDescription = null, tint = statusColor, modifier = Modifier.size(28.dp))
                        Spacer(Modifier.width(NvSpacing.Sm))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                buildUiState.statusMessage.ifEmpty { stringResource(R.string.build_status_idle) },
                                style = MaterialTheme.typography.titleMedium
                            )
                            if (buildUiState.isBuilding) {
                                Text(
                                    "Build in progress…",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (buildUiState.buildStatus == BuildStatus.FAILED) {
                            NvTonalButton(
                                text = "Ask AI",
                                onClick = onNavigateToAgent,
                                icon = Icons.Filled.Psychology
                            )
                        }
                    }
                    if (buildUiState.isBuilding) {
                        Spacer(Modifier.height(NvSpacing.Md))
                        NvLinearProgress(progress = buildUiState.buildProgress)
                    }
                }
            }
        }

        // Build Controls
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(NvSpacing.Sm)
            ) {
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
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    ),
                    border = BorderStroke(NvBorder.Thin, MaterialTheme.colorScheme.error.copy(alpha = 0.2f))
                ) {
                    Column(modifier = Modifier.padding(NvSpacing.Md)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(NvSpacing.Sm))
                            Text("Build Errors", style = MaterialTheme.typography.titleSmall)
                        }
                        Spacer(Modifier.height(NvSpacing.Sm))
                        SelectionContainer {
                            Text(
                                buildUiState.errorSummary!!,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }
        }

        // Compatibility warnings
        if (buildUiState.compatibilityWarnings.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = BuildBuddyThemeExtended.colors.warningContainer,
                        contentColor = BuildBuddyThemeExtended.colors.warning
                    ),
                    border = BorderStroke(NvBorder.Thin, BuildBuddyThemeExtended.colors.warning.copy(alpha = 0.2f))
                ) {
                    Column(modifier = Modifier.padding(NvSpacing.Md)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Warning, contentDescription = null, tint = BuildBuddyThemeExtended.colors.warning, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(NvSpacing.Sm))
                            Text("Compatibility Warnings", style = MaterialTheme.typography.titleSmall)
                        }
                        Spacer(Modifier.height(NvSpacing.Sm))
                        buildUiState.compatibilityWarnings.forEach { warning ->
                            Text("• $warning", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        // Artifacts Section
        if (artifactsUiState.artifacts.isNotEmpty()) {
            item {
                SectionHeader("APK Artifacts", showArtifacts) { showArtifacts = !showArtifacts }
            }
            item {
                AnimatedVisibility(visible = showArtifacts) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(NvSpacing.Md),
                        contentPadding = PaddingValues(vertical = NvSpacing.Xs)
                    ) {
                        items(artifactsUiState.artifacts, key = { it.id }) { artifact ->
                            ArtifactCardSmall(
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

        // Build Logs Section
        if (buildUiState.logEntries.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SectionHeader("Build Logs", showLogs, modifier = Modifier.weight(1f)) { showLogs = !showLogs }
                    if (showLogs) {
                        TextButton(
                            onClick = {
                                val logText = buildUiState.logEntries.joinToString("\n") { entry ->
                                    "[${dateFormat.format(Date(entry.timestamp))}] ${entry.level.name.first()} ${entry.message}"
                                }
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Build Logs", logText))
                                Toast.makeText(context, "Logs copied", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(NvSpacing.Xs))
                            Text("Copy", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
            item {
                AnimatedVisibility(visible = showLogs) {
                    Surface(
                        shape = NvShapes.medium,
                        color = BuildBuddyThemeExtended.colors.editorBackground,
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(NvBorder.Hairline, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    ) {
                        SelectionContainer {
                            Column(
                                modifier = Modifier
                                    .padding(NvSpacing.Sm)
                                    .heightIn(max = 400.dp)
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
                                        color = color,
                                        softWrap = false
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Build History Section
        if (buildUiState.buildHistory.isNotEmpty()) {
            item {
                SectionHeader("Build History", showHistory) { showHistory = !showHistory }
            }
            item {
                AnimatedVisibility(visible = showHistory) {
                    Column(verticalArrangement = Arrangement.spacedBy(NvSpacing.Xs)) {
                        buildUiState.buildHistory.take(5).forEach { record ->
                            NvCard(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.padding(NvSpacing.Md),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val (statusColor, statusIcon) = when (record.status) {
                                        BuildStatus.SUCCESS -> BuildBuddyThemeExtended.colors.success to Icons.Filled.CheckCircle
                                        BuildStatus.FAILED -> MaterialTheme.colorScheme.error to Icons.Filled.Error
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant to Icons.Filled.History
                                    }
                                    Icon(statusIcon, contentDescription = null, tint = statusColor, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(NvSpacing.Sm))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(record.status.displayName, style = MaterialTheme.typography.labelLarge, color = statusColor)
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
private fun SectionHeader(
    title: String,
    expanded: Boolean,
    modifier: Modifier = Modifier,
    onToggle: () -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = NvSpacing.Sm),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        IconButton(onClick = onToggle) {
            Icon(
                if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ArtifactCardSmall(
    artifact: BuildArtifact,
    onInstall: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    NvCard(
        modifier = Modifier.width(260.dp)
    ) {
        Column(modifier = Modifier.padding(NvSpacing.Md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(32.dp),
                    shape = NvShapes.small,
                    color = BuildBuddyThemeExtended.colors.successContainer.copy(alpha = 0.3f)
                ) {
                    Icon(
                        Icons.Filled.Android,
                        contentDescription = null,
                        tint = BuildBuddyThemeExtended.colors.success,
                        modifier = Modifier.padding(NvSpacing.Xs)
                    )
                }
                Spacer(Modifier.width(NvSpacing.Sm))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        artifact.fileName,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        artifact.packageName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                }
            }

            Spacer(Modifier.height(NvSpacing.Md))

            Row(horizontalArrangement = Arrangement.spacedBy(NvSpacing.Md)) {
                Text(
                    com.build.buddyai.core.common.FileUtils.formatFileSize(artifact.sizeBytes),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "v${artifact.versionName}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(NvSpacing.Md))

            Row(horizontalArrangement = Arrangement.spacedBy(NvSpacing.Sm)) {
                NvTonalButton(
                    text = "Install",
                    onClick = onInstall,
                    icon = Icons.Filled.InstallMobile,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onShare,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
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
