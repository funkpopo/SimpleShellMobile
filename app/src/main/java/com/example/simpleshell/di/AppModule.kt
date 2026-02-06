package com.example.simpleshell.di

import android.content.Context
import androidx.room.Room
import com.example.simpleshell.data.local.database.AiConfigDao
import com.example.simpleshell.data.local.database.AppDatabase
import com.example.simpleshell.data.local.database.CommandHistoryDao
import com.example.simpleshell.data.local.database.ConnectionDao
import com.example.simpleshell.data.local.database.GroupDao
import com.example.simpleshell.data.local.database.SettingsKvDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "simpleshell_database"
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    @Singleton
    fun provideConnectionDao(database: AppDatabase): ConnectionDao {
        return database.connectionDao()
    }

    @Provides
    @Singleton
    fun provideGroupDao(database: AppDatabase): GroupDao {
        return database.groupDao()
    }

    @Provides
    @Singleton
    fun provideAiConfigDao(database: AppDatabase): AiConfigDao {
        return database.aiConfigDao()
    }

    @Provides
    @Singleton
    fun provideSettingsKvDao(database: AppDatabase): SettingsKvDao {
        return database.settingsKvDao()
    }

    @Provides
    @Singleton
    fun provideCommandHistoryDao(database: AppDatabase): CommandHistoryDao {
        return database.commandHistoryDao()
    }
}
