package com.example.simpleshell.data.local.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "settings_kv")
data class SettingKvEntity(
    @PrimaryKey
    val key: String,
    val valueJson: String,
    val updatedAt: Long = System.currentTimeMillis()
)
