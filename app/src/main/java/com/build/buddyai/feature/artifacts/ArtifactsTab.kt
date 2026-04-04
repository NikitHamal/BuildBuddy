package com.build.buddyai.feature.artifacts

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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
    projectId: String,
    viewModel: ArtifactsViewModel = hiltViewModel()
) {
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
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault()) }

    NvCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(NvSpacing.Md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    Modifier.size(40.dp),
                    shape = NvShapes.small,
                    color = BuildBuddyThemeExtended.colors.successContainer
                ) {
                    Icon(
                        Icons.Filled.Android,
                        contentDescription = null,
                        modifier = Modifier.padding(NvSpacing.Xs),
                        tint = BuildBuddyThemeExtended.colors.success
                    )
                }
                Spacer(Modifier.width(NvSpacing.Sm))
                Column(modifier = Modifier.weight(1f)) {
                    Text(artifact.fileName, style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(R.string.artifacts_package, artifact.packageName), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(Modifier.height(NvSpacing.Xs))

            Row(horizontalArrangement = Arrangement.spacedBy(NvSpacing.Md)) {
                Text(stringResource(R.string.artifacts_size, FileUtils.formatFileSize(artifact.sizeBytes)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.artifacts_version, artifact.versionName), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.artifacts_built_at, dateFormat.format(Date(artifact.createdAt))), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(Modifier.height(NvSpacing.Sm))

            Row(horizontalArrangement = Arrangement.spacedBy(NvSpacing.Xs)) {
                NvFilledButton(text = stringResource(R.string.artifacts_install), onClick = onInstall, icon = Icons.Filled.InstallMobile)
                NvOutlinedButton(text = stringResource(R.string.artifacts_share), onClick = onShare, icon = Icons.Filled.Share)
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.artifacts_delete), tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
