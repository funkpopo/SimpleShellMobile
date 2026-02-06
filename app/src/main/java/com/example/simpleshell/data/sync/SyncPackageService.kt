package com.example.simpleshell.data.sync

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.withTransaction
import com.example.simpleshell.data.local.database.AiConfigDao
import com.example.simpleshell.data.local.database.AppDatabase
import com.example.simpleshell.data.local.database.CommandHistoryDao
import com.example.simpleshell.data.local.database.ConnectionDao
import com.example.simpleshell.data.local.database.GroupDao
import com.example.simpleshell.data.local.database.SettingsKvDao
import com.example.simpleshell.data.local.database.entity.AiConfigEntity
import com.example.simpleshell.data.local.database.entity.CommandHistoryEntity
import com.example.simpleshell.data.local.database.entity.ConnectionEntity
import com.example.simpleshell.data.local.database.entity.GroupEntity
import com.example.simpleshell.data.local.database.entity.SettingKvEntity
import com.example.simpleshell.data.local.preferences.UserPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class SyncImportResult(
    val backupFile: File,
    val importedConnections: Int,
    val importedGroups: Int
)

@Singleton
class SyncPackageService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appDatabase: AppDatabase,
    private val connectionDao: ConnectionDao,
    private val groupDao: GroupDao,
    private val aiConfigDao: AiConfigDao,
    private val settingsKvDao: SettingsKvDao,
    private val commandHistoryDao: CommandHistoryDao,
    private val userPreferencesRepository: UserPreferencesRepository
) {

    suspend fun exportToByteArray(): ByteArray {
        val tempFile = File.createTempFile("simpleshell-sync-export-", ".ssdb", context.cacheDir)
        return try {
            buildSyncDatabase(tempFile)
            tempFile.readBytes()
        } finally {
            tempFile.delete()
        }
    }

    suspend fun importFromByteArray(bytes: ByteArray): SyncImportResult {
        val sourceFile = File.createTempFile("simpleshell-sync-import-", ".ssdb", context.cacheDir)
        sourceFile.writeBytes(bytes)

        return try {
            importFromFile(sourceFile)
        } finally {
            sourceFile.delete()
        }
    }

    private suspend fun buildSyncDatabase(targetFile: File) {
        if (targetFile.exists()) {
            targetFile.delete()
        }

        val syncDb = SQLiteDatabase.openOrCreateDatabase(targetFile, null)
        try {
            syncDb.beginTransaction()
            createSyncSchema(syncDb)

            val groups = groupDao.getAllGroupsOnce()
            val connections = connectionDao.getAllConnectionsOnce()
            val aiConfigs = aiConfigDao.getAll()
            val settings = settingsKvDao.getAll().toMutableList()
            val commandHistory = commandHistoryDao.getAll()
            val now = System.currentTimeMillis()

            val groupIdToName = groups.associate { it.id to it.name }

            val uiSettingsJson = userPreferencesRepository.buildSyncUiSettingsJson()
            settings.removeAll { it.key == "uiSettings" }
            settings.add(
                SettingKvEntity(
                    key = "uiSettings",
                    valueJson = uiSettingsJson,
                    updatedAt = now
                )
            )

            for (group in groups) {
                val values = ContentValues().apply {
                    put("name", group.name)
                    put("createdAt", group.createdAt)
                    put("updatedAt", group.createdAt)
                }
                syncDb.insert("groups", null, values)
            }

            for (connection in connections) {
                val values = ContentValues().apply {
                    put("id", "android:${connection.id}")
                    put("groupName", connection.groupId?.let { groupIdToName[it] })
                    put("name", connection.name)
                    put("protocol", "ssh")
                    put("host", connection.host)
                    put("port", connection.port)
                    put("username", connection.username)
                    put("authType", connection.authType)
                    put("passwordEnc", connection.password)
                    put("privateKeyEnc", connection.privateKey)
                    put("privateKeyPathEnc", null as String?)
                    put("privateKeyPassphraseEnc", connection.privateKeyPassphrase)
                    put("proxyJson", null as String?)
                    put("extraJson", null as String?)
                    put("createdAt", connection.createdAt)
                    put("updatedAt", connection.createdAt)
                    put("lastConnectedAt", connection.lastConnectedAt)
                }
                syncDb.insert("connections", null, values)
            }

            for (aiConfig in aiConfigs) {
                val values = ContentValues().apply {
                    put("id", aiConfig.id)
                    put("name", aiConfig.name)
                    put("apiUrl", aiConfig.apiUrl)
                    put("apiKeyEnc", aiConfig.apiKeyEnc)
                    put("model", aiConfig.model)
                    put("maxTokens", aiConfig.maxTokens)
                    put("temperature", aiConfig.temperature)
                    put("streamEnabled", aiConfig.streamEnabled)
                    put("createdAt", aiConfig.createdAt)
                    put("updatedAt", aiConfig.updatedAt)
                }
                syncDb.insert("ai_configs", null, values)
            }

            for (setting in settings) {
                val values = ContentValues().apply {
                    put("key", setting.key)
                    put("valueJson", setting.valueJson)
                    put("updatedAt", setting.updatedAt)
                }
                syncDb.insert("settings_kv", null, values)
            }

            for (history in commandHistory) {
                val values = ContentValues().apply {
                    put("command", history.command)
                    put("count", history.count)
                    put("lastUsedAt", history.lastUsedAt)
                    put("createdAt", history.createdAt)
                }
                syncDb.insert("command_history", null, values)
            }

            syncDb.setTransactionSuccessful()
        } finally {
            syncDb.endTransaction()
            syncDb.close()
        }
    }

    private suspend fun importFromFile(sourceFile: File): SyncImportResult {
        val backupFile = File(
            context.cacheDir,
            "simpleshell-backup-${System.currentTimeMillis()}.ssdb"
        )
        backupFile.writeBytes(exportToByteArray())

        val sourceDb = SQLiteDatabase.openDatabase(
            sourceFile.absolutePath,
            null,
            SQLiteDatabase.OPEN_READONLY
        )

        val importedGroups = mutableListOf<GroupEntity>()
        val importedConnections = mutableListOf<ImportedConnection>()
        val importedAiConfigs = mutableListOf<AiConfigEntity>()
        val importedSettings = mutableListOf<SettingKvEntity>()
        val importedHistory = mutableListOf<CommandHistoryEntity>()

        try {
            sourceDb.query(
                "groups",
                arrayOf("name", "createdAt", "updatedAt"),
                null,
                null,
                null,
                null,
                "name COLLATE NOCASE ASC"
            ).use { cursor ->
                val nameIndex = cursor.getColumnIndexOrThrow("name")
                val createdIndex = cursor.getColumnIndexOrThrow("createdAt")
                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameIndex)?.trim().orEmpty()
                    if (name.isBlank()) continue
                    importedGroups.add(
                        GroupEntity(
                            name = name,
                            createdAt = cursor.getLong(createdIndex)
                        )
                    )
                }
            }

            sourceDb.query(
                "connections",
                arrayOf(
                    "groupName",
                    "name",
                    "host",
                    "port",
                    "username",
                    "authType",
                    "passwordEnc",
                    "privateKeyEnc",
                    "privateKeyPassphraseEnc",
                    "createdAt",
                    "lastConnectedAt"
                ),
                null,
                null,
                null,
                null,
                "createdAt ASC"
            ).use { cursor ->
                val groupIndex = cursor.getColumnIndexOrThrow("groupName")
                val nameIndex = cursor.getColumnIndexOrThrow("name")
                val hostIndex = cursor.getColumnIndexOrThrow("host")
                val portIndex = cursor.getColumnIndexOrThrow("port")
                val usernameIndex = cursor.getColumnIndexOrThrow("username")
                val authTypeIndex = cursor.getColumnIndexOrThrow("authType")
                val passwordIndex = cursor.getColumnIndexOrThrow("passwordEnc")
                val keyIndex = cursor.getColumnIndexOrThrow("privateKeyEnc")
                val passphraseIndex = cursor.getColumnIndexOrThrow("privateKeyPassphraseEnc")
                val createdAtIndex = cursor.getColumnIndexOrThrow("createdAt")
                val lastConnectedAtIndex = cursor.getColumnIndexOrThrow("lastConnectedAt")

                while (cursor.moveToNext()) {
                    val host = cursor.getString(hostIndex)?.trim().orEmpty()
                    val username = cursor.getString(usernameIndex)?.trim().orEmpty()
                    if (host.isBlank() || username.isBlank()) continue

                    importedConnections.add(
                        ImportedConnection(
                            groupName = cursor.getString(groupIndex),
                            name = cursor.getString(nameIndex)?.ifBlank { "$username@$host" } ?: "$username@$host",
                            host = host,
                            port = cursor.getInt(portIndex).takeIf { it in 1..65535 } ?: 22,
                            username = username,
                            authType = cursor.getString(authTypeIndex),
                            passwordEnc = cursor.getString(passwordIndex),
                            privateKeyEnc = cursor.getString(keyIndex),
                            privateKeyPassphraseEnc = cursor.getString(passphraseIndex),
                            createdAt = cursor.getLong(createdAtIndex),
                            lastConnectedAt = if (cursor.isNull(lastConnectedAtIndex)) null else cursor.getLong(lastConnectedAtIndex)
                        )
                    )
                }
            }

            sourceDb.query(
                "ai_configs",
                arrayOf(
                    "id",
                    "name",
                    "apiUrl",
                    "apiKeyEnc",
                    "model",
                    "maxTokens",
                    "temperature",
                    "streamEnabled",
                    "createdAt",
                    "updatedAt"
                ),
                null,
                null,
                null,
                null,
                null
            ).use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow("id")
                val nameIndex = cursor.getColumnIndexOrThrow("name")
                val apiUrlIndex = cursor.getColumnIndexOrThrow("apiUrl")
                val apiKeyIndex = cursor.getColumnIndexOrThrow("apiKeyEnc")
                val modelIndex = cursor.getColumnIndexOrThrow("model")
                val maxTokensIndex = cursor.getColumnIndexOrThrow("maxTokens")
                val temperatureIndex = cursor.getColumnIndexOrThrow("temperature")
                val streamEnabledIndex = cursor.getColumnIndexOrThrow("streamEnabled")
                val createdAtIndex = cursor.getColumnIndexOrThrow("createdAt")
                val updatedAtIndex = cursor.getColumnIndexOrThrow("updatedAt")

                while (cursor.moveToNext()) {
                    val id = cursor.getString(idIndex)?.trim().orEmpty()
                    if (id.isBlank()) continue
                    importedAiConfigs.add(
                        AiConfigEntity(
                            id = id,
                            name = cursor.getString(nameIndex) ?: "",
                            apiUrl = cursor.getString(apiUrlIndex) ?: "",
                            apiKeyEnc = cursor.getString(apiKeyIndex),
                            model = cursor.getString(modelIndex),
                            maxTokens = if (cursor.isNull(maxTokensIndex)) null else cursor.getInt(maxTokensIndex),
                            temperature = if (cursor.isNull(temperatureIndex)) null else cursor.getDouble(temperatureIndex),
                            streamEnabled = cursor.getInt(streamEnabledIndex),
                            createdAt = cursor.getLong(createdAtIndex),
                            updatedAt = cursor.getLong(updatedAtIndex)
                        )
                    )
                }
            }

            sourceDb.query(
                "settings_kv",
                arrayOf("key", "valueJson", "updatedAt"),
                null,
                null,
                null,
                null,
                null
            ).use { cursor ->
                val keyIndex = cursor.getColumnIndexOrThrow("key")
                val valueIndex = cursor.getColumnIndexOrThrow("valueJson")
                val updatedIndex = cursor.getColumnIndexOrThrow("updatedAt")
                while (cursor.moveToNext()) {
                    val key = cursor.getString(keyIndex)?.trim().orEmpty()
                    if (key.isBlank()) continue
                    importedSettings.add(
                        SettingKvEntity(
                            key = key,
                            valueJson = cursor.getString(valueIndex) ?: "null",
                            updatedAt = cursor.getLong(updatedIndex)
                        )
                    )
                }
            }

            sourceDb.query(
                "command_history",
                arrayOf("command", "count", "lastUsedAt", "createdAt"),
                null,
                null,
                null,
                null,
                null
            ).use { cursor ->
                val commandIndex = cursor.getColumnIndexOrThrow("command")
                val countIndex = cursor.getColumnIndexOrThrow("count")
                val lastUsedIndex = cursor.getColumnIndexOrThrow("lastUsedAt")
                val createdIndex = cursor.getColumnIndexOrThrow("createdAt")
                while (cursor.moveToNext()) {
                    val command = cursor.getString(commandIndex)?.trim().orEmpty()
                    if (command.isBlank()) continue
                    importedHistory.add(
                        CommandHistoryEntity(
                            command = command,
                            count = cursor.getInt(countIndex).coerceAtLeast(1),
                            lastUsedAt = cursor.getLong(lastUsedIndex),
                            createdAt = cursor.getLong(createdIndex)
                        )
                    )
                }
            }
        } finally {
            sourceDb.close()
        }

        appDatabase.withTransaction {
            connectionDao.clearAll()
            groupDao.clearAll()
            aiConfigDao.clearAll()
            settingsKvDao.clearAll()
            commandHistoryDao.clearAll()

            val groupNameToId = mutableMapOf<String, Long>()
            for (group in importedGroups) {
                val id = groupDao.insertGroup(group)
                groupNameToId[group.name] = id
            }

            val now = System.currentTimeMillis()
            val connectionEntities = importedConnections.map { imported ->
                val wantsKey = when (imported.authType?.lowercase()) {
                    "privatekey", "key" -> true
                    else -> false
                }
                val authType = if (wantsKey && imported.privateKeyEnc.isNullOrBlank() && !imported.passwordEnc.isNullOrBlank()) {
                    "password"
                } else if (wantsKey) {
                    "key"
                } else {
                    "password"
                }

                ConnectionEntity(
                    name = imported.name,
                    groupId = imported.groupName?.let { groupNameToId[it] },
                    host = imported.host,
                    port = imported.port,
                    username = imported.username,
                    password = imported.passwordEnc?.takeIf { it.isNotBlank() },
                    privateKey = imported.privateKeyEnc?.takeIf { it.isNotBlank() },
                    privateKeyPassphrase = imported.privateKeyPassphraseEnc?.takeIf { it.isNotBlank() },
                    authType = authType,
                    createdAt = imported.createdAt.takeIf { it > 0 } ?: now,
                    lastConnectedAt = imported.lastConnectedAt
                )
            }
            if (connectionEntities.isNotEmpty()) {
                connectionDao.insertConnections(connectionEntities)
            }

            if (importedAiConfigs.isNotEmpty()) {
                aiConfigDao.upsertAll(importedAiConfigs)
            }
            if (importedSettings.isNotEmpty()) {
                settingsKvDao.upsertAll(importedSettings)
            }
            if (importedHistory.isNotEmpty()) {
                commandHistoryDao.upsertAll(importedHistory)
            }
        }

        importedSettings.firstOrNull { it.key == "uiSettings" }?.let {
            userPreferencesRepository.applySyncUiSettingsJson(it.valueJson)
        }

        return SyncImportResult(
            backupFile = backupFile,
            importedConnections = importedConnections.size,
            importedGroups = importedGroups.size
        )
    }

    private fun createSyncSchema(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS groups (
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              name TEXT NOT NULL UNIQUE,
              createdAt INTEGER NOT NULL,
              updatedAt INTEGER NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS connections (
              id TEXT PRIMARY KEY,
              groupName TEXT,
              name TEXT NOT NULL,
              protocol TEXT NOT NULL,
              host TEXT NOT NULL,
              port INTEGER NOT NULL,
              username TEXT NOT NULL,
              authType TEXT NOT NULL,
              passwordEnc TEXT,
              privateKeyEnc TEXT,
              privateKeyPathEnc TEXT,
              privateKeyPassphraseEnc TEXT,
              proxyJson TEXT,
              extraJson TEXT,
              createdAt INTEGER NOT NULL,
              updatedAt INTEGER NOT NULL,
              lastConnectedAt INTEGER
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS ai_configs (
              id TEXT PRIMARY KEY,
              name TEXT NOT NULL,
              apiUrl TEXT NOT NULL,
              apiKeyEnc TEXT,
              model TEXT,
              maxTokens INTEGER,
              temperature REAL,
              streamEnabled INTEGER NOT NULL,
              createdAt INTEGER NOT NULL,
              updatedAt INTEGER NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS settings_kv (
              key TEXT PRIMARY KEY,
              valueJson TEXT NOT NULL,
              updatedAt INTEGER NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS command_history (
              command TEXT PRIMARY KEY,
              count INTEGER NOT NULL,
              lastUsedAt INTEGER NOT NULL,
              createdAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    private data class ImportedConnection(
        val groupName: String?,
        val name: String,
        val host: String,
        val port: Int,
        val username: String,
        val authType: String?,
        val passwordEnc: String?,
        val privateKeyEnc: String?,
        val privateKeyPassphraseEnc: String?,
        val createdAt: Long,
        val lastConnectedAt: Long?
    )
}
