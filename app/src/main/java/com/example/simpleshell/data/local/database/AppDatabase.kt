package com.example.simpleshell.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.simpleshell.data.local.database.entity.ConnectionEntity

@Database(
    entities = [ConnectionEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun connectionDao(): ConnectionDao
}
