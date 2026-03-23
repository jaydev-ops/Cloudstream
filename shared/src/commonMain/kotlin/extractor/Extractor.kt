

// The Bluepint for all Extractors
abstract class Extractor {
    abstract val name: String
    abstract val mainUrl: String
    abstract val requiresReferer: Boolean

    // Does this extractor handle this URL?
    // e.g., if url contains "mixdrop.co", return true
    fun canHandle(url: String): Boolean {
        return url.contains(mainUrl)
    }

    // The Magic Function: Turns a webpage URL into a Video URL
    abstract suspend fun extract(url: String): List<StreamLink>
}