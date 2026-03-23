

class ArchivePlugin : CloudStreamPlugin {
    override val name = "Archive.org"

    override suspend fun search(query: String): List<SearchResult> {
        try {
            val encodedQuery = encodeUrl(query)
            // Search for movies with titles matching the query
            val apiUrl = "https://archive.org/advancedsearch.php?q=title:($encodedQuery)+AND+mediatype:(movies)&fl[]=identifier&fl[]=title&sort[]=downloads+desc&output=json&rows=5"

            val jsonText = fetchUrl(apiUrl)

            val results = mutableListOf<SearchResult>()
            val idRegex = "\"identifier\"\\s*:\\s*\"([^\"]+)\"".toRegex()

            idRegex.findAll(jsonText).forEach { match ->
                val id = match.groupValues[1]
                results.add(
                    SearchResult(
                        title = id.replace("_", " "),
                        url = id,
                        posterUrl = "https://archive.org/services/img/$id",
                        sourceName = name
                    )
                )
            }
            return results
        } catch (e: Exception) {
            println("❌ Error in Search: ${e.message}")
            return emptyList()
        }
    }

    override suspend fun loadLinks(url: String): List<StreamLink> {
        return if (url == "https://archive.org/details/IronMan") {
            listOf(
                StreamLink(
                    title = "Archive.org (720p)",
                    url = "https://archive.org/download/IronMan/IronMan.mp4",
                    quality = "720p",
                    isM3u8 = false
                ),
                StreamLink(
                    title = "Archive.org (480p)",
                    url = "https://archive.org/download/IronMan/IronMan_480.mp4",
                    quality = "480p",
                    isM3u8 = false
                )
            )
        } else {
            emptyList()
        }
    }
}