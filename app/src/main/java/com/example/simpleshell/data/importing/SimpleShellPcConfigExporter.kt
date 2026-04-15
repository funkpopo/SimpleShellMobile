package com.example.simpleshell.data.importing

import android.util.Base64
import com.example.simpleshell.data.local.database.AiConfigDao
import com.example.simpleshell.data.local.database.CommandHistoryDao
import com.example.simpleshell.data.local.database.ConnectionDao
import com.example.simpleshell.data.local.database.GroupDao
import com.example.simpleshell.data.local.database.SettingsKvDao
import com.example.simpleshell.data.local.database.entity.AiConfigEntity
import com.example.simpleshell.data.local.database.entity.CommandHistoryEntity
import com.example.simpleshell.data.local.database.entity.ConnectionEntity
import com.example.simpleshell.data.local.database.entity.GroupEntity
import com.example.simpleshell.data.local.preferences.UserPreferencesRepository
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Exports mobile data as a desktop SimpleShell-compatible `config.json`.
 */
@Singleton
class SimpleShellPcConfigExporter @Inject constructor(
    private val connectionDao: ConnectionDao,
    private val groupDao: GroupDao,
    private val aiConfigDao: AiConfigDao,
    private val commandHistoryDao: CommandHistoryDao,
    private val settingsKvDao: SettingsKvDao,
    private val userPreferencesRepository: UserPreferencesRepository
) {
    suspend fun exportToConfigJson(): String {
        val groups = groupDao.getAllGroupsOnce()
        val connections = connectionDao.getAllConnectionsOnce()
        val aiConfigs = aiConfigDao.getAll()
        val commandHistory = commandHistoryDao.getAll()

        val uiSettings = buildUiSettings()
        val aiSettings = buildAiSettings(aiConfigs)
        val logSettings = loadStoredObject("logSettings") ?: defaultLogSettings()
        val shortcutCommands = loadStoredRaw("shortcutCommands") ?: "{}"
        val sshKnownHosts = loadStoredObject("sshKnownHosts") ?: JSONObject()
        val recentConnections = buildRecentConnections(connections)

        val root = JSONObject()
        root.put("security", SimpleShellPcCryptoCompat.getSecurityConfigJson())
        root.put("connections", buildConnectionsArray(groups, connections))
        root.put("uiSettings", uiSettings)
        root.put("aiSettings", aiSettings)
        root.put("logSettings", logSettings)
        root.put("shortcutCommands", shortcutCommands)
        root.put("commandHistory", compressCommandHistory(commandHistory))
        root.put("topConnections", recentConnections)
        root.put("lastConnections", recentConnections)
        root.put("sshKnownHosts", sshKnownHosts)

        return root.toString(2)
    }

    private suspend fun buildUiSettings(): JSONObject {
        val base = defaultUiSettings()
        mergeObject(base, loadStoredObject("uiSettings"))
        mergeObject(
            base,
            JSONObject(userPreferencesRepository.buildSyncUiSettingsJson())
        )
        return base
    }

    private suspend fun buildAiSettings(aiConfigs: List<AiConfigEntity>): JSONObject {
        val stored = loadStoredObject("aiSettings")
        val result = JSONObject().apply {
            put("configs", JSONArray())
            put("current", JSONObject.NULL)
            put("windowSize", JSONObject().apply {
                put("width", 360)
                put("height", 540)
            })
        }
        mergeObject(result, stored, skipKeys = setOf("configs", "current"))

        val configsArray = JSONArray()
        for (config in aiConfigs.sortedByDescending { it.updatedAt }) {
            configsArray.put(aiConfigToJson(config))
        }
        result.put("configs", configsArray)

        val currentId = stored?.optJSONObject("current")?.optString("id", "")?.trim().orEmpty()
        val currentConfig = aiConfigs.firstOrNull { it.id == currentId } ?: aiConfigs.maxByOrNull { it.updatedAt }
        result.put("current", currentConfig?.let(::aiConfigToJson) ?: JSONObject.NULL)

        return result
    }

    private fun aiConfigToJson(config: AiConfigEntity): JSONObject {
        return JSONObject().apply {
            put("id", config.id)
            put("name", config.name)
            put("apiUrl", config.apiUrl)
            put("apiKey", SimpleShellPcCryptoCompat.encryptNullableMaybe(config.apiKeyEnc).orEmpty())
            put("model", config.model ?: "")
            put("maxTokens", config.maxTokens ?: JSONObject.NULL)
            put("temperature", config.temperature ?: JSONObject.NULL)
            put("streamEnabled", config.streamEnabled != 0)
        }
    }

    private fun buildConnectionsArray(
        groups: List<GroupEntity>,
        connections: List<ConnectionEntity>
    ): JSONArray {
        val root = GroupNode(name = null)
        val nodeByGroupId = mutableMapOf<Long, GroupNode>()

        for (group in groups.sortedBy { it.name.lowercase() }) {
            val path = group.name.split(" / ").map { it.trim() }.filter { it.isNotEmpty() }
            var current = root
            for (segment in path) {
                current = current.children.getOrPut(segment) { GroupNode(name = segment) }
            }
            nodeByGroupId[group.id] = current
        }

        for (connection in connections.sortedWith(compareBy<ConnectionEntity> { it.groupId ?: Long.MIN_VALUE }.thenBy { it.createdAt })) {
            val groupId = connection.groupId
            if (groupId != null) {
                nodeByGroupId[groupId]?.connections?.add(connection) ?: root.connections.add(connection)
            } else {
                root.connections.add(connection)
            }
        }

        return buildGroupItems(root)
    }

    private fun buildGroupItems(node: GroupNode): JSONArray {
        val array = JSONArray()

        for ((name, child) in node.children) {
            array.put(
                JSONObject().apply {
                    put("id", "group_${stableDesktopIdForGroupPath(name, child)}")
                    put("type", "group")
                    put("name", name)
                    put("items", buildGroupItems(child))
                }
            )
        }

        for (connection in node.connections) {
            array.put(connectionToJson(connection))
        }

        return array
    }

    private fun stableDesktopIdForGroupPath(name: String, child: GroupNode): String {
        val descendants = mutableListOf(name)
        collectDescendantNames(child, descendants)
        return descendants.joinToString("_") { segment ->
            segment.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')
        }.ifBlank { "group" }
    }

    private fun collectDescendantNames(node: GroupNode, out: MutableList<String>) {
        for ((childName, childNode) in node.children) {
            out.add(childName)
            collectDescendantNames(childNode, out)
        }
    }

    private fun connectionToJson(connection: ConnectionEntity): JSONObject {
        val keyAuth = connection.authType.equals("key", ignoreCase = true) ||
            connection.authType.equals("privateKey", ignoreCase = true)
        val rawKey = connection.privateKey?.trim().orEmpty()
        val inlinePrivateKey = keyAuth && looksLikePrivateKeyContent(rawKey)
        val passphrase = connection.privateKeyPassphrase?.trim().orEmpty()

        return JSONObject().apply {
            put("id", "conn_${connection.id}")
            put("type", "connection")
            put("name", connection.name)
            put("host", connection.host)
            put("port", connection.port)
            put("username", connection.username)
            put(
                "password",
                if (keyAuth) "" else SimpleShellPcCryptoCompat.encryptNullableMaybe(connection.password).orEmpty()
            )
            put("authType", if (keyAuth) "privateKey" else "password")
            put("privateKey", if (inlinePrivateKey) rawKey else "")
            put(
                "privateKeyPath",
                if (keyAuth && !inlinePrivateKey) SimpleShellPcCryptoCompat.encryptNullableMaybe(rawKey).orEmpty() else ""
            )
            put("passphrase", if (keyAuth) passphrase else "")
            put("country", "")
            put("os", "")
            put("connectionType", "")
            put("protocol", "ssh")
            put("proxy", JSONObject.NULL)
        }
    }

    private fun buildRecentConnections(connections: List<ConnectionEntity>): JSONArray {
        val recent = connections
            .filter { it.lastConnectedAt != null }
            .sortedByDescending { it.lastConnectedAt }
            .take(5)

        val array = JSONArray()
        for (connection in recent) {
            array.put(
                JSONObject().apply {
                    put("id", "conn_${connection.id}")
                    put("connectionId", "conn_${connection.id}")
                    put("serverKey", "${connection.host}:${connection.port}:${connection.username}")
                    put("name", connection.name)
                    put("type", "connection")
                    put("protocol", "ssh")
                    put("host", connection.host)
                    put("port", connection.port)
                    put("username", connection.username)
                    put("password", "")
                    put("privateKeyPath", "")
                    put("proxy", JSONObject.NULL)
                    put("lastUsed", connection.lastConnectedAt)
                }
            )
        }
        return array
    }

    private fun compressCommandHistory(history: List<CommandHistoryEntity>): JSONObject {
        val array = JSONArray()
        for (item in history.sortedByDescending { it.lastUsedAt }) {
            array.put(
                JSONObject().apply {
                    put("command", item.command)
                    put("timestamp", item.lastUsedAt)
                    put("count", item.count)
                }
            )
        }

        val jsonString = array.toString()
        val compressed = ByteArrayOutputStream().use { buffer ->
            GZIPOutputStream(buffer).use { gzip ->
                gzip.write(jsonString.toByteArray(Charsets.UTF_8))
            }
            buffer.toByteArray()
        }

        return JSONObject().apply {
            put("compressed", true)
            put("data", Base64.encodeToString(compressed, Base64.NO_WRAP))
            put("originalSize", jsonString.toByteArray(Charsets.UTF_8).size)
            put("compressedSize", compressed.size)
            put("timestamp", System.currentTimeMillis())
        }
    }

    private suspend fun loadStoredObject(rootKey: String): JSONObject? {
        val raw = loadStoredRaw(rootKey) ?: return null
        return runCatching { JSONObject(raw) }.getOrNull()
    }

    private suspend fun loadStoredRaw(rootKey: String): String? {
        return settingsKvDao.getByKey(SimpleShellPcConfigImporter.desktopSettingKey(rootKey))?.valueJson
    }

    private fun mergeObject(
        target: JSONObject,
        source: JSONObject?,
        skipKeys: Set<String> = emptySet()
    ) {
        if (source == null) return
        val keys = source.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            if (key in skipKeys) continue
            target.put(key, source.get(key))
        }
    }

    private fun defaultUiSettings(): JSONObject = JSONObject().apply {
        put("language", "zh-CN")
        put("fontSize", 14)
        put("editorFont", "system")
        put("darkMode", true)
        put("sidebarPosition", "right")
        put("terminalFont", "Fira Code")
        put("terminalFontSize", 14)
        put("performance", JSONObject().apply {
            put("imageSupported", true)
            put("cacheEnabled", true)
            put("prefetchEnabled", true)
            put("webglEnabled", true)
        })
        put("externalEditor", JSONObject().apply {
            put("enabled", false)
            put("command", "")
        })
        put("terminalFontWeight", 606)
        put("dnd", JSONObject().apply {
            put("enabled", true)
            put("autoScroll", true)
            put("compactDragPreview", false)
        })
        put("transferBarMode", "sidebar")
    }

    private fun defaultLogSettings(): JSONObject = JSONObject().apply {
        put("level", "DEBUG")
        put("maxFileSize", 5_242_880)
        put("maxFiles", 5)
        put("compressOldLogs", true)
        put("cleanupIntervalDays", 7)
        put("cleanupInterval", 24)
    }

    private data class GroupNode(
        val name: String?,
        val children: LinkedHashMap<String, GroupNode> = linkedMapOf(),
        val connections: MutableList<ConnectionEntity> = mutableListOf()
    )

    private fun looksLikePrivateKeyContent(text: String): Boolean {
        if (text.isBlank()) return false
        if (text.contains('\n') || text.contains('\r')) return true
        if (text.contains("-----BEGIN")) return true
        if (text.contains("BEGIN OPENSSH PRIVATE KEY")) return true
        return false
    }
}
