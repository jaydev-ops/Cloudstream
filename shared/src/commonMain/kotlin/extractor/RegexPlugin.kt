

class RegexPlugin(private val config: PluginConfig) : CloudStreamPlugin {
    override val name: String = config.name

    override suspend fun search(query: String): List<SearchResult> {
        try {
            val encodedQuery = encodeUrl(query)
            val finalUrl = config.searchUrl.replace("%query%", encodedQuery)
            val jsonText = fetchUrl(finalUrl)

            val results = mutableListOf<SearchResult>()

            // 🛠️ FIX: Use '?:' (Elvis) to handle nulls safely
            val pattern = config.linkRegex ?: ""

            if (pattern.isNotEmpty()) {
                val regex = pattern.toRegex()

                regex.findAll(jsonText).forEach { match ->
                    // Safely get the captured group, or fallback to the whole match
                    val id = if (match.groups.size > 1) match.groupValues[1] else match.value
                    results.add(SearchResult(id, id, "", name))
                }
            }
            return results
        } catch (e: Exception) {
            return emptyList()
        }
    }

    override suspend fun loadLinks(url: String): List<StreamLink> {
        val html = fetchUrl(url)
        val linkRegex = Regex(config.linkRegex ?: "")
        val match = linkRegex.find(html)

        return if (match != null) {
            val videoUrl = match.groupValues[1]
            listOf(
                StreamLink(
                    title = "${config.name} Stream",
                    url = videoUrl,
                    quality = "Auto",                                   // 🟢 Added
                    isM3u8 = videoUrl.contains(".m3u8")                 // 🟢 Smart Check
                )
            )
        } else {
            emptyList()
        }
    }
}
