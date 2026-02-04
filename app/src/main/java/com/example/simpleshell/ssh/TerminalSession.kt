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

@Singleton
class TerminalSession @Inject constructor(
    @ApplicationContext private val context: Context
) {
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

    private val _activeConnectionId = MutableStateFlow<Long?>(null)
    val activeConnectionId: StateFlow<Long?> = _activeConnectionId.asStateFlow()

    private val outputBuffer = StringBuilder()

    suspend fun connect(connection: Connection) = withContext(Dispatchers.IO) {
        // Single shared session: ensure we don't leak resources if connect() is called repeatedly.
        disconnect()
        clearOutput()
        try {
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

            session = client?.startSession()
            session?.allocateDefaultPTY()
            shell = session?.startShell()

            shell?.let { sh ->
                outputWriter = PrintWriter(OutputStreamWriter(sh.outputStream), true)
                inputReader = BufferedReader(InputStreamReader(sh.inputStream))
            }

            _isConnected.value = true
            _sessionId.value = _sessionId.value + 1
            _connectedAtMs.value = System.currentTimeMillis()
            _activeConnectionId.value = connection.id

            startReadingOutput()
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
                while (_isConnected.value && inputReader != null) {
                    val read = inputReader?.read(buffer) ?: -1
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
                outputWriter?.println(command)
                outputWriter?.flush()
            } catch (e: Exception) {
                // Ignore send errors
            }
        }
    }

    fun sendInput(input: String) {
        scope.launch {
            try {
                outputWriter?.print(input)
                outputWriter?.flush()
            } catch (e: Exception) {
                // Ignore send errors
            }
        }
    }

    fun disconnect() {
        _isConnected.value = false
        _connectedAtMs.value = null
        _activeConnectionId.value = null

        readJob?.cancel()
        readJob = null

        try {
            outputWriter?.close()
            inputReader?.close()
            shell?.close()
            session?.close()
            try {
                client?.disconnect()
            } finally {
                client?.close()
            }
        } catch (e: Exception) {
            // Ignore disconnect errors
        } finally {
            shell = null
            session = null
            client = null
            outputWriter = null
            inputReader = null
            tempKeyFile?.delete()
            tempKeyFile = null
        }
    }

    fun clearOutput() {
        outputBuffer.clear()
        _outputFlow.value = ""
    }
}
