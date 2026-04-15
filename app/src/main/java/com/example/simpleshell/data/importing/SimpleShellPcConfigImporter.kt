package com.example.simpleshell.data.importing

import android.util.Base64
import com.example.simpleshell.data.local.database.SettingsKvDao
import com.example.simpleshell.data.local.database.entity.AiConfigEntity
import com.example.simpleshell.data.local.database.entity.CommandHistoryEntity
import com.example.simpleshell.data.local.database.entity.ConnectionEntity
import com.example.simpleshell.data.local.database.entity.SettingKvEntity
import com.example.simpleshell.data.local.preferences.UserPreferencesRepository
import com.example.simpleshell.data.repository.AiConfigRepository
import com.example.simpleshell.data.repository.CommandHistoryRepository
import com.example.simpleshell.data.repository.ConnectionRepository
import com.example.simpleshell.data.repository.GroupRepository
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.util.zip.GZIPInputStream
import javax.inject.Inject
import javax.inject.Singleton

data class SimpleShellPcImportSummary(
    val createdGroups: Int,
    val importedConnections: Int,
    val importedPasswordConnections: Int,
    val importedKeyConnections: Int,
    val skippedConnections: Int,
    val importedAiConfigs: Int = 0,
    val importedCommandHistoryEntries: Int = 0
)

/**
 * Imports the desktop SimpleShell `config.json` format into the mobile database.
 *
 * The desktop app encrypts credential fields with the root `security` object. Import therefore
 * uses a temporary desktop-compatible cipher to decrypt source data, then lets mobile repositories
 * store the plaintext through the current local credential store.
 */
@Singleton
class SimpleShellPcConfigImporter @Inject constructor(
    private val connectionRepository: ConnectionRepository,
    private val groupRepository: GroupRepository,
    private val aiConfigRepository: AiConfigRepository,
    private val commandHistoryRepository: CommandHistoryRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val settingsKvDao: SettingsKvDao
) {
    suspend fun importFromConfigJson(
        jsonText: String,
        masterPassword: String? = null
    ): SimpleShellPcImportSummary {
        val root = JSONObject(jsonText)
        val sourceCipher = SimpleShellPcCryptoCompat.createCipherFromConfigJson(
            securityJson = root.optJSONObject("security"),
            masterPassword = masterPassword
        )

        preserveDesktopOnlySettings(root)
        importUiSettings(root)

        val counters = Counters()
        val connections = root.optJSONArray("connections") ?: JSONArray()
        importItems(connections, groupId = null, groupPath = emptyList(), counters = counters, cipher = sourceCipher)

        counters.importedAiConfigs = importAiSettings(root.optJSONObject("aiSettings"), sourceCipher)
        counters.importedCommandHistoryEntries = importCommandHistory(root.opt("commandHistory"))

        return counters.toSummary()
    }

    private suspend fun preserveDesktopOnlySettings(root: JSONObject) {
        for (key in DESKTOP_ONLY_ROOT_KEYS) {
            if (!root.has(key)) continue
            settingsKvDao.upsert(
                SettingKvEntity(
                    key = desktopSettingKey(key),
                    valueJson = root.get(key).toString()
                )
            )
        }
    }

    private suspend fun importUiSettings(root: JSONObject) {
        val uiSettings = root.optJSONObject("uiSettings") ?: return
        userPreferencesRepository.applySyncUiSettingsJson(uiSettings.toString())
        settingsKvDao.upsert(
            SettingKvEntity(
                key = desktopSettingKey("uiSettings"),
                valueJson = uiSettings.toString()
            )
        )
    }

    private suspend fun importItems(
        items: JSONArray,
        groupId: Long?,
        groupPath: List<String>,
        counters: Counters,
        cipher: SimpleShellPcCredentialCipher
    ) {
        for (i in 0 until items.length()) {
            val obj = items.optJSONObject(i) ?: continue
            when (obj.optString("type", "").trim().lowercase()) {
                "group" -> importGroup(obj, groupPath, counters, cipher)
                "connection" -> importConnection(obj, groupId, counters, cipher)
                else -> Unit
            }
        }
    }

    private suspend fun importGroup(
        obj: JSONObject,
        parentPath: List<String>,
        counters: Counters,
        cipher: SimpleShellPcCredentialCipher
    ) {
        val rawName = obj.optString("name", "").trim()
        if (rawName.isBlank()) return

        val currentPath = parentPath + rawName
        val groupName = currentPath.joinToString(" / ")

        val (groupId, created) = groupRepository.getOrCreateGroupId(groupName)
        if (created) counters.createdGroups++

        val children = obj.optJSONArray("items") ?: return
        importItems(children, groupId = groupId, groupPath = currentPath, counters = counters, cipher = cipher)
    }

    private suspend fun importConnection(
        obj: JSONObject,
        groupId: Long?,
        counters: Counters,
        cipher: SimpleShellPcCredentialCipher
    ) {
        val host = obj.optString("host", "").trim()
        val username = obj.optString("username", "").trim()
        if (host.isBlank() || username.isBlank()) {
            counters.skippedConnections++
            return
        }

        val port = parsePort(obj.opt("port"))
        val name = obj.optString("name", "").trim().ifBlank {
            if (port != 22) "$username@$host:$port" else "$username@$host"
        }

        val authTypeRaw = obj.optString("authType", "").trim()
        val keyAuth =
            authTypeRaw.equals("privateKey", ignoreCase = true) || authTypeRaw.equals("key", ignoreCase = true)

        val passwordToStore = decryptDesktopCredential(cipher, obj.optString("password", ""))
        val privateKeyPathToStore = decryptDesktopCredential(cipher, obj.optString("privateKeyPath", ""))
        val privateKeyToStore = readDesktopPrivateKeyValue(obj, cipher, privateKeyPathToStore)
        val privateKeyPassphraseToStore = readDesktopPrivateKeyPassphrase(obj, cipher)

        val finalAuthType = if (keyAuth) "key" else "password"
        val entity = ConnectionEntity(
            name = name,
            groupId = groupId,
            host = host,
            port = port,
            username = username,
            password = if (finalAuthType == "password") passwordToStore else null,
            privateKey = if (finalAuthType == "key") privateKeyToStore else null,
            privateKeyPassphrase = if (finalAuthType == "key") privateKeyPassphraseToStore else null,
            authType = finalAuthType
        )

        connectionRepository.saveConnection(entity)

        counters.importedConnections++
        if (finalAuthType == "key") counters.importedKeyConnections++ else counters.importedPasswordConnections++
    }

    private fun readDesktopPrivateKeyValue(
        obj: JSONObject,
        cipher: SimpleShellPcCredentialCipher,
        privateKeyPathToStore: String?
    ): String? {
        val inlinePrivateKey = obj.optString("privateKey", "").trim()
        if (inlinePrivateKey.isBlank()) return privateKeyPathToStore

        return if (SimpleShellPcCryptoCompat.looksLikeEncryptedPayload(inlinePrivateKey)) {
            cipher.decryptNullable(inlinePrivateKey)
        } else {
            inlinePrivateKey
        }
    }

    private fun readDesktopPrivateKeyPassphrase(
        obj: JSONObject,
        cipher: SimpleShellPcCredentialCipher
    ): String? {
        val raw = obj.optString("privateKeyPassphrase", "").trim()
            .ifBlank { obj.optString("passphrase", "").trim() }
        if (raw.isBlank()) return null
        return if (SimpleShellPcCryptoCompat.looksLikeEncryptedPayload(raw)) cipher.decryptNullable(raw) else raw
    }

    private suspend fun importAiSettings(settings: JSONObject?, cipher: SimpleShellPcCredentialCipher): Int {
        if (settings == null) return 0

        settingsKvDao.upsert(
            SettingKvEntity(
                key = desktopSettingKey("aiSettings"),
                valueJson = settings.toString()
            )
        )

        val configsById = LinkedHashMap<String, AiConfigEntity>()
        val configs = settings.optJSONArray("configs") ?: JSONArray()
        for (i in 0 until configs.length()) {
            configs.optJSONObject(i)?.toAiConfigEntity(cipher)?.let { entity ->
                configsById[entity.id] = entity
            }
        }

        settings.optJSONObject("current")?.toAiConfigEntity(cipher)?.let { entity ->
            configsById.putIfAbsent(entity.id, entity)
        }

        val entities = configsById.values.toList()
        aiConfigRepository.replaceAll(entities)
        return entities.size
    }

    private fun JSONObject.toAiConfigEntity(cipher: SimpleShellPcCredentialCipher): AiConfigEntity? {
        val id = optString("id", "").trim()
        val name = optString("name", "").trim()
        val apiUrl = optString("apiUrl", "").trim()
        if (id.isBlank() || name.isBlank() || apiUrl.isBlank()) return null

        val apiKey = decryptDesktopCredential(cipher, optString("apiKey", ""))
        val model = optString("model", "").trim().ifBlank { null }
        val maxTokens = if (has("maxTokens") && !isNull("maxTokens")) optInt("maxTokens") else null
        val temperature = if (has("temperature") && !isNull("temperature")) optDouble("temperature") else null
        val streamEnabled = if (optBoolean("streamEnabled", true)) 1 else 0
        val now = System.currentTimeMillis()

        return AiConfigEntity(
            id = id,
            name = name,
            apiUrl = apiUrl,
            apiKeyEnc = apiKey,
            model = model,
            maxTokens = maxTokens,
            temperature = temperature,
            streamEnabled = streamEnabled,
            createdAt = now,
            updatedAt = now
        )
    }

    private suspend fun importCommandHistory(raw: Any?): Int {
        val history = decodeCommandHistory(raw)
        commandHistoryRepository.replaceAll(history)
        return history.size
    }

    private fun decodeCommandHistory(raw: Any?): List<CommandHistoryEntity> {
        val array = when (raw) {
            is JSONArray -> raw
            is JSONObject -> {
                if (raw.optBoolean("compressed", false)) {
                    val data = raw.optString("data", "")
                    if (data.isBlank()) JSONArray() else JSONArray(gunzipBase64(data))
                } else {
                    raw.optJSONArray("data") ?: JSONArray()
                }
            }
            else -> JSONArray()
        }

        val result = mutableListOf<CommandHistoryEntity>()
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val command = item.optString("command", "").trim()
            if (command.isBlank()) continue

            val timestamp = item.optLong("timestamp", System.currentTimeMillis())
            result.add(
                CommandHistoryEntity(
                    command = command,
                    count = item.optInt("count", 1).coerceAtLeast(1),
                    lastUsedAt = timestamp,
                    createdAt = timestamp
                )
            )
        }
        return result
    }

    private fun gunzipBase64(value: String): String {
        val compressed = Base64.decode(value, Base64.NO_WRAP)
        return GZIPInputStream(ByteArrayInputStream(compressed)).use { input ->
            input.readBytes().toString(Charsets.UTF_8)
        }
    }

    private fun decryptDesktopCredential(cipher: SimpleShellPcCredentialCipher, raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return null
        return cipher.decryptNullable(trimmed)
    }

    private fun parsePort(value: Any?): Int {
        val port = when (value) {
            is Number -> value.toInt()
            is String -> value.trim().toIntOrNull()
            else -> null
        }
        require(port != null && port in 1..65535) { "Invalid connection port: $value" }
        return port
    }

    private class Counters {
        var createdGroups: Int = 0
        var importedConnections: Int = 0
        var importedPasswordConnections: Int = 0
        var importedKeyConnections: Int = 0
        var skippedConnections: Int = 0
        var importedAiConfigs: Int = 0
        var importedCommandHistoryEntries: Int = 0

        fun toSummary(): SimpleShellPcImportSummary = SimpleShellPcImportSummary(
            createdGroups = createdGroups,
            importedConnections = importedConnections,
            importedPasswordConnections = importedPasswordConnections,
            importedKeyConnections = importedKeyConnections,
            skippedConnections = skippedConnections,
            importedAiConfigs = importedAiConfigs,
            importedCommandHistoryEntries = importedCommandHistoryEntries
        )
    }

    companion object {
        private val DESKTOP_ONLY_ROOT_KEYS = listOf(
            "logSettings",
            "shortcutCommands",
            "sshKnownHosts"
        )

        fun desktopSettingKey(rootKey: String): String = "desktop_config.$rootKey"
    }
}
