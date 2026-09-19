package com.apkmcp.app.server

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import com.apkmcp.app.MainActivity
import com.apkmcp.app.R
import com.apkmcp.app.core.Logs
import com.apkmcp.app.core.Prefs

/**
 * 常驻前台服务，里面跑 HTTP / MCP 服务器。
 */
class McpServerService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                shutdown()
                return START_NOT_STICKY
            }
            else -> {
                startForeground(NOTIF_ID, buildNotification(portSummary()))
                acquireWakeLock()
                startServer()
            }
        }
        return if (Prefs.config.value.sticky) START_STICKY else START_NOT_STICKY
    }

    override fun onDestroy() {
        shutdown()
        super.onDestroy()
    }

    private fun startServer() {
        if (server != null) {
            Logs.add("服务已在运行，端口 ${boundPort}")
            return
        }
        val cfg = Prefs.config.value
        val port = cfg.port.coerceIn(1024, 65535)
        val s = McpHttpServer(port, cfg.token, cfg.bindAll)
        try {
            s.start(SOCKET_READ_TIMEOUT, false)
            server = s
            boundPort = port
            lastError = null
            Logs.add("MCP 服务已启动 http://${if (cfg.bindAll) "0.0.0.0" else "127.0.0.1"}:$port/mcp")
        } catch (t: Throwable) {
            lastError = t.message
            Logs.add("启动失败: ${t.message}")
            server = null
        }
    }

    private fun shutdown() {
        try {
            server?.stop()
        } catch (_: Throwable) {
        }
        server = null
        boundPort = 0
        releaseWakeLock()
        stopForegroundCompat()
        stopSelf()
    }

    private fun portSummary(): String {
        val cfg = Prefs.config.value
        return "监听 ${if (cfg.bindAll) "0.0.0.0" else "127.0.0.1"}:${cfg.port}"
    }

    private fun acquireWakeLock() {
        if (wakeLock != null) return
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            val wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "apkmcp:server")
            wl.setReferenceCounted(false)
            wl.acquire()
            wakeLock = wl
        } catch (_: Throwable) {
        }
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.release()
        } catch (_: Throwable) {
        }
        wakeLock = null
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val ch = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.channel_server),
            NotificationManager.IMPORTANCE_LOW
        )
        ch.description = getString(R.string.channel_server_desc)
        nm.createNotificationChannel(ch)
    }

    private fun buildNotification(text: String): Notification {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        return builder
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(open)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val ACTION_START = "com.apkmcp.app.action.START_SERVER"
        const val ACTION_STOP = "com.apkmcp.app.action.STOP_SERVER"
        private const val CHANNEL_ID = "apkmcp_server"
        private const val NOTIF_ID = 1002
        private const val SOCKET_READ_TIMEOUT = 15_000

        @Volatile
        var server: McpHttpServer? = null
            private set

        @Volatile
        var boundPort: Int = 0
            private set

        @Volatile
        var lastError: String? = null

        fun running(): Boolean = server?.isAlive == true

        fun start(ctx: Context) {
            val i = Intent(ctx, McpServerService::class.java).apply { action = ACTION_START }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(i)
            else ctx.startService(i)
        }

        fun stop(ctx: Context) {
            val i = Intent(ctx, McpServerService::class.java).apply { action = ACTION_STOP }
            try {
                ctx.startService(i)
            } catch (_: Throwable) {
            }
        }

        fun restart(ctx: Context) {
            stop(ctx)
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                start(ctx)
            }, 400L)
        }
    }
}
