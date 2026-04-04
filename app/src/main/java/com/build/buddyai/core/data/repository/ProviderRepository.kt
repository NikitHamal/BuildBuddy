package com.build.buddyai.core.data.repository

import com.build.buddyai.core.common.SecureKeyStore
import com.build.buddyai.core.data.local.dao.ProviderConfigDao
import com.build.buddyai.core.data.local.entity.ProviderConfigEntity
import com.build.buddyai.core.model.*
import com.build.buddyai.core.network.ModelCatalog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProviderRepository @Inject constructor(
    private val providerConfigDao: ProviderConfigDao,
    private val secureKeyStore: SecureKeyStore
) {
    fun getAllProviders(): Flow<List<AiProvider>> =
        providerConfigDao.getAllProviderConfigs().map { configs ->
            ProviderType.entries.map { type ->
                val config = configs.find { it.type == type.name }
                AiProvider(
                    id = type.name,
                    type = type,
                    name = type.displayName,
                    isConfigured = config != null && secureKeyStore.hasApiKey(type.name),
                    isDefault = config?.isDefault ?: false,
                    selectedModelId = config?.selectedModelId,
                    models = ModelCatalog.getModelsForProvider(type),
                    parameters = config?.toProviderConfig()?.parameters ?: ModelParameters()
                )
            }
        }

    suspend fun getDefaultProvider(): AiProvider? {
        val config = providerConfigDao.getDefaultProvider()?.toProviderConfig() ?: return null
        val apiKey = secureKeyStore.getApiKey(config.providerId) ?: return null
        return AiProvider(
            id = config.providerId,
            type = config.type,
            name = config.type.displayName,
            isConfigured = true,
            isDefault = true,
            selectedModelId = config.selectedModelId,
            models = ModelCatalog.getModelsForProvider(config.type),
            parameters = config.parameters
        )
    }

    fun getApiKey(providerId: String): String? = secureKeyStore.getApiKey(providerId)

    suspend fun saveProvider(providerType: ProviderType, apiKey: String, modelId: String?) {
        secureKeyStore.storeApiKey(providerType.name, apiKey)
        val existing = providerConfigDao.getProviderConfig(providerType.name)
        val config = ProviderConfigEntity(
            providerId = providerType.name,
            type = providerType.name,
            apiKeyEncrypted = "",
            selectedModelId = modelId ?: existing?.selectedModelId,
            isDefault = existing?.isDefault ?: false,
            temperature = existing?.temperature ?: 0.7f,
            maxTokens = existing?.maxTokens ?: 4096,
            topP = existing?.topP ?: 0.9f
        )
        providerConfigDao.insertProviderConfig(config)
    }

    suspend fun updateProviderModel(providerId: String, modelId: String) {
        val config = providerConfigDao.getProviderConfig(providerId) ?: return
        providerConfigDao.updateProviderConfig(config.copy(selectedModelId = modelId))
    }

    suspend fun updateProviderParameters(providerId: String, params: ModelParameters) {
        val config = providerConfigDao.getProviderConfig(providerId) ?: return
        providerConfigDao.updateProviderConfig(
            config.copy(temperature = params.temperature, maxTokens = params.maxTokens, topP = params.topP)
        )
    }

    suspend fun setDefaultProvider(providerId: String) {
        providerConfigDao.clearAllDefaults()
        val config = providerConfigDao.getProviderConfig(providerId) ?: return
        providerConfigDao.updateProviderConfig(config.copy(isDefault = true))
    }

    suspend fun removeProvider(providerId: String) {
        secureKeyStore.deleteApiKey(providerId)
        providerConfigDao.deleteProviderConfig(providerId)
    }
}
