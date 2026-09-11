package com.spiritdev.proxyvault.network

import com.spiritdev.proxyvault.model.ProxyItem
import com.spiritdev.proxyvault.model.ProxySource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class ProxyFetcher(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
) {
    private val ipPortPattern: Pattern = Pattern.compile(
        "(\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}):(\\d{2,5})"
    )

    suspend fun fetchAll(sources: List<ProxySource>): List<ProxyItem> = coroutineScope {
        val enabled = sources.filter { it.enabled }
        val jobs = enabled.map { src -> async(Dispatchers.IO) { fetchOne(src) } }
        jobs.awaitAll().flatten().distinctBy { it.address }
    }

    private suspend fun fetchOne(source: ProxySource): List<ProxyItem> = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url(source.url)
                .header("User-Agent", "ProxyVault/1.1")
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext emptyList()
                val body = resp.body?.string() ?: return@withContext emptyList()
                parseBody(body, source)
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parseBody(body: String, source: ProxySource): List<ProxyItem> {
        val result = mutableListOf<ProxyItem>()
        val matcher = ipPortPattern.matcher(body)
        val protocol = when {
            source.url.contains("socks5", ignoreCase = true) -> "socks5"
            source.url.contains("https", ignoreCase = true) -> "https"
            else -> "http"
        }
        while (matcher.find()) {
            val ip = matcher.group(1) ?: continue
            val port = matcher.group(2)?.toIntOrNull() ?: continue
            if (port in 1..65535) {
                result.add(ProxyItem(ip = ip, port = port, protocol = protocol, source = source.name))
            }
        }
        return result
    }
}
