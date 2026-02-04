package com.example.simpleshell.ssh

import com.example.simpleshell.domain.model.Connection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.common.IOUtils
import net.schmizz.sshj.connection.channel.direct.Session
import net.schmizz.sshj.transport.verification.PromiscuousVerifier
import net.schmizz.sshj.userauth.keyprovider.KeyProvider
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SshManager @Inject constructor() {

    suspend fun connect(connection: Connection): SshConnection = withContext(Dispatchers.IO) {
        val client = SSHClient()
        client.addHostKeyVerifier(PromiscuousVerifier())
        client.connect(connection.host, connection.port)

        when (connection.authType) {
            Connection.AuthType.PASSWORD -> {
                client.authPassword(connection.username, connection.password ?: "")
            }
            Connection.AuthType.KEY -> {
                val keyProvider: KeyProvider = if (connection.privateKeyPassphrase != null) {
                    client.loadKeys(connection.privateKey, connection.privateKeyPassphrase)
                } else {
                    client.loadKeys(connection.privateKey, null as String?)
                }
                client.authPublickey(connection.username, keyProvider)
            }
        }

        SshConnection(client)
    }

    suspend fun executeCommand(sshConnection: SshConnection, command: String): String =
        withContext(Dispatchers.IO) {
            val session = sshConnection.client.startSession()
            try {
                val cmd = session.exec(command)
                cmd.join(30, TimeUnit.SECONDS)
                IOUtils.readFully(cmd.inputStream).toString(Charsets.UTF_8)
            } finally {
                session.close()
            }
        }
}

class SshConnection(val client: SSHClient) {
    val isConnected: Boolean
        get() = client.isConnected

    fun disconnect() {
        if (client.isConnected) {
            client.disconnect()
        }
    }
}
