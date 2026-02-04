package com.example.simpleshell.ssh

import com.example.simpleshell.domain.model.Connection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.connection.channel.direct.Session
import net.schmizz.sshj.transport.verification.PromiscuousVerifier
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import javax.inject.Inject

class TerminalSession @Inject constructor() {
    private var client: SSHClient? = null
    private var session: Session? = null
    private var shell: Session.Shell? = null
    private var outputWriter: PrintWriter? = null
    private var inputReader: BufferedReader? = null

    private val scope = CoroutineScope(Dispatchers.IO)

    private val _outputFlow = MutableStateFlow("")
    val outputFlow: StateFlow<String> = _outputFlow.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val outputBuffer = StringBuilder()

    suspend fun connect(connection: Connection) = withContext(Dispatchers.IO) {
        try {
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

            session = client?.startSession()
            session?.allocateDefaultPTY()
            shell = session?.startShell()

            shell?.let { sh ->
                outputWriter = PrintWriter(OutputStreamWriter(sh.outputStream), true)
                inputReader = BufferedReader(InputStreamReader(sh.inputStream))
            }

            _isConnected.value = true
            startReadingOutput()
        } catch (e: Exception) {
            _isConnected.value = false
            throw e
        }
    }

    private suspend fun startReadingOutput() = withContext(Dispatchers.IO) {
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
        try {
            shell?.close()
            session?.close()
            client?.disconnect()
        } catch (e: Exception) {
            // Ignore disconnect errors
        } finally {
            shell = null
            session = null
            client = null
            outputWriter = null
            inputReader = null
        }
    }

    fun clearOutput() {
        outputBuffer.clear()
        _outputFlow.value = ""
    }
}
