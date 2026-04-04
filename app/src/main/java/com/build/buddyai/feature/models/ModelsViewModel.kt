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
    val testResults: Map<ProviderType, String> = emptyMap(),
    val fetchingModels: Set<ProviderType> = emptySet(),
    val modelFetchErrors: Map<ProviderType, String> = emptyMap()
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
                _uiState.update {
                    val updated = providers.map { provider ->
                        if (provider.isConfigured && provider.cachedModels.isNotEmpty()) {
                            val cacheExpired = provider.lastModelFetchTime?.let {
                                System.currentTimeMillis() - it > AiProvider.MODEL_CACHE_DURATION_MS
                            } ?: true
                            if (cacheExpired) {
                                provider.copy(cachedModels = emptyList(), lastModelFetchTime = null)
                            } else {
                                provider
                            }
                        } else {
                            provider
                        }
                    }
                    it.copy(providers = updated)
                }
            }
        }
    }

    fun toggleProviderExpand(type: ProviderType) {
        _uiState.update {
            val newState = it.copy(expandedProvider = if (it.expandedProvider == type) null else type)
            // Fetch models if expanding a configured provider with no cached models
            if (newState.expandedProvider == type) {
                val provider = it.providers.find { p -> p.type == type }
                if (provider?.isConfigured == true && provider.cachedModels.isEmpty()) {
                    fetchModels(type)
                }
            }
            newState
        }
    }

    fun fetchModels(type: ProviderType) {
        val apiKey = _uiState.value.apiKeyInputs[type]
            ?: providerRepository.getApiKey(type.name)
            ?: return

        _uiState.update {
            it.copy(
                fetchingModels = it.fetchingModels + type,
                modelFetchErrors = it.modelFetchErrors - type
            )
        }

        viewModelScope.launch {
            val result = aiApiService.fetchModels(type, apiKey)
            _uiState.update { state ->
                when {
                    result.isSuccess -> {
                        val models = result.getOrNull() ?: emptyList()
                        providerRepository.updateProviderModels(type, models)
                        state.copy(
                            fetchingModels = state.fetchingModels - type,
                            modelFetchErrors = state.modelFetchErrors - type
                        )
                    }
                    else -> {
                        state.copy(
                            fetchingModels = state.fetchingModels - type,
                            modelFetchErrors = state.modelFetchErrors + (type to
                                    (result.exceptionOrNull()?.message ?: "Failed to fetch models"))
                        )
                    }
                }
            }
        }
    }

    fun refreshModels(type: ProviderType) {
        fetchModels(type)
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
            providerRepository.saveProvider(type, apiKey, null)
            _uiState.update {
                it.copy(apiKeyInputs = it.apiKeyInputs - type)
            }
            // Fetch models after saving
            fetchModels(type)
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
