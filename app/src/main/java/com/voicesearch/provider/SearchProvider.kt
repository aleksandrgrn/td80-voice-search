package com.voicesearch.provider

import com.voicesearch.model.SearchResult

interface SearchProvider {
    val id: String
    val displayName: String
    val type: ProviderType

    suspend fun search(query: String): List<SearchResult>
}
