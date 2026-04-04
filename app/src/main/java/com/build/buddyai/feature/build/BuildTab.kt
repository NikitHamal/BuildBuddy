package com.build.buddyai.feature.build

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import com.build.buddyai.core.model.BuildStatus
import com.build.buddyai.core.model.LogLevel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun BuildTab(
    projectId: String,
    onBuildComplete: () -> Unit,
    viewModel: BuildViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val dateFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    LaunchedEffect(projectId) { viewModel.initialize(projectId) }
    LaunchedEffect(uiState.buildStatus) {
        if (uiState.buildStatus == BuildStatus.SUCCESS) onBuildComplete()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(NvSpacing.Md),
        verticalArrangement = Arrangement.spacedBy(NvSpacing.Sm)
    ) {
        // Build controls
        item {
            NvCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(NvSpacing.Md)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val statusIcon = when (uiState.buildStatus) {
                            BuildStatus.SUCCESS -> Icons.Filled.CheckCircle
                            BuildStatus.FAILED -> Icons.Filled.Error
                            BuildStatus.BUILDING -> Icons.Filled.HourglassTop
                            BuildStatus.CANCELLED -> Icons.Filled.Cancel
                            else -> Icons.Filled.PlayCircle
                        }
                        val statusColor = when (uiState.buildStatus) {
                            BuildStatus.SUCCESS -> BuildBuddyThemeExtended.colors.success
                            BuildStatus.FAILED -> MaterialTheme.colorScheme.error
                            BuildStatus.BUILDING -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        Icon(statusIcon, contentDescription = null, tint = statusColor, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(NvSpacing.Xs))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(uiState.statusMessage.ifEmpty { stringResource(R.string.build_status_idle) }, style = MaterialTheme.typography.titleSmall)
                        }
                    }

                    if (uiState.isBuilding) {
                        Spacer(Modifier.height(NvSpacing.Xs))
                        NvLinearProgress(progress = uiState.buildProgress)
                    }

                    Spacer(Modifier.height(NvSpacing.Sm))

                    Row(horizontalArrangement = Arrangement.spacedBy(NvSpacing.Xs)) {
                        if (uiState.isBuilding) {
                            NvOutlinedButton(
                                text = stringResource(R.string.build_cancel),
                                onClick = { viewModel.cancelBuild() },
                                icon = Icons.Filled.Stop
                            )
                        } else {
                            NvFilledButton(
                                text = stringResource(R.string.build_start),
                                onClick = { viewModel.startBuild() },
                                icon = Icons.Filled.PlayArrow
                            )
                            NvOutlinedButton(
                                text = stringResource(R.string.build_clean),
                                onClick = { viewModel.cleanBuild() },
                                icon = Icons.Filled.CleaningServices
                            )
                        }
                    }
                }
            }
        }

        // Error summary
        if (uiState.errorSummary != null) {
            item {
                NvCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(NvSpacing.Md)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(NvSpacing.Xs))
                            Text(stringResource(R.string.build_error_summary), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.error)
                        }
                        Spacer(Modifier.height(NvSpacing.Xs))
                        Text(
                            uiState.errorSummary!!,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.height(NvSpacing.Sm))
                        NvFilledButton(
                            text = stringResource(R.string.build_ask_ai_fix),
                            onClick = {},
                            icon = Icons.Filled.Psychology
                        )
                    }
                }
            }
        }

        // Compatibility warnings
        if (uiState.compatibilityWarnings.isNotEmpty()) {
            item {
                NvCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(NvSpacing.Md)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Warning, contentDescription = null, tint = BuildBuddyThemeExtended.colors.warning, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(NvSpacing.Xs))
                            Text(stringResource(R.string.build_compatibility_warning), style = MaterialTheme.typography.titleSmall)
                        }
                        Spacer(Modifier.height(NvSpacing.Xs))
                        uiState.compatibilityWarnings.forEach { warning ->
                            Text("• $warning", style = MaterialTheme.typography.bodySmall, color = BuildBuddyThemeExtended.colors.warning)
                        }
                    }
                }
            }
        }

        // Build logs
        if (uiState.logEntries.isNotEmpty()) {
            item {
                Text(stringResource(R.string.build_view_logs), style = MaterialTheme.typography.titleSmall)
            }
            item {
                Surface(
                    shape = NvShapes.small,
                    color = BuildBuddyThemeExtended.colors.editorBackground,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .padding(NvSpacing.Xs)
                            .horizontalScroll(rememberScrollState())
                    ) {
                        uiState.logEntries.forEach { entry ->
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

        // Build history
        if (uiState.buildHistory.isNotEmpty()) {
            item {
                Text(stringResource(R.string.build_history), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = NvSpacing.Xs))
            }
            items(uiState.buildHistory.take(10)) { record ->
                val historyDateFormat = remember { SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()) }
                NvCard(modifier = Modifier.fillMaxWidth()) {
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
                                stringResource(R.string.build_duration, formatDuration(record.durationMs)),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // Empty state
        if (uiState.buildHistory.isEmpty() && !uiState.isBuilding) {
            item {
                NvEmptyState(
                    icon = Icons.Filled.Build,
                    title = stringResource(R.string.build_no_builds),
                    subtitle = stringResource(R.string.build_no_builds_subtitle)
                )
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
