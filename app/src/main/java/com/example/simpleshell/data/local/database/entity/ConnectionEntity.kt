package com.example.simpleshell.data.local.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "connections")
data class ConnectionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val groupId: Long? = null,
    val host: String,
    val port: Int = 22,
    val username: String,
    val password: String? = null,
    val privateKey: String? = null,
    val privateKeyPassphrase: String? = null,
    val authType: String = "password", // "password" or "key"
    val createdAt: Long = System.currentTimeMillis(),
    val lastConnectedAt: Long? = null
)
