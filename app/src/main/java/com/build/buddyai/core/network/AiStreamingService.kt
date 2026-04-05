package com.build.buddyai.core.network

import android.util.Base64
import com.build.buddyai.core.model.ProviderType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
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
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.io.File
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
        messages: List<AiChatMessage>,
        temperature: Float = 0.7f,
        maxTokens: Int = 4096,
        topP: Float = 0.9f
    ): Flow<StreamEvent> = callbackFlow {
        val request = when (providerType) {
            ProviderType.NVIDIA -> buildChatCompletionsStreamRequest(
                baseUrl = "${ProviderType.NVIDIA.baseUrl}/chat/completions",
                apiKeyHeader = "Authorization",
                apiKeyValue = "Bearer $apiKey",
                modelId = modelId,
                messages = messages,
                temperature = temperature,
                maxTokens = maxTokens,
                topP = topP
            )
            ProviderType.OPENROUTER -> buildChatCompletionsStreamRequest(
                baseUrl = "${ProviderType.OPENROUTER.baseUrl}/chat/completions",
                apiKeyHeader = "Authorization",
                apiKeyValue = "Bearer $apiKey",
                modelId = modelId,
                messages = messages,
                temperature = temperature,
                maxTokens = maxTokens,
                topP = topP,
                extraHeaders = mapOf("HTTP-Referer" to "com.build.buddyai", "X-Title" to "BuildBuddy")
            )
            ProviderType.GEMINI -> buildGeminiStreamRequest(apiKey, modelId, messages, temperature, maxTokens, topP)
            ProviderType.PAXSENIX -> buildChatCompletionsStreamRequest(
                baseUrl = "${ProviderType.PAXSENIX.baseUrl}/chat/completions",
                apiKeyHeader = "Authorization",
                apiKeyValue = "Bearer $apiKey",
                modelId = modelId,
                messages = messages,
                temperature = temperature,
                maxTokens = maxTokens,
                topP = topP
            )
        }

        val factory = EventSources.createFactory(client)
        val listener = object : EventSourceListener() {
            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                if (data.isBlank()) return
                if (data == "[DONE]") {
                    trySend(StreamEvent.Done)
                    return
                }
                try {
                    val token = when (providerType) {
                        ProviderType.NVIDIA, ProviderType.OPENROUTER, ProviderType.PAXSENIX -> extractChatCompletionsDelta(data)
                        ProviderType.GEMINI -> extractGeminiDelta(data)
                    }
                    if (!token.isNullOrEmpty()) trySend(StreamEvent.Token(token))
                } catch (e: Exception) {
                    trySend(StreamEvent.Error("Malformed stream payload: ${e.message}"))
                }
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

    fun streamMessage(
        providerType: ProviderType,
        apiKey: String,
        modelId: String,
        messages: List<Map<String, String>>,
        temperature: Float = 0.7f,
        maxTokens: Int = 4096,
        topP: Float = 0.9f
    ): Flow<StreamEvent> = streamMessage(
        providerType = providerType,
        apiKey = apiKey,
        modelId = modelId,
        messages = messages.map { AiChatMessage(role = it["role"].orEmpty(), text = it["content"].orEmpty()) },
        temperature = temperature,
        maxTokens = maxTokens,
        topP = topP
    )

    private fun buildChatCompletionsStreamRequest(
        baseUrl: String,
        apiKeyHeader: String,
        apiKeyValue: String,
        modelId: String,
        messages: List<AiChatMessage>,
        temperature: Float,
        maxTokens: Int,
        topP: Float,
        extraHeaders: Map<String, String> = emptyMap()
    ): Request {
        val body = buildJsonObject {
            put("model", modelId)
            putJsonArray("messages") {
                messages.forEach { msg -> add(openAiMessage(msg)) }
            }
            put("temperature", temperature)
            put("max_tokens", maxTokens)
            put("top_p", topP)
            put("stream", true)
        }
        return Request.Builder()
            .url(baseUrl)
            .addHeader(apiKeyHeader, apiKeyValue)
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "text/event-stream")
            .apply { extraHeaders.forEach { (k, v) -> addHeader(k, v) } }
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()
    }

    private fun buildGeminiStreamRequest(
        apiKey: String,
        modelId: String,
        messages: List<AiChatMessage>,
        temperature: Float,
        maxTokens: Int,
        topP: Float
    ): Request {
        val systemInstruction = messages.filter { it.role == "system" }
            .joinToString("\n\n") { it.text }
            .trim()
        val body = buildJsonObject {
            if (systemInstruction.isNotBlank()) {
                putJsonObject("systemInstruction") {
                    putJsonArray("parts") { add(buildJsonObject { put("text", systemInstruction) }) }
                }
            }
            putJsonArray("contents") {
                messages.filter { it.role != "system" }.forEach { msg ->
                    add(buildJsonObject {
                        put("role", if (msg.role == "assistant") "model" else "user")
                        putJsonArray("parts") {
                            if (msg.text.isNotBlank()) add(buildJsonObject { put("text", msg.text) })
                            msg.imagePaths.forEach { path -> add(geminiInlineImagePart(path)) }
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
        return Request.Builder()
            .url("${ProviderType.GEMINI.baseUrl}/models/$modelId:streamGenerateContent?alt=sse")
            .addHeader("x-goog-api-key", apiKey)
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "text/event-stream")
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()
    }

    private fun openAiMessage(message: AiChatMessage) = buildJsonObject {
        put("role", message.role)
        if (message.imagePaths.isEmpty()) {
            put("content", message.text)
        } else {
            putJsonArray("content") {
                if (message.text.isNotBlank()) add(buildJsonObject {
                    put("type", "text")
                    put("text", message.text)
                })
                message.imagePaths.forEach { path ->
                    add(buildJsonObject {
                        put("type", "image_url")
                        putJsonObject("image_url") { put("url", fileToDataUrl(path)) }
                    })
                }
            }
        }
    }

    private fun geminiInlineImagePart(path: String) = buildJsonObject {
        putJsonObject("inlineData") {
            put("mimeType", mimeTypeFor(path))
            put("data", Base64.encodeToString(File(path).readBytes(), Base64.NO_WRAP))
        }
    }

    private fun extractChatCompletionsDelta(data: String): String? {
        val jsonData = json.parseToJsonElement(data).jsonObject
        return jsonData["choices"]?.jsonArray?.firstOrNull()?.jsonObject
            ?.get("delta")?.jsonObject
            ?.get("content")?.let { content ->
                if (content is kotlinx.serialization.json.JsonPrimitive) content.contentOrNull
                else content.toString()
            }
    }

    private fun extractGeminiDelta(data: String): String? {
        val jsonData = json.parseToJsonElement(data).jsonObject
        return jsonData["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
            ?.get("content")?.jsonObject
            ?.get("parts")?.jsonArray
            ?.joinToString(separator = "") { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull.orEmpty() }
            ?.takeIf { it.isNotBlank() }
    }

    private fun fileToDataUrl(path: String): String {
        val bytes = File(path).readBytes()
        return "data:${mimeTypeFor(path)};base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    private fun mimeTypeFor(path: String): String = when (path.substringAfterLast('.', "").lowercase()) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "webp" -> "image/webp"
        else -> "application/octet-stream"
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}
