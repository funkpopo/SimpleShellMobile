package com.example.simpleshell.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.simpleshell.data.local.database.entity.AiConfigEntity
import com.example.simpleshell.data.local.database.entity.CommandHistoryEntity
import com.example.simpleshell.data.local.database.entity.ConnectionEntity
import com.example.simpleshell.data.local.database.entity.GroupEntity
import com.example.simpleshell.data.local.database.entity.PortForwardingEntity
import com.example.simpleshell.data.local.database.entity.SettingKvEntity
import com.example.simpleshell.data.local.database.entity.SnippetEntity

@Database(
    entities = [
        ConnectionEntity::class,
        GroupEntity::class,
        AiConfigEntity::class,
        SettingKvEntity::class,
        CommandHistoryEntity::class,
        SnippetEntity::class,
        PortForwardingEntity::class
    ],
    version = 5,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun connectionDao(): ConnectionDao
    abstract fun groupDao(): GroupDao
    abstract fun aiConfigDao(): AiConfigDao
    abstract fun settingsKvDao(): SettingsKvDao
    abstract fun commandHistoryDao(): CommandHistoryDao
    abstract fun snippetDao(): SnippetDao
    abstract fun portForwardingDao(): PortForwardingDao
}
