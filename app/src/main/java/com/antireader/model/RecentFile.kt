package com.antireader.model

data class RecentFile(
    val uriString: String,
    val localCachePath: String? = null,
    val displayName: String,
    val fileSize: Long,
    val lastOpenedTimestamp: Long,
    val lastReadIndex: Int = 0,
    val totalLines: Int = 0
) {
    val progressPercent: Int
        get() = if (totalLines > 0 && lastReadIndex > 0) {
            ((lastReadIndex.toFloat() / totalLines.toFloat()) * 100).toInt().coerceIn(0, 100)
        } else {
            0
        }
}
