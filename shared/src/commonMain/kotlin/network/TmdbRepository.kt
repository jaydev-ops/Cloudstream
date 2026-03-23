//package org.example.project

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

enum class ContentType { MOVIE, SERIES, ANIME }
enum class Category { TRENDING, TOP_RATED, POPULAR }

class TmdbRepository {
    private val apiKey = "<YOUR_API_KEY>" // 👈 Check your key!

    private val client = HttpClient {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true; coerceInputValues = true; isLenient = true })
        }
    }

    suspend fun getList(category: Category, type: ContentType): Result<List<Movie>> {
        val baseUrl = "https://api.themoviedb.org/3"
        val keyParam = "api_key=${apiKey.trim()}&language=en-US"

        // 1. ANIME LOGIC (Force specific Genre & Language)
        if (type == ContentType.ANIME) {
            val sort = when (category) {
                Category.TRENDING -> "popularity.desc"
                Category.TOP_RATED -> "vote_average.desc"
                Category.POPULAR -> "popularity.desc"
            }
            // Genre 16 = Animation, Original Language = ja (Japanese)
            val url = "$baseUrl/discover/tv?$keyParam&with_genres=16&with_original_language=ja&sort_by=$sort"
            return fetchMovies(url)
        }

        // 2. MOVIE / SERIES LOGIC
        val typePath = if (type == ContentType.MOVIE) "movie" else "tv"
        val endpoint = when (category) {
            Category.TRENDING -> "trending/$typePath/week"
            Category.TOP_RATED -> "$typePath/top_rated"
            Category.POPULAR -> "$typePath/popular"
        }

        return fetchMovies("$baseUrl/$endpoint?$keyParam")
    }

    suspend fun search(query: String, type: ContentType): Result<List<Movie>> {
        val searchType = if (type == ContentType.MOVIE) "movie" else "tv"
        return fetchMovies("https://api.themoviedb.org/3/search/$searchType?api_key=${apiKey.trim()}&query=$query")
    }

    suspend fun getDetails(id: Int, type: ContentType): Result<Movie> {
        val endpoint = if (type == ContentType.MOVIE) "movie" else "tv"
        val url = "https://api.themoviedb.org/3/$endpoint/$id?api_key=${apiKey.trim()}&append_to_response=credits"

        return try {
            val response = client.get(url)
            if (response.status.value == 200) Result.success(response.body())
            else Result.failure(Exception("Error"))
        } catch (e: Exception) { Result.failure(e) }
    }

    private suspend fun fetchMovies(url: String): Result<List<Movie>> {
        return try {
            val response = client.get(url)
            if (response.status.value == 200) {
                val data: MovieResponse = response.body()
                Result.success(data.results)
            } else Result.failure(Exception("Error ${response.status}"))
        } catch (e: Exception) { Result.failure(e) }
    }
}