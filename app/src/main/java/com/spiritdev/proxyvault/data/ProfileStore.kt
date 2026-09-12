package com.spiritdev.proxyvault.data

import android.content.Context
import com.spiritdev.proxyvault.crypto.MrCrypto
import com.spiritdev.proxyvault.model.TunnelProfile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class ProfileStore(private val context: Context) {

    private val prefs = context.getSharedPreferences("proxyvault_profiles", Context.MODE_PRIVATE)
    private val keyProfiles = "profiles_json"

    fun list(): List<TunnelProfile> {
        val raw = prefs.getString(keyProfiles, "[]") ?: "[]"
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { TunnelProfile.fromJson(arr.getJSONObject(it)) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun save(profile: TunnelProfile) {
        val all = list().toMutableList()
        val idx = all.indexOfFirst { it.id == profile.id }
        if (idx >= 0) all[idx] = profile else all.add(profile)
        writeAll(all)
    }

    fun delete(id: String) {
        writeAll(list().filter { it.id != id })
    }

    fun get(id: String): TunnelProfile? = list().find { it.id == id }

    private fun writeAll(profiles: List<TunnelProfile>) {
        val arr = JSONArray()
        profiles.forEach { arr.put(it.toJson()) }
        prefs.edit().putString(keyProfiles, arr.toString()).apply()
    }

    fun exportMr(password: CharArray, outFile: File): Boolean {
        return try {
            val arr = JSONArray()
            list().forEach { arr.put(it.toJson()) }
            val payload = arr.toString().toByteArray(Charsets.UTF_8)
            val encrypted = MrCrypto.export(password, payload)
            outFile.writeBytes(encrypted)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun importMr(password: CharArray, file: File): Int {
        val bytes = file.readBytes()
        val plain = MrCrypto.import(password, bytes)
        val arr = JSONArray(String(plain, Charsets.UTF_8))
        var count = 0
        for (i in 0 until arr.length()) {
            val p = TunnelProfile.fromJson(arr.getJSONObject(i))
            save(p)
            count++
        }
        return count
    }
}
