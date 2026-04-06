package com.build.buddyai.core.network

import android.util.Base64
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.io.File

object AiImagePayloadUtils {
    fun openAiMessage(message: AiChatMessage): JsonObject = buildJsonObject {
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

    fun geminiInlineImagePart(path: String): JsonObject = buildJsonObject {
        putJsonObject("inlineData") {
            put("mimeType", mimeTypeFor(path))
            put("data", Base64.encodeToString(File(path).readBytes(), Base64.NO_WRAP))
        }
    }

    fun fileToDataUrl(path: String): String {
        val bytes = File(path).readBytes()
        return "data:${mimeTypeFor(path)};base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    fun mimeTypeFor(path: String): String = when (path.substringAfterLast('.', "").lowercase()) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "webp" -> "image/webp"
        else -> "application/octet-stream"
    }
}
