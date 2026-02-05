package com.example.simpleshell.data.importing

import com.example.simpleshell.data.local.database.entity.ConnectionEntity
import com.example.simpleshell.data.repository.ConnectionRepository
import com.example.simpleshell.data.repository.GroupRepository
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

data class SimpleShellPcImportSummary(
    val createdGroups: Int,
    val importedConnections: Int,
    val importedPasswordConnections: Int,
    val importedKeyConnections: Int,
    val skippedConnections: Int
)

/**
 * Imports the PC application's `config.json` format into the mobile Room database.
 *
 * Notes:
 * - Passwords/privateKeyPath are encrypted in the PC app config; we keep the encrypted payload and decrypt
 *   it only when connecting. If a config contains plaintext passwords, we encrypt them before storage.
 * - The PC app stores *privateKeyPath* (file path), while mobile stores the PEM content. For now we
 *   import key-based connections without the private key content; users can edit and import the key file.
 */
@Singleton
class SimpleShellPcConfigImporter @Inject constructor(
    private val connectionRepository: ConnectionRepository,
    private val groupRepository: GroupRepository
) {
    suspend fun importFromConfigJson(jsonText: String): SimpleShellPcImportSummary {
        val root = JSONObject(jsonText)
        val connections = root.optJSONArray("connections") ?: JSONArray()

        val counters = Counters()
        importItems(connections, groupId = null, groupPath = emptyList(), counters = counters)
        return counters.toSummary()
    }

    private suspend fun importItems(
        items: JSONArray,
        groupId: Long?,
        groupPath: List<String>,
        counters: Counters
    ) {
        for (i in 0 until items.length()) {
            val obj = items.optJSONObject(i) ?: continue
            when (obj.optString("type", "").lowercase()) {
                "group" -> importGroup(obj, groupPath, counters)
                "connection" -> importConnection(obj, groupId, counters)
                else -> Unit
            }
        }
    }

    private suspend fun importGroup(obj: JSONObject, parentPath: List<String>, counters: Counters) {
        val rawName = obj.optString("name", "").trim()
        if (rawName.isBlank()) return

        val currentPath = parentPath + rawName
        val groupName = currentPath.joinToString(" / ")

        val (groupId, created) = groupRepository.getOrCreateGroupId(groupName)
        if (created) counters.createdGroups++

        val children = obj.optJSONArray("items") ?: return
        importItems(children, groupId = groupId, groupPath = currentPath, counters = counters)
    }

    private suspend fun importConnection(obj: JSONObject, groupId: Long?, counters: Counters) {
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
        val wantsKeyAuth =
            authTypeRaw.equals("privateKey", ignoreCase = true) || authTypeRaw.equals("key", ignoreCase = true)

        val passwordRaw = obj.optString("password", "").trim()
        val passwordToStore = passwordRaw.ifBlank { null }

        // Keep password-based connections usable immediately. If PC config says "privateKey" but a password
        // is present, prefer password auth (mobile can't import the key file path as PEM content).
        val finalAuthType = if (wantsKeyAuth && passwordToStore.isNullOrBlank()) "key" else "password"

        val entity = ConnectionEntity(
            name = name,
            groupId = groupId,
            host = host,
            port = port,
            username = username,
            // Stored encrypted at rest by ConnectionRepository; we keep the raw PC payload here.
            password = if (finalAuthType == "password") passwordToStore else null,
            privateKey = null,
            privateKeyPassphrase = null,
            authType = finalAuthType
        )

        connectionRepository.saveConnection(entity)

        counters.importedConnections++
        if (finalAuthType == "key") counters.importedKeyConnections++ else counters.importedPasswordConnections++
    }

    private fun parsePort(value: Any?): Int {
        val port = when (value) {
            is Number -> value.toInt()
            is String -> value.trim().toIntOrNull()
            else -> null
        } ?: 22
        return if (port in 1..65535) port else 22
    }

    private class Counters {
        var createdGroups: Int = 0
        var importedConnections: Int = 0
        var importedPasswordConnections: Int = 0
        var importedKeyConnections: Int = 0
        var skippedConnections: Int = 0

        fun toSummary(): SimpleShellPcImportSummary = SimpleShellPcImportSummary(
            createdGroups = createdGroups,
            importedConnections = importedConnections,
            importedPasswordConnections = importedPasswordConnections,
            importedKeyConnections = importedKeyConnections,
            skippedConnections = skippedConnections
        )
    }
}
