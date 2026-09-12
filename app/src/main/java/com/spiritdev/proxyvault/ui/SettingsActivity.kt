package com.spiritdev.proxyvault.ui

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.spiritdev.proxyvault.data.ProfileStore
import com.spiritdev.proxyvault.databinding.ActivitySettingsBinding
import java.io.File

class SettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsBinding
    private lateinit var store: ProfileStore
    private var pendingPassword: CharArray? = null

    private val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        val pass = pendingPassword ?: return@registerForActivityResult
        try {
            val tmp = File(cacheDir, "export.mr")
            if (!store.exportMr(pass, tmp)) { Toast.makeText(this, "Export failed", Toast.LENGTH_SHORT).show(); return@registerForActivityResult }
            contentResolver.openOutputStream(uri)?.use { out -> tmp.inputStream().use { it.copyTo(out) } }
            tmp.delete(); Toast.makeText(this, "Exported .MR", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Export error: ${e.message}", Toast.LENGTH_LONG).show()
        } finally { pendingPassword = null }
    }

    private val importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        val pass = pendingPassword ?: return@registerForActivityResult
        try {
            val tmp = File(cacheDir, "import.mr")
            contentResolver.openInputStream(uri)?.use { input -> tmp.outputStream().use { input.copyTo(it) } }
            val n = store.importMr(pass, tmp); tmp.delete()
            Toast.makeText(this, "Imported $n profile(s)", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Import failed: ${e.message}", Toast.LENGTH_LONG).show()
        } finally { pendingPassword = null }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        store = ProfileStore(this)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.switchNight.setOnCheckedChangeListener { _, checked ->
            AppCompatDelegate.setDefaultNightMode(if (checked) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO)
        }
        binding.switchNight.isChecked = AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_YES
        binding.btnExport.setOnClickListener { askPassword(true) }
        binding.btnImport.setOnClickListener { askPassword(false) }
        binding.tvAbout.text = "ProxyVault 2.0.0\nSecure Networking Suite\nSSH Direct · SlowDNS · .MR Crypto\nBuilt sharp. Delivered clean."
    }

    private fun askPassword(export: Boolean) {
        val input = android.widget.EditText(this).apply {
            hint = "Encryption password"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(if (export) "Export .MR" else "Import .MR")
            .setMessage("Strong password required. Scrypt + AES-256-GCM.")
            .setView(input)
            .setPositiveButton(if (export) "Export" else "Import") { _, _ ->
                val pw = input.text?.toString()?.toCharArray()
                if (pw == null || pw.isEmpty()) { Toast.makeText(this, "Password required", Toast.LENGTH_SHORT).show(); return@setPositiveButton }
                pendingPassword = pw
                if (export) exportLauncher.launch("proxyvault_export.mr") else importLauncher.launch(arrayOf("*/*"))
            }
            .setNegativeButton("Cancel", null).show()
    }
}
