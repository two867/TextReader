package com.antireader.model

data class RecentFile(
    val uriString: String,
    val displayName: String,
    val fileSize: Long,
    val lastOpenedTimestamp: Long
)
