package com.spiritdev.proxyvault.network

import com.spiritdev.proxyvault.model.ProxySource

object SourceCatalog {
    val DEFAULT: List<ProxySource> = listOf(
        ProxySource("speedx", "TheSpeedX/PROXY-List",
            "https://raw.githubusercontent.com/TheSpeedX/PROXY-List/master/http.txt", "github"),
        ProxySource("speedx_socks5", "TheSpeedX SOCKS5",
            "https://raw.githubusercontent.com/TheSpeedX/PROXY-List/master/socks5.txt", "github"),
        ProxySource("shiftytr", "ShiftyTR/Proxy-List",
            "https://raw.githubusercontent.com/ShiftyTR/Proxy-List/master/http.txt", "github"),
        ProxySource("monosans", "monosans/proxy-list",
            "https://raw.githubusercontent.com/monosans/proxy-list/main/proxies/http.txt", "github"),
        ProxySource("proxyscrape_http", "ProxyScrape HTTP",
            "https://api.proxyscrape.com/v2/?request=displayproxies&protocol=http&timeout=10000&country=all&ssl=all&anonymity=all", "api"),
        ProxySource("proxyscrape_socks5", "ProxyScrape SOCKS5",
            "https://api.proxyscrape.com/v2/?request=displayproxies&protocol=socks5&timeout=10000&country=all", "api"),
        ProxySource("free_proxy_list", "Free-Proxy-List.net",
            "https://www.proxy-list.download/api/v1/get?type=http", "api"),
        ProxySource("openproxylist", "openproxylist",
            "https://raw.githubusercontent.com/roosterkid/openproxylist/main/HTTPS_RAW.txt", "github"),
        ProxySource("clarketm", "clarketm/proxy-list",
            "https://raw.githubusercontent.com/clarketm/proxy-list/master/proxy-list-raw.txt", "github"),
        ProxySource("jetkai", "jetkai/proxy-list",
            "https://raw.githubusercontent.com/jetkai/proxy-list/main/online-proxies/txt/proxies-http.txt", "github")
    )
}
