package com.build.buddyai.feature.models

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.build.buddyai.core.data.repository.ProviderRepository
import com.build.buddyai.core.model.AiProvider
import com.build.buddyai.core.model.ModelParameters
import com.build.buddyai.core.model.ProviderType
import com.build.buddyai.core.network.AiApiService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ModelsUiState(
    val providers: List<AiProvider> = emptyList(),
    val apiKeyInputs: Map<ProviderType, String> = emptyMap(),
    val expandedProvider: ProviderType? = null,
    val testingProvider: ProviderType? = null,
    val testResults: Map<ProviderType, String> = emptyMap()
)

@HiltViewModel
class ModelsViewModel @Inject constructor(
    private val providerRepository: ProviderRepository,
    private val aiApiService: AiApiService
) : ViewModel() {

    private val _uiState = MutableStateFlow(ModelsUiState())
    val uiState: StateFlow<ModelsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            providerRepository.getAllProviders().collect { providers ->
                _uiState.update { it.copy(providers = providers) }
            }
        }
    }

    fun toggleProviderExpand(type: ProviderType) {
        _uiState.update {
            it.copy(expandedProvider = if (it.expandedProvider == type) null else type)
        }
    }

    fun updateApiKeyInput(type: ProviderType, key: String) {
        _uiState.update {
            it.copy(apiKeyInputs = it.apiKeyInputs + (type to key))
        }
    }

    fun saveProvider(type: ProviderType) {
        val apiKey = _uiState.value.apiKeyInputs[type] ?: return
        if (apiKey.isBlank()) return

        viewModelScope.launch {
            val models = _uiState.value.providers.find { it.type == type }?.models
            val defaultModel = models?.firstOrNull()?.id
            providerRepository.saveProvider(type, apiKey, defaultModel)
            _uiState.update {
                it.copy(apiKeyInputs = it.apiKeyInputs - type)
            }
        }
    }

    fun testConnection(type: ProviderType) {
        val apiKey = _uiState.value.apiKeyInputs[type]
            ?: providerRepository.getApiKey(type.name)
            ?: return

        _uiState.update {
            it.copy(testingProvider = type, testResults = it.testResults - type)
        }

        viewModelScope.launch {
            val result = aiApiService.testConnection(type, apiKey)
            _uiState.update {
                it.copy(
                    testingProvider = null,
                    testResults = it.testResults + (type to
                            if (result.isSuccess) "Connection successful"
                            else "Connection failed: ${result.exceptionOrNull()?.message}")
                )
            }
        }
    }

    fun setDefaultProvider(providerId: String) {
        viewModelScope.launch { providerRepository.setDefaultProvider(providerId) }
    }

    fun removeProvider(providerId: String) {
        viewModelScope.launch { providerRepository.removeProvider(providerId) }
    }

    fun selectModel(providerId: String, modelId: String) {
        viewModelScope.launch { providerRepository.updateProviderModel(providerId, modelId) }
    }

    fun updateTemperature(providerId: String, value: Float) {
        val provider = _uiState.value.providers.find { it.id == providerId } ?: return
        viewModelScope.launch {
            providerRepository.updateProviderParameters(providerId, provider.parameters.copy(temperature = value))
        }
    }

    fun updateMaxTokens(providerId: String, value: Int) {
        val provider = _uiState.value.providers.find { it.id == providerId } ?: return
        viewModelScope.launch {
            providerRepository.updateProviderParameters(providerId, provider.parameters.copy(maxTokens = value))
        }
    }

    fun updateTopP(providerId: String, value: Float) {
        val provider = _uiState.value.providers.find { it.id == providerId } ?: return
        viewModelScope.launch {
            providerRepository.updateProviderParameters(providerId, provider.parameters.copy(topP = value))
        }
    }
}
