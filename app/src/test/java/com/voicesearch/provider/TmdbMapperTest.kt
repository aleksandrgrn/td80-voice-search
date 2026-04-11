package com.voicesearch.provider

import com.voicesearch.model.SearchResult
import org.junit.Assert.*
import org.junit.Test

class TmdbMapperTest {

    private val emptyGenreCache = emptyMap<Int, String>()
    private val sampleGenreCache = mapOf(
        28 to "Боевик",
        12 to "Приключения",
        878 to "Фантастика",
        18 to "Драма",
        35 to "Комедия"
    )

    // ===== Movie mapping =====

    @Test
    fun `mapResultItem maps movie with all fields`() {
        val item = TmdbResultItem(
            id = 603,
            mediaType = "movie",
            title = "Матрица",
            originalTitle = "The Matrix",
            releaseDate = "1999-03-31",
            posterPath = "/f89U3A6DcoOHkAQTWQrU3S4BtE.jpg",
            overview = "Компьютерный хакер...",
            genreIds = listOf(28, 12, 878),
            voteAverage = 8.2,
            popularity = 45.6
        )

        val result = TmdbMapper.mapResultItem(item, sampleGenreCache)!!

        assertEquals("tmdb_movie_603", result.id)
        assertEquals("Матрица", result.title)
        assertEquals("https://image.tmdb.org/t/p/w500/f89U3A6DcoOHkAQTWQrU3S4BtE.jpg", result.posterUrl)
        assertEquals("1999", result.year)
        assertEquals("Компьютерный хакер...", result.overview)
        assertEquals("tmdb", result.providerId)
        assertEquals("movie", result.metadata["type"])
        assertEquals("603", result.metadata["tmdbId"])
        assertEquals("Боевик, Приключения, Фантастика", result.metadata["genre"])
        assertEquals("8.2", result.metadata["rating"])
    }

    @Test
    fun `mapResultItem maps movie with title fallback to originalTitle`() {
        val item = TmdbResultItem(
            id = 1,
            mediaType = "movie",
            title = null,
            originalTitle = "Original Movie",
            releaseDate = "2020-05-01"
        )

        val result = TmdbMapper.mapResultItem(item, emptyGenreCache)!!

        assertEquals("Original Movie", result.title)
    }

    @Test
    fun `mapResultItem maps movie with title fallback to default`() {
        val item = TmdbResultItem(
            id = 1,
            mediaType = "movie",
            title = null,
            originalTitle = null
        )

        val result = TmdbMapper.mapResultItem(item, emptyGenreCache)!!

        assertEquals("Без названия", result.title)
    }

    @Test
    fun `mapResultItem extracts year from releaseDate`() {
        val item = TmdbResultItem(
            id = 1,
            mediaType = "movie",
            title = "Test",
            releaseDate = "2024-01-15"
        )

        val result = TmdbMapper.mapResultItem(item, emptyGenreCache)!!

        assertEquals("2024", result.year)
    }

    @Test
    fun `mapResultItem returns null year when releaseDate is too short`() {
        val item = TmdbResultItem(
            id = 1,
            mediaType = "movie",
            title = "Test",
            releaseDate = "20"
        )

        val result = TmdbMapper.mapResultItem(item, emptyGenreCache)!!

        assertNull(result.year)
    }

    @Test
    fun `mapResultItem returns null year when releaseDate is null`() {
        val item = TmdbResultItem(
            id = 1,
            mediaType = "movie",
            title = "Test",
            releaseDate = null
        )

        val result = TmdbMapper.mapResultItem(item, emptyGenreCache)!!

        assertNull(result.year)
    }

    // ===== TV mapping =====

    @Test
    fun `mapResultItem maps tv with all fields`() {
        val item = TmdbResultItem(
            id = 1399,
            mediaType = "tv",
            name = "Игра престолов",
            originalName = "Game of Thrones",
            firstAirDate = "2011-04-17",
            posterPath = "/7WUHnblNMErOdQrPgrPoH2sSjb.jpg",
            overview = "Семь аристократических семей...",
            genreIds = listOf(18, 12),
            voteAverage = 8.5
        )

        val result = TmdbMapper.mapResultItem(item, sampleGenreCache)!!

        assertEquals("tmdb_tv_1399", result.id)
        assertEquals("Игра престолов", result.title)
        assertEquals("https://image.tmdb.org/t/p/w500/7WUHnblNMErOdQrPgrPoH2sSjb.jpg", result.posterUrl)
        assertEquals("2011", result.year)
        assertEquals("tv", result.metadata["type"])
        assertEquals("1399", result.metadata["tmdbId"])
        assertEquals("Драма, Приключения", result.metadata["genre"])
        assertEquals("8.5", result.metadata["rating"])
    }

    @Test
    fun `mapResultItem maps tv with name fallback to originalName`() {
        val item = TmdbResultItem(
            id = 2,
            mediaType = "tv",
            name = null,
            originalName = "Original Show",
            firstAirDate = "2022-01-01"
        )

        val result = TmdbMapper.mapResultItem(item, emptyGenreCache)!!

        assertEquals("Original Show", result.title)
    }

    @Test
    fun `mapResultItem maps tv with name fallback to default`() {
        val item = TmdbResultItem(
            id = 2,
            mediaType = "tv",
            name = null,
            originalName = null
        )

        val result = TmdbMapper.mapResultItem(item, emptyGenreCache)!!

        assertEquals("Без названия", result.title)
    }

    // ===== Person filtering =====

    @Test
    fun `mapResultItem returns null for person mediaType`() {
        val item = TmdbResultItem(
            id = 123,
            mediaType = "person",
            name = "Keanu Reeves"
        )

        val result = TmdbMapper.mapResultItem(item, sampleGenreCache)

        assertNull(result)
    }

    @Test
    fun `mapResultItem returns null for unknown mediaType`() {
        val item = TmdbResultItem(
            id = 456,
            mediaType = "collection"
        )

        val result = TmdbMapper.mapResultItem(item, sampleGenreCache)

        assertNull(result)
    }

    // ===== Genre mapping =====

    @Test
    fun `mapGenreNames returns null for empty genreIds`() {
        val item = TmdbResultItem(
            id = 1,
            mediaType = "movie",
            title = "Test",
            genreIds = emptyList()
        )

        val result = TmdbMapper.mapResultItem(item, sampleGenreCache)!!

        assertNull(result.metadata["genre"])
    }

    @Test
    fun `mapGenreNames returns null for null genreIds`() {
        val item = TmdbResultItem(
            id = 1,
            mediaType = "movie",
            title = "Test",
            genreIds = null
        )

        val result = TmdbMapper.mapResultItem(item, sampleGenreCache)!!

        assertNull(result.metadata["genre"])
    }

    @Test
    fun `mapGenreNames skips unknown genre IDs`() {
        val item = TmdbResultItem(
            id = 1,
            mediaType = "movie",
            title = "Test",
            genreIds = listOf(28, 999, 878)
        )

        val result = TmdbMapper.mapResultItem(item, sampleGenreCache)!!

        assertEquals("Боевик, Фантастика", result.metadata["genre"])
    }

    @Test
    fun `mapGenreNames returns null when all genre IDs are unknown`() {
        val item = TmdbResultItem(
            id = 1,
            mediaType = "movie",
            title = "Test",
            genreIds = listOf(999, 998)
        )

        val result = TmdbMapper.mapResultItem(item, sampleGenreCache)!!

        assertNull(result.metadata["genre"])
    }

    // ===== Poster URL =====

    @Test
    fun `mapResultItem returns null posterUrl when posterPath is null`() {
        val item = TmdbResultItem(
            id = 1,
            mediaType = "movie",
            title = "Test",
            posterPath = null
        )

        val result = TmdbMapper.mapResultItem(item, emptyGenreCache)!!

        assertNull(result.posterUrl)
    }

    // ===== Rating =====

    @Test
    fun `mapResultItem omits rating when voteAverage is null`() {
        val item = TmdbResultItem(
            id = 1,
            mediaType = "movie",
            title = "Test",
            voteAverage = null
        )

        val result = TmdbMapper.mapResultItem(item, emptyGenreCache)!!

        assertFalse(result.metadata.containsKey("rating"))
    }

    @Test
    fun `mapResultItem formats rating with one decimal place`() {
        val item = TmdbResultItem(
            id = 1,
            mediaType = "movie",
            title = "Test",
            voteAverage = 7.567
        )

        val result = TmdbMapper.mapResultItem(item, emptyGenreCache)!!

        assertEquals("7.6", result.metadata["rating"])
    }
}
