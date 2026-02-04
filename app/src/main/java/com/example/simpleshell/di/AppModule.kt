package com.example.simpleshell.di

import android.content.Context
import androidx.room.Room
import com.example.simpleshell.data.local.database.AppDatabase
import com.example.simpleshell.data.local.database.ConnectionDao
import com.example.simpleshell.data.local.database.GroupDao
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
            // Early-stage app: prefer a clean rebuild over maintaining migrations.
            // NOTE: This will wipe existing data on schema changes.
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
}
