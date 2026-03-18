package com.example.simpleshell.data.local.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.simpleshell.data.local.database.entity.PortForwardingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PortForwardingDao {
    @Query("SELECT * FROM port_forwarding_rules WHERE connectionId = :connectionId")
    fun getRulesForConnection(connectionId: Long): Flow<List<PortForwardingEntity>>

    @Query("SELECT * FROM port_forwarding_rules WHERE connectionId = :connectionId")
    suspend fun getRulesForConnectionOnce(connectionId: Long): List<PortForwardingEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRule(rule: PortForwardingEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRules(rules: List<PortForwardingEntity>)

    @Update
    suspend fun updateRule(rule: PortForwardingEntity)

    @Delete
    suspend fun deleteRule(rule: PortForwardingEntity)

    @Query("DELETE FROM port_forwarding_rules WHERE connectionId = :connectionId")
    suspend fun deleteRulesForConnection(connectionId: Long)
}
