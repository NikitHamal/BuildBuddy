package com.build.buddyai.core.network

import com.build.buddyai.core.model.ProviderType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import javax.inject.Inject
import javax.inject.Singleton

sealed class StreamEvent {
    data class Token(val content: String) : StreamEvent()
    data object Done : StreamEvent()
    data class Error(val message: String) : StreamEvent()
}

@Singleton
class AiStreamingService @Inject constructor(
    private val client: OkHttpClient,
    private val json: Json
) {
    fun streamMessage(
        providerType: ProviderType,
        apiKey: String,
        modelId: String,
        messages: List<Map<String, String>>,
        temperature: Float = 0.7f,
        maxTokens: Int = 4096,
        topP: Float = 0.9f
    ): Flow<StreamEvent> = callbackFlow {
        val request = when (providerType) {
            ProviderType.NVIDIA -> buildNvidiaStreamRequest(apiKey, modelId, messages, temperature, maxTokens, topP)
            ProviderType.OPENROUTER -> buildOpenRouterStreamRequest(apiKey, modelId, messages, temperature, maxTokens, topP)
            ProviderType.GEMINI -> {
                // Gemini uses different streaming - fall back to non-streaming with chunked simulation
                val nonStreamRequest = buildGeminiNonStreamRequest(apiKey, modelId, messages, temperature, maxTokens, topP)
                val response = client.newCall(nonStreamRequest).execute()
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    val content = extractGeminiContent(body)
                    // Simulate streaming by chunking
                    content.chunked(20).forEach { chunk ->
                        trySend(StreamEvent.Token(chunk))
                    }
                    trySend(StreamEvent.Done)
                } else {
                    trySend(StreamEvent.Error("Gemini error: ${response.code}"))
                }
                close()
                return@callbackFlow
            }
        }

        val factory = EventSources.createFactory(client)
        val listener = object : EventSourceListener() {
            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                if (data == "[DONE]") {
                    trySend(StreamEvent.Done)
                    return
                }
                try {
                    val jsonData = json.parseToJsonElement(data).jsonObject
                    val content = jsonData["choices"]?.jsonArray?.firstOrNull()
                        ?.jsonObject?.get("delta")
                        ?.jsonObject?.get("content")
                        ?.jsonPrimitive?.contentOrNull
                    if (content != null) {
                        trySend(StreamEvent.Token(content))
                    }
                } catch (_: Exception) {}
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                trySend(StreamEvent.Error(t?.message ?: "Stream error: ${response?.code}"))
                close()
            }

            override fun onClosed(eventSource: EventSource) {
                trySend(StreamEvent.Done)
                close()
            }
        }

        val eventSource = factory.newEventSource(request, listener)
        awaitClose { eventSource.cancel() }
    }.flowOn(Dispatchers.IO)

    private fun buildNvidiaStreamRequest(
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
            put("stream", true)
        }
        return Request.Builder()
            .url("${ProviderType.NVIDIA.baseUrl}/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "text/event-stream")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
    }

    private fun buildOpenRouterStreamRequest(
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
            put("stream", true)
        }
        return Request.Builder()
            .url("${ProviderType.OPENROUTER.baseUrl}/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "text/event-stream")
            .addHeader("HTTP-Referer", "com.build.buddyai")
            .addHeader("X-Title", "BuildBuddy")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
    }

    private fun buildGeminiNonStreamRequest(
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

    private fun extractGeminiContent(body: String): String {
        return try {
            val jsonResponse = json.parseToJsonElement(body).jsonObject
            jsonResponse["candidates"]?.jsonArray?.firstOrNull()
                ?.jsonObject?.get("content")
                ?.jsonObject?.get("parts")
                ?.jsonArray?.firstOrNull()
                ?.jsonObject?.get("text")
                ?.jsonPrimitive?.content ?: ""
        } catch (_: Exception) { "" }
    }
}
