package com.build.buddyai.core.network

import com.build.buddyai.core.model.ProviderType

object ModelCapabilityRegistry {
    fun supportsVision(providerType: ProviderType, modelId: String): Boolean {
        val id = modelId.lowercase()
        return when (providerType) {
            ProviderType.GEMINI -> id.contains("gemini")
            ProviderType.OPENROUTER,
            ProviderType.PAXSENIX -> listOf(
                "gpt-4o", "gpt-4.1", "claude-3", "gemini", "vision", "llava", "pixtral", "qwen-vl", "internvl"
            ).any { id.contains(it) }
            ProviderType.NVIDIA -> listOf("vision", "vl", "vila", "llava").any { id.contains(it) }
        }
    }

    fun supportedImageExtensions(): Set<String> = setOf("jpg", "jpeg", "png", "webp")
}
