package com.example.simpleshell.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.simpleshell.data.local.database.entity.ConnectionEntity
import com.example.simpleshell.data.local.database.entity.GroupEntity

@Database(
    entities = [ConnectionEntity::class, GroupEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun connectionDao(): ConnectionDao
    abstract fun groupDao(): GroupDao
}
