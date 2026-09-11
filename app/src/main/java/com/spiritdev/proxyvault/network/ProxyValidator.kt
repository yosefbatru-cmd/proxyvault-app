package com.spiritdev.proxyvault.network

import com.spiritdev.proxyvault.model.ProxyItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit

class ProxyValidator(
    private val testUrl: String = "http://httpbin.org/ip",
    private val timeoutMs: Long = 8000
) {
    suspend fun validateBatch(
        candidates: List<ProxyItem>,
        concurrencyHint: Int = 40
    ): List<ProxyItem> = coroutineScope {
        if (candidates.isEmpty()) return@coroutineScope emptyList()
        val chunkSize = concurrencyHint.coerceAtLeast(10)
        val results = mutableListOf<ProxyItem>()
        candidates.chunked(chunkSize).forEach { chunk ->
            val jobs = chunk.map { proxy -> async(Dispatchers.IO) { validateOne(proxy) } }
            results.addAll(jobs.awaitAll().filter { it.isAlive })
        }
        results.sortedBy { it.speedMs }
    }

    private suspend fun validateOne(item: ProxyItem): ProxyItem = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        try {
            val proxyType = when (item.protocol.lowercase()) {
                "socks5", "socks" -> Proxy.Type.SOCKS
                else -> Proxy.Type.HTTP
            }
            val javaProxy = Proxy(proxyType, InetSocketAddress(item.ip, item.port))
            val client = OkHttpClient.Builder()
                .proxy(javaProxy)
                .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .callTimeout(timeoutMs + 1000, TimeUnit.MILLISECONDS)
                .build()
            val req = Request.Builder()
                .url(testUrl)
                .header("User-Agent", "ProxyVault-Validator/1.1")
                .get()
                .build()
            client.newCall(req).execute().use { resp ->
                val latency = System.currentTimeMillis() - start
                if (resp.isSuccessful) {
                    item.copy(isAlive = true, speedMs = latency, lastChecked = System.currentTimeMillis(), uptimePercent = 100f)
                } else {
                    item.copy(isAlive = false, speedMs = -1)
                }
            }
        } catch (_: Exception) {
            item.copy(isAlive = false, speedMs = -1)
        }
    }
}
