package com.spiritdev.proxyvault.model

data class ProxyItem(
    val ip: String,
    val port: Int,
    val protocol: String = "http",
    val country: String = "XX",
    val countryCode: String = "XX",
    val anonymity: String = "unknown",
    val speedMs: Long = -1,
    val uptimePercent: Float = 0f,
    val source: String = "",
    val lastChecked: Long = System.currentTimeMillis(),
    val isAlive: Boolean = false
) {
    val address: String get() = "$ip:$port"
    val displaySpeed: String
        get() = when {
            speedMs < 0 -> "—"
            speedMs < 1000 -> "${speedMs}ms"
            else -> "${speedMs / 1000}s"
        }
}
