package com.antireader.model

data class RecentFile(
    val uriString: String,
    val localCachePath: String? = null,
    val displayName: String,
    val fileSize: Long,
    val lastOpenedTimestamp: Long
)
