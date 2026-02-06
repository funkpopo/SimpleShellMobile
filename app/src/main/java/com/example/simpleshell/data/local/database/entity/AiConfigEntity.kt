package com.example.simpleshell.data.local.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ai_configs")
data class AiConfigEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val apiUrl: String,
    val apiKeyEnc: String? = null,
    val model: String? = null,
    val maxTokens: Int? = null,
    val temperature: Double? = null,
    val streamEnabled: Int = 1,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
