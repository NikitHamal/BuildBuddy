package com.build.buddyai.core.model

data class AiProvider(
    val id: String,
    val name: String,
    val baseUrl: String,
    val iconResName: String? = null,
    val requiresApiKey: Boolean = true,
    val supportsStreaming: Boolean = true,
    val models: List<AiModel> = emptyList()
)

data class AiModel(
    val id: String,
    val name: String,
    val providerId: String,
    val contextWindow: Int = 8192,
    val maxOutputTokens: Int = 4096,
    val supportsStreaming: Boolean = true,
    val isDefault: Boolean = false
)

data class AiProviderConfig(
    val providerId: String,
    val apiKey: String,
    val isEnabled: Boolean = true,
    val selectedModelId: String? = null,
    val customBaseUrl: String? = null
)

data class ModelParameters(
    val temperature: Float = 0.7f,
    val maxTokens: Int = 4096,
    val topP: Float = 0.95f
)

sealed class AiError {
    data class InvalidApiKey(val providerId: String) : AiError()
    data class NetworkError(val message: String) : AiError()
    data class RateLimited(val retryAfterMs: Long?) : AiError()
    data class ProviderUnavailable(val providerId: String) : AiError()
    data class MalformedResponse(val details: String) : AiError()
    data class Unknown(val throwable: Throwable) : AiError()
}

object DefaultProviders {
    val NVIDIA = AiProvider(
        id = "nvidia",
        name = "NVIDIA",
        baseUrl = "https://integrate.api.nvidia.com/v1",
        supportsStreaming = true,
        models = listOf(
            AiModel("nvidia/llama-3.1-nemotron-ultra-253b-v1", "Nemotron Ultra 253B", "nvidia", 131072, 4096),
            AiModel("nvidia/llama-3.3-nemotron-super-49b-v1", "Nemotron Super 49B", "nvidia", 32768, 4096),
            AiModel("deepseek-ai/deepseek-r1", "DeepSeek R1", "nvidia", 65536, 8192),
        )
    )

    val OPEN_ROUTER = AiProvider(
        id = "openrouter",
        name = "OpenRouter",
        baseUrl = "https://openrouter.ai/api/v1",
        supportsStreaming = true,
        models = listOf(
            AiModel("anthropic/claude-sonnet-4", "Claude Sonnet 4", "openrouter", 200000, 16384),
            AiModel("google/gemini-2.5-pro-preview", "Gemini 2.5 Pro", "openrouter", 1048576, 65536),
            AiModel("deepseek/deepseek-r1", "DeepSeek R1", "openrouter", 65536, 8192),
            AiModel("meta-llama/llama-4-maverick", "Llama 4 Maverick", "openrouter", 1048576, 65536),
        )
    )

    val GEMINI = AiProvider(
        id = "gemini",
        name = "Gemini",
        baseUrl = "https://generativelanguage.googleapis.com/v1beta",
        supportsStreaming = true,
        models = listOf(
            AiModel("gemini-2.5-pro-preview-05-06", "Gemini 2.5 Pro", "gemini", 1048576, 65536),
            AiModel("gemini-2.5-flash-preview-05-20", "Gemini 2.5 Flash", "gemini", 1048576, 65536),
            AiModel("gemini-2.0-flash", "Gemini 2.0 Flash", "gemini", 1048576, 8192),
        )
    )

    val ALL = listOf(NVIDIA, OPEN_ROUTER, GEMINI)
}
