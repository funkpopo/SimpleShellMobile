package com.example.simpleshell.ssh

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

data class ResourceStats(
    val cpuPercent: Float,
    val memUsedKb: Long,
    val memTotalKb: Long
) {
    val memPercent: Float
        get() = if (memTotalKb > 0) (memUsedKb.toFloat() / memTotalKb * 100f) else 0f

    fun memDisplayString(): String {
        val usedGb = memUsedKb / 1_048_576.0
        val totalGb = memTotalKb / 1_048_576.0
        return if (totalGb >= 1.0) {
            "%.1f / %.1f GB".format(usedGb, totalGb)
        } else {
            val usedMb = memUsedKb / 1024.0
            val totalMb = memTotalKb / 1024.0
            "%.0f / %.0f MB".format(usedMb, totalMb)
        }
    }
}

@Singleton
class ResourceMonitor @Inject constructor(
    private val terminalSessionManager: TerminalSessionManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pollingJob: Job? = null

    private val _stats = MutableStateFlow<Map<Long, ResourceStats>>(emptyMap())
    val stats: StateFlow<Map<Long, ResourceStats>> = _stats.asStateFlow()

    companion object {
        private const val POLL_INTERVAL_MS = 10_000L
        private const val MONITOR_COMMAND =
            "head -1 /proc/stat; sleep 1; head -1 /proc/stat; " +
            "awk '/MemTotal/{t=\$2}/MemAvailable/{a=\$2}END{print t-a,t}' /proc/meminfo"
    }

    init {
        scope.launch {
            terminalSessionManager.connectedSessions.collect { sessions ->
                if (sessions.isNotEmpty()) {
                    startPollingIfNeeded()
                } else {
                    stopPolling()
                    _stats.value = emptyMap()
                }
                val currentIds = sessions.keys
                _stats.value = _stats.value.filterKeys { it in currentIds }
            }
        }
    }

    private fun startPollingIfNeeded() {
        if (pollingJob?.isActive == true) return
        pollingJob = scope.launch {
            while (isActive) {
                pollAll()
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    private fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    private suspend fun pollAll() {
        val connectedIds = terminalSessionManager.connectedSessions.value.keys
        val results = mutableMapOf<Long, ResourceStats>()

        coroutineScope {
            connectedIds.map { connectionId ->
                async {
                    val session = terminalSessionManager.getSession(connectionId)
                    val output = session.executeExecCommand(MONITOR_COMMAND, timeoutSeconds = 5)
                    val parsed = output?.let { parseMonitorOutput(it) }
                    if (parsed != null) connectionId to parsed else null
                }
            }.mapNotNull { it.await() }
             .forEach { (id, stat) -> results[id] = stat }
        }

        _stats.value = _stats.value + results
    }

    internal fun parseMonitorOutput(output: String): ResourceStats? {
        val lines = output.trim().lines()
        if (lines.size < 3) return null

        return try {
            val cpu1 = parseCpuLine(lines[0]) ?: return null
            val cpu2 = parseCpuLine(lines[1]) ?: return null

            val totalDelta = cpu2.total - cpu1.total
            val idleDelta = cpu2.idle - cpu1.idle
            val cpuPercent = if (totalDelta > 0) {
                ((totalDelta - idleDelta).toFloat() / totalDelta * 100f)
            } else {
                0f
            }

            val memParts = lines[2].trim().split("\\s+".toRegex())
            if (memParts.size < 2) return null
            val memUsedKb = memParts[0].toLongOrNull() ?: return null
            val memTotalKb = memParts[1].toLongOrNull() ?: return null

            ResourceStats(
                cpuPercent = cpuPercent.coerceIn(0f, 100f),
                memUsedKb = memUsedKb,
                memTotalKb = memTotalKb
            )
        } catch (_: Exception) {
            null
        }
    }

    private data class CpuSnapshot(val total: Long, val idle: Long)

    private fun parseCpuLine(line: String): CpuSnapshot? {
        val parts = line.trim().split("\\s+".toRegex())
        if (parts.size < 5 || parts[0] != "cpu") return null
        val values = parts.drop(1).mapNotNull { it.toLongOrNull() }
        if (values.size < 4) return null
        val total = values.sum()
        val idle = values[3]
        return CpuSnapshot(total = total, idle = idle)
    }
}
