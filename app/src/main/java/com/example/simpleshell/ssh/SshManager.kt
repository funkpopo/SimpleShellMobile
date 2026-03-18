package com.example.simpleshell.ssh

import android.content.Context
import com.example.simpleshell.domain.model.Connection
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.common.IOUtils
import net.schmizz.sshj.connection.channel.direct.Session
import net.schmizz.sshj.connection.channel.direct.LocalPortForwarder
import net.schmizz.sshj.connection.channel.direct.Parameters
import net.schmizz.sshj.connection.channel.forwarded.RemotePortForwarder.Forward
import net.schmizz.sshj.connection.channel.forwarded.SocketForwardingConnectListener
import java.net.InetSocketAddress
import java.net.ServerSocket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import android.util.Log
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

        SshConnection(client, tempKeyFile).apply {
            startPortForwarding(connection.portForwardingRules)
        }
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
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private val localServerSockets = mutableListOf<ServerSocket>()

    val isConnected: Boolean
        get() = client.isConnected

    fun startPortForwarding(rules: List<com.example.simpleshell.domain.model.PortForwardingRule>) {
        rules.filter { it.isEnabled }.forEach { rule ->
            when (rule.type) {
                com.example.simpleshell.domain.model.PortForwardingRule.Type.LOCAL -> {
                    scope.launch {
                        try {
                            val params = Parameters(
                                "127.0.0.1", rule.localPort,
                                rule.remoteHost ?: "127.0.0.1", rule.remotePort ?: 80
                            )
                            val ss = ServerSocket()
                            ss.reuseAddress = true
                            ss.bind(InetSocketAddress(params.localHost, params.localPort))
                            localServerSockets.add(ss)
                            client.newLocalPortForwarder(params, ss).listen()
                        } catch (e: Exception) {
                            Log.e("SshConnection", "Failed to start local port forwarding", e)
                        }
                    }
                }
                com.example.simpleshell.domain.model.PortForwardingRule.Type.REMOTE -> {
                    scope.launch {
                        try {
                            val bindAddress = if (rule.remoteHost.isNullOrBlank()) "0.0.0.0" else rule.remoteHost
                            val forward = Forward(bindAddress, rule.remotePort ?: 8080)
                            client.remotePortForwarder.bind(
                                forward,
                                SocketForwardingConnectListener(InetSocketAddress("127.0.0.1", rule.localPort))
                            )
                        } catch (e: Exception) {
                            Log.e("SshConnection", "Failed to start remote port forwarding", e)
                        }
                    }
                }
                com.example.simpleshell.domain.model.PortForwardingRule.Type.DYNAMIC -> {
                    Log.w("SshConnection", "Dynamic port forwarding (SOCKS) is not natively supported by SSHJ yet.")
                    // TODO: Implement SOCKS proxy using local port forwarding or a custom SOCKS server
                }
            }
        }
    }

    override fun close() {
        // Best-effort cleanup. SSHJ can throw on close/disconnect depending on socket state.
        scope.cancel()
        localServerSockets.forEach { runCatching { it.close() } }
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
