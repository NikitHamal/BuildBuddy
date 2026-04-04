package com.build.buddyai.core.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.build.buddyai.core.model.ModelParameters
import com.build.buddyai.core.model.ProviderConfig
import com.build.buddyai.core.model.ProviderType

@Entity(tableName = "provider_configs")
data class ProviderConfigEntity(
    @PrimaryKey val providerId: String,
    val type: String,
    val apiKeyEncrypted: String,
    val selectedModelId: String?,
    val isDefault: Boolean,
    val temperature: Float,
    val maxTokens: Int,
    val topP: Float,
    val cachedModels: String = "",
    val lastModelFetchTime: Long? = null
) {
    fun toProviderConfig() = ProviderConfig(
        providerId = providerId,
        type = ProviderType.valueOf(type),
        apiKeyEncrypted = apiKeyEncrypted,
        selectedModelId = selectedModelId,
        isDefault = isDefault,
        parameters = ModelParameters(temperature = temperature, maxTokens = maxTokens, topP = topP)
    )

    companion object {
        fun fromProviderConfig(config: ProviderConfig) = ProviderConfigEntity(
            providerId = config.providerId,
            type = config.type.name,
            apiKeyEncrypted = config.apiKeyEncrypted,
            selectedModelId = config.selectedModelId,
            isDefault = config.isDefault,
            temperature = config.parameters.temperature,
            maxTokens = config.parameters.maxTokens,
            topP = config.parameters.topP
        )
    }
}
