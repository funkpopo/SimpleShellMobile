package com.example.simpleshell.ssh

import android.content.Context
import com.example.simpleshell.domain.model.Connection
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.common.IOUtils
import net.schmizz.sshj.connection.channel.direct.Session
import net.schmizz.sshj.transport.verification.PromiscuousVerifier
import net.schmizz.sshj.userauth.keyprovider.KeyProvider
import java.io.Closeable
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SshManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    suspend fun connect(connection: Connection): SshConnection = withContext(Dispatchers.IO) {
        val client = SSHClient()
        client.addHostKeyVerifier(PromiscuousVerifier())
        client.connect(connection.host, connection.port)

        var tempKeyFile: File? = null
        when (connection.authType) {
            Connection.AuthType.PASSWORD -> {
                client.authPassword(connection.username, connection.password ?: "")
            }
            Connection.AuthType.KEY -> {
                val rawKey = connection.privateKey.orEmpty()
                val materialized = materializePrivateKey(context, rawKey)
                tempKeyFile = if (materialized.isTemp) materialized.file else null

                val keyProvider: KeyProvider = connection.privateKeyPassphrase?.let { passphrase ->
                    client.loadKeys(materialized.file.absolutePath, passphrase)
                } ?: client.loadKeys(materialized.file.absolutePath)
                client.authPublickey(connection.username, keyProvider)
            }
        }

        SshConnection(client, tempKeyFile)
    }

    suspend fun executeCommand(sshConnection: SshConnection, command: String): String =
        withContext(Dispatchers.IO) {
            val session = sshConnection.client.startSession()
            try {
                val cmd = session.exec(command)
                cmd.join(30, TimeUnit.SECONDS)
                // Avoid ByteArrayOutputStream#toString(Charset) (API 33+). Decode manually for minSdk 26.
                val bytes = IOUtils.readFully(cmd.inputStream).toByteArray()
                String(bytes, Charsets.UTF_8)
            } finally {
                session.close()
            }
        }
}

class SshConnection internal constructor(
    internal val client: SSHClient,
    private val tempKeyFile: File?
) : Closeable {
    val isConnected: Boolean
        get() = client.isConnected

    override fun close() {
        // Best-effort cleanup. SSHJ can throw on close/disconnect depending on socket state.
        runCatching {
            if (client.isConnected) {
                client.disconnect()
            }
        }
        runCatching { client.close() }
        runCatching { tempKeyFile?.delete() }
    }

    fun disconnect() = close()
}
