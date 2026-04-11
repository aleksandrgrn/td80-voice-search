package com.voicesearch.model

data class TargetApp(
    val packageName: String,
    val searchAction: String,
    val displayName: String,
    val mediaFocus: String? = null
)
