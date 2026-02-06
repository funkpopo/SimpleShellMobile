package com.example.simpleshell.data.repository

import com.example.simpleshell.data.local.database.SettingsKvDao
import com.example.simpleshell.data.local.database.entity.SettingKvEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsKvRepository @Inject constructor(
    private val settingsKvDao: SettingsKvDao
) {
    fun observeAll(): Flow<List<SettingKvEntity>> = settingsKvDao.observeAll()

    suspend fun getAll(): List<SettingKvEntity> = settingsKvDao.getAll()

    suspend fun getByKey(key: String): SettingKvEntity? = settingsKvDao.getByKey(key)

    suspend fun upsert(key: String, valueJson: String, updatedAt: Long = System.currentTimeMillis()) {
        settingsKvDao.upsert(
            SettingKvEntity(
                key = key,
                valueJson = valueJson,
                updatedAt = updatedAt
            )
        )
    }

    suspend fun upsertAll(settings: List<SettingKvEntity>) {
        if (settings.isNotEmpty()) {
            settingsKvDao.upsertAll(settings)
        }
    }

    suspend fun clearAll() {
        settingsKvDao.clearAll()
    }
}
