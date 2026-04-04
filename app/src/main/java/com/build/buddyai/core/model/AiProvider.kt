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
    val maxTokens: Int = 4096,
    val supportsStreaming: Boolean = true,
    val description: String = ""
)

/**
 * Known model metadata lookup table.
 * Provides accurate context window and max tokens for popular models
 * when the API doesn't return this information.
 */
object ModelMetadataRegistry {
    data class ModelInfo(
        val contextWindow: Int,
        val maxTokens: Int
    )

    private val knownModels = mapOf(
        // NVIDIA models
        "meta/llama-3.1-8b-instruct" to ModelInfo(128_000, 8192),
        "meta/llama-3.1-70b-instruct" to ModelInfo(128_000, 8192),
        "meta/llama-3.1-405b-instruct" to ModelInfo(128_000, 8192),
        "meta/llama3-8b-instruct" to ModelInfo(8192, 8192),
        "meta/llama3-70b-instruct" to ModelInfo(8192, 8192),
        "mistralai/mixtral-8x7b-instruct-v0.1" to ModelInfo(32_000, 8192),
        "mistralai/mistral-large" to ModelInfo(32_000, 8192),
        "google/gemma-2b" to ModelInfo(8192, 2048),
        "google/gemma-7b" to ModelInfo(8192, 8192),
        "google/gemma-2-2b-it" to ModelInfo(8192, 2048),
        "google/gemma-2-9b-it" to ModelInfo(8192, 8192),
        "google/gemma-2-27b-it" to ModelInfo(8192, 8192),
        "microsoft/phi-3-mini-128k-instruct" to ModelInfo(128_000, 4096),
        "microsoft/phi-3-medium-128k-instruct" to ModelInfo(128_000, 4096),
        "nvidia/llama3-chatqa-1.5-70b" to ModelInfo(8192, 1024),
        "nvidia/llama3-chatqa-1.5-8b" to ModelInfo(8192, 1024),
        "baichuan-inc/baichuan2-13b-chat" to ModelInfo(4096, 4096),
        "thudm/chatglm3-6b" to ModelInfo(8192, 8192),
        "snowflake/arctic" to ModelInfo(8192, 1024),
        "databricks/dbrx-instruct" to ModelInfo(32_000, 8192),
        "qwen/qwen2-72b-instruct" to ModelInfo(32_000, 8192),
        "qwen/qwen2.5-72b-instruct" to ModelInfo(128_000, 8192),
        "qwen/qwen2.5-coder-32b-instruct" to ModelInfo(128_000, 8192),
        "deepseek-ai/deepseek-coder-6.7b-instruct" to ModelInfo(16_000, 4096),
        "upstage/solar-10.7b-instruct" to ModelInfo(4096, 4096),

        // OpenRouter models (common ones)
        "meta-llama/llama-3.1-8b-instruct" to ModelInfo(128_000, 8192),
        "meta-llama/llama-3.1-70b-instruct" to ModelInfo(128_000, 8192),
        "meta-llama/llama-3.1-405b-instruct" to ModelInfo(128_000, 8192),
        "meta-llama/llama-3-8b-instruct" to ModelInfo(8192, 8192),
        "meta-llama/llama-3-70b-instruct" to ModelInfo(8192, 8192),
        "meta-llama/llama-3-8b-instruct:extended" to ModelInfo(16_384, 8192),
        "meta-llama/llama-3-70b-instruct:extended" to ModelInfo(16_384, 8192),
        "meta-llama/llama-3-8b-instruct:free" to ModelInfo(8192, 8192),
        "meta-llama/llama-3-70b-instruct:free" to ModelInfo(8192, 8192),
        "mistralai/mistral-7b-instruct" to ModelInfo(32_000, 8192),
        "mistralai/mistral-7b-instruct:free" to ModelInfo(32_000, 8192),
        "mistralai/mixtral-8x7b-instruct" to ModelInfo(32_000, 8192),
        "mistralai/mixtral-8x7b-instruct:free" to ModelInfo(32_000, 8192),
        "mistralai/mistral-large" to ModelInfo(32_000, 8192),
        "mistralai/mistral-large-2411" to ModelInfo(128_000, 8192),
        "mistralai/mistral-small" to ModelInfo(32_000, 8192),
        "mistralai/mistral-nemo" to ModelInfo(128_000, 8192),
        "mistralai/codestral-2501" to ModelInfo(256_000, 8192),
        "anthropic/claude-3.5-sonnet" to ModelInfo(200_000, 8192),
        "anthropic/claude-3.5-sonnet:beta" to ModelInfo(200_000, 8192),
        "anthropic/claude-3.5-sonnet-20240620" to ModelInfo(200_000, 8192),
        "anthropic/claude-3.5-sonnet-20241022" to ModelInfo(200_000, 8192),
        "anthropic/claude-3-opus" to ModelInfo(200_000, 4096),
        "anthropic/claude-3-opus:beta" to ModelInfo(200_000, 4096),
        "anthropic/claude-3-haiku" to ModelInfo(200_000, 4096),
        "anthropic/claude-3-haiku:beta" to ModelInfo(200_000, 4096),
        "anthropic/claude-3-sonnet" to ModelInfo(200_000, 4096),
        "anthropic/claude-3-sonnet:beta" to ModelInfo(200_000, 4096),
        "google/gemini-flash-1.5" to ModelInfo(1_000_000, 8192),
        "google/gemini-flash-1.5-exp" to ModelInfo(1_000_000, 8192),
        "google/gemini-pro-1.5" to ModelInfo(2_000_000, 8192),
        "google/gemini-pro-1.5-exp" to ModelInfo(2_000_000, 8192),
        "google/gemini-2.0-flash-exp:free" to ModelInfo(1_000_000, 8192),
        "openai/gpt-4o" to ModelInfo(128_000, 4096),
        "openai/gpt-4o-mini" to ModelInfo(128_000, 4096),
        "openai/gpt-4" to ModelInfo(8192, 4096),
        "openai/gpt-4-turbo" to ModelInfo(128_000, 4096),
        "openai/o1" to ModelInfo(200_000, 100_000),
        "openai/o1-mini" to ModelInfo(128_000, 65_536),
        "openai/o3-mini" to ModelInfo(200_000, 100_000),
        "openai/gpt-3.5-turbo" to ModelInfo(16_385, 4096),
        "openai/gpt-3.5-turbo-0125" to ModelInfo(16_385, 4096),
        "qwen/qwen-2.5-72b-instruct" to ModelInfo(128_000, 8192),
        "qwen/qwen-max" to ModelInfo(32_000, 8192),
        "deepseek/deepseek-chat" to ModelInfo(128_000, 8192),
        "deepseek/deepseek-coder" to ModelInfo(128_000, 8192),
        "deepseek/deepseek-r1" to ModelInfo(128_000, 8192),
        "deepseek/deepseek-r1-distill-llama-70b" to ModelInfo(128_000, 8192),
        "minimax/minimax-01" to ModelInfo(4096, 4096),

        // Gemini models
        "gemini-2.0-flash" to ModelInfo(1_000_000, 8192),
        "gemini-2.0-flash-lite" to ModelInfo(1_000_000, 8192),
        "gemini-2.0-flash-exp" to ModelInfo(1_000_000, 8192),
        "gemini-2.0-flash-thinking-exp" to ModelInfo(32_000, 8192),
        "gemini-2.0-pro-exp" to ModelInfo(2_000_000, 8192),
        "gemini-1.5-flash" to ModelInfo(1_000_000, 8192),
        "gemini-1.5-flash-8b" to ModelInfo(1_000_000, 8192),
        "gemini-1.5-flash-latest" to ModelInfo(1_000_000, 8192),
        "gemini-1.5-pro" to ModelInfo(2_000_000, 8192),
        "gemini-1.5-pro-latest" to ModelInfo(2_000_000, 8192),
        "gemini-1.0-pro" to ModelInfo(32_000, 8192),
        "gemini-1.0-pro-vision" to ModelInfo(16_384, 2048),
        "gemini-pro" to ModelInfo(32_000, 8192),
        "gemini-pro-vision" to ModelInfo(16_384, 2048),
        "text-embedding-004" to ModelInfo(2048, 2048),
        "aqa" to ModelInfo(7168, 1024),

        // Paxsenix models (OpenAI-compatible)
        "gpt-4o" to ModelInfo(128_000, 4096),
        "gpt-4o-mini" to ModelInfo(128_000, 4096),
        "gpt-4" to ModelInfo(8192, 4096),
        "gpt-4-turbo" to ModelInfo(128_000, 4096),
        "gpt-3.5-turbo" to ModelInfo(16_385, 4096),
        "gpt-3.5-turbo-16k" to ModelInfo(16_385, 4096),
        "claude-3.5-sonnet" to ModelInfo(200_000, 8192),
        "claude-3-opus" to ModelInfo(200_000, 4096),
        "claude-3-haiku" to ModelInfo(200_000, 4096),
        "llama-3.1-8b-instruct" to ModelInfo(128_000, 8192),
        "llama-3.1-70b-instruct" to ModelInfo(128_000, 8192),
        "llama-3.1-405b-instruct" to ModelInfo(128_000, 8192),
        "mistral-7b-instruct" to ModelInfo(32_000, 8192),
        "mixtral-8x7b-instruct" to ModelInfo(32_000, 8192)
    )

    /**
     * Looks up known metadata for a model by its ID.
     * Returns null if the model is not in the registry.
     */
    fun getModelInfo(modelId: String): ModelInfo? {
        // Try exact match first
        knownModels[modelId]?.let { return it }
        
        // Try case-insensitive match
        val lowerId = modelId.lowercase()
        knownModels.entries.find { it.key.lowercase() == lowerId }?.let { return it.value }
        
        // Try partial match for common patterns
        knownModels.entries.find { it.key.contains(lowerId, ignoreCase = true) || lowerId.contains(it.key, ignoreCase = true) }
            ?.let { return it.value }
        
        return null
    }
}

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
