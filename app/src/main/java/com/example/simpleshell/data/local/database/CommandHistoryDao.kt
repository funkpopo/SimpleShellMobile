package com.example.simpleshell.data.local.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.simpleshell.data.local.database.entity.CommandHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CommandHistoryDao {
    @Query("SELECT * FROM command_history ORDER BY lastUsedAt DESC, createdAt DESC")
    fun observeAll(): Flow<List<CommandHistoryEntity>>

    @Query("SELECT * FROM command_history ORDER BY lastUsedAt DESC, createdAt DESC")
    suspend fun getAll(): List<CommandHistoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: CommandHistoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<CommandHistoryEntity>)

    @Query("DELETE FROM command_history")
    suspend fun clearAll()
}
