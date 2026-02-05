package com.example.simpleshell.data.repository

import com.example.simpleshell.data.importing.SimpleShellPcCryptoCompat
import com.example.simpleshell.data.local.database.ConnectionDao
import com.example.simpleshell.data.local.database.entity.ConnectionEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConnectionRepository @Inject constructor(
    private val connectionDao: ConnectionDao
) {
    fun getAllConnections(): Flow<List<ConnectionEntity>> {
        return connectionDao.getAllConnections()
    }

    suspend fun getConnectionById(id: Long): ConnectionEntity? {
        return connectionDao.getConnectionById(id)
    }

    suspend fun saveConnection(connection: ConnectionEntity): Long {
        return connectionDao.insertConnection(connection.encryptSecretsForStorage())
    }

    suspend fun updateConnection(connection: ConnectionEntity) {
        connectionDao.updateConnection(connection.encryptSecretsForStorage())
    }

    suspend fun deleteConnection(connection: ConnectionEntity) {
        connectionDao.deleteConnection(connection)
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
            privateKeyPassphrase = SimpleShellPcCryptoCompat.encryptNullableMaybe(privateKeyPassphrase)
        )
    }
}
