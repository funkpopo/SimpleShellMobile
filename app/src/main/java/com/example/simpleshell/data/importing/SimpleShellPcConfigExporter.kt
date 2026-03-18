package com.example.simpleshell.data.importing

import com.example.simpleshell.data.local.database.ConnectionDao
import com.example.simpleshell.data.local.database.GroupDao
import com.example.simpleshell.data.local.database.entity.ConnectionEntity
import com.example.simpleshell.data.local.database.entity.GroupEntity
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Exports the current mobile data to a PC-compatible `config.json` payload.
 *
 * The resulting JSON keeps groups and connections in the same "connections" tree
 * shape consumed by [SimpleShellPcConfigImporter].
 */
@Singleton
class SimpleShellPcConfigExporter @Inject constructor(
    private val connectionDao: ConnectionDao,
    private val groupDao: GroupDao
) {
    suspend fun exportToConfigJson(): String {
        val groups = groupDao.getAllGroupsOnce()
        val connections = connectionDao.getAllConnectionsOnce()

        val groupedConnections = groups
            .associate { it.id to mutableListOf<ConnectionEntity>() }
            .toMutableMap()
        val ungroupedConnections = mutableListOf<ConnectionEntity>()

        for (connection in connections) {
            val groupId = connection.groupId
            if (groupId != null && groupedConnections.containsKey(groupId)) {
                groupedConnections[groupId]?.add(connection)
            } else {
                ungroupedConnections.add(connection)
            }
        }

        val root = JSONObject()
        root.put("connections", buildConnectionsArray(groups, groupedConnections, ungroupedConnections))
        
        // Add default PC config fields to match the exact structure
        root.put("uiSettings", JSONObject().apply {
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
            put("terminalFontWeight", 500)
            put("dnd", JSONObject().apply {
                put("enabled", true)
                put("autoScroll", true)
                put("compactDragPreview", false)
            })
            put("transferBarMode", "sidebar")
        })
        
        root.put("aiSettings", JSONObject().apply {
            put("configs", JSONArray())
            put("current", JSONObject.NULL)
            put("windowSize", JSONObject().apply {
                put("width", 360)
                put("height", 540)
            })
        })
        
        root.put("logSettings", JSONObject().apply {
            put("level", "DEBUG")
            put("maxFileSize", 5242880)
            put("maxFiles", 5)
            put("compressOldLogs", true)
            put("cleanupIntervalDays", 7)
            put("cleanupInterval", 24)
        })
        
        root.put("shortcutCommands", "{}")
        
        root.put("commandHistory", JSONObject().apply {
            put("compressed", true)
            put("data", "")
            put("originalSize", 0)
            put("compressedSize", 0)
            put("timestamp", System.currentTimeMillis())
        })
        
        root.put("topConnections", JSONArray())
        root.put("lastConnections", JSONArray())
        root.put("sshKnownHosts", JSONObject())

        return root.toString(2)
    }

    private fun buildConnectionsArray(
        groups: List<GroupEntity>,
        groupedConnections: Map<Long, List<ConnectionEntity>>,
        ungroupedConnections: List<ConnectionEntity>
    ): JSONArray {
        val array = JSONArray()

        for (group in groups) {
            val items = JSONArray()
            for (connection in groupedConnections[group.id].orEmpty()) {
                items.put(connectionToJson(connection))
            }
            array.put(
                JSONObject().apply {
                    put("id", "group_${group.id}")
                    put("type", "group")
                    put("name", group.name)
                    put("items", items)
                }
            )
        }

        for (connection in ungroupedConnections) {
            array.put(connectionToJson(connection))
        }

        return array
    }

    private fun connectionToJson(connection: ConnectionEntity): JSONObject {
        val keyAuth = connection.authType.equals("key", ignoreCase = true) ||
            connection.authType.equals("privateKey", ignoreCase = true)
        val authType = if (keyAuth) "privateKey" else "password"

        val password = if (!keyAuth) {
            SimpleShellPcCryptoCompat.encryptNullableMaybe(connection.password).orEmpty()
        } else {
            ""
        }
        val privateKey = if (keyAuth) {
            SimpleShellPcCryptoCompat.encryptNullableMaybe(connection.privateKey).orEmpty()
        } else {
            ""
        }
        val privateKeyPassphrase = if (keyAuth) {
            SimpleShellPcCryptoCompat.encryptNullableMaybe(connection.privateKeyPassphrase).orEmpty()
        } else {
            ""
        }

        return JSONObject().apply {
            put("id", "conn_${connection.id}")
            put("type", "connection")
            put("name", connection.name)
            put("host", connection.host)
            put("port", connection.port)
            put("username", connection.username)
            put("authType", authType)
            put("password", password)
            put("privateKey", privateKey)
            put("privateKeyPassphrase", privateKeyPassphrase)
            put("privateKeyPath", "")
            put("country", "")
            put("os", "")
            put("connectionType", "")
            put("protocol", "ssh")
            put("proxy", JSONObject.NULL)
        }
    }
}
