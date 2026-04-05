package com.build.buddyai.feature.build

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.build.buddyai.core.common.ArtifactLauncher
import com.build.buddyai.core.common.ChangeSetInfo
import com.build.buddyai.core.common.SnapshotManager
import com.build.buddyai.core.designsystem.component.NvCard
import com.build.buddyai.core.designsystem.component.NvEmptyState
import com.build.buddyai.core.designsystem.component.NvFilledButton
import com.build.buddyai.core.designsystem.component.NvLinearProgress
import com.build.buddyai.core.designsystem.component.NvOutlinedButton
import com.build.buddyai.core.designsystem.component.NvTextButton
import com.build.buddyai.core.designsystem.theme.BuildBuddyThemeExtended
import com.build.buddyai.core.designsystem.theme.NvBorder
import com.build.buddyai.core.designsystem.theme.NvShapes
import com.build.buddyai.core.designsystem.theme.NvSpacing
import com.build.buddyai.core.model.BuildArtifact
import com.build.buddyai.core.model.BuildRecord
import com.build.buddyai.core.model.BuildStatus
import com.build.buddyai.core.model.LogLevel
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun BuildWorkspaceScreen(
    projectId: String,
    onNavigateToAgent: () -> Unit
) {
    val viewModel: BuildViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val longFormat = remember { SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()) }

    LaunchedEffect(projectId) {
        viewModel.initialize(projectId)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(NvSpacing.Md),
        verticalArrangement = Arrangement.spacedBy(NvSpacing.Md)
    ) {
        item {
            BuildStatusCard(
                uiState = uiState,
                onStartBuild = viewModel::startBuild,
                onCancelBuild = viewModel::cancelBuild,
                onCleanBuild = viewModel::cleanBuild
            )
        }

        item {
            LogConsolePane(
                entries = uiState.logEntries,
                formatter = timeFormat
            )
        }

        if (uiState.problems.isNotEmpty()) {
            item {
                ProblemsPane(
                    problems = uiState.problems,
                    errorSummary = uiState.errorSummary
                )
            }
        }

        uiState.latestArtifact?.let { artifact ->
            item {
                ArtifactDetailsPane(
                    artifact = artifact,
                    onInstall = { ArtifactLauncher.install(context, artifact) },
                    onShare = { ArtifactLauncher.share(context, artifact) }
                )
            }
        }

        if (uiState.buildHistory.isNotEmpty() || uiState.logEntries.isNotEmpty()) {
            item {
                BuildTimelinePane(
                    records = uiState.buildHistory.take(8),
                    onCopyLogs = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val payload = uiState.logEntries.joinToString("\n") { entry ->
                            "[${timeFormat.format(Date(entry.timestamp))}] ${entry.level.name.first()} ${entry.message}"
                        }
                        clipboard.setPrimaryClip(ClipData.newPlainText("Build logs", payload))
                        Toast.makeText(context, "Build logs copied", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }

        if (uiState.snapshots.isNotEmpty() || uiState.changeSets.isNotEmpty()) {
            item {
                RestorePointBrowser(
                    snapshots = uiState.snapshots,
                    changeSets = uiState.changeSets,
                    longFormat = longFormat,
                    onRestoreSnapshot = viewModel::restoreSnapshot,
                    onRestoreChangeSet = viewModel::restoreChangeSet
                )
            }
        }

        if (!uiState.isBuilding && uiState.buildHistory.isEmpty() && uiState.latestArtifact == null && uiState.problems.isEmpty() && uiState.logEntries.isEmpty()) {
            item {
                NvEmptyState(
                    icon = Icons.Filled.Build,
                    title = "No builds yet",
                    subtitle = "Run the build pipeline to generate an artifact, inspect logs, and create restore points.",
                    modifier = Modifier.padding(vertical = 48.dp)
                )
            }
        }
    }
}

@Composable
private fun BuildStatusCard(
    uiState: BuildUiState,
    onStartBuild: () -> Unit,
    onCancelBuild: () -> Unit,
    onCleanBuild: () -> Unit
) {
    val (icon, tint) = when (uiState.buildStatus) {
        BuildStatus.SUCCESS -> Icons.Filled.CheckCircle to BuildBuddyThemeExtended.colors.success
        BuildStatus.FAILED -> Icons.Filled.ErrorOutline to MaterialTheme.colorScheme.error
        BuildStatus.CANCELLED -> Icons.Filled.WarningAmber to MaterialTheme.colorScheme.onSurfaceVariant
        BuildStatus.BUILDING -> Icons.Filled.Build to MaterialTheme.colorScheme.primary
        else -> Icons.Filled.History to MaterialTheme.colorScheme.onSurfaceVariant
    }

    NvCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(NvSpacing.Md),
            verticalArrangement = Arrangement.spacedBy(NvSpacing.Md)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(26.dp))
                Spacer(Modifier.width(NvSpacing.Sm))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = when {
                            uiState.statusMessage.isNotBlank() -> uiState.statusMessage
                            uiState.buildStatus == BuildStatus.NONE -> "Build workspace ready"
                            else -> uiState.buildStatus.displayName
                        },
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = when {
                            uiState.isBuilding -> "Build logs stream below in real time."
                            uiState.buildStatus == BuildStatus.SUCCESS -> "Artifact details, install, and share actions are ready below."
                            uiState.buildStatus == BuildStatus.FAILED -> "Checks and logs are ready for diagnosis."
                            else -> "Run a build whenever you want a fresh artifact."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (uiState.isBuilding) {
                NvLinearProgress(
                    progress = uiState.buildProgress,
                    label = "${(uiState.buildProgress * 100).toInt()}% complete"
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(NvSpacing.Sm)
            ) {
                if (uiState.isBuilding) {
                    NvOutlinedButton(
                        text = "Cancel build",
                        onClick = onCancelBuild,
                        icon = Icons.Filled.Stop,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    NvFilledButton(
                        text = "Run build",
                        onClick = onStartBuild,
                        icon = Icons.Filled.Build,
                        modifier = Modifier.weight(1f)
                    )
                    NvOutlinedButton(
                        text = "Clean outputs",
                        onClick = onCleanBuild,
                        icon = Icons.Filled.CleaningServices,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun ProblemsPane(
    problems: List<String>,
    errorSummary: String?
) {
    val blocking = !errorSummary.isNullOrBlank()
    val containerColor = if (blocking) MaterialTheme.colorScheme.errorContainer else BuildBuddyThemeExtended.colors.warningContainer
    val contentColor = if (blocking) MaterialTheme.colorScheme.onErrorContainer else BuildBuddyThemeExtended.colors.onWarning
    val accent = if (blocking) MaterialTheme.colorScheme.error else BuildBuddyThemeExtended.colors.warning
    val subtitle = if (blocking) {
        "Blocking checks and build failures appear here."
    } else {
        "Non-blocking compatibility notes and pre-build checks appear here."
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        border = BorderStroke(NvBorder.Thin, accent.copy(alpha = 0.2f))
    ) {
        Column(
            modifier = Modifier.padding(NvSpacing.Md),
            verticalArrangement = Arrangement.spacedBy(NvSpacing.Sm)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = accent)
                Spacer(Modifier.width(NvSpacing.Sm))
                Column(modifier = Modifier.weight(1f)) {
                    Text(if (blocking) "Problems" else "Checks", style = MaterialTheme.typography.titleSmall)
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor.copy(alpha = 0.8f)
                    )
                }
            }
            SelectionContainer {
                Column(verticalArrangement = Arrangement.spacedBy(NvSpacing.Xs)) {
                    problems.forEach { problem ->
                        Text("• $problem", style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
                    }
                }
            }
        }
    }
}

@Composable
private fun ArtifactDetailsPane(
    artifact: BuildArtifact,
    onInstall: () -> Unit,
    onShare: () -> Unit
) {
    NvCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(NvSpacing.Md),
            verticalArrangement = Arrangement.spacedBy(NvSpacing.Sm)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.InstallMobile, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(NvSpacing.Sm))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Artifact details", style = MaterialTheme.typography.titleSmall)
                    Text(artifact.fileName, style = MaterialTheme.typography.bodyMedium)
                }
            }
            ArtifactMetadataRow(label = "Package", value = artifact.packageName)
            ArtifactMetadataRow(label = "Version", value = "${artifact.versionName} (${artifact.versionCode})")
            ArtifactMetadataRow(label = "SDK", value = "min ${artifact.minSdk} • target ${artifact.targetSdk}")
            ArtifactMetadataRow(label = "Size", value = formatBytes(artifact.sizeBytes))
            ArtifactMetadataRow(label = "Created", value = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(artifact.createdAt)))
            Row(horizontalArrangement = Arrangement.spacedBy(NvSpacing.Sm)) {
                NvFilledButton(text = "Install", onClick = onInstall, icon = Icons.Filled.InstallMobile, modifier = Modifier.weight(1f))
                NvOutlinedButton(text = "Share", onClick = onShare, icon = Icons.Filled.Share, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ArtifactMetadataRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun BuildTimelinePane(
    records: List<BuildRecord>,
    onCopyLogs: (() -> Unit)
) {
    NvCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(NvSpacing.Md),
            verticalArrangement = Arrangement.spacedBy(NvSpacing.Sm)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.History, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(NvSpacing.Sm))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Build timeline", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Recent build runs with status and duration.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                NvTextButton(text = "Copy logs", onClick = onCopyLogs, icon = Icons.Filled.ContentCopy)
            }
            if (records.isEmpty()) {
                Text("No build history yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                records.forEach { record -> TimelineRow(record) }
            }
        }
    }
}

@Composable
private fun TimelineRow(record: BuildRecord) {
    val (statusColor, statusLabel) = when (record.status) {
        BuildStatus.SUCCESS -> BuildBuddyThemeExtended.colors.success to "Success"
        BuildStatus.FAILED -> MaterialTheme.colorScheme.error to "Failed"
        BuildStatus.CANCELLED -> MaterialTheme.colorScheme.onSurfaceVariant to "Cancelled"
        BuildStatus.BUILDING -> MaterialTheme.colorScheme.primary to "Building"
        else -> MaterialTheme.colorScheme.onSurfaceVariant to "Idle"
    }
    val formatter = remember { SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()) }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = NvShapes.medium
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(NvSpacing.Sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(10.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(color = statusColor, shape = NvShapes.small, modifier = Modifier.size(8.dp)) {}
            }
            Spacer(Modifier.width(NvSpacing.Sm))
            Column(modifier = Modifier.weight(1f)) {
                Text(statusLabel, style = MaterialTheme.typography.labelLarge, color = statusColor)
                Text(formatter.format(Date(record.startedAt)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                text = record.durationMs?.let(::formatDuration) ?: "—",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun LogConsolePane(
    entries: List<com.build.buddyai.core.model.BuildLogEntry>,
    formatter: SimpleDateFormat
) {
    NvCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(NvSpacing.Md),
            verticalArrangement = Arrangement.spacedBy(NvSpacing.Sm)
        ) {
            Text("Build log console", style = MaterialTheme.typography.titleSmall)
            Surface(
                shape = NvShapes.medium,
                color = BuildBuddyThemeExtended.colors.editorBackground,
                border = BorderStroke(NvBorder.Hairline, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                SelectionContainer {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 180.dp, max = 420.dp)
                            .horizontalScroll(rememberScrollState())
                            .padding(NvSpacing.Sm),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        if (entries.isEmpty()) {
                            Text(
                                text = "Build logs will stream here once a build starts.",
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            entries.takeLast(160).forEach { entry ->
                                Text(
                                    text = "[${formatter.format(Date(entry.timestamp))}] ${entry.level.name.first()} ${entry.message}",
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                    color = when (entry.level) {
                                        LogLevel.ERROR -> MaterialTheme.colorScheme.error
                                        LogLevel.WARNING -> BuildBuddyThemeExtended.colors.warning
                                        LogLevel.DEBUG -> MaterialTheme.colorScheme.onSurfaceVariant
                                        LogLevel.VERBOSE -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                        LogLevel.INFO -> MaterialTheme.colorScheme.onSurface
                                    },
                                    softWrap = false
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RestorePointBrowser(
    snapshots: List<SnapshotManager.SnapshotInfo>,
    changeSets: List<ChangeSetInfo>,
    longFormat: SimpleDateFormat,
    onRestoreSnapshot: (String) -> Unit,
    onRestoreChangeSet: (String) -> Unit
) {
    NvCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(NvSpacing.Md),
            verticalArrangement = Arrangement.spacedBy(NvSpacing.Md)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Restore, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(NvSpacing.Sm))
                Column {
                    Text("Restore point browser", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Snapshots restore the full project. Change sets roll back only the files touched by an agent pass.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (changeSets.isNotEmpty()) {
                Text("Change sets", style = MaterialTheme.typography.labelLarge)
                changeSets.take(8).forEach { changeSet ->
                    RestorePointRow(
                        title = changeSet.summary.ifBlank { "Agent change set" },
                        subtitle = "${changeSet.changeCount} file change(s) • ${longFormat.format(Date(changeSet.createdAt))}",
                        onRestore = { onRestoreChangeSet(changeSet.path) }
                    )
                }
            }

            if (snapshots.isNotEmpty()) {
                Text("Snapshots", style = MaterialTheme.typography.labelLarge)
                snapshots.take(8).forEach { snapshot ->
                    RestorePointRow(
                        title = snapshot.fileName,
                        subtitle = "${snapshot.label} • ${formatBytes(snapshot.sizeBytes)} • ${longFormat.format(Date(snapshot.createdAt))}",
                        onRestore = { onRestoreSnapshot(snapshot.path) }
                    )
                }
            }
        }
    }
}

@Composable
private fun RestorePointRow(
    title: String,
    subtitle: String,
    onRestore: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = NvShapes.medium
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(NvSpacing.Sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.labelLarge)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            NvTextButton(text = "Restore", onClick = onRestore, icon = Icons.Filled.Restore)
        }
    }
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return if (minutes > 0) "${minutes}m ${seconds}s" else "${seconds}s"
}

private fun formatBytes(sizeBytes: Long): String {
    if (sizeBytes <= 0) return "0 B"
    val units = listOf("B", "KB", "MB", "GB")
    var value = sizeBytes.toDouble()
    var index = 0
    while (value >= 1024 && index < units.lastIndex) {
        value /= 1024
        index++
    }
    return "${DecimalFormat("0.#").format(value)} ${units[index]}"
}
