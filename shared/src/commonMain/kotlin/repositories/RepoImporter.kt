
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json

class RepoImporter(private val client: HttpClient) {
    private val jsonParser = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    suspend fun importFromUrl(url: String): List<PluginConfig> {
        return try {
            println("🌍 Fetching Repository from: $url")
            val content = client.get(url).bodyAsText()
            val repo = jsonParser.decodeFromString<ExtensionRepository>(content)

            println("📦 Found Repo: '${repo.name}'")
            // Tag plugins with source name
            repo.plugins.map { plugin ->
                plugin.copy(sourceRepository = repo.name)
            }
        } catch (e: Exception) {
            println("❌ Failed to import: ${e.message}")
            e.printStackTrace()
            emptyList()
        }
    }
}