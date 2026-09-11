package com.spiritdev.proxyvault.ui

import android.app.Dialog
import android.os.Bundle
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.spiritdev.proxyvault.data.LicenseManager

class RedeemDialog(private val onActivated: () -> Unit) : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val ctx = requireContext()
        val input = EditText(ctx).apply {
            hint = "Enter activation code"
            setPadding(48, 32, 48, 16)
            setSingleLine()
        }
        val current = LicenseManager.currentTier(ctx).name
        val code = LicenseManager.activatedCode(ctx)
        val message = buildString {
            append("Current: $current")
            if (!code.isNullOrBlank()) append("\nCode: $code")
            append("\n\nPaste the code you received after payment.")
        }
        return AlertDialog.Builder(ctx)
            .setTitle("Activate Premium / Pro")
            .setMessage(message)
            .setView(input)
            .setPositiveButton("Activate") { _, _ ->
                val result = LicenseManager.redeem(ctx, input.text.toString())
                Toast.makeText(ctx, result.message, Toast.LENGTH_LONG).show()
                if (result.ok) onActivated()
            }
            .setNegativeButton("Cancel", null)
            .create()
    }
}
