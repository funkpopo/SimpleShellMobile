package com.example.simpleshell.data.local.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.simpleshell.data.local.database.entity.SettingKvEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SettingsKvDao {
    @Query("SELECT * FROM settings_kv ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<SettingKvEntity>>

    @Query("SELECT * FROM settings_kv ORDER BY updatedAt DESC")
    suspend fun getAll(): List<SettingKvEntity>

    @Query("SELECT * FROM settings_kv WHERE `key` = :key LIMIT 1")
    suspend fun getByKey(key: String): SettingKvEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(setting: SettingKvEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(settings: List<SettingKvEntity>)

    @Query("DELETE FROM settings_kv")
    suspend fun clearAll()

    @Query("DELETE FROM settings_kv WHERE `key` = :key")
    suspend fun deleteByKey(key: String)
}
