package com.example.simpleshell.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.simpleshell.MainActivity
import com.example.simpleshell.R
import com.example.simpleshell.ssh.SftpManager
import com.example.simpleshell.ssh.TerminalSession
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

    @Inject lateinit var terminalSession: TerminalSession
    @Inject lateinit var sftpManager: SftpManager

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()

        // If all connections are closed, stop the service automatically so we don't leave a stale
        // notification around (and so we don't accidentally disconnect on configuration changes).
        serviceScope.launch {
            combine(
                terminalSession.isConnected,
                sftpManager.isConnectedFlow
            ) { terminalConnected, sftpConnected ->
                terminalConnected || sftpConnected
            }
                .distinctUntilChanged()
                .collect { anyConnected ->
                    if (!anyConnected) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DISCONNECT -> {
                // Disconnect may involve blocking I/O. Do it off the main thread, then stop.
                serviceScope.launch {
                    withContext(Dispatchers.IO) {
                        disconnectAll()
                    }
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
                return START_NOT_STICKY
            }
            else -> {
                val title = intent?.getStringExtra(EXTRA_TITLE) ?: "SSH Connected"
                val subtitle = intent?.getStringExtra(EXTRA_SUBTITLE)

                val notification = buildNotification(
                    title = title,
                    subtitle = subtitle
                )

                // Keep process alive while a connection is active.
                startForeground(NOTIFICATION_ID, notification)
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
            terminalSession.disconnect()
        } catch (_: Exception) {
        }
        try {
            sftpManager.disconnect()
        } catch (_: Exception) {
        }
    }

    private fun buildNotification(title: String, subtitle: String?): android.app.Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val openPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val disconnectIntent = Intent(this, ConnectionForegroundService::class.java).apply {
            action = ACTION_DISCONNECT
        }
        val disconnectPendingIntent = PendingIntent.getService(
            this,
            1,
            disconnectIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentIntent(openPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(
                R.drawable.ic_launcher_foreground,
                "Disconnect",
                disconnectPendingIntent
            )

        if (!subtitle.isNullOrBlank()) {
            builder.setContentText(subtitle)
        }

        return builder.build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

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
        const val ACTION_DISCONNECT = "com.example.simpleshell.action.DISCONNECT"

        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_SUBTITLE = "extra_subtitle"

        private const val CHANNEL_ID = "connection_status"
        private const val NOTIFICATION_ID = 1001
    }
}
