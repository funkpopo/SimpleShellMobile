package com.example.simpleshell.data.local.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.simpleshell.data.local.database.entity.AiConfigEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AiConfigDao {
    @Query("SELECT * FROM ai_configs ORDER BY updatedAt DESC, createdAt DESC")
    fun observeAll(): Flow<List<AiConfigEntity>>

    @Query("SELECT * FROM ai_configs ORDER BY updatedAt DESC, createdAt DESC")
    suspend fun getAll(): List<AiConfigEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(config: AiConfigEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(configs: List<AiConfigEntity>)

    @Query("DELETE FROM ai_configs")
    suspend fun clearAll()
}
