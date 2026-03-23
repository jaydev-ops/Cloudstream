

import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File

actual object PluginStorage {
    // Saves to: User Home Folder
    private val file = File(System.getProperty("user.home"), ".stream_plugins.json")

    actual fun save(plugins: List<PluginConfig>) {
        try {
            val json = Json.encodeToString(plugins)
            file.writeText(json)
            println("💾 [PluginStorage] Saved ${plugins.size} plugins.")
        } catch (e: Exception) {
            println("❌ [PluginStorage] Save failed: ${e.message}")
        }
    }

    actual fun load(): List<PluginConfig> {
        if (!file.exists()) return emptyList()
        return try {
            val json = file.readText()
            Json.decodeFromString(json)
        } catch (e: Exception) {
            println("❌ [PluginStorage] Load failed: ${e.message}")
            emptyList()
        }
    }
}