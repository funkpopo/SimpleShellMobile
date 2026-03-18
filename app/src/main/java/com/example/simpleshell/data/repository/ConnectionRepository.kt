package com.example.simpleshell.data.repository

import com.example.simpleshell.data.importing.SimpleShellPcCryptoCompat
import com.example.simpleshell.data.local.database.ConnectionDao
import com.example.simpleshell.data.local.database.PortForwardingDao
import com.example.simpleshell.data.local.database.entity.ConnectionEntity
import com.example.simpleshell.data.local.database.entity.PortForwardingEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConnectionRepository @Inject constructor(
    private val connectionDao: ConnectionDao,
    private val portForwardingDao: PortForwardingDao
) {
    fun getAllConnections(): Flow<List<ConnectionEntity>> {
        return connectionDao.getAllConnections()
    }

    suspend fun getConnectionById(id: Long): ConnectionEntity? {
        return connectionDao.getConnectionById(id)
    }

    suspend fun saveConnection(connection: ConnectionEntity, rules: List<PortForwardingEntity> = emptyList()): Long {
        val id = connectionDao.insertConnection(connection.encryptSecretsForStorage())
        if (rules.isNotEmpty()) {
            portForwardingDao.insertRules(rules.map { it.copy(connectionId = id) })
        }
        return id
    }

    suspend fun updateConnection(connection: ConnectionEntity, rules: List<PortForwardingEntity> = emptyList()) {
        connectionDao.updateConnection(connection.encryptSecretsForStorage())
        portForwardingDao.deleteRulesForConnection(connection.id)
        if (rules.isNotEmpty()) {
            portForwardingDao.insertRules(rules.map { it.copy(connectionId = connection.id) })
        }
    }

    suspend fun deleteConnection(connection: ConnectionEntity) {
        connectionDao.deleteConnection(connection)
        portForwardingDao.deleteRulesForConnection(connection.id)
    }

    fun getPortForwardingRules(connectionId: Long): Flow<List<PortForwardingEntity>> {
        return portForwardingDao.getRulesForConnection(connectionId)
    }

    suspend fun getPortForwardingRulesOnce(connectionId: Long): List<PortForwardingEntity> {
        return portForwardingDao.getRulesForConnectionOnce(connectionId)
    }

    suspend fun updateLastConnectedAt(id: Long) {
        connectionDao.updateLastConnectedAt(id, System.currentTimeMillis())
    }

    suspend fun clearGroupForConnections(groupId: Long) {
        connectionDao.clearGroupForConnections(groupId)
    }

    private fun ConnectionEntity.encryptSecretsForStorage(): ConnectionEntity {
        // Keep the rest of the entity intact, but ensure secrets are encrypted at rest.
        return copy(
            password = SimpleShellPcCryptoCompat.encryptNullableMaybe(password),
            privateKey = SimpleShellPcCryptoCompat.encryptNullableMaybe(privateKey),
            privateKeyPassphrase = SimpleShellPcCryptoCompat.encryptNullableMaybe(privateKeyPassphrase)
        )
    }
}
