package com.example.simpleshell.data.local.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.simpleshell.data.local.database.entity.GroupEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GroupDao {
    @Query("SELECT * FROM groups ORDER BY name COLLATE NOCASE ASC, createdAt ASC")
    fun getAllGroups(): Flow<List<GroupEntity>>

    @Query("SELECT * FROM groups ORDER BY name COLLATE NOCASE ASC, createdAt ASC")
    suspend fun getAllGroupsOnce(): List<GroupEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertGroup(group: GroupEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroups(groups: List<GroupEntity>)

    @Update
    suspend fun updateGroup(group: GroupEntity)

    @Delete
    suspend fun deleteGroup(group: GroupEntity)

    @Query("SELECT * FROM groups WHERE id = :id")
    suspend fun getGroupById(id: Long): GroupEntity?

    @Query("SELECT id FROM groups WHERE name = :name LIMIT 1")
    suspend fun getGroupIdByName(name: String): Long?

    @Query("SELECT COUNT(*) FROM groups WHERE name = :name")
    suspend fun countByName(name: String): Int

    @Query("DELETE FROM groups")
    suspend fun clearAll()
}
