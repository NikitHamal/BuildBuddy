package com.build.buddyai.feature.build

import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.build.buddyai.core.model.ArtifactFormat
import com.build.buddyai.core.model.BuildStatus
import com.build.buddyai.core.model.BuildVariant
import com.build.buddyai.core.model.LogLevel
import com.build.buddyai.core.model.ProblemSeverity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BuildWorkspaceScreen(
    projectId: String,
    onNavigateToAgent: () -> Unit
) {
    val viewModel: BuildViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val latestArtifact = uiState.latestArtifact
    val provenance = uiState.artifactProvenance
    val dateFormat = remember { SimpleDateFormat("MMM d • HH:mm", Locale.getDefault()) }
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    var alias by remember(uiState.buildProfile.signing?.keyAlias) { mutableStateOf(uiState.buildProfile.signing?.keyAlias.orEmpty()) }
    var storePassword by remember { mutableStateOf("") }
    var keyPassword by remember { mutableStateOf("") }
    var placeholdersText by remember(uiState.buildProfile.manifestPlaceholders) {
        mutableStateOf(uiState.buildProfile.manifestPlaceholders.entries.joinToString("\n") { "${it.key}=${it.value}" })
    }

    val keystorePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            viewModel.importKeystore(
                uri = uri,
                displayName = null,
                keyAlias = alias,
                storePassword = storePassword,
                keyPassword = keyPassword
            )
        }
    }

    LaunchedEffect(projectId) { viewModel.initialize(projectId) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            BuildHeroCard(
                status = uiState.buildStatus,
                message = uiState.statusMessage,
                progress = uiState.buildProgress,
                isBuilding = uiState.isBuilding,
                onStart = viewModel::startBuild,
                onCancel = viewModel::cancelBuild,
                onClean = viewModel::cleanBuild,
                onAskAi = onNavigateToAgent
            )
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Build profile", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Use variant-aware profiles, artifact format controls, placeholders, and release signing. APK install works on-device. AAB is exposed as a product-grade profile target and will fail fast until the bundle toolchain is bundled locally.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        BuildVariant.entries.forEach { variant ->
                            FilterChip(
                                selected = uiState.buildProfile.variant == variant,
                                onClick = { viewModel.updateVariant(variant) },
                                label = { Text(variant.displayName) }
                            )
                        }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ArtifactFormat.entries.forEach { format ->
                            FilterChip(
                                selected = uiState.buildProfile.artifactFormat == format,
                                onClick = { viewModel.updateArtifactFormat(format) },
                                label = { Text(format.displayName) }
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Install after successful build", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                if (uiState.buildProfile.artifactFormat == ArtifactFormat.APK) "Opens the Android installer as soon as the APK is ready."
                                else "AAB files are not installable directly, so install-after-build is ignored for bundles.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = uiState.buildProfile.installAfterBuild,
                            onCheckedChange = viewModel::updateInstallAfterBuild
                        )
                    }
                    HorizontalDivider()
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = uiState.buildProfile.flavorName,
                            onValueChange = viewModel::updateFlavorName,
                            modifier = Modifier.weight(1f),
                            label = { Text("Flavor") },
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = uiState.buildProfile.applicationIdSuffix,
                            onValueChange = viewModel::updateApplicationIdSuffix,
                            modifier = Modifier.weight(1f),
                            label = { Text("App ID suffix") },
                            singleLine = true
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = uiState.buildProfile.versionNameSuffix,
                            onValueChange = viewModel::updateVersionNameSuffix,
                            modifier = Modifier.weight(1f),
                            label = { Text("Version name suffix") },
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = uiState.buildProfile.versionCodeOverride?.toString().orEmpty(),
                            onValueChange = viewModel::updateVersionCodeOverride,
                            modifier = Modifier.weight(1f),
                            label = { Text("Version code override") },
                            singleLine = true
                        )
                    }
                    OutlinedTextField(
                        value = uiState.buildProfile.versionNameOverride.orEmpty(),
                        onValueChange = viewModel::updateVersionNameOverride,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Version name override") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = placeholdersText,
                        onValueChange = {
                            placeholdersText = it
                            viewModel.updateManifestPlaceholders(it)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Manifest placeholders (key=value)") },
                        minLines = 3,
                        maxLines = 6,
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                    )
                    HorizontalDivider()
                    Text("Release signing", style = MaterialTheme.typography.titleSmall)
                    if (uiState.buildProfile.signing != null) {
                        Text(
                            "Keystore: ${uiState.buildProfile.signing.keystoreFileName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedTextField(
                        value = alias,
                        onValueChange = {
                            alias = it
                            viewModel.updateSigningAlias(it)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Key alias") },
                        singleLine = true
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = storePassword,
                            onValueChange = { storePassword = it },
                            modifier = Modifier.weight(1f),
                            label = { Text("Store password") },
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = keyPassword,
                            onValueChange = { keyPassword = it },
                            modifier = Modifier.weight(1f),
                            label = { Text("Key password") },
                            singleLine = true
                        )
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { keystorePicker.launch(arrayOf("*/*")) }) {
                            Icon(Icons.Filled.FileUpload, contentDescription = null)
                            Spacer(Modifier.size(8.dp))
                            Text("Import keystore")
                        }
                        if (uiState.buildProfile.signing != null) {
                            TextButton(onClick = viewModel::clearSigning) {
                                Icon(Icons.Filled.Delete, contentDescription = null)
                                Spacer(Modifier.size(8.dp))
                                Text("Clear")
                            }
                        }
                    }
                }
            }
        }

        if (latestArtifact != null) {
            item {
                Card {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Artifact details", style = MaterialTheme.typography.titleMedium)
                        Text(latestArtifact.fileName, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${Formatter.formatShortFileSize(context, latestArtifact.sizeBytes)} • ${latestArtifact.packageName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = {
                                when (val result = viewModel.installLatestArtifact()) {
                                    is com.build.buddyai.core.common.BuildArtifactInstaller.InstallResult.Started -> Toast.makeText(context, "Installer opened", Toast.LENGTH_SHORT).show()
                                    is com.build.buddyai.core.common.BuildArtifactInstaller.InstallResult.PermissionRequired -> Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                                    is com.build.buddyai.core.common.BuildArtifactInstaller.InstallResult.Error -> Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                                }
                            }) {
                                Icon(Icons.Filled.InstallMobile, contentDescription = null)
                                Spacer(Modifier.size(8.dp))
                                Text("Install")
                            }
                            OutlinedButton(onClick = {
                                viewModel.shareLatestArtifact().onFailure {
                                    Toast.makeText(context, it.message ?: "Unable to share artifact", Toast.LENGTH_LONG).show()
                                }
                            }) {
                                Icon(Icons.Filled.Share, contentDescription = null)
                                Spacer(Modifier.size(8.dp))
                                Text("Share")
                            }
                        }
                    }
                }
            }
        }

        if (provenance != null) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Artifact provenance", style = MaterialTheme.typography.titleMedium)
                        Text(provenance.artifactName, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Profile: ${provenance.buildProfile.variant.displayName} • ${provenance.buildProfile.artifactFormat.displayName} • Signer: ${provenance.signerAlias ?: "bundled debug"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "Change sets: ${provenance.changeSetIds.joinToString().ifBlank { "none recorded" }}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "Validation: ${provenance.validationSummary}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        if (uiState.problems.isNotEmpty()) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Problems pane", style = MaterialTheme.typography.titleMedium)
                        uiState.problems.take(12).forEach { problem ->
                            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Icon(
                                    imageVector = when (problem.severity) {
                                        ProblemSeverity.ERROR -> Icons.Filled.Error
                                        ProblemSeverity.WARNING -> Icons.Filled.Warning
                                        ProblemSeverity.INFO -> Icons.Filled.Build
                                    },
                                    contentDescription = null,
                                    tint = when (problem.severity) {
                                        ProblemSeverity.ERROR -> MaterialTheme.colorScheme.error
                                        ProblemSeverity.WARNING -> MaterialTheme.colorScheme.tertiary
                                        ProblemSeverity.INFO -> MaterialTheme.colorScheme.primary
                                    },
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                                Column {
                                    Text(problem.title, style = MaterialTheme.typography.bodyMedium)
                                    Text(problem.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    val location = listOfNotNull(problem.filePath, problem.lineNumber?.let { "line $it" }).joinToString(" • ")
                                    if (location.isNotBlank()) {
                                        Text(location, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (uiState.timeline.isNotEmpty()) {
            item {
                Card {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Build timeline", style = MaterialTheme.typography.titleMedium)
                        uiState.timeline.forEach { entry ->
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                                AssistChip(onClick = {}, label = { Text(entry.label) })
                                Column {
                                    Text(entry.detail, style = MaterialTheme.typography.bodySmall)
                                    Text(dateFormat.format(Date(entry.timestamp)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }

        if (uiState.restorePoints.isNotEmpty()) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Restore points", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Full snapshots live here. Agent change-set rollback is tracked separately and recorded in artifact provenance.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        uiState.restorePoints.take(8).forEach { point ->
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(point.fileName, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        "${point.label} • ${Formatter.formatShortFileSize(context, point.sizeBytes)} • ${dateFormat.format(Date(point.createdAt))}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                TextButton(onClick = { viewModel.restorePoint(point) }) {
                                    Icon(Icons.Filled.Restore, contentDescription = null)
                                    Spacer(Modifier.size(6.dp))
                                    Text("Restore")
                                }
                                TextButton(onClick = { viewModel.deleteRestorePoint(point) }) {
                                    Icon(Icons.Filled.Delete, contentDescription = null)
                                }
                            }
                        }
                    }
                }
            }
        }

        if (uiState.logEntries.isNotEmpty()) {
            item {
                Card {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Build logs", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            TextButton(onClick = {
                                val text = uiState.logEntries.joinToString("\n") {
                                    "[${timeFormat.format(Date(it.timestamp))}] ${it.level.name.first()} ${it.message}"
                                }
                                val clip = android.content.ClipData.newPlainText("Build logs", text)
                                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Logs copied", Toast.LENGTH_SHORT).show()
                            }) {
                                Icon(Icons.Filled.ContentCopy, contentDescription = null)
                                Spacer(Modifier.size(6.dp))
                                Text("Copy")
                            }
                        }
                        uiState.logEntries.takeLast(80).forEach { entry ->
                            Text(
                                text = "[${timeFormat.format(Date(entry.timestamp))}] ${entry.level.name.first()} ${entry.message}",
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                color = when (entry.level) {
                                    LogLevel.ERROR -> MaterialTheme.colorScheme.error
                                    LogLevel.WARNING -> MaterialTheme.colorScheme.tertiary
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BuildHeroCard(
    status: BuildStatus,
    message: String,
    progress: Float,
    isBuilding: Boolean,
    onStart: () -> Unit,
    onCancel: () -> Unit,
    onClean: () -> Unit,
    onAskAi: () -> Unit
) {
    Card {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = when (status) {
                        BuildStatus.SUCCESS -> Icons.Filled.Build
                        BuildStatus.FAILED -> Icons.Filled.Error
                        BuildStatus.CANCELLED -> Icons.Filled.Stop
                        BuildStatus.BUILDING -> Icons.Filled.Build
                        BuildStatus.NONE -> Icons.Filled.Build
                    },
                    contentDescription = null,
                    tint = when (status) {
                        BuildStatus.SUCCESS -> MaterialTheme.colorScheme.primary
                        BuildStatus.FAILED -> MaterialTheme.colorScheme.error
                        BuildStatus.CANCELLED -> MaterialTheme.colorScheme.tertiary
                        BuildStatus.BUILDING -> MaterialTheme.colorScheme.primary
                        BuildStatus.NONE -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                Spacer(Modifier.size(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(message.ifBlank { "Ready to build" }, style = MaterialTheme.typography.titleMedium)
                    Text(
                        when (status) {
                            BuildStatus.SUCCESS -> "Latest build passed validation."
                            BuildStatus.FAILED -> "Build failed. Review the problems pane or send it to the agent."
                            BuildStatus.CANCELLED -> "Build was cancelled before completion."
                            BuildStatus.BUILDING -> "On-device compiler and packager are running now."
                            BuildStatus.NONE -> "No build has been run yet for this project."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (status == BuildStatus.FAILED) {
                    AssistChip(
                        onClick = onAskAi,
                        label = { Text("Fix with agent") },
                        leadingIcon = { Icon(Icons.Filled.Psychology, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                }
            }
            if (isBuilding) {
                LinearProgressIndicator(progress = progress, modifier = Modifier.fillMaxWidth())
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isBuilding) {
                    OutlinedButton(onClick = onCancel) {
                        Icon(Icons.Filled.Stop, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text("Cancel")
                    }
                } else {
                    OutlinedButton(onClick = onStart) {
                        Icon(Icons.Filled.Build, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text("Start build")
                    }
                    OutlinedButton(onClick = onClean) {
                        Icon(Icons.Filled.CleaningServices, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text("Clean outputs")
                    }
                }
            }
        }
    }
}
