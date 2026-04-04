package com.build.buddyai.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.build.buddyai.core.data.datastore.SecureKeyStore
import com.build.buddyai.core.data.datastore.SettingsDataStore
import com.build.buddyai.core.model.*
import com.build.buddyai.core.network.AiProviderService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProviderState(
    val provider: AiProvider,
    val hasApiKey: Boolean,
    val isEnabled: Boolean,
    val selectedModelId: String? = null,
    val isTesting: Boolean = false,
    val testResult: Boolean? = null
)

data class SettingsState(
    val settings: AppSettings = AppSettings(),
    val providers: List<ProviderState> = emptyList(),
    val editingApiKeyProvider: String? = null,
    val apiKeyInput: String = "",
    val isLoading: Boolean = true
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    private val secureKeyStore: SecureKeyStore,
    private val aiProviderService: AiProviderService
) : ViewModel() {
    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            settingsDataStore.appSettings.collect { settings ->
                val providerStates = DefaultProviders.ALL.map { provider ->
                    ProviderState(
                        provider = provider,
                        hasApiKey = secureKeyStore.hasApiKey(provider.id),
                        isEnabled = secureKeyStore.getEnabledProviders().contains(provider.id),
                        selectedModelId = if (provider.id == settings.aiSettings.defaultProviderId) settings.aiSettings.defaultModelId else null
                    )
                }
                _state.update { it.copy(settings = settings, providers = providerStates, isLoading = false) }
            }
        }
    }

    fun updateTheme(theme: AppTheme) {
        viewModelScope.launch { settingsDataStore.updateTheme(theme) }
    }

    fun updateEditorFontSize(size: Int) {
        viewModelScope.launch {
            val current = _state.value.settings.editorSettings
            settingsDataStore.updateEditorSettings(current.copy(fontSize = size.coerceIn(10, 24)))
        }
    }

    fun updateEditorTabWidth(width: Int) {
        viewModelScope.launch {
            val current = _state.value.settings.editorSettings
            settingsDataStore.updateEditorSettings(current.copy(tabWidth = width.coerceIn(2, 8)))
        }
    }

    fun toggleSoftWrap() {
        viewModelScope.launch {
            val current = _state.value.settings.editorSettings
            settingsDataStore.updateEditorSettings(current.copy(softWrap = !current.softWrap))
        }
    }

    fun toggleLineNumbers() {
        viewModelScope.launch {
            val current = _state.value.settings.editorSettings
            settingsDataStore.updateEditorSettings(current.copy(showLineNumbers = !current.showLineNumbers))
        }
    }

    fun toggleAutoSave() {
        viewModelScope.launch {
            val current = _state.value.settings.editorSettings
            settingsDataStore.updateEditorSettings(current.copy(autoSave = !current.autoSave))
        }
    }

    fun toggleHighlightLine() {
        viewModelScope.launch {
            val current = _state.value.settings.editorSettings
            settingsDataStore.updateEditorSettings(current.copy(highlightCurrentLine = !current.highlightCurrentLine))
        }
    }

    fun setDefaultProvider(providerId: String) {
        viewModelScope.launch {
            val provider = DefaultProviders.ALL.find { it.id == providerId } ?: return@launch
            val modelId = provider.models.firstOrNull()?.id
            val current = _state.value.settings.aiSettings
            settingsDataStore.updateAiSettings(current.copy(defaultProviderId = providerId, defaultModelId = modelId))
        }
    }

    fun setDefaultModel(modelId: String) {
        viewModelScope.launch {
            val current = _state.value.settings.aiSettings
            settingsDataStore.updateAiSettings(current.copy(defaultModelId = modelId))
        }
    }

    fun updateTemperature(temp: Float) {
        viewModelScope.launch {
            val current = _state.value.settings.aiSettings
            settingsDataStore.updateAiSettings(current.copy(parameters = current.parameters.copy(temperature = temp.coerceIn(0f, 2f))))
        }
    }

    fun updateMaxTokens(tokens: Int) {
        viewModelScope.launch {
            val current = _state.value.settings.aiSettings
            settingsDataStore.updateAiSettings(current.copy(parameters = current.parameters.copy(maxTokens = tokens.coerceIn(256, 32768))))
        }
    }

    fun toggleStreamResponses() {
        viewModelScope.launch {
            val current = _state.value.settings.aiSettings
            settingsDataStore.updateAiSettings(current.copy(streamResponses = !current.streamResponses))
        }
    }

    fun toggleBuildNotify() {
        viewModelScope.launch {
            val current = _state.value.settings.buildSettings
            settingsDataStore.updateBuildSettings(current.copy(showNotificationOnComplete = !current.showNotificationOnComplete))
        }
    }

    fun toggleCleanBeforeBuild() {
        viewModelScope.launch {
            val current = _state.value.settings.buildSettings
            settingsDataStore.updateBuildSettings(current.copy(cleanBeforeBuild = !current.cleanBeforeBuild))
        }
    }

    fun toggleAnalytics() {
        viewModelScope.launch {
            val current = _state.value.settings.privacySettings
            settingsDataStore.updatePrivacySettings(current.copy(analyticsEnabled = !current.analyticsEnabled))
        }
    }

    fun startEditingApiKey(providerId: String) {
        val existing = secureKeyStore.getApiKey(providerId) ?: ""
        _state.update { it.copy(editingApiKeyProvider = providerId, apiKeyInput = existing) }
    }

    fun updateApiKeyInput(key: String) {
        _state.update { it.copy(apiKeyInput = key) }
    }

    fun saveApiKey() {
        val providerId = _state.value.editingApiKeyProvider ?: return
        val key = _state.value.apiKeyInput.trim()
        if (key.isNotEmpty()) {
            secureKeyStore.setApiKey(providerId, key)
            secureKeyStore.setProviderEnabled(providerId, true)
        } else {
            secureKeyStore.removeApiKey(providerId)
            secureKeyStore.setProviderEnabled(providerId, false)
        }
        _state.update {
            it.copy(
                editingApiKeyProvider = null,
                apiKeyInput = "",
                providers = it.providers.map { ps ->
                    if (ps.provider.id == providerId) ps.copy(hasApiKey = key.isNotEmpty(), isEnabled = key.isNotEmpty())
                    else ps
                }
            )
        }
    }

    fun cancelEditingApiKey() {
        _state.update { it.copy(editingApiKeyProvider = null, apiKeyInput = "") }
    }

    fun removeApiKey(providerId: String) {
        secureKeyStore.removeApiKey(providerId)
        secureKeyStore.setProviderEnabled(providerId, false)
        _state.update {
            it.copy(providers = it.providers.map { ps ->
                if (ps.provider.id == providerId) ps.copy(hasApiKey = false, isEnabled = false, testResult = null) else ps
            })
        }
    }

    fun testConnection(providerId: String) {
        val provider = DefaultProviders.ALL.find { it.id == providerId } ?: return
        val apiKey = secureKeyStore.getApiKey(providerId) ?: return
        val modelId = provider.models.firstOrNull()?.id ?: return

        _state.update {
            it.copy(providers = it.providers.map { ps ->
                if (ps.provider.id == providerId) ps.copy(isTesting = true, testResult = null) else ps
            })
        }

        viewModelScope.launch {
            val result = aiProviderService.testConnection(provider, apiKey, modelId)
            _state.update {
                it.copy(providers = it.providers.map { ps ->
                    if (ps.provider.id == providerId) ps.copy(isTesting = false, testResult = result.isSuccess) else ps
                })
            }
        }
    }
}
