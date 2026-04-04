package com.build.buddyai.core.network

import com.build.buddyai.core.model.ProviderType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
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
    ): Result<List<com.build.buddyai.core.model.AiModel>> = withContext(Dispatchers.IO) {
        try {
            val request = when (providerType) {
                ProviderType.NVIDIA -> buildNvidiaModelsRequest(apiKey)
                ProviderType.OPENROUTER -> buildOpenRouterModelsRequest(apiKey)
                ProviderType.GEMINI -> buildGeminiModelsRequest(apiKey)
            }
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: return@withContext Result.failure(Exception("Empty response"))
                val models = parseModels(providerType, body)
                Result.success(models)
            } else {
                Result.failure(Exception("Failed to fetch models: ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Network error: ${e.message}"))
        }
    }

    suspend fun testConnection(providerType: ProviderType, apiKey: String): Result<Boolean> =
        withContext(Dispatchers.IO) {
            try {
                val request = when (providerType) {
                    ProviderType.NVIDIA -> buildNvidiaTestRequest(apiKey)
                    ProviderType.OPENROUTER -> buildOpenRouterTestRequest(apiKey)
                    ProviderType.GEMINI -> buildGeminiTestRequest(apiKey)
                }
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    Result.success(true)
                } else {
                    val errorBody = response.body?.string() ?: "Unknown error"
                    val errorMsg = when (response.code) {
                        401, 403 -> "Invalid API key"
                        429 -> "Rate limited. Please wait and try again."
                        in 500..599 -> "Provider service error (${response.code})"
                        else -> "Error ${response.code}: $errorBody"
                    }
                    Result.failure(Exception(errorMsg))
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
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: return@withContext Result.failure(Exception("Empty response"))
                val content = extractContent(providerType, body)
                Result.success(content)
            } else {
                val errorBody = response.body?.string() ?: "Unknown error"
                val errorMsg = when (response.code) {
                    401, 403 -> "Invalid API key"
                    429 -> "Rate limited. Please wait and try again."
                    in 500..599 -> "Provider service error (${response.code})"
                    else -> "Error ${response.code}: $errorBody"
                }
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Network error: ${e.message}"))
        }
    }

    private fun buildNvidiaTestRequest(apiKey: String): Request {
        val body = buildJsonObject {
            put("model", "meta/llama-3.1-8b-instruct")
            putJsonArray("messages") {
                addJsonObject {
                    put("role", "user")
                    put("content", "hi")
                }
            }
            put("max_tokens", 5)
        }
        return Request.Builder()
            .url("${ProviderType.NVIDIA.baseUrl}/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
    }

    private fun buildOpenRouterTestRequest(apiKey: String): Request {
        val body = buildJsonObject {
            put("model", "meta-llama/llama-3.1-8b-instruct:free")
            putJsonArray("messages") {
                addJsonObject {
                    put("role", "user")
                    put("content", "hi")
                }
            }
            put("max_tokens", 5)
        }
        return Request.Builder()
            .url("${ProviderType.OPENROUTER.baseUrl}/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .addHeader("HTTP-Referer", "com.build.buddyai")
            .addHeader("X-Title", "BuildBuddy")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
    }

    private fun buildGeminiTestRequest(apiKey: String): Request {
        val body = buildJsonObject {
            putJsonArray("contents") {
                addJsonObject {
                    putJsonArray("parts") {
                        addJsonObject { put("text", "hi") }
                    }
                }
            }
            putJsonObject("generationConfig") {
                put("maxOutputTokens", 5)
            }
        }
        return Request.Builder()
            .url("${ProviderType.GEMINI.baseUrl}/models/gemini-1.5-flash:generateContent?key=$apiKey")
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
    }

    private fun buildNvidiaRequest(
        apiKey: String, modelId: String, messages: List<Map<String, String>>,
        temperature: Float, maxTokens: Int, topP: Float
    ): Request {
        val body = buildJsonObject {
            put("model", modelId)
            putJsonArray("messages") {
                messages.forEach { msg ->
                    addJsonObject {
                        put("role", msg["role"])
                        put("content", msg["content"])
                    }
                }
            }
            put("temperature", temperature)
            put("max_tokens", maxTokens)
            put("top_p", topP)
        }
        return Request.Builder()
            .url("${ProviderType.NVIDIA.baseUrl}/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
    }

    private fun buildOpenRouterRequest(
        apiKey: String, modelId: String, messages: List<Map<String, String>>,
        temperature: Float, maxTokens: Int, topP: Float
    ): Request {
        val body = buildJsonObject {
            put("model", modelId)
            putJsonArray("messages") {
                messages.forEach { msg ->
                    addJsonObject {
                        put("role", msg["role"])
                        put("content", msg["content"])
                    }
                }
            }
            put("temperature", temperature)
            put("max_tokens", maxTokens)
            put("top_p", topP)
        }
        return Request.Builder()
            .url("${ProviderType.OPENROUTER.baseUrl}/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .addHeader("HTTP-Referer", "com.build.buddyai")
            .addHeader("X-Title", "BuildBuddy")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
    }

    private fun buildGeminiRequest(
        apiKey: String, modelId: String, messages: List<Map<String, String>>,
        temperature: Float, maxTokens: Int, topP: Float
    ): Request {
        val body = buildJsonObject {
            putJsonArray("contents") {
                messages.forEach { msg ->
                    addJsonObject {
                        put("role", if (msg["role"] == "assistant") "model" else "user")
                        putJsonArray("parts") {
                            addJsonObject { put("text", msg["content"]) }
                        }
                    }
                }
            }
            putJsonObject("generationConfig") {
                put("temperature", temperature)
                put("maxOutputTokens", maxTokens)
                put("topP", topP)
            }
        }
        return Request.Builder()
            .url("${ProviderType.GEMINI.baseUrl}/models/$modelId:generateContent?key=$apiKey")
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
    }

    private fun buildNvidiaModelsRequest(apiKey: String): Request {
        return Request.Builder()
            .url("${ProviderType.NVIDIA.baseUrl}/models")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .get()
            .build()
    }

    private fun buildOpenRouterModelsRequest(apiKey: String): Request {
        return Request.Builder()
            .url("${ProviderType.OPENROUTER.baseUrl}/models")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .addHeader("HTTP-Referer", "com.build.buddyai")
            .addHeader("X-Title", "BuildBuddy")
            .get()
            .build()
    }

    private fun buildGeminiModelsRequest(apiKey: String): Request {
        return Request.Builder()
            .url("${ProviderType.GEMINI.baseUrl}/models?key=$apiKey")
            .addHeader("Content-Type", "application/json")
            .get()
            .build()
    }

    private fun parseModels(
        providerType: ProviderType,
        responseBody: String
    ): List<com.build.buddyai.core.model.AiModel> {
        val jsonResponse = json.parseToJsonElement(responseBody).jsonObject
        val rawModels = when (providerType) {
            ProviderType.NVIDIA, ProviderType.OPENROUTER -> {
                jsonResponse["data"]?.jsonArray ?: emptyList()
            }
            ProviderType.GEMINI -> {
                jsonResponse["models"]?.jsonArray ?: emptyList()
            }
        }

        return rawModels.mapNotNull { modelElement ->
            val modelObj = modelElement.jsonObject
            val id = when (providerType) {
                ProviderType.NVIDIA, ProviderType.OPENROUTER -> {
                    modelObj["id"]?.jsonPrimitive?.content
                }
                ProviderType.GEMINI -> {
                    modelObj["name"]?.jsonPrimitive?.content?.removePrefix("models/")
                }
            } ?: return@mapNotNull null

            // Filter out deprecated Gemini 1.5 Pro models
            if (id.contains("1.5-pro", ignoreCase = true) ||
                (id.contains("gemini-pro", ignoreCase = true) && !id.contains("2.", ignoreCase = true))) {
                return@mapNotNull null
            }

            val contextWindow = modelObj["context_length"]?.jsonPrimitive?.long?.toInt()
                ?: modelObj["input_token_limit"]?.jsonPrimitive?.long?.toInt()
                ?: 4096

            val name = modelObj["name"]?.jsonPrimitive?.content
                ?: id.substringAfterLast("/").replace("-", " ").replaceFirstChar { it.uppercase() }

            com.build.buddyai.core.model.AiModel(
                id = id,
                name = name,
                providerId = providerType.name,
                providerType = providerType,
                contextWindow = contextWindow,
                supportsStreaming = true,
                description = ""
            )
        }
    }

    private fun extractContent(providerType: ProviderType, responseBody: String): String {
        val jsonResponse = json.parseToJsonElement(responseBody).jsonObject
        return when (providerType) {
            ProviderType.NVIDIA, ProviderType.OPENROUTER -> {
                jsonResponse["choices"]?.jsonArray?.firstOrNull()
                    ?.jsonObject?.get("message")
                    ?.jsonObject?.get("content")
                    ?.jsonPrimitive?.content ?: ""
            }
            ProviderType.GEMINI -> {
                jsonResponse["candidates"]?.jsonArray?.firstOrNull()
                    ?.jsonObject?.get("content")
                    ?.jsonObject?.get("parts")
                    ?.jsonArray?.firstOrNull()
                    ?.jsonObject?.get("text")
                    ?.jsonPrimitive?.content ?: ""
            }
        }
    }
}
