package com.voicesearch.provider

import com.squareup.moshi.Json

// DTO classes for TMDB API v3 JSON responses.
// Uses Moshi reflection (KotlinJsonAdapterFactory) — no @JsonClass needed.
// NOTE: Moshi does NOT auto-convert snake_case → camelCase.
// @Json(name=...) is required for all fields where JSON key != Kotlin property name.

data class TmdbMultiSearchResponse(
    val page: Int = 0,
    val results: List<TmdbResultItem> = emptyList(),
    @Json(name = "total_pages") val totalPages: Int = 0,
    @Json(name = "total_results") val totalResults: Int = 0
)

data class TmdbResultItem(
    val id: Long = 0,
    @Json(name = "media_type") val mediaType: String = "",       // "movie" | "tv" | "person"
    // Movie-specific
    val title: String? = null,
    @Json(name = "original_title") val originalTitle: String? = null,
    @Json(name = "release_date") val releaseDate: String? = null,  // "YYYY-MM-DD"
    // TV-specific
    val name: String? = null,
    @Json(name = "original_name") val originalName: String? = null,
    @Json(name = "first_air_date") val firstAirDate: String? = null, // "YYYY-MM-DD"
    // Common
    @Json(name = "poster_path") val posterPath: String? = null,
    val overview: String? = null,
    @Json(name = "genre_ids") val genreIds: List<Int>? = null,
    @Json(name = "vote_average") val voteAverage: Double? = null,
    val popularity: Double? = null
)

data class TmdbGenreListResponse(
    val genres: List<TmdbGenre> = emptyList()
)

data class TmdbGenre(
    val id: Int = 0,
    val name: String = ""
)
