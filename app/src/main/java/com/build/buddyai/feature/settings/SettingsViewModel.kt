package com.build.buddyai.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.build.buddyai.core.data.repository.PreferencesRepository
import com.build.buddyai.core.model.AppPreferences
import com.build.buddyai.core.model.BuildMode
import com.build.buddyai.core.model.ProviderId
import com.build.buddyai.core.model.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
) : ViewModel() {
    val uiState = preferencesRepository.preferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppPreferences())

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            preferencesRepository.setThemeMode(mode)
        }
    }

    fun updateEditorSettings(fontSize: Int, tabWidth: Int, softWrap: Boolean, lineNumbers: Boolean, autosave: Boolean) {
        viewModelScope.launch {
            preferencesRepository.updateEditorSettings(fontSize, tabWidth, softWrap, lineNumbers, autosave)
        }
    }

    fun updateDefaults(providerId: ProviderId, modelId: String, buildMode: BuildMode) {
        viewModelScope.launch {
            preferencesRepository.updateDefaults(providerId, modelId, buildMode)
        }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setNotificationsEnabled(enabled)
        }
    }

    fun setToolchainRoot(path: String?) {
        viewModelScope.launch {
            preferencesRepository.setToolchainRootOverride(path)
        }
    }

    fun markOnboardingComplete() {
        viewModelScope.launch {
            preferencesRepository.setOnboardingComplete(true)
        }
    }

    fun exportDebugBundle(output: File, dashboard: com.build.buddyai.core.model.DashboardState) {
        viewModelScope.launch {
            preferencesRepository.exportDebugBundle(output, dashboard)
        }
    }
}

