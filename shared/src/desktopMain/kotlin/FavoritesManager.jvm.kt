//package org.example.project

import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File

actual class FavoritesManager {
    // 📂 We save to a file named "my_stream_app_favorites.json" in your Home folder
    private val file = File(System.getProperty("user.home"), "my_stream_app_favorites.json")

    // 🛠️ Setup JSON tool to be lenient (forgiving)
    private val json = Json { ignoreUnknownKeys = true }

    actual fun saveFavorites(list: List<Movie>) {
        try {
            val text = json.encodeToString(list)
            file.writeText(text)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    actual fun loadFavorites(): List<Movie> {
        return try {
            if (file.exists()) {
                val text = file.readText()
                json.decodeFromString(text)
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
}