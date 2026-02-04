package com.example.simpleshell.data.local.database

import androidx.room.*
import com.example.simpleshell.data.local.database.entity.ConnectionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ConnectionDao {
    @Query("SELECT * FROM connections ORDER BY lastConnectedAt DESC, createdAt DESC")
    fun getAllConnections(): Flow<List<ConnectionEntity>>

    @Query("SELECT * FROM connections WHERE id = :id")
    suspend fun getConnectionById(id: Long): ConnectionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConnection(connection: ConnectionEntity): Long

    @Update
    suspend fun updateConnection(connection: ConnectionEntity)

    @Delete
    suspend fun deleteConnection(connection: ConnectionEntity)

    @Query("UPDATE connections SET lastConnectedAt = :timestamp WHERE id = :id")
    suspend fun updateLastConnectedAt(id: Long, timestamp: Long)

    @Query("UPDATE connections SET groupId = NULL WHERE groupId = :groupId")
    suspend fun clearGroupForConnections(groupId: Long)
}
