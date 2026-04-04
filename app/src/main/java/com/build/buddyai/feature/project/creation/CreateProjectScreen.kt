package com.build.buddyai.feature.project.creation

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.build.buddyai.R
import com.build.buddyai.core.designsystem.component.*
import com.build.buddyai.core.designsystem.theme.*
import com.build.buddyai.core.model.*

@Composable
fun CreateProjectScreen(
    onProjectCreated: (String) -> Unit,
    onBack: () -> Unit
) {
    val viewModel: CreateProjectViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.createdProjectId) {
        uiState.createdProjectId?.let { onProjectCreated(it) }
    }

    Scaffold(
        topBar = {
            NvTopBar(
                title = stringResource(R.string.create_project_title),
                navigationIcon = { NvBackButton(onBack) },
                subtitle = when (uiState.step) {
                    0 -> "Configure"
                    1 -> "Template"
                    2 -> "Review"
                    else -> null
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Step indicator
            LinearProgressIndicator(
                progress = { (uiState.step + 1) / 3f },
                modifier = Modifier.fillMaxWidth().height(3.dp),
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
            )

            // Content
            AnimatedContent(
                targetState = uiState.step,
                modifier = Modifier.weight(1f),
                transitionSpec = {
                    if (targetState > initialState) {
                        slideInHorizontally { it / 3 } + fadeIn() togetherWith slideOutHorizontally { -it / 3 } + fadeOut()
                    } else {
                        slideInHorizontally { -it / 3 } + fadeIn() togetherWith slideOutHorizontally { it / 3 } + fadeOut()
                    }
                },
                label = "step"
            ) { step ->
                when (step) {
                    0 -> ConfigureStep(uiState, viewModel)
                    1 -> TemplateStep(uiState, viewModel)
                    2 -> ReviewStep(uiState)
                }
            }

            // Bottom navigation
            Surface(
                tonalElevation = NvElevation.Sm,
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
            ) {
                Row(
                    modifier = Modifier.padding(NvSpacing.Md),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (uiState.step > 0) {
                        NvOutlinedButton(text = stringResource(R.string.action_back), onClick = { viewModel.previousStep() })
                    } else {
                        Spacer(Modifier.width(1.dp))
                    }

                    if (uiState.step < 2) {
                        NvFilledButton(text = stringResource(R.string.onboarding_next), onClick = { viewModel.nextStep() })
                    } else {
                        NvFilledButton(
                            text = if (uiState.isCreating) stringResource(R.string.loading) else stringResource(R.string.create_project_button),
                            onClick = { viewModel.createProject() },
                            enabled = !uiState.isCreating,
                            icon = Icons.Filled.RocketLaunch
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfigureStep(uiState: CreateProjectUiState, viewModel: CreateProjectViewModel) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(NvSpacing.Md),
        verticalArrangement = Arrangement.spacedBy(NvSpacing.Sm)
    ) {
        item {
            NvTextField(
                value = uiState.appName,
                onValueChange = viewModel::updateAppName,
                label = stringResource(R.string.create_app_name),
                placeholder = stringResource(R.string.create_app_name_hint),
                isError = uiState.errors.containsKey("appName"),
                supportingText = uiState.errors["appName"]
            )
        }
        item {
            NvTextField(
                value = uiState.packageName,
                onValueChange = viewModel::updatePackageName,
                label = stringResource(R.string.create_package_name),
                placeholder = stringResource(R.string.create_package_name_hint),
                isError = uiState.errors.containsKey("packageName"),
                supportingText = uiState.errors["packageName"]
            )
        }
        item {
            NvTextField(
                value = uiState.description,
                onValueChange = viewModel::updateDescription,
                label = stringResource(R.string.create_description),
                placeholder = stringResource(R.string.create_description_hint),
                singleLine = false,
                maxLines = 4
            )
        }
        item {
            Text(stringResource(R.string.create_language), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = NvSpacing.Xs))
            Row(horizontalArrangement = Arrangement.spacedBy(NvSpacing.Xs), modifier = Modifier.padding(top = NvSpacing.Xxs)) {
                ProjectLanguage.entries.forEach { lang ->
                    NvFilterChip(label = lang.displayName, selected = uiState.language == lang, onClick = { viewModel.updateLanguage(lang) })
                }
            }
        }
        item {
            Text(stringResource(R.string.create_ui_framework), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = NvSpacing.Xs))
            Row(horizontalArrangement = Arrangement.spacedBy(NvSpacing.Xs), modifier = Modifier.padding(top = NvSpacing.Xxs)) {
                UiFramework.entries.forEach { fw ->
                    NvFilterChip(label = fw.displayName, selected = uiState.uiFramework == fw, onClick = { viewModel.updateUiFramework(fw) })
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(NvSpacing.Sm), modifier = Modifier.padding(top = NvSpacing.Xs)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.create_min_sdk), style = MaterialTheme.typography.labelLarge)
                    NvTextField(
                        value = uiState.minSdk.toString(),
                        onValueChange = { it.toIntOrNull()?.let { sdk -> viewModel.updateMinSdk(sdk) } },
                        singleLine = true
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.create_target_sdk), style = MaterialTheme.typography.labelLarge)
                    NvTextField(
                        value = uiState.targetSdk.toString(),
                        onValueChange = { it.toIntOrNull()?.let { sdk -> viewModel.updateTargetSdk(sdk) } },
                        singleLine = true
                    )
                }
            }
        }
    }
}

@Composable
private fun TemplateStep(uiState: CreateProjectUiState, viewModel: CreateProjectViewModel) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(NvSpacing.Md),
        verticalArrangement = Arrangement.spacedBy(NvSpacing.Xs)
    ) {
        item {
            Text(
                text = stringResource(R.string.create_template),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(bottom = NvSpacing.Xs)
            )
        }
        items(ProjectTemplate.entries.toList()) { template ->
            NvCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = { viewModel.updateTemplate(template) }
            ) {
                Row(
                    modifier = Modifier.padding(NvSpacing.Sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = uiState.template == template,
                        onClick = { viewModel.updateTemplate(template) }
                    )
                    Spacer(Modifier.width(NvSpacing.Xs))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(template.displayName, style = MaterialTheme.typography.titleSmall)
                        Text(template.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(NvSpacing.Xs), modifier = Modifier.padding(top = NvSpacing.Xxs)) {
                            NvStatusChip(label = template.language.displayName, containerColor = MaterialTheme.colorScheme.secondaryContainer)
                            NvStatusChip(label = template.uiFramework.displayName, containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReviewStep(uiState: CreateProjectUiState) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(NvSpacing.Md),
        verticalArrangement = Arrangement.spacedBy(NvSpacing.Sm)
    ) {
        item {
            Text(stringResource(R.string.create_review_title), style = MaterialTheme.typography.titleMedium)
        }
        item {
            NvCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(NvSpacing.Md), verticalArrangement = Arrangement.spacedBy(NvSpacing.Sm)) {
                    ReviewRow(stringResource(R.string.create_app_name), uiState.appName)
                    ReviewRow(stringResource(R.string.create_package_name), uiState.packageName)
                    if (uiState.description.isNotBlank()) ReviewRow(stringResource(R.string.create_description), uiState.description)
                    ReviewRow(stringResource(R.string.create_language), uiState.language.displayName)
                    ReviewRow(stringResource(R.string.create_ui_framework), uiState.uiFramework.displayName)
                    ReviewRow(stringResource(R.string.create_template), uiState.template.displayName)
                    ReviewRow(stringResource(R.string.create_min_sdk), "API ${uiState.minSdk}")
                    ReviewRow(stringResource(R.string.create_target_sdk), "API ${uiState.targetSdk}")
                }
            }
        }
    }
}

@Composable
private fun ReviewRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(120.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
