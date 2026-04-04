package com.build.buddyai.feature.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.build.buddyai.core.common.FileUtils
import com.build.buddyai.core.data.datastore.SettingsDataStore
import com.build.buddyai.core.model.AppSettings
import com.build.buddyai.core.model.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val showThemeMenu: Boolean = false,
    val showClearDataDialog: Boolean = false,
    val storageUsed: String = "Calculating…"
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            settingsDataStore.settings.collect { settings ->
                _uiState.update { it.copy(settings = settings) }
            }
        }
        viewModelScope.launch {
            val size = FileUtils.calculateDirectorySize(context.filesDir) +
                    FileUtils.calculateDirectorySize(context.cacheDir)
            _uiState.update { it.copy(storageUsed = FileUtils.formatFileSize(size)) }
        }
    }

    fun toggleThemeMenu() = _uiState.update { it.copy(showThemeMenu = !it.showThemeMenu) }
    fun updateTheme(theme: ThemeMode) { viewModelScope.launch { settingsDataStore.updateTheme(theme) } }
    fun updateFontSize(size: Int) { viewModelScope.launch { settingsDataStore.updateEditorFontSize(size) } }
    fun updateTabWidth(width: Int) { viewModelScope.launch { settingsDataStore.updateEditorTabWidth(width) } }
    fun updateSoftWrap(enabled: Boolean) { viewModelScope.launch { settingsDataStore.updateEditorSoftWrap(enabled) } }
    fun updateLineNumbers(enabled: Boolean) { viewModelScope.launch { settingsDataStore.updateEditorLineNumbers(enabled) } }
    fun updateAutosave(enabled: Boolean) { viewModelScope.launch { settingsDataStore.updateEditorAutosave(enabled) } }
    fun updateBuildCache(enabled: Boolean) { viewModelScope.launch { settingsDataStore.updateBuildCacheEnabled(enabled) } }
    fun updateBuildNotifications(enabled: Boolean) { viewModelScope.launch { settingsDataStore.updateBuildNotifications(enabled) } }

    fun clearCache() {
        viewModelScope.launch {
            FileUtils.getCacheDir(context).deleteRecursively()
        }
    }

    fun showClearDataDialog() = _uiState.update { it.copy(showClearDataDialog = true) }
    fun dismissClearDataDialog() = _uiState.update { it.copy(showClearDataDialog = false) }

    fun clearAllData() {
        viewModelScope.launch {
            settingsDataStore.clearAll()
            context.filesDir.deleteRecursively()
            context.cacheDir.deleteRecursively()
            _uiState.update { it.copy(showClearDataDialog = false) }
        }
    }
}
