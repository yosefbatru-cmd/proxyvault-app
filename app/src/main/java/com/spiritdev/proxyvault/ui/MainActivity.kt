package com.spiritdev.proxyvault.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
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
    private val notifPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        store = ProfileStore(this)
        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setupUi(); refreshProfiles(); startStatusPoll()
    }

    private fun setupUi() {
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_profiles -> { startActivity(Intent(this, ProfileActivity::class.java)); true }
                R.id.action_settings -> { startActivity(Intent(this, SettingsActivity::class.java)); true }
                R.id.action_check_ip -> { checkIp(); true }
                else -> false
            }
        }
        binding.btnConnect.setOnClickListener { toggleConnect() }
        binding.btnRefresh.setOnClickListener { refreshProfiles() }
        binding.spinnerProfiles.setOnItemClickListener { _, _, pos, _ ->
            selected = profiles.getOrNull(pos); bindSelected()
        }
    }

    private fun refreshProfiles() {
        profiles = store.list()
        val names = profiles.map { "${it.name}  [${it.mode.name}]" }
        binding.spinnerProfiles.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, names))
        if (profiles.isNotEmpty() && selected == null) {
            selected = profiles[0]; binding.spinnerProfiles.setText(names[0], false); bindSelected()
        }
        if (profiles.isEmpty()) { binding.tvStatus.text = "No profiles. Create one in Profiles."; selected = null }
    }

    private fun bindSelected() {
        val p = selected ?: return
        binding.tvMode.text = p.mode.name.replace('_', ' ')
        binding.tvHost.text = if (p.host.isBlank()) "—" else "${p.host}:${p.port}"
        binding.tvLocalPort.text = "Local :${p.localPort}"
        binding.tvNotes.text = p.notes.ifBlank { "No notes" }
    }

    private fun toggleConnect() {
        if (TunnelService.isRunning) {
            startService(Intent(this, TunnelService::class.java).setAction(TunnelService.ACTION_STOP))
            binding.btnConnect.text = "Connect"; binding.tvLiveStatus.text = "Stopped"; return
        }
        val p = selected
        if (p == null) { Toast.makeText(this, "Select a profile first", Toast.LENGTH_SHORT).show(); return }
        if (p.host.isBlank() && p.mode != ConnectionMode.SLOWDNS) {
            Toast.makeText(this, "Profile needs a host", Toast.LENGTH_SHORT).show(); return
        }
        val i = Intent(this, TunnelService::class.java).apply {
            action = TunnelService.ACTION_START
            putExtra(TunnelService.EXTRA_PROFILE_JSON, p.toJson().toString())
        }
        ContextCompat.startForegroundService(this, i)
        binding.btnConnect.text = "Disconnect"; binding.tvLiveStatus.text = "Starting…"
    }

    private fun startStatusPoll() {
        lifecycleScope.launch {
            while (isActive) {
                binding.tvLiveStatus.text = TunnelService.statusText
                binding.btnConnect.text = if (TunnelService.isRunning) "Disconnect" else "Connect"
                delay(1500)
            }
        }
    }

    private fun checkIp() {
        binding.tvLiveStatus.text = "Checking exit IP…"
        lifecycleScope.launch {
            try {
                val client = OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS).readTimeout(8, TimeUnit.SECONDS).build()
                val body = client.newCall(Request.Builder().url("https://api.ipify.org").build()).execute().body?.string()?.trim() ?: "?"
                binding.tvLiveStatus.text = "Exit IP: $body"
                Toast.makeText(this@MainActivity, "Exit IP: $body", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                binding.tvLiveStatus.text = "IP check failed: ${e.message}"
            }
        }
    }

    override fun onResume() { super.onResume(); refreshProfiles() }
}
