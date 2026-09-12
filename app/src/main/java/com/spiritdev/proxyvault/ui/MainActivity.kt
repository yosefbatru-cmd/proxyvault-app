package com.spiritdev.proxyvault.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.PopupMenu
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.spiritdev.proxyvault.R
import com.spiritdev.proxyvault.data.ProfileStore
import com.spiritdev.proxyvault.databinding.ActivityMainBinding
import com.spiritdev.proxyvault.model.ConnectionMode
import com.spiritdev.proxyvault.model.TunnelProfile
import com.spiritdev.proxyvault.tunnel.TunnelService
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var store: ProfileStore
    private var profiles: List<TunnelProfile> = emptyList()
    private var selected: TunnelProfile? = null
    private val logBuffer = StringBuilder()
    private var hotspotOn = false
    private var advOpen = false

    private val notifPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        store = ProfileStore(this)

        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        setupTabs()
        setupActions()
        refreshProfiles()
        startStatusPoll()
        appendLog("ProxyVault ready")
    }

    private fun setupTabs() {
        binding.tabHome.setOnClickListener { showPanel(0) }
        binding.tabLogs.setOnClickListener { showPanel(1) }
        binding.tabTools.setOnClickListener { showPanel(2) }
        showPanel(0)
    }

    private fun showPanel(index: Int) {
        binding.panelHome.visibility = if (index == 0) View.VISIBLE else View.GONE
        binding.panelLogs.visibility = if (index == 1) View.VISIBLE else View.GONE
        binding.panelTools.visibility = if (index == 2) View.VISIBLE else View.GONE

        binding.tabHome.setBackgroundResource(if (index == 0) R.drawable.tab_selected else 0)
        binding.tabLogs.setBackgroundResource(if (index == 1) R.drawable.tab_selected else 0)
        binding.tabTools.setBackgroundResource(if (index == 2) R.drawable.tab_selected else 0)

        val active = 0xFFFFFFFF.toInt()
        val dim = 0xCCFFFFFF.toInt()
        binding.tabHome.setTextColor(if (index == 0) active else dim)
        binding.tabLogs.setTextColor(if (index == 1) active else dim)
        binding.tabTools.setTextColor(if (index == 2) active else dim)
    }

    private fun setupActions() {
        binding.btnConnect.setOnClickListener { toggleConnect() }
        binding.btnRefreshTop.setOnClickListener {
            refreshProfiles()
            appendLog("Profiles refreshed")
            Toast.makeText(this, "Refreshed", Toast.LENGTH_SHORT).show()
        }
        binding.btnSettingsTop.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        binding.btnEditServer.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }
        binding.rowServer.setOnClickListener { pickServer() }
        binding.cardMode.setOnClickListener { pickMode() }
        binding.cardAdvanced.setOnClickListener {
            advOpen = !advOpen
            binding.advBody.visibility = if (advOpen) View.VISIBLE else View.GONE
            binding.tvAdvChevron.text = if (advOpen) "▲" else "▼"
        }

        binding.btnMore.setOnClickListener { v ->
            val popup = PopupMenu(this, v)
            popup.menu.add("Cloud Configuration")
            popup.menu.add("Import Configuration")
            popup.menu.add("Export Configuration")
            popup.menu.add("Profiles")
            popup.setOnMenuItemClickListener { item ->
                when (item.title.toString()) {
                    "Cloud Configuration", "Import Configuration", "Export Configuration" ->
                        startActivity(Intent(this, SettingsActivity::class.java))
                    "Profiles" ->
                        startActivity(Intent(this, ProfileActivity::class.java))
                }
                true
            }
            popup.show()
        }

        binding.btnMenu.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        binding.btnClearLogs.setOnClickListener {
            logBuffer.clear()
            binding.tvLogs.text = "— cleared —"
        }

        binding.toolCheckIp.setOnClickListener { checkIp() }
        binding.toolHostChecker.setOnClickListener {
            val host = selected?.host?.ifBlank { null } ?: "example.com"
            binding.tvToolResult.text = "Host: $host\nPort: ${selected?.port ?: 22}\nMode: ${selected?.mode ?: ConnectionMode.SSH_DIRECT}"
            appendLog("Host check → $host")
        }
        binding.toolTheme.setOnClickListener {
            val night = AppCompatDelegate.getDefaultNightMode() != AppCompatDelegate.MODE_NIGHT_NO
            if (night) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                binding.tvThemeState.text = "Theme: Light"
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                binding.tvThemeState.text = "Theme: Dark"
            }
        }
        binding.toolHotspot.setOnClickListener {
            hotspotOn = !hotspotOn
            binding.tvHotspotState.text = if (hotspotOn) "ON — sharing via local SOCKS" else "OFF — tap to enable"
            appendLog("Hotspot proxy ${if (hotspotOn) "enabled" else "disabled"}")
            Toast.makeText(this, binding.tvHotspotState.text, Toast.LENGTH_SHORT).show()
        }
    }

    private fun pickServer() {
        val list = store.list()
        if (list.isEmpty()) {
            Toast.makeText(this, "No profiles — create one first", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, ProfileActivity::class.java))
            return
        }
        val names = list.map { "${it.name}  ·  ${it.host.ifBlank { "no host" }}" }.toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle("Select server")
            .setItems(names) { _, which ->
                selected = list[which]
                bindSelected()
                appendLog("Server → ${list[which].name}")
            }
            .show()
    }

    private fun pickMode() {
        val modes = ConnectionMode.values()
        val labels = modes.map {
            when (it) {
                ConnectionMode.SSH_DIRECT -> "SSH Direct"
                ConnectionMode.SLOWDNS -> "SlowDNS"
                ConnectionMode.ADVANCED -> "Advanced Payload"
            }
        }.toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle("Connection mode")
            .setItems(labels) { _, which ->
                val p = selected ?: TunnelProfile().also {
                    store.save(it)
                    selected = it
                    refreshProfiles()
                }
                p.mode = modes[which]
                store.save(p)
                selected = p
                bindSelected()
                appendLog("Mode → ${labels[which]}")
            }
            .show()
    }

    private fun refreshProfiles() {
        profiles = store.list()
        if (profiles.isEmpty()) {
            selected = null
            binding.tvServerName.text = "No server"
            binding.tvServerHint.text = "Tap to add a profile"
            binding.tvServerSaved.visibility = View.GONE
            binding.tvMode.text = "SSH Direct"
            return
        }
        if (selected == null || profiles.none { it.id == selected?.id }) {
            selected = profiles[0]
        } else {
            selected = profiles.find { it.id == selected?.id }
        }
        bindSelected()
    }

    private fun bindSelected() {
        val p = selected ?: return
        binding.tvServerName.text = p.name.ifBlank { "Manual Server" }
        binding.tvServerHint.text = if (p.host.isBlank()) "Tap to change server" else "${p.host}:${p.port}"
        binding.tvServerSaved.visibility = if (p.host.isNotBlank()) View.VISIBLE else View.GONE
        binding.tvServerSaved.text = "✓  Using your saved manual server"

        binding.tvMode.text = when (p.mode) {
            ConnectionMode.SSH_DIRECT -> "SSH Direct"
            ConnectionMode.SLOWDNS -> "SlowDNS"
            ConnectionMode.ADVANCED -> "Advanced Payload"
        }

        binding.tvAdvPayload.text = "Payload: ${p.payload.ifBlank { "—" }}"
        binding.tvAdvSni.text = "SNI: ${p.sni.ifBlank { "—" }}"
        binding.tvAdvLocal.text = "Local SOCKS: ${p.localPort}"
    }

    private fun toggleConnect() {
        if (TunnelService.isRunning) {
            startService(Intent(this, TunnelService::class.java).setAction(TunnelService.ACTION_STOP))
            setStatus("Disconnected", "Ready to connect", false)
            binding.btnConnect.text = "Connect"
            appendLog("Tunnel stopped")
            return
        }
        val p = selected
        if (p == null) {
            Toast.makeText(this, "Select a server first", Toast.LENGTH_SHORT).show()
            return
        }
        if (p.host.isBlank() && p.mode != ConnectionMode.SLOWDNS) {
            Toast.makeText(this, "Profile needs a host", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, ProfileActivity::class.java))
            return
        }
        val i = Intent(this, TunnelService::class.java).apply {
            action = TunnelService.ACTION_START
            putExtra(TunnelService.EXTRA_PROFILE_JSON, p.toJson().toString())
        }
        ContextCompat.startForegroundService(this, i)
        binding.btnConnect.text = "Disconnect"
        setStatus("Connecting…", p.name, true)
        appendLog("Connecting → ${p.name} (${p.mode})")
    }

    private fun setStatus(title: String, sub: String, active: Boolean) {
        binding.tvStatusTitle.text = title
        binding.tvStatusSub.text = sub
        binding.statusDot.setBackgroundResource(
            if (active) R.drawable.status_dot_ok else R.drawable.status_dot_idle
        )
    }

    private fun startStatusPoll() {
        lifecycleScope.launch {
            while (isActive) {
                val running = TunnelService.isRunning
                binding.btnConnect.text = if (running) "Disconnect" else "Connect"
                if (running) {
                    setStatus("Connected", TunnelService.statusText, true)
                } else if (binding.tvStatusTitle.text == "Connecting…") {
                    // keep until service flips
                } else if (!running && binding.tvStatusTitle.text == "Connected") {
                    setStatus("Disconnected", "Ready to connect", false)
                }
                delay(1500)
            }
        }
    }

    private fun checkIp() {
        binding.tvToolResult.text = "Checking…"
        appendLog("IP check started")
        lifecycleScope.launch {
            try {
                val client = OkHttpClient.Builder()
                    .connectTimeout(8, TimeUnit.SECONDS)
                    .readTimeout(8, TimeUnit.SECONDS)
                    .build()
                val body = client.newCall(Request.Builder().url("https://api.ipify.org").build())
                    .execute().body?.string()?.trim() ?: "?"
                binding.tvToolResult.text = "Exit IP: $body"
                appendLog("Exit IP: $body")
                Toast.makeText(this@MainActivity, "Exit IP: $body", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                binding.tvToolResult.text = "Failed: ${e.message}"
                appendLog("IP check failed: ${e.message}")
            }
        }
    }

    private fun appendLog(line: String) {
        val ts = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US)
            .format(java.util.Date())
        logBuffer.append("[").append(ts).append("] ").append(line).append('\n')
        binding.tvLogs.text = logBuffer.toString()
    }

    override fun onResume() {
        super.onResume()
        refreshProfiles()
    }
}
