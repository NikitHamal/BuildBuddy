package com.build.buddyai.feature.artifacts

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.build.buddyai.core.data.repository.ArtifactRepository
import com.build.buddyai.core.model.BuildArtifact
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
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

    fun loadArtifacts(projectId: String) {
        viewModelScope.launch {
            artifactRepository.getArtifactsByProject(projectId).collect { artifacts ->
                _uiState.update { it.copy(artifacts = artifacts) }
            }
        }
    }

    fun installArtifact(context: Context, artifact: BuildArtifact) {
        val file = File(artifact.filePath)
        if (!file.exists()) return

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }

    fun shareArtifact(context: Context, artifact: BuildArtifact) {
        val file = File(artifact.filePath)
        if (!file.exists()) return

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/vnd.android.package-archive"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share APK"))
    }

    fun deleteArtifact(artifact: BuildArtifact) {
        viewModelScope.launch {
            artifactRepository.deleteArtifact(artifact)
        }
    }
}
