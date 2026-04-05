package com.build.buddyai.core.network

data class AiChatMessage(
    val role: String,
    val text: String,
    val imagePaths: List<String> = emptyList()
)
