package com.build.buddyai.feature.artifacts

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
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
        val file = File(artifact.filePath)
        if (!file.exists()) {
            Toast.makeText(context, "APK file not found", Toast.LENGTH_SHORT).show()
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Toast.makeText(context, "Please allow BuildBuddy to install apps", Toast.LENGTH_LONG).show()
            return
        }

        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
                data = uri
                putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
                putExtra(Intent.EXTRA_RETURN_RESULT, false)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                clipData = android.content.ClipData.newUri(context.contentResolver, file.name, uri)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Error starting installer: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    fun shareArtifact(context: Context, artifact: BuildArtifact) {
        val file = File(artifact.filePath)
        if (!file.exists()) {
            Toast.makeText(context, "APK file not found", Toast.LENGTH_SHORT).show()
            return
        }

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/vnd.android.package-archive"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = android.content.ClipData.newUri(context.contentResolver, file.name, uri)
        }
        context.startActivity(Intent.createChooser(intent, "Share APK"))
    }

    fun deleteArtifact(artifact: BuildArtifact) {
        viewModelScope.launch {
            artifactRepository.deleteArtifact(artifact)
        }
    }
}
