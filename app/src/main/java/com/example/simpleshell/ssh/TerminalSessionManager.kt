package com.example.simpleshell.ssh

import android.content.Context
import com.example.simpleshell.domain.model.Connection
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.connection.channel.direct.Session
import net.schmizz.sshj.transport.verification.PromiscuousVerifier
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import javax.inject.Inject
import javax.inject.Singleton

data class TerminalConnectionSummary(
    val connectionId: Long,
    val connectionName: String,
    val connectedAtMs: Long
)

@Singleton
class TerminalSessionManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val lock = Any()
    private val sessions = mutableMapOf<Long, TerminalSshSession>()

    private val _connectedSessions =
        MutableStateFlow<Map<Long, TerminalConnectionSummary>>(emptyMap())
    val connectedSessions: StateFlow<Map<Long, TerminalConnectionSummary>> =
        _connectedSessions.asStateFlow()

    fun getSession(connectionId: Long): TerminalSshSession {
        synchronized(lock) {
            return sessions.getOrPut(connectionId) {
                TerminalSshSession(
                    context = context,
                    connectionId = connectionId,
                    onConnected = { summary ->
                        _connectedSessions.update { current ->
                            current + (summary.connectionId to summary)
                        }
                    },
                    onDisconnected = { id ->
                        _connectedSessions.update { current ->
                            current - id
                        }
                    }
                )
            }
        }
    }

    /**
     * Connects a terminal session for the given [connection] without affecting other sessions.
     *
     * @return true if a new connection was established, false if the session was already connected.
     */
    suspend fun connectIfNeeded(connection: Connection): Boolean {
        val session = getSession(connection.id)
        return session.connectIfNeeded(connection)
    }

    fun disconnect(connectionId: Long) {
        synchronized(lock) {
            sessions[connectionId]
        }?.disconnect()
    }

    fun disconnectAll() {
        val all: List<TerminalSshSession> = synchronized(lock) { sessions.values.toList() }
        all.forEach { it.disconnect() }
    }
}

/**
 * A single interactive SSH shell session (PTY + shell) for one saved connection.
 *
 * This is intentionally kept independent of UI lifecycle so that multiple sessions can remain
 * connected while users navigate around the app.
 */
class TerminalSshSession internal constructor(
    private val context: Context,
    private val connectionId: Long,
    private val onConnected: (TerminalConnectionSummary) -> Unit,
    private val onDisconnected: (Long) -> Unit
) {
    private val stateLock = Any()

    private var client: SSHClient? = null
    private var session: Session? = null
    private var shell: Session.Shell? = null
    private var outputWriter: PrintWriter? = null
    private var inputReader: BufferedReader? = null
    private var tempKeyFile: File? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var readJob: Job? = null

    private val _outputFlow = MutableStateFlow("")
    val outputFlow: StateFlow<String> = _outputFlow.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _sessionId = MutableStateFlow(0L)
    val sessionId: StateFlow<Long> = _sessionId.asStateFlow()

    private val _connectedAtMs = MutableStateFlow<Long?>(null)
    val connectedAtMs: StateFlow<Long?> = _connectedAtMs.asStateFlow()

    private val outputBuffer = StringBuilder()

    suspend fun connectIfNeeded(connection: Connection): Boolean = withContext(Dispatchers.IO) {
        require(connection.id == connectionId) {
            "Terminal session requested for id=$connectionId but got connection.id=${connection.id}"
        }

        if (_isConnected.value) return@withContext false

        // Ensure we don't leak resources if connect() is called repeatedly.
        disconnect()

        // Match previous behavior: a *new* connect clears output. If the session was already
        // connected we would have returned earlier and preserved the buffer.
        clearOutput()

        try {
            val newClient = SSHClient().apply {
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

                        val keyProvider = connection.privateKeyPassphrase?.let { passphrase ->
                            loadKeys(materialized.file.absolutePath, passphrase)
                        } ?: loadKeys(materialized.file.absolutePath)
                        authPublickey(connection.username, keyProvider)
                    }
                }
            }

            // Assign as we go so disconnect() can clean up on partial failures.
            synchronized(stateLock) {
                client = newClient
            }

            val newSession = newClient.startSession()
            synchronized(stateLock) {
                session = newSession
            }
            newSession.allocateDefaultPTY()
            val newShell = newSession.startShell()
            synchronized(stateLock) {
                shell = newShell
            }

            val newWriter = PrintWriter(OutputStreamWriter(newShell.outputStream), true)
            val newReader = BufferedReader(InputStreamReader(newShell.inputStream))

            synchronized(stateLock) {
                outputWriter = newWriter
                inputReader = newReader
            }

            val now = System.currentTimeMillis()
            _isConnected.value = true
            _sessionId.value = _sessionId.value + 1
            _connectedAtMs.value = now
            onConnected(
                TerminalConnectionSummary(
                    connectionId = connectionId,
                    connectionName = connection.name,
                    connectedAtMs = now
                )
            )

            startReadingOutput()

            true
        } catch (e: Exception) {
            disconnect()
            throw e
        }
    }

    private fun startReadingOutput() {
        readJob?.cancel()
        readJob = scope.launch {
            try {
                val buffer = CharArray(1024)
                while (_isConnected.value) {
                    val reader = synchronized(stateLock) { inputReader } ?: break
                    val read = reader.read(buffer)
                    if (read > 0) {
                        val text = String(buffer, 0, read)
                        outputBuffer.append(text)
                        _outputFlow.value = outputBuffer.toString()
                    } else if (read == -1) {
                        break
                    }
                }
            } catch (e: Exception) {
                if (_isConnected.value) {
                    outputBuffer.append("\n[Connection closed: ${e.message}]\n")
                    _outputFlow.value = outputBuffer.toString()
                }
            } finally {
                // If the remote end closed unexpectedly, ensure we reflect disconnected state.
                if (_isConnected.value) {
                    disconnect()
                }
            }
        }
    }

    fun sendCommand(command: String) {
        scope.launch {
            try {
                // Keep the write+flush atomic with respect to disconnect() replacing/closing the writer.
                synchronized(stateLock) {
                    outputWriter?.apply {
                        println(command)
                        flush()
                    }
                }
            } catch (_: Exception) {
                // Ignore send errors
            }
        }
    }

    fun sendInput(input: String) {
        scope.launch {
            try {
                synchronized(stateLock) {
                    outputWriter?.apply {
                        print(input)
                        flush()
                    }
                }
            } catch (_: Exception) {
                // Ignore send errors
            }
        }
    }

    private data class DisconnectSnapshot(
        val writer: PrintWriter?,
        val reader: BufferedReader?,
        val shell: Session.Shell?,
        val session: Session?,
        val client: SSHClient?,
        val tempKeyFile: File?
    )

    fun disconnect() {
        val wasConnected = _isConnected.value

        _isConnected.value = false
        _connectedAtMs.value = null

        readJob?.cancel()
        readJob = null

        val toClose = synchronized(stateLock) {
            val snapshot = DisconnectSnapshot(
                writer = outputWriter,
                reader = inputReader,
                shell = shell,
                session = session,
                client = client,
                tempKeyFile = tempKeyFile
            )
            outputWriter = null
            inputReader = null
            shell = null
            session = null
            client = null
            tempKeyFile = null
            snapshot
        }

        runCatching { toClose.writer?.close() }
        runCatching { toClose.reader?.close() }
        runCatching { toClose.shell?.close() }
        runCatching { toClose.session?.close() }
        runCatching {
            toClose.client?.let { c ->
                try {
                    if (c.isConnected) {
                        c.disconnect()
                    }
                } finally {
                    c.close()
                }
            }
        }
        runCatching { toClose.tempKeyFile?.delete() }

        if (wasConnected) {
            onDisconnected(connectionId)
        }
    }

    fun clearOutput() {
        outputBuffer.clear()
        _outputFlow.value = ""
    }
}
