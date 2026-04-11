package com.voicesearch.model

data class SearchResult(
    val id: String,
    val title: String,
    val posterUrl: String?,
    val year: String?,
    val overview: String?,
    val providerId: String,
    val metadata: Map<String, String> = emptyMap()
)
