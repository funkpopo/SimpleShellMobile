package com.example.simpleshell.ssh

import com.example.simpleshell.domain.model.Connection
import com.example.simpleshell.domain.model.SftpFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.sftp.FileMode
import net.schmizz.sshj.sftp.SFTPClient
import net.schmizz.sshj.transport.verification.PromiscuousVerifier
import javax.inject.Inject

class SftpManager @Inject constructor() {
    private var client: SSHClient? = null
    private var sftpClient: SFTPClient? = null

    suspend fun connect(connection: Connection) = withContext(Dispatchers.IO) {
        client = SSHClient().apply {
            addHostKeyVerifier(PromiscuousVerifier())
            connect(connection.host, connection.port)

            when (connection.authType) {
                Connection.AuthType.PASSWORD -> {
                    authPassword(connection.username, connection.password ?: "")
                }
                Connection.AuthType.KEY -> {
                    val keyProvider = if (connection.privateKeyPassphrase != null) {
                        loadKeys(connection.privateKey, connection.privateKeyPassphrase)
                    } else {
                        loadKeys(connection.privateKey, null as String?)
                    }
                    authPublickey(connection.username, keyProvider)
                }
            }
        }
        sftpClient = client?.newSFTPClient()
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
        try {
            sftpClient?.close()
            client?.disconnect()
        } catch (e: Exception) {
            // Ignore disconnect errors
        } finally {
            sftpClient = null
            client = null
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
