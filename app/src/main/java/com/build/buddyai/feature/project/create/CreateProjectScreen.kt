package com.build.buddyai.feature.project.create

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.build.buddyai.R
import com.build.buddyai.core.designsystem.BuildBuddyCard
import com.build.buddyai.core.designsystem.NeoVedicTheme
import com.build.buddyai.core.model.ProjectLanguage
import com.build.buddyai.core.model.ProjectTemplate
import com.build.buddyai.core.model.UiToolkit

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CreateProjectScreen(
    viewModel: CreateProjectViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val spacing = NeoVedicTheme.spacing
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.create_project_title)) },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text(stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            BuildBuddyCard {
                Text(
                    text = when (state.step) {
                        0 -> stringResource(R.string.create_project_step_details)
                        1 -> stringResource(R.string.create_project_step_template)
                        else -> stringResource(R.string.create_project_step_review)
                    },
                    style = MaterialTheme.typography.titleLarge,
                )
            }
            when (state.step) {
                0 -> IdentityStep(state = state, viewModel = viewModel)
                1 -> TemplateStep(state = state, viewModel = viewModel)
                2 -> ReviewStep(state = state)
            }
            state.validationMessage?.let {
                Text(text = it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                if (state.step > 0) {
                    TextButton(onClick = viewModel::previousStep) {
                        Text(stringResource(R.string.action_back))
                    }
                }
                if (state.step < 2) {
                    Button(onClick = viewModel::nextStep) {
                        Text(stringResource(R.string.action_next))
                    }
                } else {
                    Button(onClick = viewModel::createProject, enabled = !state.creating) {
                        Text(stringResource(R.string.action_create))
                    }
                }
            }
        }
    }
}

@Composable
private fun IdentityStep(
    state: CreateProjectUiState,
    viewModel: CreateProjectViewModel,
) {
    val spacing = NeoVedicTheme.spacing
    Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = state.draft.name,
            onValueChange = viewModel::updateName,
            label = { Text(stringResource(R.string.create_project_name)) },
        )
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = state.draft.packageName,
            onValueChange = viewModel::updatePackage,
            label = { Text(stringResource(R.string.create_project_package)) },
        )
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = state.draft.description,
            onValueChange = viewModel::updateDescription,
            label = { Text(stringResource(R.string.create_project_description)) },
            minLines = 3,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.sm), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
            ProjectLanguage.entries.forEach { language ->
                FilterChip(
                    selected = state.draft.language == language,
                    onClick = { viewModel.updateLanguage(language) },
                    label = { Text(language.name) },
                )
            }
            UiToolkit.entries.forEach { toolkit ->
                FilterChip(
                    selected = state.draft.uiToolkit == toolkit,
                    onClick = { viewModel.updateUiToolkit(toolkit) },
                    label = { Text(toolkit.name) },
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            OutlinedTextField(
                modifier = Modifier.weight(1f),
                value = state.draft.minSdk.toString(),
                onValueChange = { it.toIntOrNull()?.let(viewModel::updateMinSdk) },
                label = { Text(stringResource(R.string.create_project_min_sdk)) },
            )
            OutlinedTextField(
                modifier = Modifier.weight(1f),
                value = state.draft.targetSdk.toString(),
                onValueChange = { it.toIntOrNull()?.let(viewModel::updateTargetSdk) },
                label = { Text(stringResource(R.string.create_project_target_sdk)) },
            )
        }
    }
}

@Composable
private fun TemplateStep(
    state: CreateProjectUiState,
    viewModel: CreateProjectViewModel,
) {
    val spacing = NeoVedicTheme.spacing
    Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = state.draft.accentColor,
            onValueChange = viewModel::updateAccent,
            label = { Text(stringResource(R.string.create_project_branding)) },
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.sm), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
            ProjectTemplate.entries.forEach { template ->
                FilterChip(
                    selected = state.draft.template == template,
                    onClick = { viewModel.updateTemplate(template) },
                    label = { Text(template.name.replace('_', ' ')) },
                )
            }
        }
    }
}

@Composable
private fun ReviewStep(state: CreateProjectUiState) {
    val draft = state.draft
    BuildBuddyCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.create_project_summary), style = MaterialTheme.typography.titleLarge)
            Text("${draft.name} • ${draft.packageName}")
            Text("${draft.language.name} • ${draft.uiToolkit.name} • ${draft.template.name}")
            Text("SDK ${draft.minSdk} to ${draft.targetSdk}")
            Text(draft.description.ifBlank { "No product brief entered." })
        }
    }
}
