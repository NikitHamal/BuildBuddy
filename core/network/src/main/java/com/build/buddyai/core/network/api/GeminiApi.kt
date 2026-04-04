package com.build.buddyai.core.network.api

import com.build.buddyai.core.network.model.GeminiRequest
import com.build.buddyai.core.network.model.GeminiResponse
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

interface GeminiApi {
    @POST
    suspend fun generateContent(
        @Url url: String,
        @Body request: GeminiRequest
    ): Response<GeminiResponse>

    @Streaming
    @POST
    suspend fun generateContentStream(
        @Url url: String,
        @Body request: GeminiRequest
    ): Response<ResponseBody>
}
