package com.build.buddyai.core.model

import kotlinx.serialization.Serializable

@Serializable
data class AiProvider(
    val id: String,
    val type: ProviderType,
    val name: String,
    val isConfigured: Boolean = false,
    val isDefault: Boolean = false,
    val selectedModelId: String? = null,
    val models: List<AiModel> = emptyList(),
    val parameters: ModelParameters = ModelParameters(),
    val cachedModels: List<AiModel> = emptyList(),
    val lastModelFetchTime: Long? = null
) {
    companion object {
        const val MODEL_CACHE_DURATION_MS = 24 * 60 * 60 * 1000L // 24 hours
    }
}

@Serializable
enum class ProviderType(
    val displayName: String,
    val baseUrl: String,
    val requiresKey: Boolean = true
) {
    NVIDIA("NVIDIA", "https://integrate.api.nvidia.com/v1"),
    OPENROUTER("OpenRouter", "https://openrouter.ai/api/v1"),
    GEMINI("Gemini", "https://generativelanguage.googleapis.com/v1beta"),
    PAXSENIX("Paxsenix", "https://api.paxsenix.org/v1")
}

@Serializable
data class AiModel(
    val id: String,
    val name: String,
    val providerId: String,
    val providerType: ProviderType,
    val contextWindow: Int = 4096,
    val supportsStreaming: Boolean = true,
    val description: String = ""
)

@Serializable
data class ModelParameters(
    val temperature: Float = 0.7f,
    val maxTokens: Int = 4096,
    val topP: Float = 0.9f
)

@Serializable
data class ProviderConfig(
    val providerId: String,
    val type: ProviderType,
    val apiKeyEncrypted: String = "",
    val selectedModelId: String? = null,
    val isDefault: Boolean = false,
    val parameters: ModelParameters = ModelParameters()
)
