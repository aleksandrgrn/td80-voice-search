package com.voicesearch.provider

import com.voicesearch.model.SearchResult
import java.util.Locale

object TmdbMapper {
    private const val POSTER_BASE_URL = "https://image.tmdb.org/t/p/w500"
    private const val FALLBACK_TITLE = "Без названия"

    fun mapResultItem(item: TmdbResultItem, genreCache: Map<Int, String>): SearchResult? {
        return when (item.mediaType) {
            "movie" -> mapMovie(item, genreCache)
            "tv" -> mapTv(item, genreCache)
            else -> null  // person and unknown types filtered out
        }
    }

    private fun mapMovie(item: TmdbResultItem, genreCache: Map<Int, String>): SearchResult {
        val year = item.releaseDate?.takeIf { it.length >= 4 }?.substring(0, 4)
        val genreNames = mapGenreNames(item.genreIds, genreCache)
        return SearchResult(
            id = "tmdb_movie_${item.id}",
            title = item.title ?: item.originalTitle ?: FALLBACK_TITLE,
            posterUrl = item.posterPath?.let { "$POSTER_BASE_URL$it" },
            year = year,
            overview = item.overview,
            providerId = "tmdb",
            metadata = buildMap {
                put("type", "movie")
                put("tmdbId", item.id.toString())
                if (genreNames != null) put("genre", genreNames)
                item.voteAverage?.let { put("rating", String.format(Locale.US, "%.1f", it)) }
            }
        )
    }

    private fun mapTv(item: TmdbResultItem, genreCache: Map<Int, String>): SearchResult {
        val year = item.firstAirDate?.takeIf { it.length >= 4 }?.substring(0, 4)
        val genreNames = mapGenreNames(item.genreIds, genreCache)
        return SearchResult(
            id = "tmdb_tv_${item.id}",
            title = item.name ?: item.originalName ?: FALLBACK_TITLE,
            posterUrl = item.posterPath?.let { "$POSTER_BASE_URL$it" },
            year = year,
            overview = item.overview,
            providerId = "tmdb",
            metadata = buildMap {
                put("type", "tv")
                put("tmdbId", item.id.toString())
                if (genreNames != null) put("genre", genreNames)
                item.voteAverage?.let { put("rating", String.format(Locale.US, "%.1f", it)) }
            }
        )
    }

    private fun mapGenreNames(genreIds: List<Int>?, genreCache: Map<Int, String>): String? {
        if (genreIds.isNullOrEmpty()) return null
        val names = genreIds.mapNotNull { genreCache[it] }
        return names.takeIf { it.isNotEmpty() }?.joinToString(", ")
    }
}
