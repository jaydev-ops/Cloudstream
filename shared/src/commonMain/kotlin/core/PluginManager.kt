
import kotlinx.serialization.json.Json



class PluginManager {
    // ✅ Main List of Plugins
    val activePlugins = mutableListOf<CloudStreamPlugin>()

    // Memory List (Keeps track of what we installed to avoid re-downloading same URL session)
    private val importedUrls = mutableListOf<String>()

    // 🔧 EXTRACTOR REGISTRY (The "Keys" to the encrypted sites)
    private val extractors = listOf<Extractor>(
        MixDropExtractor()
        // We will add VidCloud, RabbitStream, etc. here later
    )

    init {
        // Add built-in plugins
        registerPlugin(ArchivePlugin())
    }

    // 🧠 SMART REGISTER: Replaces old plugins instead of duplicating them
    fun registerPlugin(plugin: CloudStreamPlugin) {
        val existing = activePlugins.find { it.name == plugin.name }

        if (existing != null) {
            // 🛑 REMOVE the old version to allow the update
            activePlugins.remove(existing)
            println("♻️ Updated existing plugin: ${plugin.name}")
        } else {
            println("✅ Added new plugin: ${plugin.name}")
        }

        activePlugins.add(plugin)
    }

    // ✅ Search Function (Combines results from all plugins)
    suspend fun searchAll(query: String): List<SearchResult> {
        val allResults = mutableListOf<SearchResult>()
        activePlugins.forEach { plugin ->
            try {
                val results = plugin.search(query)
                allResults.addAll(results)
            } catch (e: Exception) {
                println("⚠️ Error searching ${plugin.name}: ${e.message}")
            }
        }
        return allResults
    }

    // ⚡️ UPDATED LOAD LINKS (Now uses Extractors!)
    suspend fun loadLinks(url: String, sourceName: String): List<StreamLink> {
        val plugin = activePlugins.find { it.name == sourceName } ?: return emptyList()

        // 1. Ask the Plugin to find the links (often these are "Embed" links like mixdrop.co/e/...)
        val initialLinks = plugin.loadLinks(url)

        val finalLinks = mutableListOf<StreamLink>()

        initialLinks.forEach { link ->
            // 2. Check if we have a Key (Extractor) for this link
            val matchingExtractor = extractors.find { it.canHandle(link.url) }

            if (matchingExtractor != null) {
                println("🔧 Extractor Found! Unpacking ${link.url} using ${matchingExtractor.name}...")
                val extracted = matchingExtractor.extract(link.url)

                if (extracted.isNotEmpty()) {
                    finalLinks.addAll(extracted)
                } else {
                    println("⚠️ Extractor failed to unpack ${link.url}")
                }
            } else {
                // 3. If no extractor needed (it's already a direct file), keep it.
                finalLinks.add(link)
            }
        }

        return finalLinks
    }

    // ---------------------------------------------------------
    // 👇 REPO INSTALLER & SAVING LOGIC
    // ---------------------------------------------------------

    suspend fun installRepository(url: String): String {
        // A. Cheat Code Check
        if (url == "repo") {
            return "Success! Installed Test Plugin."
        }

        // B. Real Download
        return try {
            println("📦 Downloading Repo from: $url")
            val jsonText = fetchUrl(url)

            // Extract the "plugins" list part
            val content = jsonText.substringAfter("\"plugins\"", "")
            if (content.isEmpty()) return "Error: No 'plugins' list found."

            var count = 0
            val rawBlocks = content.split("}") // Split by closing brace

            rawBlocks.forEach { block ->
                // 1. Safety Check: Skip empty blocks
                if (block.isBlank() || block.length < 5) return@forEach

                try {
                    val name = extractValue(block, "name")
                    val searchUrl = extractValue(block, "searchUrl")

                    if (name.isNotEmpty() && searchUrl.isNotEmpty()) {

                        val config = PluginConfig(
                            name = name,
                            searchUrl = searchUrl,
                            searchSelector = extractValue(block, "searchSelector").ifEmpty { null },
                            titleSelector = extractValue(block, "titleSelector").ifEmpty { null },
                            linkSelector = extractValue(block, "linkSelector").ifEmpty { null },
                            posterSelector = extractValue(block, "posterSelector").ifEmpty { null },
                            videoSelector = extractValue(block, "videoSelector").ifEmpty { null }
                        )

                        // Smart register handles duplicates
                        registerPlugin(UniversalPlugin(config))
                        count++
                    }
                } catch (e: Exception) {
                    println("⚠️ Failed to parse a block: ${e.message}")
                }
            }

            // C. SAVE TO DISK
            if (count > 0 && !importedUrls.contains(url)) {
                importedUrls.add(url)
                saveTextData("installed_repos.txt", importedUrls.joinToString(","))
            }

            if (count > 0) "Success! Processed $count plugins." else "Plugins already installed."

        } catch (e: Exception) {
            "Failed: ${e.message}"
        }
    }

    suspend fun restorePlugins() {
        println("♻️ Restoring Plugins...")
        val data = loadTextData("installed_repos.txt")

        if (data.isNotBlank()) {
            val urls = data.split(",")
            urls.forEach { url ->
                if (url.isNotBlank()) {
                    println("♻️ Re-installing: $url")
                    importedUrls.add(url)
                    installRepository(url)
                }
            }
        }
    }

    private fun extractValue(text: String, key: String): String {
        val regex = Regex("\"$key\"\\s*:\\s*\"([^\"]+)\"")
        val match = regex.find(text)
        return match?.groupValues?.get(1) ?: ""
    }

    fun addCustomPlugin(name: String, searchUrl: String, regex: String, pattern: String?) {
        val config = PluginConfig(
            name = name,
            searchUrl = searchUrl,
            linkRegex = regex,
            videoUrlPattern = pattern
        )
        registerPlugin(RegexPlugin(config))
    }

    fun removePlugin(plugin: CloudStreamPlugin) {
        activePlugins.remove(plugin)
    }
}