package com.build.buddyai.feature.project.create

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.build.buddyai.core.designsystem.component.*
import com.build.buddyai.core.designsystem.theme.NeoVedicSpacing
import com.build.buddyai.core.model.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateProjectScreen(
    onProjectCreated: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: CreateProjectViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.createdProjectId) {
        state.createdProjectId?.let { onProjectCreated(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New Project") },
                navigationIcon = {
                    IconButton(onClick = {
                        if (state.step > 0) viewModel.previousStep() else onBack()
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            LinearProgressIndicator(
                progress = { (state.step + 1).toFloat() / state.totalSteps },
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHigh
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(NeoVedicSpacing.XL)
            ) {
                when (state.step) {
                    0 -> ProjectDetailsStep(state, viewModel)
                    1 -> TemplateStep(state, viewModel)
                    2 -> ReviewStep(state)
                }
            }

            NeoVedicDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(NeoVedicSpacing.LG),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (state.step > 0) {
                    NeoVedicOutlinedButton(
                        text = "Previous",
                        onClick = { viewModel.previousStep() }
                    )
                } else {
                    Spacer(Modifier.width(1.dp))
                }
                if (state.step < state.totalSteps - 1) {
                    NeoVedicButton(
                        text = "Next",
                        onClick = { viewModel.nextStep() }
                    )
                } else {
                    NeoVedicButton(
                        text = if (state.isCreating) "Creating..." else "Create Project",
                        onClick = { viewModel.createProject() },
                        enabled = !state.isCreating,
                        icon = Icons.Default.Add
                    )
                }
            }
        }
    }
}

@Composable
private fun ProjectDetailsStep(
    state: CreateProjectState,
    viewModel: CreateProjectViewModel
) {
    Text("Project Details", style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(NeoVedicSpacing.LG))

    OutlinedTextField(
        value = state.appName,
        onValueChange = { viewModel.updateAppName(it) },
        label = { Text("App Name") },
        isError = state.appNameError != null,
        supportingText = state.appNameError?.let { { Text(it) } },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small
    )
    Spacer(Modifier.height(NeoVedicSpacing.MD))

    OutlinedTextField(
        value = state.packageName,
        onValueChange = { viewModel.updatePackageName(it) },
        label = { Text("Package Name") },
        isError = state.packageNameError != null,
        supportingText = state.packageNameError?.let { { Text(it) } },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small
    )
    Spacer(Modifier.height(NeoVedicSpacing.MD))

    OutlinedTextField(
        value = state.description,
        onValueChange = { viewModel.updateDescription(it) },
        label = { Text("Description (optional)") },
        modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp),
        shape = MaterialTheme.shapes.small,
        maxLines = 3
    )
    Spacer(Modifier.height(NeoVedicSpacing.XL))

    Text("Language", style = MaterialTheme.typography.titleSmall)
    Spacer(Modifier.height(NeoVedicSpacing.SM))
    Row(horizontalArrangement = Arrangement.spacedBy(NeoVedicSpacing.SM)) {
        ProjectLanguage.entries.forEach { lang ->
            NeoVedicChip(
                label = lang.displayName,
                selected = state.language == lang,
                onClick = { viewModel.updateLanguage(lang) }
            )
        }
    }
    Spacer(Modifier.height(NeoVedicSpacing.LG))

    if (state.language == ProjectLanguage.KOTLIN) {
        Text("UI Framework", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(NeoVedicSpacing.SM))
        Row(horizontalArrangement = Arrangement.spacedBy(NeoVedicSpacing.SM)) {
            UiFramework.entries.forEach { ui ->
                NeoVedicChip(
                    label = ui.displayName,
                    selected = state.uiFramework == ui,
                    onClick = { viewModel.updateUiFramework(ui) }
                )
            }
        }
    }
}

@Composable
private fun TemplateStep(
    state: CreateProjectState,
    viewModel: CreateProjectViewModel
) {
    Text("Choose Template", style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(NeoVedicSpacing.LG))

    val templates = ProjectTemplate.entries.filter {
        it.language == state.language && it.uiFramework == state.uiFramework
    }.ifEmpty { ProjectTemplate.entries.filter { it.language == state.language } }

    templates.forEach { template ->
        NeoVedicCard(
            modifier = Modifier.fillMaxWidth().padding(bottom = NeoVedicSpacing.SM),
            onClick = { viewModel.updateTemplate(template) }
        ) {
            Row(
                modifier = Modifier.padding(NeoVedicSpacing.LG),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = state.template == template,
                    onClick = { viewModel.updateTemplate(template) }
                )
                Spacer(Modifier.width(NeoVedicSpacing.MD))
                Column(modifier = Modifier.weight(1f)) {
                    Text(template.displayName, style = MaterialTheme.typography.titleSmall)
                    Text(
                        template.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    Spacer(Modifier.height(NeoVedicSpacing.XL))
    Text("SDK Configuration", style = MaterialTheme.typography.titleSmall)
    Spacer(Modifier.height(NeoVedicSpacing.SM))
    Row(horizontalArrangement = Arrangement.spacedBy(NeoVedicSpacing.MD)) {
        OutlinedTextField(
            value = state.minSdk.toString(),
            onValueChange = { it.toIntOrNull()?.let { v -> viewModel.updateMinSdk(v) } },
            label = { Text("Min SDK") },
            modifier = Modifier.weight(1f),
            singleLine = true,
            shape = MaterialTheme.shapes.small,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        OutlinedTextField(
            value = state.targetSdk.toString(),
            onValueChange = { it.toIntOrNull()?.let { v -> viewModel.updateTargetSdk(v) } },
            label = { Text("Target SDK") },
            modifier = Modifier.weight(1f),
            singleLine = true,
            shape = MaterialTheme.shapes.small,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
    }
}

@Composable
private fun ReviewStep(state: CreateProjectState) {
    Text("Review & Create", style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(NeoVedicSpacing.LG))

    NeoVedicCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(NeoVedicSpacing.LG)) {
            ReviewRow("App Name", state.appName)
            ReviewRow("Package", state.packageName)
            if (state.description.isNotBlank()) ReviewRow("Description", state.description)
            ReviewRow("Language", state.language.displayName)
            ReviewRow("UI Framework", state.uiFramework.displayName)
            ReviewRow("Template", state.template.displayName)
            ReviewRow("Min SDK", state.minSdk.toString())
            ReviewRow("Target SDK", state.targetSdk.toString())
        }
    }
}

@Composable
private fun ReviewRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = NeoVedicSpacing.XS),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}