package com.example.simpleshell.ssh

import android.content.Context
import com.example.simpleshell.domain.model.Connection
import com.example.simpleshell.domain.model.SftpFile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.sftp.FileMode
import net.schmizz.sshj.sftp.SFTPClient
import net.schmizz.sshj.transport.verification.PromiscuousVerifier
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SftpManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var client: SSHClient? = null
    private var sftpClient: SFTPClient? = null
    private var tempKeyFile: File? = null

    private val _isConnected = MutableStateFlow(false)
    val isConnectedFlow: StateFlow<Boolean> = _isConnected.asStateFlow()

    suspend fun connect(connection: Connection) = withContext(Dispatchers.IO) {
        try {
            disconnect()
            client = SSHClient().apply {
                addHostKeyVerifier(PromiscuousVerifier())
                connect(connection.host, connection.port)

                when (connection.authType) {
                    Connection.AuthType.PASSWORD -> {
                        authPassword(connection.username, connection.password ?: "")
                    }
                    Connection.AuthType.KEY -> {
                        val rawKey = connection.privateKey ?: ""
                        val materialized = materializePrivateKey(context, rawKey)
                        tempKeyFile = if (materialized.isTemp) materialized.file else null

                        val keyProvider = if (connection.privateKeyPassphrase != null) {
                            loadKeys(materialized.file.absolutePath, connection.privateKeyPassphrase)
                        } else {
                            loadKeys(materialized.file.absolutePath, null as String?)
                        }
                        authPublickey(connection.username, keyProvider)
                    }
                }
            }
            sftpClient = client?.newSFTPClient()
            _isConnected.value = true
        } catch (e: Exception) {
            _isConnected.value = false
            throw e
        }
    }

    suspend fun listFiles(path: String): List<SftpFile> = withContext(Dispatchers.IO) {
        sftpClient?.ls(path)?.map { file ->
            SftpFile(
                name = file.name,
                path = "$path/${file.name}".replace("//", "/"),
                isDirectory = file.isDirectory,
                size = file.attributes.size,
                modifiedTime = file.attributes.mtime * 1000L,
                permissions = formatPermissions(file.attributes.mode)
            )
        }?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            ?: emptyList()
    }

    suspend fun downloadFile(remotePath: String, localPath: String) = withContext(Dispatchers.IO) {
        sftpClient?.get(remotePath, localPath)
    }

    suspend fun uploadFile(localPath: String, remotePath: String) = withContext(Dispatchers.IO) {
        sftpClient?.put(localPath, remotePath)
    }

    suspend fun deleteFile(path: String) = withContext(Dispatchers.IO) {
        sftpClient?.rm(path)
    }

    suspend fun deleteDirectory(path: String) = withContext(Dispatchers.IO) {
        sftpClient?.rmdir(path)
    }

    suspend fun createDirectory(path: String) = withContext(Dispatchers.IO) {
        sftpClient?.mkdir(path)
    }

    suspend fun getHomeDirectory(): String = withContext(Dispatchers.IO) {
        sftpClient?.canonicalize(".") ?: "/"
    }

    fun disconnect() {
        _isConnected.value = false
        try {
            sftpClient?.close()
            client?.disconnect()
        } catch (e: Exception) {
            // Ignore disconnect errors
        } finally {
            sftpClient = null
            client = null
            tempKeyFile?.delete()
            tempKeyFile = null
        }
    }

    val isConnected: Boolean
        get() = client?.isConnected == true

    private fun formatPermissions(mode: FileMode): String {
        val mask = mode.mask
        val perms = StringBuilder()
        perms.append(if ((mask and 0x4000) != 0) 'd' else '-')
        perms.append(if ((mask and 0x100) != 0) 'r' else '-')
        perms.append(if ((mask and 0x80) != 0) 'w' else '-')
        perms.append(if ((mask and 0x40) != 0) 'x' else '-')
        perms.append(if ((mask and 0x20) != 0) 'r' else '-')
        perms.append(if ((mask and 0x10) != 0) 'w' else '-')
        perms.append(if ((mask and 0x8) != 0) 'x' else '-')
        perms.append(if ((mask and 0x4) != 0) 'r' else '-')
        perms.append(if ((mask and 0x2) != 0) 'w' else '-')
        perms.append(if ((mask and 0x1) != 0) 'x' else '-')
        return perms.toString()
    }
}
