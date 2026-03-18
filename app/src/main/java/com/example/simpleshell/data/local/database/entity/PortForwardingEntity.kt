package com.example.simpleshell.data.local.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "port_forwarding_rules",
    foreignKeys = [
        ForeignKey(
            entity = ConnectionEntity::class,
            parentColumns = ["id"],
            childColumns = ["connectionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("connectionId")]
)
data class PortForwardingEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val connectionId: Long,
    val type: String, // "LOCAL", "REMOTE", "DYNAMIC"
    val localPort: Int,
    val remoteHost: String? = null,
    val remotePort: Int? = null,
    val isEnabled: Boolean = true
)
