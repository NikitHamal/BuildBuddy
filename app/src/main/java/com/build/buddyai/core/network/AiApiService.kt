package com.build.buddyai.core.network

import com.build.buddyai.core.model.AiModel
import com.build.buddyai.core.model.ProviderType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiApiService @Inject constructor(
    private val client: OkHttpClient,
    private val json: Json
) {
    suspend fun fetchModels(
        providerType: ProviderType,
        apiKey: String
    ): Result<List<AiModel>> = withContext(Dispatchers.IO) {
        try {
            val request = when (providerType) {
                ProviderType.NVIDIA -> Request.Builder()
                    .url("${ProviderType.NVIDIA.baseUrl}/models")
                    .addHeader("Authorization", "Bearer $apiKey")
                    .build()
                ProviderType.OPENROUTER -> Request.Builder()
                    .url("${ProviderType.OPENROUTER.baseUrl}/models")
                    .addHeader("Authorization", "Bearer $apiKey")
                    .addHeader("HTTP-Referer", "com.build.buddyai")
                    .addHeader("X-Title", "BuildBuddy")
                    .build()
                ProviderType.GEMINI -> Request.Builder()
                    .url("${ProviderType.GEMINI.baseUrl}/models")
                    .addHeader("x-goog-api-key", apiKey)
                    .build()
            }

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("Failed to fetch models: ${response.code}"))
                }
                val body = response.body?.string() ?: return@withContext Result.failure(Exception("Empty response"))
                Result.success(parseModels(providerType, body))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Network error: ${e.message}"))
        }
    }

    suspend fun testConnection(providerType: ProviderType, apiKey: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val request = when (providerType) {
                ProviderType.NVIDIA -> buildNvidiaRequest(apiKey, "meta/llama-3.1-8b-instruct", listOf(mapOf("role" to "user", "content" to "hi")), 0.1f, 5, 1f)
                ProviderType.OPENROUTER -> buildOpenRouterRequest(apiKey, "meta-llama/llama-3.1-8b-instruct:free", listOf(mapOf("role" to "user", "content" to "hi")), 0.1f, 5, 1f)
                ProviderType.GEMINI -> buildGeminiRequest(apiKey, "gemini-1.5-flash", listOf(mapOf("role" to "user", "content" to "hi")), 0.1f, 5, 1f)
            }

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Result.success(true)
                } else {
                    Result.failure(Exception(describeError(response.code, response.body?.string())))
                }
            }
        } catch (e: Exception) {
            Result.failure(Exception("Network error: ${e.message}"))
        }
    }

    suspend fun sendMessage(
        providerType: ProviderType,
        apiKey: String,
        modelId: String,
        messages: List<Map<String, String>>,
        temperature: Float = 0.7f,
        maxTokens: Int = 4096,
        topP: Float = 0.9f
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val request = when (providerType) {
                ProviderType.NVIDIA -> buildNvidiaRequest(apiKey, modelId, messages, temperature, maxTokens, topP)
                ProviderType.OPENROUTER -> buildOpenRouterRequest(apiKey, modelId, messages, temperature, maxTokens, topP)
                ProviderType.GEMINI -> buildGeminiRequest(apiKey, modelId, messages, temperature, maxTokens, topP)
            }

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception(describeError(response.code, response.body?.string())))
                }
                val body = response.body?.string() ?: return@withContext Result.failure(Exception("Empty response"))
                Result.success(extractContent(providerType, body))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Network error: ${e.message}"))
        }
    }

    private fun buildNvidiaRequest(
        apiKey: String,
        modelId: String,
        messages: List<Map<String, String>>,
        temperature: Float,
        maxTokens: Int,
        topP: Float
    ): Request = Request.Builder()
        .url("${ProviderType.NVIDIA.baseUrl}/chat/completions")
        .addHeader("Authorization", "Bearer $apiKey")
        .addHeader("Content-Type", "application/json")
        .post(buildChatCompletionsBody(modelId, messages, temperature, maxTokens, topP).toString().toRequestBody(JSON_MEDIA_TYPE))
        .build()

    private fun buildOpenRouterRequest(
        apiKey: String,
        modelId: String,
        messages: List<Map<String, String>>,
        temperature: Float,
        maxTokens: Int,
        topP: Float
    ): Request = Request.Builder()
        .url("${ProviderType.OPENROUTER.baseUrl}/chat/completions")
        .addHeader("Authorization", "Bearer $apiKey")
        .addHeader("Content-Type", "application/json")
        .addHeader("HTTP-Referer", "com.build.buddyai")
        .addHeader("X-Title", "BuildBuddy")
        .post(buildChatCompletionsBody(modelId, messages, temperature, maxTokens, topP).toString().toRequestBody(JSON_MEDIA_TYPE))
        .build()

    private fun buildGeminiRequest(
        apiKey: String,
        modelId: String,
        messages: List<Map<String, String>>,
        temperature: Float,
        maxTokens: Int,
        topP: Float
    ): Request = Request.Builder()
        .url("${ProviderType.GEMINI.baseUrl}/models/$modelId:generateContent")
        .addHeader("x-goog-api-key", apiKey)
        .addHeader("Content-Type", "application/json")
        .post(buildGeminiBody(messages, temperature, maxTokens, topP).toString().toRequestBody(JSON_MEDIA_TYPE))
        .build()

    private fun buildChatCompletionsBody(
        modelId: String,
        messages: List<Map<String, String>>,
        temperature: Float,
        maxTokens: Int,
        topP: Float
    ) = buildJsonObject {
        put("model", modelId)
        putJsonArray("messages") {
            messages.forEach { message ->
                add(buildJsonObject {
                    put("role", message["role"])
                    put("content", message["content"].orEmpty())
                })
            }
        }
        put("temperature", temperature)
        put("max_tokens", maxTokens)
        put("top_p", topP)
    }

    private fun buildGeminiBody(
        messages: List<Map<String, String>>,
        temperature: Float,
        maxTokens: Int,
        topP: Float
    ) = buildJsonObject {
        val systemInstruction = messages.filter { it["role"] == "system" }
            .joinToString("\n\n") { it["content"].orEmpty() }
            .trim()
        if (systemInstruction.isNotBlank()) {
            putJsonObject("systemInstruction") {
                putJsonArray("parts") {
                    add(buildJsonObject { put("text", systemInstruction) })
                }
            }
        }
        putJsonArray("contents") {
            messages.filter { it["role"] != "system" }.forEach { message ->
                add(buildJsonObject {
                    put("role", if (message["role"] == "assistant") "model" else "user")
                    putJsonArray("parts") {
                        add(buildJsonObject { put("text", message["content"].orEmpty()) })
                    }
                })
            }
        }
        putJsonObject("generationConfig") {
            put("temperature", temperature)
            put("maxOutputTokens", maxTokens)
            put("topP", topP)
        }
    }

    private fun parseModels(providerType: ProviderType, body: String): List<AiModel> {
        val root = json.parseToJsonElement(body).jsonObject
        return when (providerType) {
            ProviderType.NVIDIA, ProviderType.OPENROUTER -> root["data"]?.jsonArray?.mapNotNull { element ->
                val item = element.jsonObject
                val id = item["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                AiModel(
                    id = id,
                    name = item["name"]?.jsonPrimitive?.contentOrNull ?: id,
                    providerId = providerType.name.lowercase(),
                    providerType = providerType,
                    contextWindow = item["context_length"]?.jsonPrimitive?.intOrNull ?: 4096,
                    supportsStreaming = true,
                    description = item["description"]?.jsonPrimitive?.contentOrNull.orEmpty()
                )
            } ?: emptyList()
            ProviderType.GEMINI -> root["models"]?.jsonArray?.mapNotNull { element ->
                val item = element.jsonObject
                val name = item["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val id = name.substringAfterLast('/')
                AiModel(
                    id = id,
                    name = item["displayName"]?.jsonPrimitive?.contentOrNull ?: id,
                    providerId = providerType.name.lowercase(),
                    providerType = providerType,
                    contextWindow = item["inputTokenLimit"]?.jsonPrimitive?.intOrNull ?: 4096,
                    supportsStreaming = true,
                    description = item["description"]?.jsonPrimitive?.contentOrNull.orEmpty()
                )
            } ?: emptyList()
        }
    }

    private fun extractContent(providerType: ProviderType, body: String): String {
        val root = json.parseToJsonElement(body).jsonObject
        return when (providerType) {
            ProviderType.NVIDIA, ProviderType.OPENROUTER -> root["choices"]?.jsonArray?.firstOrNull()?.jsonObject
                ?.get("message")?.jsonObject
                ?.get("content")?.jsonPrimitive?.contentOrNull.orEmpty()
            ProviderType.GEMINI -> root["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
                ?.get("content")?.jsonObject
                ?.get("parts")?.jsonArray
                ?.joinToString(separator = "") { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull.orEmpty() }
                .orEmpty()
        }
    }

    private fun describeError(code: Int, body: String?): String = when (code) {
        401, 403 -> "Invalid API key"
        429 -> "Rate limited. Please wait and try again."
        in 500..599 -> "Provider service error ($code)"
        else -> "Error $code: ${body ?: "Unknown error"}"
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}
