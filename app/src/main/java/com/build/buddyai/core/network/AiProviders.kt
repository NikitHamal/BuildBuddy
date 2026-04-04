package com.build.buddyai.core.network

import com.build.buddyai.core.data.secure.SecureStore
import com.build.buddyai.core.model.AgentEnvelope
import com.build.buddyai.core.model.AgentRequest
import com.build.buddyai.core.model.AiStreamEvent
import com.build.buddyai.core.model.ModelDescriptor
import com.build.buddyai.core.model.ProviderConnectionResult
import com.build.buddyai.core.model.ProviderId
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources

interface AiProviderClient {
    val providerId: ProviderId
    suspend fun testConnection(apiKey: String): ProviderConnectionResult
    suspend fun listModels(apiKey: String): List<ModelDescriptor>
    fun stream(request: AgentRequest, apiKey: String): Flow<AiStreamEvent>
}

@Singleton
class ProviderRegistry @Inject constructor(
    private val clients: Set<@JvmSuppressWildcards AiProviderClient>,
    private val secureStore: SecureStore,
) {
    fun client(providerId: ProviderId): AiProviderClient = clients.first { it.providerId == providerId }

    fun apiKey(providerId: ProviderId): String? = secureStore.getApiKey(providerId)
}

class OpenRouterProvider @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val json: Json,
) : AiProviderClient {
    override val providerId: ProviderId = ProviderId.OPENROUTER

    override suspend fun testConnection(apiKey: String): ProviderConnectionResult {
        val request = Request.Builder()
            .url("https://openrouter.ai/api/v1/models")
            .header("Authorization", "Bearer $apiKey")
            .build()
        return runRequest(request) { payload ->
            val models = payload["data"]?.jsonArray.orEmpty().take(6).map { element ->
                val id = element.jsonObject["id"]?.jsonPrimitive?.content.orEmpty()
                ModelDescriptor(id, id, supportsStreaming = true, supportsToolUse = true)
            }
            ProviderConnectionResult(true, "Connected to OpenRouter", models)
        }
    }

    override suspend fun listModels(apiKey: String): List<ModelDescriptor> =
        testConnection(apiKey).models

    override fun stream(request: AgentRequest, apiKey: String): Flow<AiStreamEvent> = sseFlow(
        url = "https://openrouter.ai/api/v1/chat/completions",
        apiKey = apiKey,
        body = buildOpenAiStyleBody(request),
        chunkExtractor = { payload ->
            payload["choices"]?.jsonArray?.firstOrNull()
                ?.jsonObject?.get("delta")
                ?.jsonObject?.get("content")
                ?.jsonPrimitive?.content
        },
    )

    private suspend fun runRequest(request: Request, mapper: (JsonObject) -> ProviderConnectionResult): ProviderConnectionResult =
        runCatching {
            okHttpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return ProviderConnectionResult(false, body.ifBlank { "Request failed" })
                }
                mapper(json.parseToJsonElement(body).jsonObject)
            }
        }.getOrElse { ProviderConnectionResult(false, it.message ?: "Unknown provider error") }

    private fun buildOpenAiStyleBody(request: AgentRequest): String = json.encodeToString(
        JsonObject.serializer(),
        buildJsonObject {
            put("model", JsonPrimitive(request.model))
            put("stream", JsonPrimitive(true))
            put("temperature", JsonPrimitive(request.temperature))
            put("max_tokens", JsonPrimitive(request.maxTokens))
            put("top_p", JsonPrimitive(request.topP))
            put("messages", buildJsonArray {
                add(
                    buildJsonObject {
                        put("role", JsonPrimitive("system"))
                        put("content", JsonPrimitive(systemPrompt(request)))
                    },
                )
                add(
                    buildJsonObject {
                        put("role", JsonPrimitive("user"))
                        put("content", JsonPrimitive(request.prompt))
                    },
                )
            })
        },
    )

    private fun systemPrompt(request: AgentRequest): String = """
        You are BuildBuddy, a native Android coding agent.
        Project: ${request.project.name}
        Mode: ${request.mode}
        Return plain helpful text. When proposing file edits, include a JSON object matching:
        {"message":"...","changes":[{"operation":"UPDATE","path":"...","content":"...","reason":"..."}]}
    """.trimIndent()

    private fun sseFlow(
        url: String,
        apiKey: String,
        body: String,
        chunkExtractor: (JsonObject) -> String?,
    ): Flow<AiStreamEvent> = callbackFlow {
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        val factory = EventSources.createFactory(okHttpClient)
        val listener = object : EventSourceListener() {
            private val buffer = StringBuilder()

            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                if (data == "[DONE]") {
                    val text = buffer.toString()
                    trySend(parsedCompletion(text))
                    trySend(AiStreamEvent.Completed(text))
                    close()
                    return
                }
                val payload = runCatching { json.parseToJsonElement(data).jsonObject }.getOrNull() ?: return
                val chunk = chunkExtractor(payload).orEmpty()
                if (chunk.isNotBlank()) {
                    buffer.append(chunk)
                    trySend(AiStreamEvent.Delta(chunk))
                }
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: okhttp3.Response?) {
                trySend(AiStreamEvent.Failed(t?.message ?: response?.message ?: "Provider stream failed"))
                close(t ?: IOException("Provider stream failed"))
            }
        }
        val source = factory.newEventSource(request, listener)
        awaitClose { source.cancel() }
    }

    private fun parsedCompletion(text: String): AiStreamEvent {
        val payload = text.substringAfter("{", missingDelimiterValue = "").substringBeforeLast("}", "")
        if (payload.isBlank()) return AiStreamEvent.Completed(text)
        return runCatching {
            val envelope = json.decodeFromString(AgentEnvelope.serializer(), "{$payload}")
            if (envelope.proposedChanges.isNotEmpty()) {
                AiStreamEvent.ProposedPatch(envelope.message, envelope.proposedChanges)
            } else {
                AiStreamEvent.Completed(envelope.message)
            }
        }.getOrElse { AiStreamEvent.Completed(text) }
    }
}

class NvidiaProvider @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val json: Json,
) : AiProviderClient {
    override val providerId: ProviderId = ProviderId.NVIDIA

    override suspend fun testConnection(apiKey: String): ProviderConnectionResult {
        val request = Request.Builder()
            .url("https://integrate.api.nvidia.com/v1/models")
            .header("Authorization", "Bearer $apiKey")
            .build()
        return runCatching {
            okHttpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    ProviderConnectionResult(false, body.ifBlank { "NVIDIA request failed" })
                } else {
                    val models = json.parseToJsonElement(body).jsonObject["data"]?.jsonArray.orEmpty().take(6).map { element ->
                        val id = element.jsonObject["id"]?.jsonPrimitive?.content.orEmpty()
                        ModelDescriptor(id, id, supportsStreaming = true, supportsToolUse = false)
                    }
                    ProviderConnectionResult(true, "Connected to NVIDIA", models)
                }
            }
        }.getOrElse { ProviderConnectionResult(false, it.message ?: "NVIDIA request failed") }
    }

    override suspend fun listModels(apiKey: String): List<ModelDescriptor> = testConnection(apiKey).models

    override fun stream(request: AgentRequest, apiKey: String): Flow<AiStreamEvent> =
        OpenRouterProvider(okHttpClient, json).stream(
            request = request.copy(provider = ProviderId.NVIDIA),
            apiKey = apiKey,
        )
}

class GeminiProvider @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val json: Json,
) : AiProviderClient {
    override val providerId: ProviderId = ProviderId.GEMINI

    override suspend fun testConnection(apiKey: String): ProviderConnectionResult {
        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models?key=$apiKey")
            .build()
        return runCatching {
            okHttpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    ProviderConnectionResult(false, body.ifBlank { "Gemini request failed" })
                } else {
                    val models = json.parseToJsonElement(body).jsonObject["models"]?.jsonArray.orEmpty().take(6).map { element ->
                        val name = element.jsonObject["name"]?.jsonPrimitive?.content.orEmpty().substringAfterLast("/")
                        ModelDescriptor(name, name, supportsStreaming = true, supportsToolUse = false)
                    }
                    ProviderConnectionResult(true, "Connected to Gemini", models)
                }
            }
        }.getOrElse { ProviderConnectionResult(false, it.message ?: "Gemini request failed") }
    }

    override suspend fun listModels(apiKey: String): List<ModelDescriptor> = testConnection(apiKey).models

    override fun stream(request: AgentRequest, apiKey: String): Flow<AiStreamEvent> = callbackFlow {
        val requestBody = buildJsonObject {
            put("contents", buildJsonArray {
                add(
                    buildJsonObject {
                        put("role", JsonPrimitive("user"))
                        put("parts", buildJsonArray {
                            add(buildJsonObject {
                                put("text", JsonPrimitive(request.prompt))
                            })
                        })
                    },
                )
            })
            put("generationConfig", buildJsonObject {
                put("temperature", JsonPrimitive(request.temperature))
                put("maxOutputTokens", JsonPrimitive(request.maxTokens))
                put("topP", JsonPrimitive(request.topP))
            })
        }

        val httpRequest = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/${request.model}:streamGenerateContent?alt=sse&key=$apiKey")
            .post(json.encodeToString(JsonObject.serializer(), requestBody).toRequestBody("application/json".toMediaType()))
            .build()

        val factory = EventSources.createFactory(okHttpClient)
        val listener = object : EventSourceListener() {
            private val buffer = StringBuilder()

            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                val payload = runCatching { json.parseToJsonElement(data).jsonObject }.getOrNull() ?: return
                val text = payload["candidates"]?.jsonArray?.firstOrNull()
                    ?.jsonObject?.get("content")
                    ?.jsonObject?.get("parts")
                    ?.jsonArray?.firstOrNull()
                    ?.jsonObject?.get("text")
                    ?.jsonPrimitive?.content
                    .orEmpty()
                if (text.isNotBlank()) {
                    buffer.append(text)
                    trySend(AiStreamEvent.Delta(text))
                }
            }

            override fun onClosed(eventSource: EventSource) {
                val text = buffer.toString()
                trySend(AiStreamEvent.Completed(text))
                close()
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: okhttp3.Response?) {
                trySend(AiStreamEvent.Failed(t?.message ?: response?.message ?: "Gemini stream failed"))
                close(t ?: IOException("Gemini stream failed"))
            }
        }
        val source = factory.newEventSource(httpRequest, listener)
        awaitClose { source.cancel() }
    }
}
