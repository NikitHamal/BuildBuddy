package com.build.buddyai.feature.project.create

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.build.buddyai.core.data.repository.ProjectRepository
import com.build.buddyai.core.model.ProjectDraft
import com.build.buddyai.core.model.ProjectLanguage
import com.build.buddyai.core.model.ProjectTemplate
import com.build.buddyai.core.model.UiToolkit
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CreateProjectUiState(
    val step: Int = 0,
    val draft: ProjectDraft = ProjectDraft(),
    val validationMessage: String? = null,
    val creating: Boolean = false,
)

@HiltViewModel
class CreateProjectViewModel @Inject constructor(
    private val projectRepository: ProjectRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(CreateProjectUiState())
    val uiState = _uiState

    private val createdProject = MutableSharedFlow<String>()
    val createdProjectEvents = createdProject.asSharedFlow()

    fun updateName(value: String) = updateDraft { copy(name = value) }
    fun updatePackage(value: String) = updateDraft { copy(packageName = value) }
    fun updateDescription(value: String) = updateDraft { copy(description = value) }
    fun updateLanguage(value: ProjectLanguage) = updateDraft { copy(language = value) }
    fun updateUiToolkit(value: UiToolkit) = updateDraft { copy(uiToolkit = value) }
    fun updateMinSdk(value: Int) = updateDraft { copy(minSdk = value) }
    fun updateTargetSdk(value: Int) = updateDraft { copy(targetSdk = value) }
    fun updateTemplate(value: ProjectTemplate) = updateDraft { copy(template = value) }
    fun updateAccent(value: String) = updateDraft { copy(accentColor = value) }

    fun nextStep() {
        _uiState.update { it.copy(step = (it.step + 1).coerceAtMost(2)) }
    }

    fun previousStep() {
        _uiState.update { it.copy(step = (it.step - 1).coerceAtLeast(0)) }
    }

    fun createProject() {
        val state = _uiState.value
        val validation = validate(state.draft)
        if (validation != null) {
            _uiState.update { it.copy(validationMessage = validation) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(creating = true, validationMessage = null) }
            val project = projectRepository.createProject(state.draft)
            createdProject.emit(project.id)
            _uiState.update { it.copy(creating = false) }
        }
    }

    private fun updateDraft(transform: ProjectDraft.() -> ProjectDraft) {
        _uiState.update { it.copy(draft = it.draft.transform(), validationMessage = null) }
    }

    private fun validate(draft: ProjectDraft): String? {
        if (draft.name.trim().length < 2) return "Enter a valid app name."
        val packageRegex = Regex("[a-zA-Z]+(\\.[a-zA-Z][a-zA-Z0-9_]*)+")
        if (!packageRegex.matches(draft.packageName.trim())) return "Enter a valid package name."
        if (draft.targetSdk < draft.minSdk) return "Target SDK must be greater than or equal to min SDK."
        return null
    }
}

