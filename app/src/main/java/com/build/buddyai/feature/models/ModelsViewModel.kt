package com.build.buddyai.feature.models

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.build.buddyai.core.data.repository.ProviderSettingsRepository
import com.build.buddyai.core.network.ProviderRegistry
import com.build.buddyai.core.model.ProviderConnectionResult
import com.build.buddyai.core.model.ProviderId
import com.build.buddyai.core.model.ProviderSecret
import com.build.buddyai.core.model.ProviderSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ModelsUiState(
    val providerSettings: List<ProviderSettings> = emptyList(),
    val secrets: List<ProviderSecret> = emptyList(),
    val connectionResults: Map<ProviderId, ProviderConnectionResult> = emptyMap(),
    val testingProvider: ProviderId? = null,
)

@HiltViewModel
class ModelsViewModel @Inject constructor(
    private val providerSettingsRepository: ProviderSettingsRepository,
    private val providerRegistry: ProviderRegistry,
) : ViewModel() {
    private val connectionResults = MutableStateFlow<Map<ProviderId, ProviderConnectionResult>>(emptyMap())
    private val testingProvider = MutableStateFlow<ProviderId?>(null)

    val uiState = combine(
        providerSettingsRepository.settings,
        connectionResults,
        testingProvider,
    ) { settings, results, testing ->
        ModelsUiState(
            providerSettings = settings,
            secrets = providerSettingsRepository.secretsState(),
            connectionResults = results,
            testingProvider = testing,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ModelsUiState())

    fun saveApiKey(providerId: ProviderId, apiKey: String) {
        providerSettingsRepository.putApiKey(providerId, apiKey)
        connectionResults.update { it - providerId }
    }

    fun updateProviderSettings(settings: ProviderSettings) {
        viewModelScope.launch {
            providerSettingsRepository.update(settings)
        }
    }

    fun testConnection(providerId: ProviderId) {
        val key = providerRegistry.apiKey(providerId)
        if (key.isNullOrBlank()) {
            connectionResults.update {
                it + (providerId to ProviderConnectionResult(false, "No API key saved for this provider."))
            }
            return
        }
        viewModelScope.launch {
            testingProvider.value = providerId
            val result = providerRegistry.client(providerId).testConnection(key)
            connectionResults.update { it + (providerId to result) }
            testingProvider.value = null
        }
    }
}

