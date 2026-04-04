package com.build.buddyai.feature.artifacts

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.build.buddyai.R
import com.build.buddyai.core.common.FileUtils
import com.build.buddyai.core.designsystem.component.*
import com.build.buddyai.core.designsystem.theme.*
import com.build.buddyai.core.model.BuildArtifact
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ArtifactsTab(
    projectId: String
) {
    val viewModel: ArtifactsViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showDeleteDialog by remember { mutableStateOf<BuildArtifact?>(null) }

    LaunchedEffect(projectId) { viewModel.loadArtifacts(projectId) }

    if (uiState.artifacts.isEmpty()) {
        NvEmptyState(
            icon = Icons.Filled.Inventory2,
            title = stringResource(R.string.artifacts_empty_title),
            subtitle = stringResource(R.string.artifacts_empty_subtitle),
            modifier = Modifier.fillMaxSize()
        )
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(NvSpacing.Md),
            verticalArrangement = Arrangement.spacedBy(NvSpacing.Sm)
        ) {
            items(uiState.artifacts, key = { it.id }) { artifact ->
                ArtifactItem(
                    artifact = artifact,
                    onInstall = { viewModel.installArtifact(context, artifact) },
                    onShare = { viewModel.shareArtifact(context, artifact) },
                    onDelete = { showDeleteDialog = artifact }
                )
            }
        }
    }

    showDeleteDialog?.let { artifact ->
        NvAlertDialog(
            title = stringResource(R.string.artifacts_delete),
            message = "Delete ${artifact.fileName}?",
            confirmText = stringResource(R.string.action_delete),
            onConfirm = { viewModel.deleteArtifact(artifact); showDeleteDialog = null },
            onDismiss = { showDeleteDialog = null },
            isDestructive = true
        )
    }
}

@Composable
private fun ArtifactItem(
    artifact: BuildArtifact,
    onInstall: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy · HH:mm", Locale.getDefault()) }

    NvCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(NvSpacing.Md)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Surface(
                    Modifier.size(48.dp),
                    shape = NvShapes.medium,
                    color = BuildBuddyThemeExtended.colors.successContainer.copy(alpha = 0.5f),
                    border = BorderStroke(NvBorder.Hairline, BuildBuddyThemeExtended.colors.success.copy(alpha = 0.2f))
                ) {
                    Icon(
                        Icons.Filled.Android,
                        contentDescription = null,
                        modifier = Modifier.padding(NvSpacing.Sm),
                        tint = BuildBuddyThemeExtended.colors.success
                    )
                }
                Spacer(Modifier.width(NvSpacing.Md))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        artifact.fileName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        artifact.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = stringResource(R.string.artifacts_delete),
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(Modifier.height(NvSpacing.Md))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(NvSpacing.Sm)
            ) {
                InfoBadge(Icons.Filled.Storage, FileUtils.formatFileSize(artifact.sizeBytes))
                InfoBadge(Icons.Filled.Tag, artifact.versionName)
                InfoBadge(Icons.Filled.CalendarToday, dateFormat.format(Date(artifact.createdAt)))
            }

            Spacer(Modifier.height(NvSpacing.Lg))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(NvSpacing.Sm)
            ) {
                NvFilledButton(
                    text = stringResource(R.string.artifacts_install),
                    onClick = onInstall,
                    icon = Icons.Filled.InstallMobile,
                    modifier = Modifier.weight(1f)
                )
                NvOutlinedButton(
                    text = stringResource(R.string.artifacts_share),
                    onClick = onShare,
                    icon = Icons.Filled.Share,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun InfoBadge(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(12.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

