package com.antireader.utils

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.antireader.model.RecentFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.util.Locale

object FileUtils {

    private const val PREFS_NAME = "antireader_prefs"
    private const val KEY_RECENTS = "recent_files"
    private const val MAX_RECENTS = 20

    fun getRecentsDir(context: Context): File {
        val dir = File(context.filesDir, "recents")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    /**
     * 将外部 ContentProvider 的 URI（如微信、QQ、系统文件管理器分享的临时流）
     * 自动备份到应用内部私有存储目录中，彻底解决由于外部 Intent 临时 URI 授权过期
     * 导致下次在「最近阅读」点击提示「文档不存在或已被移动」的系统级权限问题。
     */
    fun cacheUriLocally(context: Context, uri: Uri, displayName: String): File? {
        // 如果已经是内部私有文件，直接返回
        if (uri.scheme == "file" && uri.path?.startsWith(context.filesDir.path) == true) {
            val file = File(uri.path!!)
            if (file.exists()) return file
        }

        val recentsDir = getRecentsDir(context)
        val safeName = displayName.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        val hashPrefix = (uri.toString().hashCode().toLong() and 0xFFFFFFFFL).toString(16)
        val targetFile = File(recentsDir, "${hashPrefix}_$safeName")

        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                targetFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            if (targetFile.exists() && targetFile.length() > 0) {
                targetFile
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    fun cacheTextLocally(context: Context, title: String, text: String): File? {
        val recentsDir = getRecentsDir(context)
        val safeName = title.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        val hashPrefix = (title.hashCode().toLong() and 0xFFFFFFFFL).toString(16)
        val targetFile = File(recentsDir, "${hashPrefix}_$safeName")

        return try {
            targetFile.writeText(text, Charsets.UTF_8)
            targetFile
        } catch (e: Exception) {
            null
        }
    }

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
            try {
                uri.path?.let {
                    val file = File(it)
                    if (file.exists()) {
                        size = file.length()
                    }
                }
            } catch (_: Exception) {}
        }

        // 兜底：如果没拿到名称，尝试从 URI 路径提取
        if (name == "未知文档.txt" && uri.lastPathSegment != null) {
            name = uri.lastPathSegment!!
        }

        return Pair(name, size)
    }

    /**
     * 打开输入流，兼顾 file:// 与 content://
     */
    fun openInputStream(context: Context, uri: Uri): InputStream? {
        return if (uri.scheme == "file") {
            uri.path?.let { FileInputStream(File(it)) }
        } else {
            context.contentResolver.openInputStream(uri)
        }
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

        val stream = openInputStream(context, uri) ?: return emptyList()
        stream.use { inputStream ->
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

    fun readLinesFromFile(
        file: File,
        charsetName: String
    ): List<String> {
        val lines = mutableListOf<String>()
        val charset = EncodingDetector.getCharset(charsetName)

        FileInputStream(file).use { inputStream ->
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
        // 去重（根据原始 uriString 或 localCachePath）
        list.removeAll {
            it.uriString == recent.uriString ||
                    (it.localCachePath != null && it.localCachePath == recent.localCachePath)
        }
        list.add(0, recent)

        // 超出最大历史数量时，物理删除被挤出的本地缓存文件
        while (list.size > MAX_RECENTS) {
            val evicted = list.removeAt(list.lastIndex)
            evicted.localCachePath?.let { path ->
                try {
                    File(path).delete()
                } catch (_: Exception) {}
            }
        }

        val jsonArray = JSONArray()
        for (item in list) {
            val obj = JSONObject().apply {
                put("uriString", item.uriString)
                put("localCachePath", item.localCachePath ?: "")
                put("displayName", item.displayName)
                put("fileSize", item.fileSize)
                put("lastOpenedTimestamp", item.lastOpenedTimestamp)
                put("lastReadIndex", item.lastReadIndex)
                put("totalLines", item.totalLines)
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
                val localPath = obj.optString("localCachePath", "").takeIf { it.isNotEmpty() }
                result.add(
                    RecentFile(
                        uriString = obj.getString("uriString"),
                        localCachePath = localPath,
                        displayName = obj.getString("displayName"),
                        fileSize = obj.optLong("fileSize", 0L),
                        lastOpenedTimestamp = obj.optLong("lastOpenedTimestamp", 0L),
                        lastReadIndex = obj.optInt("lastReadIndex", 0),
                        totalLines = obj.optInt("totalLines", 0)
                    )
                )
            }
            result
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * 更新指定文档的阅读进度
     */
    fun updateReadingProgress(
        context: Context,
        uriString: String,
        lineIndex: Int,
        totalLines: Int
    ) {
        val list = getRecentFiles(context).toMutableList()
        val index = list.indexOfFirst { it.uriString == uriString }
        if (index != -1) {
            val current = list[index]
            list[index] = current.copy(
                lastReadIndex = lineIndex,
                totalLines = totalLines,
                lastOpenedTimestamp = System.currentTimeMillis()
            )
            val jsonArray = JSONArray()
            for (item in list) {
                val obj = JSONObject().apply {
                    put("uriString", item.uriString)
                    put("localCachePath", item.localCachePath ?: "")
                    put("displayName", item.displayName)
                    put("fileSize", item.fileSize)
                    put("lastOpenedTimestamp", item.lastOpenedTimestamp)
                    put("lastReadIndex", item.lastReadIndex)
                    put("totalLines", item.totalLines)
                }
                jsonArray.put(obj)
            }
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_RECENTS, jsonArray.toString())
                .apply()
        }
    }

    /**
     * 将 Android 各种 URI (content://, file://) 转换为直观、用户友好的手机文件存储路径
     */
    fun formatReadablePath(uriString: String, localCachePath: String?): String {
        try {
            val uri = Uri.parse(uriString)
            if (uri.scheme == "file") {
                return uri.path ?: uriString
            }
            if (uri.scheme == "content") {
                val decodedUri = java.net.URLDecoder.decode(uriString, "UTF-8")
                // 常见系统 SAF 路径：如 document/primary:Download/book.txt
                if (decodedUri.contains("primary:")) {
                    val subPath = decodedUri.substringAfter("primary:")
                    return "/storage/emulated/0/$subPath"
                }
                // 第三方文件管理器常见格式：如 /external_files/Download/book.txt
                if (decodedUri.contains("/external_files/")) {
                    val subPath = decodedUri.substringAfter("/external_files/")
                    return "/storage/emulated/0/$subPath"
                }
                if (decodedUri.contains("/root/storage/emulated/0/")) {
                    return "/storage/emulated/0/" + decodedUri.substringAfter("/root/storage/emulated/0/")
                }
                val path = uri.path
                if (!path.isNullOrEmpty() && path.startsWith("/storage/")) {
                    return path
                }
                return decodedUri
            }
        } catch (_: Exception) {}
        return uriString
    }

    fun removeRecentFile(context: Context, uriString: String) {
        val list = getRecentFiles(context).toMutableList()
        val removed = list.filter { it.uriString == uriString }
        list.removeAll { it.uriString == uriString }

        // 清理被删除项对应的私有备份文件
        for (item in removed) {
            item.localCachePath?.let { path ->
                try {
                    File(path).delete()
                } catch (_: Exception) {}
            }
        }

        val jsonArray = JSONArray()
        for (item in list) {
            val obj = JSONObject().apply {
                put("uriString", item.uriString)
                put("localCachePath", item.localCachePath ?: "")
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
        // 清除所有缓存文件
        try {
            getRecentsDir(context).listFiles()?.forEach { it.delete() }
        } catch (_: Exception) {}

        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_RECENTS)
            .apply()
    }
}
