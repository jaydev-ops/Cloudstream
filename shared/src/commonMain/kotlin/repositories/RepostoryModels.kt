
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName


// --- 1. PLUGIN CONFIGURATION MODELS ---

@Serializable
data class ExtensionRepository(
    val name: String,
    val author: String,
    val iconUrl: String? = null,
    // ⚡️ CHANGED: Use PluginConfig here so everything is consistent
    val plugins: List<PluginConfig>
)

@Serializable
data class PluginConfig(
    val name: String,
    val version: Int = 1,
    val searchUrl: String,

    // 1. CSS SELECTORS (New System)
    val searchSelector: String? = null,
    val titleSelector: String? = null,
    val linkSelector: String? = null,
    val posterSelector: String? = null,

    // 👇 THIS IS THE MISSING LINE!
    val videoSelector: String? = null,

    // 2. REGEX (Old System)
    val linkRegex: String? = null,
    val videoUrlPattern: String? = null,

    var sourceRepository: String = "User Custom"
)

// --- 2. TMDB MOVIE MODELS (Required for App.kt) ---
@Serializable
data class MovieResponse(val results: List<Movie>)

@Serializable
data class Movie(
    val id: Int,
    val title: String = "",
    // TV Shows use 'name' instead of 'title', so we map it
    @SerialName("name") val name: String? = null,

    @SerialName("overview") val description: String = "No description available",
    @SerialName("poster_path") val posterPath: String? = null,

    // Movies use release_date, TV uses first_air_date
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("first_air_date") val firstAirDate: String? = null,
    @SerialName("vote_average") val voteAverage: Double? = null, // 👈 ADD THIS LINE

    // --- NEW FIELDS FOR DETAILS ---
    @SerialName("number_of_seasons") val numberOfSeasons: Int? = null,
    @SerialName("number_of_episodes") val numberOfEpisodes: Int? = null,
    val credits: Credits? = null

) {
    // Smart Helpers to handle both Movies and TV
    val displayTitle: String
        get() = if (title.isNotEmpty()) title else name ?: "Unknown"

    val displayDate: String
        get() = releaseDate ?: firstAirDate ?: "Unknown"

    val fullPosterUrl: String
        get() = if (posterPath != null) "https://image.tmdb.org/t/p/w500$posterPath" else ""
}

// --- CAST MODELS ---
@Serializable
data class Credits(
    val cast: List<CastMember>
)

@Serializable
data class CastMember(
    val id: Int,
    val name: String,
    @SerialName("character") val character: String,
    @SerialName("profile_path") val profilePath: String?
) {
    val fullProfileUrl: String
        get() = if (profilePath != null) "https://image.tmdb.org/t/p/w200$profilePath" else ""
}
