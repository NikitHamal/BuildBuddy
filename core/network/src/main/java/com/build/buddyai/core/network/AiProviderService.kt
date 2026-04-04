package com.build.buddyai.core.network

import com.build.buddyai.core.model.*
import com.build.buddyai.core.network.api.GeminiApi
import com.build.buddyai.core.network.api.OpenAiCompatibleApi
import com.build.buddyai.core.network.model.*
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import javax.inject.Inject
import javax.inject.Singleton

sealed class AiResponse {
    data class Chunk(val text: String) : AiResponse()
    data class Complete(val text: String, val tokenCount: Int?) : AiResponse()
    data class Error(val error: AiError) : AiResponse()
}

@Singleton
class AiProviderService @Inject constructor(
    private val openAiApi: OpenAiCompatibleApi,
    private val geminiApi: GeminiApi,
    private val moshi: Moshi
) {
    suspend fun sendMessage(
        provider: AiProvider,
        apiKey: String,
        modelId: String,
        messages: List<ChatMessage>,
        parameters: ModelParameters
    ): Result<AiResponse.Complete> = withContext(Dispatchers.IO) {
        runCatching {
            if (provider.id == "gemini") {
                sendGeminiMessage(apiKey, modelId, messages, parameters)
            } else {
                sendOpenAiCompatibleMessage(provider, apiKey, modelId, messages, parameters)
            }
        }.recoverCatching { e -> throw mapException(e, provider.id) }
    }

    fun streamMessage(
        provider: AiProvider,
        apiKey: String,
        modelId: String,
        messages: List<ChatMessage>,
        parameters: ModelParameters
    ): Flow<AiResponse> = flow {
        try {
            if (provider.id == "gemini") {
                streamGeminiMessage(apiKey, modelId, messages, parameters).collect { emit(it) }
            } else {
                streamOpenAiCompatibleMessage(provider, apiKey, modelId, messages, parameters).collect { emit(it) }
            }
        } catch (e: Exception) {
            emit(AiResponse.Error(mapThrowableToError(e, provider.id)))
        }
    }.flowOn(Dispatchers.IO)

    suspend fun testConnection(
        provider: AiProvider,
        apiKey: String,
        modelId: String
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            val testMessage = ChatMessage(
                sessionId = "",
                role = MessageRole.USER,
                content = "Reply with OK.",
                status = MessageStatus.COMPLETE
            )
            val params = ModelParameters(temperature = 0f, maxTokens = 10)
            if (provider.id == "gemini") {
                sendGeminiMessage(apiKey, modelId, listOf(testMessage), params)
            } else {
                sendOpenAiCompatibleMessage(provider, apiKey, modelId, listOf(testMessage), params)
            }
            true
        }
    }

    private suspend fun sendOpenAiCompatibleMessage(
        provider: AiProvider,
        apiKey: String,
        modelId: String,
        messages: List<ChatMessage>,
        parameters: ModelParameters
    ): AiResponse.Complete {
        val request = ChatCompletionRequest(
            model = modelId,
            messages = messages.map { ApiMessage(role = it.role.name.lowercase(), content = it.content) },
            temperature = parameters.temperature,
            maxTokens = parameters.maxTokens,
            topP = parameters.topP,
            stream = false
        )
        val authHeader = if (provider.id == "openrouter") "Bearer $apiKey" else "Bearer $apiKey"
        val url = "${provider.baseUrl}/chat/completions"
        val response = openAiApi.chatCompletion(url, authHeader, request)
        if (!response.isSuccessful) {
            val errorBody = response.errorBody()?.string()
            throw ApiException(response.code(), errorBody ?: "Request failed")
        }
        val body = response.body() ?: throw ApiException(0, "Empty response body")
        val text = body.choices.firstOrNull()?.message?.content ?: ""
        val tokens = body.usage?.totalTokens
        return AiResponse.Complete(text, tokens)
    }

    private fun streamOpenAiCompatibleMessage(
        provider: AiProvider,
        apiKey: String,
        modelId: String,
        messages: List<ChatMessage>,
        parameters: ModelParameters
    ): Flow<AiResponse> = flow {
        val request = ChatCompletionRequest(
            model = modelId,
            messages = messages.map { ApiMessage(role = it.role.name.lowercase(), content = it.content) },
            temperature = parameters.temperature,
            maxTokens = parameters.maxTokens,
            topP = parameters.topP,
            stream = true
        )
        val authHeader = "Bearer $apiKey"
        val url = "${provider.baseUrl}/chat/completions"
        val response = openAiApi.chatCompletionStream(url, authHeader, request)
        if (!response.isSuccessful) {
            val errorBody = response.errorBody()?.string()
            emit(AiResponse.Error(parseApiError(response.code(), errorBody, provider.id)))
            return@flow
        }
        val responseBody = response.body() ?: return@flow
        val reader = BufferedReader(InputStreamReader(responseBody.byteStream()))
        val fullText = StringBuilder()
        var line: String?
        while (reader.readLine().also { line = it } != null) {
            val data = line ?: continue
            if (!data.startsWith("data: ")) continue
            val json = data.removePrefix("data: ").trim()
            if (json == "[DONE]") break
            try {
                val adapter = moshi.adapter(ChatCompletionResponse::class.java)
                val chunk = adapter.fromJson(json)
                val content = chunk?.choices?.firstOrNull()?.delta?.content
                if (!content.isNullOrEmpty()) {
                    fullText.append(content)
                    emit(AiResponse.Chunk(content))
                }
            } catch (_: Exception) { }
        }
        reader.close()
        emit(AiResponse.Complete(fullText.toString(), null))
    }

    private suspend fun sendGeminiMessage(
        apiKey: String,
        modelId: String,
        messages: List<ChatMessage>,
        parameters: ModelParameters
    ): AiResponse.Complete {
        val contents = messages.map { msg ->
            GeminiContent(
                role = if (msg.role == MessageRole.USER) "user" else "model",
                parts = listOf(GeminiPart(msg.content))
            )
        }
        val request = GeminiRequest(
            contents = contents,
            generationConfig = GeminiGenerationConfig(
                temperature = parameters.temperature,
                maxOutputTokens = parameters.maxTokens,
                topP = parameters.topP
            )
        )
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelId:generateContent?key=$apiKey"
        val response = geminiApi.generateContent(url, request)
        if (!response.isSuccessful) {
            val errorBody = response.errorBody()?.string()
            throw ApiException(response.code(), errorBody ?: "Request failed")
        }
        val body = response.body() ?: throw ApiException(0, "Empty response body")
        val text = body.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: ""
        val tokens = body.usageMetadata?.totalTokenCount
        return AiResponse.Complete(text, tokens)
    }

    private fun streamGeminiMessage(
        apiKey: String,
        modelId: String,
        messages: List<ChatMessage>,
        parameters: ModelParameters
    ): Flow<AiResponse> = flow {
        val contents = messages.map { msg ->
            GeminiContent(
                role = if (msg.role == MessageRole.USER) "user" else "model",
                parts = listOf(GeminiPart(msg.content))
            )
        }
        val request = GeminiRequest(
            contents = contents,
            generationConfig = GeminiGenerationConfig(
                temperature = parameters.temperature,
                maxOutputTokens = parameters.maxTokens,
                topP = parameters.topP
            )
        )
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelId:streamGenerateContent?alt=sse&key=$apiKey"
        val response = geminiApi.generateContentStream(url, request)
        if (!response.isSuccessful) {
            val errorBody = response.errorBody()?.string()
            emit(AiResponse.Error(parseApiError(response.code(), errorBody, "gemini")))
            return@flow
        }
        val responseBody = response.body() ?: return@flow
        val reader = BufferedReader(InputStreamReader(responseBody.byteStream()))
        val fullText = StringBuilder()
        var line: String?
        val adapter = moshi.adapter(GeminiResponse::class.java)
        while (reader.readLine().also { line = it } != null) {
            val data = line ?: continue
            if (!data.startsWith("data: ")) continue
            val json = data.removePrefix("data: ").trim()
            try {
                val chunk = adapter.fromJson(json)
                val text = chunk?.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                if (!text.isNullOrEmpty()) {
                    fullText.append(text)
                    emit(AiResponse.Chunk(text))
                }
            } catch (_: Exception) { }
        }
        reader.close()
        emit(AiResponse.Complete(fullText.toString(), null))
    }

    private fun parseApiError(code: Int, body: String?, providerId: String): AiError {
        return when (code) {
            401, 403 -> AiError.InvalidApiKey(providerId)
            429 -> AiError.RateLimited(null)
            in 500..599 -> AiError.ProviderUnavailable(providerId)
            else -> AiError.NetworkError(body ?: "HTTP $code")
        }
    }

    private fun mapException(e: Throwable, providerId: String): Throwable {
        return when (e) {
            is ApiException -> {
                val aiError = parseApiError(e.code, e.message, providerId)
                AiProviderException(aiError)
            }
            else -> e
        }
    }

    private fun mapThrowableToError(e: Throwable, providerId: String): AiError {
        return when (e) {
            is AiProviderException -> e.aiError
            is ApiException -> parseApiError(e.code, e.message, providerId)
            is java.net.UnknownHostException -> AiError.NetworkError("No internet connection")
            is java.net.SocketTimeoutException -> AiError.NetworkError("Connection timed out")
            else -> AiError.Unknown(e)
        }
    }
}

class ApiException(val code: Int, message: String) : Exception(message)
class AiProviderException(val aiError: AiError) : Exception(aiError.toString())
