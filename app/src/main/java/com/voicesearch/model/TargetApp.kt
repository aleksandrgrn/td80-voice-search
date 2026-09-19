package com.voicesearch.model

data class TargetApp(
    val packageName: String,
    val searchAction: String,
    val displayName: String,
    val dataUriTemplate: String? = null  // e.g. "https://www.youtube.com/results?search_query={query}"
)
