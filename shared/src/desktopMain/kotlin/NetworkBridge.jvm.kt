// 👇 If your Common file has no package, keep this empty.
// If it has "package org.example.project", uncomment the next line:
// package org.example.project

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.dnsoverhttps.DnsOverHttps
import java.io.File
import java.net.InetAddress
import java.util.concurrent.TimeUnit
import org.jsoup.Jsoup
import java.net.URLEncoder
import org.w3c.dom.Text

// 1. Define the DNS "Tank" (Cloudflare & Google)
private val appClient: OkHttpClient by lazy {
    val bootstrapClient = OkHttpClient.Builder().build()

    val dns = DnsOverHttps.Builder().client(bootstrapClient)
        .url("https://dns.cloudflare.com/dns-query".toHttpUrl())
        .bootstrapDnsHosts(InetAddress.getByName("1.1.1.1"), InetAddress.getByName("1.0.0.1"))
        .build()

    OkHttpClient.Builder()
        .dns(dns)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
}

// 2. 👇 THIS WAS MISSING! The function that actually fetches the data.
// 👇 REPLACE YOUR EXISTING fetchUrl WITH THIS
actual suspend fun fetchUrl(url: String): String {
    // 🔍 ANALYZE: Should we use the Tank (Direct) or the Ghost (Proxy)?
    // If the URL is for a site we KNOW is blocked (YTS, HDToday), force the proxy.
    val useProxy = url.contains("yts") || url.contains("hdtoday") || url.contains("himovies")

    if (useProxy) {
        return fetchViaProxy(url)
    } else {
        // For other sites (like Archive.org or Google), try Direct first.
        return try {
            fetchDirect(url)
        } catch (e: Exception) {
            println("⚠️ Direct failed, falling back to proxy...")
            fetchViaProxy(url)
        }
    }
}

// 🛡️ THE TANK (Direct Connection)
fun fetchDirect(url: String): String {
    println("🛡️ Safe-Fetch: $url")
    val request = Request.Builder()
        .url(url)
        .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
        .build()

    val response = appClient.newCall(request).execute()
    if (!response.isSuccessful) throw Exception("HTTP ${response.code}")
    return response.body?.string() ?: ""
}

// 🥷 THE GHOST (AllOrigins Proxy)
fun fetchViaProxy(url: String): String {
    try {
        println("🥷 Stealth-Fetch (Forced): $url")
        val encodedUrl = java.net.URLEncoder.encode(url, "UTF-8")
        val proxyUrl = "https://api.allorigins.win/raw?url=$encodedUrl"

        val request = Request.Builder()
            .url(proxyUrl)
            .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7)")
            .build()

        val response = appClient.newCall(request).execute()
        if (response.isSuccessful) {
            return response.body?.string() ?: ""
        }
    } catch (e: Exception) {
        println("❌ Proxy Failed: ${e.message}")
    }
    return "" // Give up
}

// 3. URL Encoder
actual  fun encodeUrl(text: String): String {
    return try {
        java.net.URLEncoder.encode(text, "UTF-8")
    } catch (e: Exception) {
        text // Fallback if encoding fails
    }
}

// 4. File Saving
actual fun saveTextData(fileName: String, content: String) {
    File(fileName).writeText(content)
}

// 5. File Loading
actual fun loadTextData(fileName: String): String {
    val file = File(fileName)
    return if (file.exists()) file.readText() else ""
}

// 6. HTML Parser (Jsoup)
actual suspend fun parseHtmlSearch(
    html: String,
    containerSelector: String,
    titleSelector: String,
    linkSelector: String,
    posterSelector: String
): List<SearchResult> {
    val results = mutableListOf<SearchResult>()
    try {
        val doc = Jsoup.parse(html)
        val items = doc.select(containerSelector)

        for (element in items) {
            try {
                val title = element.select(titleSelector).text()
                val link = element.select(linkSelector).attr("href")

                var poster = element.select(posterSelector).attr("src")
                if (poster.isEmpty()) {
                    poster = element.select(posterSelector).attr("data-src")
                }

                if (title.isNotEmpty() && link.isNotEmpty()) {
                    results.add(SearchResult(title, link, poster, "Scraper"))
                }
            } catch (e: Exception) {
                println("⚠️ Failed to parse one item: ${e.message}")
            }
        }
    } catch (e: Exception) {
        println("❌ Jsoup Error: ${e.message}")
    }
    return results
}