package com.example.simpleshell.data.repository

import com.example.simpleshell.data.importing.SimpleShellPcCryptoCompat
import com.example.simpleshell.data.local.database.AiConfigDao
import com.example.simpleshell.data.local.database.entity.AiConfigEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiConfigRepository @Inject constructor(
    private val aiConfigDao: AiConfigDao
) {
    fun observeAll(): Flow<List<AiConfigEntity>> = aiConfigDao.observeAll()

    suspend fun getAll(): List<AiConfigEntity> = aiConfigDao.getAll()

    suspend fun upsert(config: AiConfigEntity) {
        aiConfigDao.upsert(config.encryptApiKeyForStorage())
    }

    suspend fun replaceAll(configs: List<AiConfigEntity>) {
        aiConfigDao.clearAll()
        if (configs.isNotEmpty()) {
            aiConfigDao.upsertAll(configs.map { it.encryptApiKeyForStorage() })
        }
    }

    suspend fun clearAll() {
        aiConfigDao.clearAll()
    }

    private fun AiConfigEntity.encryptApiKeyForStorage(): AiConfigEntity {
        return copy(apiKeyEnc = SimpleShellPcCryptoCompat.encryptNullableMaybe(apiKeyEnc))
    }
}
