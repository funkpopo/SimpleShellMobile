package com.example.simpleshell.ssh

import android.content.Context
import android.util.Log
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
import net.schmizz.sshj.common.IOUtils
import net.schmizz.sshj.connection.channel.direct.Session
import net.schmizz.sshj.transport.verification.PromiscuousVerifier
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
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
    private val disconnectGeneration = AtomicLong(0L)

    companion object {
        /** Cap the output buffer at ~512 KB to prevent OOM on long-running sessions. */
        private const val MAX_OUTPUT_BUFFER_SIZE = 512 * 1024
    }

    suspend fun connectIfNeeded(connection: Connection): Boolean = withContext(Dispatchers.IO) {
        require(connection.id == connectionId) {
            "Terminal session requested for id=$connectionId but got connection.id=${connection.id}"
        }

        if (_isConnected.value) return@withContext false

        // Clean up any stale/half-open resources from a previous attempt without emitting
        // a "disconnected" event (we're not transitioning from a connected state).
        disconnectInternal(bumpGeneration = false, emitDisconnected = false)

        // Match previous behavior: a *new* connect clears output. If the session was already
        // connected we would have returned earlier and preserved the buffer.
        clearOutput()

        // If disconnect() is called while we're connecting, it bumps this generation.
        val connectGeneration = disconnectGeneration.get()

        var newClient: SSHClient? = null
        var newSession: Session? = null
        var newShell: Session.Shell? = null
        var newWriter: PrintWriter? = null
        var newReader: BufferedReader? = null
        var newTempKeyFile: File? = null

        try {
            newClient = SSHClient().apply {
                addHostKeyVerifier(PromiscuousVerifier())
                connect(connection.host, connection.port)

                when (connection.authType) {
                    Connection.AuthType.PASSWORD -> {
                        authPassword(connection.username, connection.password ?: "")
                    }
                    Connection.AuthType.KEY -> {
                        val rawKey = connection.privateKey ?: ""
                        val materialized = materializePrivateKey(context, rawKey)
                        newTempKeyFile = if (materialized.isTemp) materialized.file else null

                        val keyProvider = connection.privateKeyPassphrase?.let { passphrase ->
                            loadKeys(materialized.file.absolutePath, passphrase)
                        } ?: loadKeys(materialized.file.absolutePath)
                        authPublickey(connection.username, keyProvider)
                    }
                }
            }

            newSession = newClient.startSession().also { it.allocateDefaultPTY() }
            newShell = newSession.startShell()
            newWriter = PrintWriter(OutputStreamWriter(newShell.outputStream), true)
            newReader = BufferedReader(InputStreamReader(newShell.inputStream))

            val now = System.currentTimeMillis()
            val committed = synchronized(stateLock) {
                // If the user requested disconnect while we were connecting, don't publish a
                // connected state; just tear down what we created.
                if (disconnectGeneration.get() != connectGeneration) {
                    false
                } else {
                    client = newClient
                    session = newSession
                    shell = newShell
                    outputWriter = newWriter
                    inputReader = newReader
                    tempKeyFile = newTempKeyFile

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
                    true
                }
            }
            if (!committed) return@withContext false

            startReadingOutput()

            true
        } finally {
            // If we didn't commit the new resources into the shared state, close them here.
            val committed = synchronized(stateLock) { client === newClient && newClient != null }
            if (!committed) {
                runCatching { newWriter?.close() }
                runCatching { newReader?.close() }
                runCatching { newShell?.close() }
                runCatching { newSession?.close() }
                runCatching {
                    newClient?.let { c ->
                        // Force-close the socket first to unblock any lingering transport reads/writes.
                        runCatching { c.socket?.close() }
                        runCatching { c.disconnect() }
                        runCatching { c.close() }
                    }
                }
                runCatching { newTempKeyFile?.delete() }
            }
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
                        if (outputBuffer.length > MAX_OUTPUT_BUFFER_SIZE) {
                            outputBuffer.delete(0, outputBuffer.length - MAX_OUTPUT_BUFFER_SIZE)
                        }
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

    /**
     * Executes a one-shot command on the existing SSH connection via a new exec channel.
     * Returns the command's stdout, or null if not connected / on error.
     * This does NOT affect the interactive shell session.
     */
    suspend fun executeExecCommand(command: String, timeoutSeconds: Long = 10): String? =
        withContext(Dispatchers.IO) {
            try {
                val execSession: Session = synchronized(stateLock) {
                    client?.startSession()
                } ?: return@withContext null

                try {
                    val cmd = execSession.exec(command)
                    cmd.join(timeoutSeconds, TimeUnit.SECONDS)
                    val bytes = IOUtils.readFully(cmd.inputStream).toByteArray()
                    String(bytes, Charsets.UTF_8)
                } finally {
                    runCatching { execSession.close() }
                }
            } catch (e: Exception) {
                Log.w("TerminalSshSession", "executeExecCommand failed: ${e.message}")
                null
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

    private fun disconnectInternal(bumpGeneration: Boolean, emitDisconnected: Boolean) {
        // The read loop can be blocked on I/O; cancel early and also close the socket below to
        // ensure it unblocks promptly.
        readJob?.cancel()
        readJob = null

        val (wasConnected, toClose) = synchronized(stateLock) {
            // Signal to any in-flight connect attempt that it should not publish a connected state.
            if (bumpGeneration) {
                disconnectGeneration.incrementAndGet()
            }

            val wasConnected = _isConnected.value
            _isConnected.value = false
            _connectedAtMs.value = null

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
            wasConnected to snapshot
        }

        // Update observers early so the UI/notification can reflect the disconnected state even
        // if socket/channel shutdown takes a moment.
        if (emitDisconnected && wasConnected) {
            onDisconnected(connectionId)
        }

        // Force-close socket first to unblock any blocking reads/writes inside sshj/channel close.
        runCatching { toClose.client?.socket?.close() }

        runCatching { toClose.writer?.close() }
        runCatching { toClose.reader?.close() }
        runCatching { toClose.shell?.close() }
        runCatching { toClose.session?.close() }
        runCatching { toClose.client?.disconnect() }
        runCatching { toClose.client?.close() }
        runCatching { toClose.tempKeyFile?.delete() }
    }

    fun disconnect() {
        disconnectInternal(bumpGeneration = true, emitDisconnected = true)
    }

    fun clearOutput() {
        outputBuffer.clear()
        _outputFlow.value = ""
    }
}
