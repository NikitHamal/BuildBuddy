package com.build.buddyai.feature.artifacts

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.build.buddyai.core.common.BuildArtifactInstaller
import com.build.buddyai.core.data.repository.ArtifactRepository
import com.build.buddyai.core.model.BuildArtifact
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class ArtifactsUiState(
    val artifacts: List<BuildArtifact> = emptyList()
)

@HiltViewModel
class ArtifactsViewModel @Inject constructor(
    private val artifactRepository: ArtifactRepository,
    private val artifactInstaller: BuildArtifactInstaller
) : ViewModel() {

    private val _uiState = MutableStateFlow(ArtifactsUiState())
    val uiState: StateFlow<ArtifactsUiState> = _uiState.asStateFlow()

    private var artifactsJob: Job? = null

    fun loadArtifacts(projectId: String) {
        artifactsJob?.cancel()
        artifactsJob = viewModelScope.launch {
            artifactRepository.getArtifactsByProject(projectId).collectLatest { artifacts ->
                _uiState.update { it.copy(artifacts = artifacts) }
            }
        }
    }

    fun installArtifact(context: Context, artifact: BuildArtifact): BuildArtifactInstaller.InstallResult {
        return artifactInstaller.install(context, File(artifact.filePath))
    }

    fun shareArtifact(context: Context, artifact: BuildArtifact): Result<Unit> {
        return artifactInstaller.share(context, File(artifact.filePath))
    }

    fun deleteArtifact(artifact: BuildArtifact) {
        viewModelScope.launch {
            artifactRepository.deleteArtifact(artifact)
        }
    }
}
