package com.spiritdev.proxyvault.model

import org.json.JSONObject
import java.util.UUID

enum class ConnectionMode {
    SSH_DIRECT,
    SLOWDNS,
    ADVANCED
}

data class TunnelProfile(
    val id: String = UUID.randomUUID().toString(),
    var name: String = "New Tunnel",
    var mode: ConnectionMode = ConnectionMode.SSH_DIRECT,
    var host: String = "",
    var port: Int = 22,
    var username: String = "",
    var password: String = "",
    var privateKey: String = "",
    var payload: String = "",
    var sni: String = "",
    var dnsResolver: String = "1.1.1.1",
    var slowDnsNameserver: String = "",
    var localPort: Int = 1080,
    var bufferSend: Int = 32768,
    var bufferRecv: Int = 32768,
    var compression: Boolean = false,
    var udpForward: Boolean = false,
    var gateway: String = "",
    var keepAlive: Boolean = true,
    var httpPingUrl: String = "http://connectivitycheck.gstatic.com/generate_204",
    var notes: String = ""
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("mode", mode.name)
        put("host", host)
        put("port", port)
        put("username", username)
        put("password", password)
        put("privateKey", privateKey)
        put("payload", payload)
        put("sni", sni)
        put("dnsResolver", dnsResolver)
        put("slowDnsNameserver", slowDnsNameserver)
        put("localPort", localPort)
        put("bufferSend", bufferSend)
        put("bufferRecv", bufferRecv)
        put("compression", compression)
        put("udpForward", udpForward)
        put("gateway", gateway)
        put("keepAlive", keepAlive)
        put("httpPingUrl", httpPingUrl)
        put("notes", notes)
    }

    companion object {
        fun fromJson(o: JSONObject): TunnelProfile = TunnelProfile(
            id = o.optString("id", UUID.randomUUID().toString()),
            name = o.optString("name", "Tunnel"),
            mode = try {
                ConnectionMode.valueOf(o.optString("mode", "SSH_DIRECT"))
            } catch (_: Exception) {
                ConnectionMode.SSH_DIRECT
            },
            host = o.optString("host"),
            port = o.optInt("port", 22),
            username = o.optString("username"),
            password = o.optString("password"),
            privateKey = o.optString("privateKey"),
            payload = o.optString("payload"),
            sni = o.optString("sni"),
            dnsResolver = o.optString("dnsResolver", "1.1.1.1"),
            slowDnsNameserver = o.optString("slowDnsNameserver"),
            localPort = o.optInt("localPort", 1080),
            bufferSend = o.optInt("bufferSend", 32768),
            bufferRecv = o.optInt("bufferRecv", 32768),
            compression = o.optBoolean("compression", false),
            udpForward = o.optBoolean("udpForward", false),
            gateway = o.optString("gateway"),
            keepAlive = o.optBoolean("keepAlive", true),
            httpPingUrl = o.optString("httpPingUrl", "http://connectivitycheck.gstatic.com/generate_204"),
            notes = o.optString("notes")
        )
    }
}
