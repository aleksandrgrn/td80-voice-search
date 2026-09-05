package com.voicesearch.model

data class TargetApp(
    val packageName: String,
    val searchAction: String,
    val displayName: String,
    val mediaFocus: String? = null,
    val dataUriTemplate: String? = null  // e.g. "https://www.youtube.com/results?search_query={query}"
)
