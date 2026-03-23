

// The "Promise" (Common Code)
expect suspend fun fetchUrl(url: String): String
expect fun encodeUrl(text: String): String
expect suspend fun parseHtmlSearch(
    html: String,
    containerSelector: String,
    titleSelector: String,
    linkSelector: String,
    posterSelector: String
): List<SearchResult>

// 👇 NEW: Storage Functions
expect fun saveTextData(fileName: String, content: String)
expect fun loadTextData(fileName: String): String