package com.spiritdev.proxyvault.ui

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.spiritdev.proxyvault.data.ProfileStore
import com.spiritdev.proxyvault.databinding.ActivityProfileBinding
import com.spiritdev.proxyvault.model.ConnectionMode
import com.spiritdev.proxyvault.model.TunnelProfile

class ProfileActivity : AppCompatActivity() {
    private lateinit var binding: ActivityProfileBinding
    private lateinit var store: ProfileStore
    private var editing: TunnelProfile? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)
        store = ProfileStore(this)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.spinnerMode.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item,
            ConnectionMode.values().map { it.name.replace('_', ' ') })
        binding.btnSave.setOnClickListener { save() }
        binding.btnNew.setOnClickListener { editing = null; clearForm(); Toast.makeText(this, "New profile", Toast.LENGTH_SHORT).show() }
        binding.btnDelete.setOnClickListener { deleteCurrent() }
        binding.btnLoad.setOnClickListener { pickProfile() }
        clearForm()
    }

    private fun clearForm() {
        binding.etName.setText(""); binding.etHost.setText(""); binding.etPort.setText("22")
        binding.etUser.setText(""); binding.etPass.setText(""); binding.etKey.setText("")
        binding.etPayload.setText(""); binding.etSni.setText(""); binding.etLocalPort.setText("1080")
        binding.etDns.setText("1.1.1.1"); binding.etSlowNs.setText(""); binding.etNotes.setText("")
        binding.cbCompression.isChecked = false; binding.cbUdp.isChecked = false; binding.cbKeepAlive.isChecked = true
        binding.spinnerMode.setSelection(0)
    }

    private fun fillForm(p: TunnelProfile) {
        editing = p
        binding.etName.setText(p.name); binding.etHost.setText(p.host); binding.etPort.setText(p.port.toString())
        binding.etUser.setText(p.username); binding.etPass.setText(p.password); binding.etKey.setText(p.privateKey)
        binding.etPayload.setText(p.payload); binding.etSni.setText(p.sni); binding.etLocalPort.setText(p.localPort.toString())
        binding.etDns.setText(p.dnsResolver); binding.etSlowNs.setText(p.slowDnsNameserver); binding.etNotes.setText(p.notes)
        binding.cbCompression.isChecked = p.compression; binding.cbUdp.isChecked = p.udpForward; binding.cbKeepAlive.isChecked = p.keepAlive
        binding.spinnerMode.setSelection(p.mode.ordinal)
    }

    private fun save() {
        val name = binding.etName.text?.toString()?.trim().orEmpty()
        if (name.isBlank()) { Toast.makeText(this, "Name required", Toast.LENGTH_SHORT).show(); return }
        val base = editing ?: TunnelProfile()
        val mode = ConnectionMode.values()[binding.spinnerMode.selectedItemPosition]
        val p = base.copy(
            name = name, mode = mode,
            host = binding.etHost.text?.toString()?.trim().orEmpty(),
            port = binding.etPort.text?.toString()?.toIntOrNull() ?: 22,
            username = binding.etUser.text?.toString()?.trim().orEmpty(),
            password = binding.etPass.text?.toString().orEmpty(),
            privateKey = binding.etKey.text?.toString().orEmpty(),
            payload = binding.etPayload.text?.toString().orEmpty(),
            sni = binding.etSni.text?.toString()?.trim().orEmpty(),
            localPort = binding.etLocalPort.text?.toString()?.toIntOrNull() ?: 1080,
            dnsResolver = binding.etDns.text?.toString()?.trim().orEmpty().ifBlank { "1.1.1.1" },
            slowDnsNameserver = binding.etSlowNs.text?.toString()?.trim().orEmpty(),
            notes = binding.etNotes.text?.toString().orEmpty(),
            compression = binding.cbCompression.isChecked,
            udpForward = binding.cbUdp.isChecked,
            keepAlive = binding.cbKeepAlive.isChecked
        )
        store.save(p); editing = p
        Toast.makeText(this, "Saved: ${p.name}", Toast.LENGTH_SHORT).show()
    }

    private fun deleteCurrent() {
        val p = editing ?: return
        MaterialAlertDialogBuilder(this).setTitle("Delete profile?").setMessage(p.name)
            .setPositiveButton("Delete") { _, _ -> store.delete(p.id); editing = null; clearForm(); Toast.makeText(this, "Deleted", Toast.LENGTH_SHORT).show() }
            .setNegativeButton("Cancel", null).show()
    }

    private fun pickProfile() {
        val list = store.list()
        if (list.isEmpty()) { Toast.makeText(this, "No profiles yet", Toast.LENGTH_SHORT).show(); return }
        MaterialAlertDialogBuilder(this).setTitle("Load profile")
            .setItems(list.map { it.name }.toTypedArray()) { _, which -> fillForm(list[which]) }.show()
    }
}
