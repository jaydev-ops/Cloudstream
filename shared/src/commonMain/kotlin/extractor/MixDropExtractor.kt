class MixDropExtractor : Extractor() {
    override val name = "MixDrop"
    override val mainUrl = "mixdrop" // Matches mixdrop.co, mixdrop.to, etc.
    override val requiresReferer = false

    override suspend fun extract(url: String): List<StreamLink> {
        return try {
            // 1. Download the page source (e.g., mixdrop.co/e/...)
            val html = fetchUrl(url)

            // 2. MixDrop hides the link inside a packed javascript function:
            // "eval(function(p,a,c,k,e,d)..."
            // We need to find the "wurl" variable inside the unpacked code.

            // Simple Regex to find the packed code (This is a simplified version)
            // In a real generic app, we'd need a JS Unpacker, but MixDrop often
            // leaves the key plainly visible if you know where to look.

            // Look for: MDCore.wurl="//subdomain.mixdrop.co/..."
            val regex = Regex("MDCore\\.wurl\\s*=\\s*\"([^\"]+)\"")
            val match = regex.find(html)

            if (match != null) {
                var rawLink = match.groupValues[1]

                // Fix the link protocol (it often comes as "//site.com")
                if (rawLink.startsWith("//")) {
                    rawLink = "https:$rawLink"
                }

                listOf(
                    StreamLink(
                        title = "MixDrop",
                        url = rawLink,
                        quality = "720p", // MixDrop is usually 720p/480p
                        isM3u8 = false
                    )
                )
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            println("❌ MixDrop Error: ${e.message}")
            emptyList()
        }
    }
}