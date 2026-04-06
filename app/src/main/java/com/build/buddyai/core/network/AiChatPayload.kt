package com.build.buddyai.core.network

import kotlinx.serialization.Serializable

@Serializable
data class AiChatMessage(
    val role: String,
    val text: String,
    val imagePaths: List<String> = emptyList()
)
