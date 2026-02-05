package com.example.simpleshell.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.simpleshell.MainActivity
import com.example.simpleshell.R
import com.example.simpleshell.ssh.SftpManager
import com.example.simpleshell.ssh.TerminalConnectionSummary
import com.example.simpleshell.ssh.TerminalSessionManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class ConnectionForegroundService : Service() {

    @Inject lateinit var terminalSessionManager: TerminalSessionManager
    @Inject lateinit var sftpManager: SftpManager

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var foregroundStarted = false

    private var shownTerminalNotificationIds: Set<Long> = emptySet()
    private var shownSftpNotification: Boolean = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()

        serviceScope.launch {
            combine(
                terminalSessionManager.connectedSessions,
                sftpManager.isConnectedFlow
            ) { terminalSessions, sftpConnected ->
                ConnectionsSnapshot(
                    terminalSessions = terminalSessions.values.sortedBy { it.connectionId },
                    sftpConnected = sftpConnected
                )
            }
                .distinctUntilChanged()
                .collect { snapshot ->
                    updateNotifications(snapshot)

                    if (snapshot.terminalSessions.isEmpty() && !snapshot.sftpConnected) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DISCONNECT_ALL -> {
                // Disconnect may involve blocking I/O. Do it off the main thread.
                serviceScope.launch {
                    withContext(Dispatchers.IO) {
                        disconnectAll()
                    }
                }
                return START_NOT_STICKY
            }
            ACTION_DISCONNECT_TERMINAL -> {
                val connectionId = intent.getLongExtra(EXTRA_CONNECTION_ID, -1L)
                if (connectionId > 0) {
                    serviceScope.launch {
                        withContext(Dispatchers.IO) {
                            terminalSessionManager.disconnect(connectionId)
                        }
                    }
                }
                return START_NOT_STICKY
            }
            ACTION_DISCONNECT_SFTP -> {
                serviceScope.launch {
                    withContext(Dispatchers.IO) {
                        sftpManager.disconnect()
                    }
                }
                return START_NOT_STICKY
            }
            else -> {
                // Keep process alive while any connection is active.
                val snapshot = ConnectionsSnapshot(
                    terminalSessions = terminalSessionManager.connectedSessions.value.values
                        .sortedBy { it.connectionId },
                    sftpConnected = sftpManager.isConnectedFlow.value
                )

                val notification = buildSummaryNotification(snapshot)
                startForeground(NOTIFICATION_ID_SUMMARY, notification)
                foregroundStarted = true

                // Also publish child notifications immediately (grouped).
                updateNotifications(snapshot)
                return START_NOT_STICKY
            }
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // User swiped the app away from Recents. Close active SSH connections to avoid
        // server-side resource waste.
        disconnectAll()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        // If the service is being torn down, ensure connections are not leaked.
        disconnectAll()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun disconnectAll() {
        try {
            terminalSessionManager.disconnectAll()
        } catch (_: Exception) {
        }
        try {
            sftpManager.disconnect()
        } catch (_: Exception) {
        }
    }

    private fun updateNotifications(snapshot: ConnectionsSnapshot) {
        val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Foreground summary notification
        val summary = buildSummaryNotification(snapshot)
        if (foregroundStarted) {
            mgr.notify(NOTIFICATION_ID_SUMMARY, summary)
        }

        // Child notifications for each terminal connection (so each has its own Disconnect action).
        val connectedTerminalIds = snapshot.terminalSessions.map { it.connectionId }.toSet()
        val removedIds = shownTerminalNotificationIds - connectedTerminalIds
        val addedOrStillIds = connectedTerminalIds

        removedIds.forEach { id ->
            mgr.cancel(terminalNotificationId(id))
        }
        snapshot.terminalSessions.forEach { terminal ->
            if (terminal.connectionId in addedOrStillIds) {
                mgr.notify(
                    terminalNotificationId(terminal.connectionId),
                    buildTerminalNotification(terminal)
                )
            }
        }
        shownTerminalNotificationIds = connectedTerminalIds

        // Optional: show SFTP status as a single notification (still manageable independently).
        if (snapshot.sftpConnected) {
            mgr.notify(NOTIFICATION_ID_SFTP, buildSftpNotification())
            shownSftpNotification = true
        } else if (shownSftpNotification) {
            mgr.cancel(NOTIFICATION_ID_SFTP)
            shownSftpNotification = false
        }
    }

    private fun buildSummaryNotification(snapshot: ConnectionsSnapshot): android.app.Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val openPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val disconnectAllIntent = Intent(this, ConnectionForegroundService::class.java).apply {
            action = ACTION_DISCONNECT_ALL
        }
        val disconnectAllPendingIntent = PendingIntent.getService(
            this,
            1,
            disconnectAllIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val terminalCount = snapshot.terminalSessions.size
        val totalCount = terminalCount + if (snapshot.sftpConnected) 1 else 0
        val contentText = when {
            totalCount <= 0 -> "No active connections"
            totalCount == 1 && terminalCount == 1 -> "1 Terminal connection active"
            totalCount == 1 && snapshot.sftpConnected -> "1 SFTP connection active"
            else -> "$totalCount connections active"
        }

        val inbox = NotificationCompat.InboxStyle()
        snapshot.terminalSessions.take(5).forEach { terminal ->
            inbox.addLine("Terminal: ${terminal.connectionName}")
        }
        if (snapshot.sftpConnected) {
            inbox.addLine("SFTP: connected")
        }
        if (snapshot.terminalSessions.size > 5) {
            inbox.addLine("...and ${snapshot.terminalSessions.size - 5} more")
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_s)
            .setContentTitle("SimpleShell")
            .setContentText(contentText)
            .setContentIntent(openPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setGroup(GROUP_KEY_CONNECTIONS)
            .setGroupSummary(true)
            .setStyle(inbox)
            .addAction(
                R.drawable.ic_notification_s,
                "Disconnect all",
                disconnectAllPendingIntent
            )

        return builder.build()
    }

    private fun buildTerminalNotification(terminal: TerminalConnectionSummary): android.app.Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val openPendingIntent = PendingIntent.getActivity(
            this,
            terminal.connectionId.hashCode(),
            openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val disconnectIntent = Intent(this, ConnectionForegroundService::class.java).apply {
            action = ACTION_DISCONNECT_TERMINAL
            putExtra(EXTRA_CONNECTION_ID, terminal.connectionId)
        }
        val disconnectPendingIntent = PendingIntent.getService(
            this,
            ("terminal_disconnect_" + terminal.connectionId).hashCode(),
            disconnectIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_s)
            .setContentTitle(terminal.connectionName)
            .setContentText("Terminal 已连接")
            .setContentIntent(openPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setGroup(GROUP_KEY_CONNECTIONS)
            .addAction(
                R.drawable.ic_notification_s,
                "断开连接",
                disconnectPendingIntent
            )
            .build()
    }

    private fun buildSftpNotification(): android.app.Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val openPendingIntent = PendingIntent.getActivity(
            this,
            3,
            openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val disconnectIntent = Intent(this, ConnectionForegroundService::class.java).apply {
            action = ACTION_DISCONNECT_SFTP
        }
        val disconnectPendingIntent = PendingIntent.getService(
            this,
            4,
            disconnectIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_s)
            .setContentTitle("SFTP")
            .setContentText("SFTP 已连接")
            .setContentIntent(openPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setGroup(GROUP_KEY_CONNECTIONS)
            .addAction(
                R.drawable.ic_notification_s,
                "Disconnect",
                disconnectPendingIntent
            )
            .build()
    }

    private data class ConnectionsSnapshot(
        val terminalSessions: List<TerminalConnectionSummary>,
        val sftpConnected: Boolean
    )

    private fun terminalNotificationId(connectionId: Long): Int {
        val hash = (connectionId xor (connectionId ushr 32)).toInt()
        // Keep ids in a reasonable range and away from summary/sftp ids.
        return TERMINAL_NOTIFICATION_ID_BASE + ((hash and 0x7FFFFFFF) % 1_000_000)
    }

    private fun ensureChannel() {
        val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val existing = mgr.getNotificationChannel(CHANNEL_ID)
        if (existing != null) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            "Connection status",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows active SSH connection status"
            setShowBadge(false)
        }
        mgr.createNotificationChannel(channel)
    }

    companion object {
        const val ACTION_DISCONNECT_ALL = "com.example.simpleshell.action.DISCONNECT_ALL"
        const val ACTION_DISCONNECT_TERMINAL = "com.example.simpleshell.action.DISCONNECT_TERMINAL"
        const val ACTION_DISCONNECT_SFTP = "com.example.simpleshell.action.DISCONNECT_SFTP"

        const val EXTRA_CONNECTION_ID = "extra_connection_id"

        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_SUBTITLE = "extra_subtitle"

        private const val CHANNEL_ID = "connection_status"
        private const val GROUP_KEY_CONNECTIONS = "connections"
        private const val NOTIFICATION_ID_SUMMARY = 1001
        private const val NOTIFICATION_ID_SFTP = 1002
        private const val TERMINAL_NOTIFICATION_ID_BASE = 2000
    }
}
