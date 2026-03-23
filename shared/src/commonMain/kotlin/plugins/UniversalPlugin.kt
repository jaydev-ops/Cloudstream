// 👇 If your project doesn't use packages, delete this line.
// package org.example.project

import kotlinx.serialization.json.Json

class UniversalPlugin(private val config: PluginConfig) : CloudStreamPlugin {
    override val name = config.name

    override suspend fun search(query: String): List<SearchResult> {
        // 1. Prepare the URL (replace %query% with actual text)
        // We use the new 'encodeUrl' function to fix spaces/symbols
        val encodedQuery = encodeUrl(query)
        val targetUrl = config.searchUrl.replace("%query%", encodedQuery)

        return try {
            // 2. Fetch the HTML
            val html = fetchUrl(targetUrl)

            // 3. Parse it using the selectors from JSON
            if (config.searchSelector != null && config.titleSelector != null && config.linkSelector != null) {
                parseHtmlSearch(
                    html = html,
                    containerSelector = config.searchSelector,
                    titleSelector = config.titleSelector,
                    linkSelector = config.linkSelector,
                    posterSelector = config.posterSelector ?: "img"
                )
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            println("Error searching $name: ${e.message}")
            emptyList()
        }
    }

    override suspend fun loadLinks(url: String): List<StreamLink> {
        return try {
            val html = fetchUrl(url)
            val results = mutableListOf<StreamLink>()

            // A. IF JSON HAS A "VIDEO SELECTOR" (For finding Iframes/Embeds)
            if (!config.videoSelector.isNullOrEmpty()) {
                // We use a simple regex to find the 'src' of the iframe defined in JSON
                // Example: config.videoSelector = "iframe[src*='mixdrop']"

                // Note: Since we don't have a generic "parseHtmlElement" yet,
                // we will do a robust regex search for the src inside the HTML.
                // This finds: src="https://..." inside the specific tag if possible,
                // or just searches the whole body if the selector is simple.

                val regex = Regex("src=[\"']([^\"']+)[\"']")
                val match = regex.find(html) // simple find for now

                // In a real generic scraper, we would use Jsoup here to target the specific element.
                // For now, we assume if the user provided a selector, we look for links.

                // Let's try to find the specific iframe source if we can
                // (This is a simplified logic to keep it working without complex Jsoup logic here)
                val embedUrl = extractEmbedUrl(html, config.videoSelector)

                if (embedUrl.isNotEmpty()) {
                    results.add(
                        StreamLink(
                            title = "$name Embed",
                            url = embedUrl,
                            quality = "Auto",                // 🟢 ADDED THIS
                            isM3u8 = embedUrl.contains(".m3u8") // 🟢 ADDED THIS
                        )
                    )
                }
            }

            // B. FALLBACK / DIRECT SEARCH
            // If we didn't find specific embeds, or if no selector was given,
            // we return the page itself so the Extractor Manager can try to scan it.
            if (results.isEmpty()) {
                results.add(
                    StreamLink(
                        title = "$name Page",
                        url = url,
                        quality = "Auto",               // 🟢 ADDED THIS
                        isM3u8 = false                  // 🟢 ADDED THIS
                    )
                )
            }

            results
        } catch (e: Exception) {
            println("Error loading links for $name: ${e.message}")
            emptyList()
        }
    }

    // Helper to find iframe src roughly matching the selector
    private fun extractEmbedUrl(html: String, selector: String): String {
        // Very basic extractor: looks for http links near the selector keyword
        // e.g. if selector is "mixdrop", looks for "https://...mixdrop..."
        return try {
            val keyword = selector.substringAfter("src*='").substringBefore("'")
            if (keyword.isNotEmpty()) {
                val regex = Regex("(https?:[^\"'\\s>]+$keyword[^\"'\\s>]+)")
                val match = regex.find(html)
                match?.value ?: ""
            } else {
                ""
            }
        } catch (e: Exception) {
            ""
        }
    }
}