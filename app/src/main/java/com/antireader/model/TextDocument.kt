package com.antireader.model

import android.net.Uri

data class TextDocument(
    val uri: Uri?,
    val displayName: String,
    val fileSize: Long,
    val lines: List<String>,
    val charsetName: String
) {
    val totalLines: Int get() = lines.size
}
