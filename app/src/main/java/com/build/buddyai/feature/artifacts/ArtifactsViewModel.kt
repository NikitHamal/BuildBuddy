package com.build.buddyai.feature.artifacts

import android.content.Context
import com.build.buddyai.core.common.ArtifactLauncher
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.build.buddyai.core.data.repository.ArtifactRepository
import com.build.buddyai.core.model.BuildArtifact
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ArtifactsUiState(
    val artifacts: List<BuildArtifact> = emptyList()
)

@HiltViewModel
class ArtifactsViewModel @Inject constructor(
    private val artifactRepository: ArtifactRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ArtifactsUiState())
    val uiState: StateFlow<ArtifactsUiState> = _uiState.asStateFlow()

    private var artifactsJob: Job? = null

    fun loadArtifacts(projectId: String) {
        artifactsJob?.cancel()
        artifactsJob = viewModelScope.launch {
            artifactRepository.getArtifactsByProject(projectId).collect { artifacts ->
                _uiState.update { it.copy(artifacts = artifacts) }
            }
        }
    }

    fun installArtifact(context: Context, artifact: BuildArtifact) {
        ArtifactLauncher.install(context, artifact)
    }

    fun shareArtifact(context: Context, artifact: BuildArtifact) {
        ArtifactLauncher.share(context, artifact)
    }

    fun deleteArtifact(artifact: BuildArtifact) {
        viewModelScope.launch {
            artifactRepository.deleteArtifact(artifact)
        }
    }
}
