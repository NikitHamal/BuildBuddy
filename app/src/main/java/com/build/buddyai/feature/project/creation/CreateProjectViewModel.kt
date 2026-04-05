package com.build.buddyai.feature.project.creation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.build.buddyai.core.data.repository.ProjectRepository
import com.build.buddyai.core.model.Project
import com.build.buddyai.core.model.ProjectLanguage
import com.build.buddyai.core.model.ProjectTemplate
import com.build.buddyai.core.model.UiFramework
import com.build.buddyai.domain.usecase.GenerateProjectFilesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class CreateProjectUiState(
    val appName: String = "",
    val packageName: String = "com.example.myapp",
    val description: String = "",
    val language: ProjectLanguage = ProjectLanguage.JAVA,
    val uiFramework: UiFramework = UiFramework.VIEWS,
    val template: ProjectTemplate = ProjectTemplate.BLANK_VIEWS,
    val minSdk: Int = 26,
    val targetSdk: Int = 35,
    val step: Int = 0,
    val isCreating: Boolean = false,
    val createdProjectId: String? = null,
    val errors: Map<String, String> = emptyMap()
)

@HiltViewModel
class CreateProjectViewModel @Inject constructor(
    private val projectRepository: ProjectRepository,
    private val generateProjectFiles: GenerateProjectFilesUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(CreateProjectUiState())
    val uiState: StateFlow<CreateProjectUiState> = _uiState.asStateFlow()

    fun updateAppName(name: String) {
        _uiState.update { it.copy(appName = name, errors = it.errors - "appName") }
        if (name.isNotBlank()) {
            val suggested = "com.example.${name.lowercase().replace(Regex("[^a-z0-9]"), "")}"
            _uiState.update { it.copy(packageName = suggested) }
        }
    }

    fun updatePackageName(name: String) = _uiState.update { it.copy(packageName = name, errors = it.errors - "packageName") }
    fun updateDescription(desc: String) = _uiState.update { it.copy(description = desc) }

    fun updateLanguage(lang: ProjectLanguage) = _uiState.update { state ->
        val template = defaultTemplate(lang = lang, framework = state.uiFramework)
        state.copy(
            language = template.language,
            uiFramework = template.uiFramework,
            template = template
        )
    }

    fun updateUiFramework(fw: UiFramework) = _uiState.update { state ->
        val template = defaultTemplate(lang = state.language, framework = fw)
        state.copy(
            language = template.language,
            uiFramework = template.uiFramework,
            template = template
        )
    }

    fun updateMinSdk(sdk: Int) = _uiState.update { it.copy(minSdk = sdk) }
    fun updateTargetSdk(sdk: Int) = _uiState.update { it.copy(targetSdk = sdk) }

    fun updateTemplate(template: ProjectTemplate) {
        _uiState.update {
            it.copy(
                template = template,
                language = template.language,
                uiFramework = template.uiFramework
            )
        }
    }

    fun nextStep(): Boolean {
        val state = _uiState.value
        if (state.step == 0) {
            val errors = mutableMapOf<String, String>()
            if (state.appName.isBlank()) errors["appName"] = "App name is required"
            else if (state.appName.length > 50) errors["appName"] = "App name must be under 50 characters"
            if (state.packageName.isBlank()) errors["packageName"] = "Package name is required"
            else if (!isValidPackageName(state.packageName)) errors["packageName"] = "Invalid package name format"
            if (errors.isNotEmpty()) {
                _uiState.update { it.copy(errors = errors) }
                return false
            }
        }
        _uiState.update { it.copy(step = it.step + 1) }
        return true
    }

    fun previousStep() = _uiState.update { it.copy(step = maxOf(0, it.step - 1)) }

    fun createProject() {
        val state = _uiState.value
        _uiState.update { it.copy(isCreating = true) }

        viewModelScope.launch {
            val project = Project(
                id = UUID.randomUUID().toString(),
                name = state.appName.trim(),
                packageName = state.packageName.trim(),
                description = state.description.trim(),
                language = state.template.language,
                uiFramework = state.template.uiFramework,
                template = state.template,
                minSdk = state.minSdk,
                targetSdk = state.targetSdk
            )
            val created = projectRepository.createProject(project)
            generateProjectFiles(created)
            _uiState.update { it.copy(isCreating = false, createdProjectId = created.id) }
        }
    }

    private fun isValidPackageName(name: String): Boolean {
        val regex = Regex("^[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*){1,}$")
        return regex.matches(name) && !name.contains("..")
    }

    private fun defaultTemplate(lang: ProjectLanguage, framework: UiFramework): ProjectTemplate {
        return when (lang to framework) {
            ProjectLanguage.JAVA to UiFramework.VIEWS -> ProjectTemplate.BLANK_VIEWS
            ProjectLanguage.KOTLIN to UiFramework.COMPOSE -> ProjectTemplate.BLANK_COMPOSE
            ProjectLanguage.KOTLIN to UiFramework.VIEWS -> ProjectTemplate.BLANK_VIEWS
            ProjectLanguage.JAVA to UiFramework.COMPOSE -> ProjectTemplate.BLANK_COMPOSE
            else -> ProjectTemplate.default
        }
    }
}
