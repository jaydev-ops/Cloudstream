//package org.example.project // 👈 Keep your package name!

expect class FavoritesManager() {
    fun saveFavorites(list: List<Movie>)
    fun loadFavorites(): List<Movie>
}