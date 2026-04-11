package com.voicesearch.provider

import com.voicesearch.model.SearchResult

class TmdbSearchProvider(
    private val apiKey: String,
    private val language: String = "ru-RU"
) : SearchProvider {

    override val id = "tmdb"
    override val displayName = "TMDB"
    override val type = ProviderType.CARDS

    override suspend fun search(query: String): List<SearchResult> {
        // TODO: implement TMDB API calls (task 6)
        return emptyList()
    }
}
