package com.example.simpleshell.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.simpleshell.data.local.database.entity.AiConfigEntity
import com.example.simpleshell.data.local.database.entity.CommandHistoryEntity
import com.example.simpleshell.data.local.database.entity.ConnectionEntity
import com.example.simpleshell.data.local.database.entity.GroupEntity
import com.example.simpleshell.data.local.database.entity.SettingKvEntity

@Database(
    entities = [
        ConnectionEntity::class,
        GroupEntity::class,
        AiConfigEntity::class,
        SettingKvEntity::class,
        CommandHistoryEntity::class
    ],
    version = 3,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun connectionDao(): ConnectionDao
    abstract fun groupDao(): GroupDao
    abstract fun aiConfigDao(): AiConfigDao
    abstract fun settingsKvDao(): SettingsKvDao
    abstract fun commandHistoryDao(): CommandHistoryDao
}
