package com.build.buddyai.core.data.local.dao

import androidx.room.*
import com.build.buddyai.core.data.local.entity.ProviderConfigEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProviderConfigDao {
    @Query("SELECT * FROM provider_configs")
    fun getAllProviderConfigs(): Flow<List<ProviderConfigEntity>>

    @Query("SELECT * FROM provider_configs WHERE providerId = :providerId")
    suspend fun getProviderConfig(providerId: String): ProviderConfigEntity?

    @Query("SELECT * FROM provider_configs WHERE isDefault = 1 LIMIT 1")
    suspend fun getDefaultProvider(): ProviderConfigEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProviderConfig(config: ProviderConfigEntity)

    @Update
    suspend fun updateProviderConfig(config: ProviderConfigEntity)

    @Query("DELETE FROM provider_configs WHERE providerId = :providerId")
    suspend fun deleteProviderConfig(providerId: String)

    @Query("UPDATE provider_configs SET isDefault = 0")
    suspend fun clearAllDefaults()

    @Query("DELETE FROM provider_configs")
    suspend fun clearAllProviderConfigs()
}
