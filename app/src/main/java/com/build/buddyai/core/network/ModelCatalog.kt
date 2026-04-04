package com.build.buddyai.core.network

import com.build.buddyai.core.model.AiModel
import com.build.buddyai.core.model.ProviderType

object ModelCatalog {
    fun getModelsForProvider(providerType: ProviderType): List<AiModel> = when (providerType) {
        ProviderType.NVIDIA -> listOf(
            AiModel("meta/llama-3.1-405b-instruct", "Llama 3.1 405B", providerType.name, providerType, 128000, true, "Most capable open model"),
            AiModel("meta/llama-3.1-70b-instruct", "Llama 3.1 70B", providerType.name, providerType, 128000, true, "Strong balance of capability and speed"),
            AiModel("meta/llama-3.1-8b-instruct", "Llama 3.1 8B", providerType.name, providerType, 128000, true, "Fast and efficient"),
            AiModel("nvidia/nemotron-4-340b-instruct", "Nemotron-4 340B", providerType.name, providerType, 4096, true, "NVIDIA's flagship model"),
            AiModel("mistralai/mixtral-8x22b-instruct-v0.1", "Mixtral 8x22B", providerType.name, providerType, 65536, true, "Mixture of experts"),
            AiModel("google/gemma-2-27b-it", "Gemma 2 27B", providerType.name, providerType, 8192, true, "Google's open model"),
            AiModel("microsoft/phi-3-medium-128k-instruct", "Phi-3 Medium 128K", providerType.name, providerType, 128000, true, "Microsoft's compact model"),
            AiModel("deepseek-ai/deepseek-coder-v2", "DeepSeek Coder V2", providerType.name, providerType, 128000, true, "Optimized for code generation")
        )
        ProviderType.OPENROUTER -> listOf(
            AiModel("anthropic/claude-3.5-sonnet", "Claude 3.5 Sonnet", providerType.name, providerType, 200000, true, "Best for complex coding tasks"),
            AiModel("openai/gpt-4o", "GPT-4o", providerType.name, providerType, 128000, true, "OpenAI's flagship model"),
            AiModel("openai/gpt-4o-mini", "GPT-4o Mini", providerType.name, providerType, 128000, true, "Fast and affordable"),
            AiModel("google/gemini-pro-1.5", "Gemini 1.5 Pro", providerType.name, providerType, 2000000, true, "Largest context window"),
            AiModel("meta-llama/llama-3.1-405b-instruct", "Llama 3.1 405B", providerType.name, providerType, 128000, true, "Most capable open model"),
            AiModel("meta-llama/llama-3.1-70b-instruct", "Llama 3.1 70B", providerType.name, providerType, 128000, true, "Strong balance of capability and speed"),
            AiModel("deepseek/deepseek-chat", "DeepSeek Chat", providerType.name, providerType, 128000, true, "Strong code generation"),
            AiModel("mistralai/mistral-large", "Mistral Large", providerType.name, providerType, 128000, true, "Mistral's most capable model"),
            AiModel("meta-llama/llama-3.1-8b-instruct:free", "Llama 3.1 8B (Free)", providerType.name, providerType, 128000, true, "Free tier model")
        )
        ProviderType.GEMINI -> listOf(
            AiModel("gemini-1.5-pro", "Gemini 1.5 Pro", providerType.name, providerType, 2000000, false, "Best for complex reasoning"),
            AiModel("gemini-1.5-flash", "Gemini 1.5 Flash", providerType.name, providerType, 1000000, false, "Fast and efficient"),
            AiModel("gemini-1.5-flash-8b", "Gemini 1.5 Flash-8B", providerType.name, providerType, 1000000, false, "Fastest, most lightweight"),
            AiModel("gemini-1.0-pro", "Gemini 1.0 Pro", providerType.name, providerType, 32000, false, "Previous generation")
        )
    }
}
