package com.antireader.utils

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.antireader.model.RecentFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Locale

object FileUtils {

    private const val PREFS_NAME = "antireader_prefs"
    private const val KEY_RECENTS = "recent_files"
    private const val MAX_RECENTS = 20

    fun getFileNameAndSize(context: Context, uri: Uri): Pair<String, Long> {
        var name = "未知文档.txt"
        var size = 0L

        // 尝试通过 contentResolver 查询标准元数据
        if (uri.scheme == "content") {
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (cursor.moveToFirst()) {
                        if (nameIndex != -1) {
                            name = cursor.getString(nameIndex) ?: name
                        }
                        if (sizeIndex != -1) {
                            size = cursor.getLong(sizeIndex)
                        }
                    }
                }
            } catch (_: Exception) {}
        } else if (uri.scheme == "file") {
            name = uri.lastPathSegment ?: name
        }

        // 兜底：如果没拿到名称，尝试从 URI 路径提取
        if (name == "未知文档.txt" && uri.lastPathSegment != null) {
            name = uri.lastPathSegment!!
        }

        return Pair(name, size)
    }

    /**
     * 流式读取文本文件，返回所有行数据
     */
    fun readLines(
        context: Context,
        uri: Uri,
        charsetName: String
    ): List<String> {
        val lines = mutableListOf<String>()
        val charset = EncodingDetector.getCharset(charsetName)

        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            BufferedReader(InputStreamReader(inputStream, charset)).use { reader ->
                var line = reader.readLine()
                while (line != null) {
                    lines.add(line)
                    line = reader.readLine()
                }
            }
        }
        return lines
    }

    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "未知大小"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        return when {
            mb >= 1.0 -> String.format(Locale.getDefault(), "%.1f MB", mb)
            kb >= 1.0 -> String.format(Locale.getDefault(), "%.1f KB", kb)
            else -> "$bytes B"
        }
    }

    fun saveRecentFile(context: Context, recent: RecentFile) {
        val list = getRecentFiles(context).toMutableList()
        // 去重
        list.removeAll { it.uriString == recent.uriString }
        list.add(0, recent)
        if (list.size > MAX_RECENTS) {
            list.removeAt(list.lastIndex)
        }

        val jsonArray = JSONArray()
        for (item in list) {
            val obj = JSONObject().apply {
                put("uriString", item.uriString)
                put("displayName", item.displayName)
                put("fileSize", item.fileSize)
                put("lastOpenedTimestamp", item.lastOpenedTimestamp)
            }
            jsonArray.put(obj)
        }

        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_RECENTS, jsonArray.toString())
            .apply()
    }

    fun getRecentFiles(context: Context): List<RecentFile> {
        val jsonStr = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_RECENTS, null) ?: return emptyList()

        return try {
            val array = JSONArray(jsonStr)
            val result = mutableListOf<RecentFile>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                result.add(
                    RecentFile(
                        uriString = obj.getString("uriString"),
                        displayName = obj.getString("displayName"),
                        fileSize = obj.optLong("fileSize", 0L),
                        lastOpenedTimestamp = obj.optLong("lastOpenedTimestamp", 0L)
                    )
                )
            }
            result
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun removeRecentFile(context: Context, uriString: String) {
        val list = getRecentFiles(context).toMutableList()
        list.removeAll { it.uriString == uriString }

        val jsonArray = JSONArray()
        for (item in list) {
            val obj = JSONObject().apply {
                put("uriString", item.uriString)
                put("displayName", item.displayName)
                put("fileSize", item.fileSize)
                put("lastOpenedTimestamp", item.lastOpenedTimestamp)
            }
            jsonArray.put(obj)
        }

        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_RECENTS, jsonArray.toString())
            .apply()
    }

    fun clearRecentFiles(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_RECENTS)
            .apply()
    }
}
