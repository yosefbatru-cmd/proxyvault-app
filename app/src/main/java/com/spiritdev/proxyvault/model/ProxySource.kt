package com.spiritdev.proxyvault.model

data class ProxySource(
    val id: String,
    val name: String,
    val url: String,
    val type: String,
    val enabled: Boolean = true,
    val lastFetch: Long = 0,
    val lastCount: Int = 0,
    val reliability: Float = 0.5f
)
