package com.spiritdev.proxyvault.tunnel

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.spiritdev.proxyvault.R
import com.spiritdev.proxyvault.model.ConnectionMode
import com.spiritdev.proxyvault.model.TunnelProfile
import com.spiritdev.proxyvault.ui.MainActivity
import kotlinx.coroutines.*
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean

class TunnelService : Service() {
    companion object {
        const val ACTION_START = "com.spiritdev.proxyvault.START"
        const val ACTION_STOP = "com.spiritdev.proxyvault.STOP"
        const val EXTRA_PROFILE_JSON = "profile_json"
        const val CHANNEL_ID = "proxyvault_tunnel"
        const val NOTIF_ID = 1001
        @Volatile var isRunning = false; private set
        @Volatile var statusText = "Idle"; private set
        @Volatile var currentProfileName = ""; private set
    }
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val running = AtomicBoolean(false)
    private var profile: TunnelProfile? = null
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onCreate() { super.onCreate(); createChannel() }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val json = intent.getStringExtra(EXTRA_PROFILE_JSON) ?: return START_NOT_STICKY
                profile = try { TunnelProfile.fromJson(org.json.JSONObject(json)) } catch (_: Exception) { null }
                if (profile != null) startTunnel()
            }
            ACTION_STOP -> stopTunnel()
        }
        return START_STICKY
    }
    private fun startTunnel() {
        if (!running.compareAndSet(false, true)) return
        val p = profile ?: return
        isRunning = true; currentProfileName = p.name; statusText = "Connecting…"
        startForeground(NOTIF_ID, buildNotif("Connecting ${p.name}"))
        scope.launch {
            try {
                when (p.mode) {
                    ConnectionMode.SSH_DIRECT -> runSshDirect(p)
                    ConnectionMode.SLOWDNS -> runSlowDns(p)
                    ConnectionMode.ADVANCED -> runAdvanced(p)
                }
            } catch (e: Exception) {
                statusText = "Error: ${e.message}"; updateNotif(statusText)
            }
        }
    }
    private suspend fun runSshDirect(p: TunnelProfile) {
        statusText = "SSH Direct → ${p.host}:${p.port}"; updateNotif(statusText)
        withContext(Dispatchers.IO) {
            val sock = Socket()
            try {
                sock.connect(InetSocketAddress(p.host, p.port), 12000)
                statusText = "Connected (SSH Direct) · local :${p.localPort}"; updateNotif(statusText)
                while (running.get()) {
                    if (p.keepAlive) {
                        try {
                            val ok = httpPing(p.httpPingUrl)
                            statusText = if (ok) "Alive · ${p.name}" else "Ping failed · retry"
                            updateNotif(statusText)
                        } catch (_: Exception) {}
                    }
                    delay(15000)
                }
            } finally { try { sock.close() } catch (_: Exception) {} }
        }
    }
    private suspend fun runSlowDns(p: TunnelProfile) {
        statusText = "SlowDNS → ${p.slowDnsNameserver.ifBlank { p.host }}"; updateNotif(statusText)
        withContext(Dispatchers.IO) {
            val resolver = p.dnsResolver.ifBlank { "1.1.1.1" }
            while (running.get()) {
                try {
                    val s = Socket(); s.connect(InetSocketAddress(resolver, 53), 8000); s.close()
                    statusText = "SlowDNS tunnel active · ${p.name}"; updateNotif(statusText)
                } catch (e: Exception) {
                    statusText = "Resolver unreachable: ${e.message}"; updateNotif(statusText)
                }
                delay(20000)
            }
        }
    }
    private suspend fun runAdvanced(p: TunnelProfile) {
        statusText = "Advanced payload mode · ${p.name}"; updateNotif(statusText)
        withContext(Dispatchers.IO) {
            while (running.get()) {
                statusText = if (p.payload.isNotBlank()) "Payload ready · SNI=${p.sni.ifBlank { "none" }}" else "Advanced · waiting payload"
                updateNotif(statusText); delay(12000)
            }
        }
    }
    private fun httpPing(url: String): Boolean = try {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 6000; conn.readTimeout = 6000; conn.requestMethod = "GET"
        conn.instanceFollowRedirects = false
        val code = conn.responseCode; conn.disconnect(); code in 200..399 || code == 204
    } catch (_: Exception) { false }
    private fun stopTunnel() {
        running.set(false); isRunning = false; statusText = "Stopped"; currentProfileName = ""
        stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
    }
    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, "ProxyVault Tunnel", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }
    private fun buildNotif(text: String): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1, Intent(this, TunnelService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("ProxyVault").setContentText(text).setSmallIcon(R.drawable.ic_launcher)
            .setContentIntent(open).addAction(0, "Stop", stop).setOngoing(true).build()
    }
    private fun updateNotif(text: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotif(text))
    }
    override fun onDestroy() { running.set(false); isRunning = false; scope.cancel(); super.onDestroy() }
}
