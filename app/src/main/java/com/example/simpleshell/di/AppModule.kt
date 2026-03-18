package com.example.simpleshell.di

import android.content.Context
import androidx.room.Room
import com.example.simpleshell.data.local.database.AiConfigDao
import com.example.simpleshell.data.local.database.AppDatabase
import com.example.simpleshell.data.local.database.CommandHistoryDao
import com.example.simpleshell.data.local.database.ConnectionDao
import com.example.simpleshell.data.local.database.GroupDao
import com.example.simpleshell.data.local.database.PortForwardingDao
import com.example.simpleshell.data.local.database.SettingsKvDao
import com.example.simpleshell.data.local.database.SnippetDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `snippets` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `content` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)"
        )
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `port_forwarding_rules` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `connectionId` INTEGER NOT NULL, `type` TEXT NOT NULL, `localPort` INTEGER NOT NULL, `remoteHost` TEXT, `remotePort` INTEGER, `isEnabled` INTEGER NOT NULL, FOREIGN KEY(`connectionId`) REFERENCES `connections`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_port_forwarding_rules_connectionId` ON `port_forwarding_rules` (`connectionId`)"
        )
    }
}

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
            .addMigrations(MIGRATION_3_4, MIGRATION_4_5)
            .fallbackToDestructiveMigrationFrom(1, 2)
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

    @Provides
    @Singleton
    fun provideSnippetDao(database: AppDatabase): SnippetDao {
        return database.snippetDao()
    }

    @Provides
    @Singleton
    fun providePortForwardingDao(database: AppDatabase): PortForwardingDao {
        return database.portForwardingDao()
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}
