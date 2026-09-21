package com.antireader

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.antireader.model.ReaderSettings
import com.antireader.model.ReaderUiState
import com.antireader.model.RecentFile
import com.antireader.model.TextDocument
import com.antireader.utils.EncodingDetector
import com.antireader.utils.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream

class ReaderViewModel : ViewModel() {

    private val _uiState = MutableStateFlow<ReaderUiState>(ReaderUiState.Home())
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    private var currentSettings = ReaderSettings(
        fontSizeSp = 16f,
        showLineNumbers = true,
        isLineWrap = true
    )

    fun loadRecentFiles(context: Context) {
        val recents = FileUtils.getRecentFiles(context)
        val state = _uiState.value
        if (state is ReaderUiState.Home) {
            _uiState.value = ReaderUiState.Home(recentFiles = recents)
        }
    }

    /**
     * 点击「最近阅读」里的历史条目打开
     * 核心逻辑：优先使用保存在私有目录的本地副本，规避外部第三方 App 临时授权过期问题。
     */
    fun openRecentFile(context: Context, recent: RecentFile, forcedCharset: String? = null) {
        viewModelScope.launch {
            _uiState.value = ReaderUiState.Loading(fileName = recent.displayName)

            try {
                val (lines, detectedCharset, resolvedSize) = withContext(Dispatchers.IO) {
                    val cacheFile = recent.localCachePath?.let { File(it) }
                    val hasValidCache = cacheFile != null && cacheFile.exists() && cacheFile.length() > 0

                    if (hasValidCache) {
                        val file = cacheFile!!
                        val charset = forcedCharset ?: FileInputStream(file).use {
                            EncodingDetector.detectEncoding(it)
                        }
                        val loadedLines = FileUtils.readLinesFromFile(file, charset)
                        Triple(loadedLines, charset, file.length())
                    } else {
                        // 如果没有本地缓存（例如旧版本产生的数据），尝试读取原始 URI 并补充缓存
                        val originalUri = Uri.parse(recent.uriString)
                        val charset = forcedCharset ?: run {
                            val stream = FileUtils.openInputStream(context, originalUri)
                                ?: throw java.io.FileNotFoundException("无法打开输入流")
                            stream.use { EncodingDetector.detectEncoding(it) }
                        }
                        val loadedLines = FileUtils.readLines(context, originalUri, charset)
                        if (loadedLines.isEmpty()) {
                            FileUtils.openInputStream(context, originalUri)?.close()
                                ?: throw java.io.FileNotFoundException("文件为空或不存在")
                        }

                        // 补充缓存
                        val newCache = FileUtils.cacheUriLocally(context, originalUri, recent.displayName)
                        Triple(loadedLines, charset, newCache?.length() ?: recent.fileSize)
                    }
                }

                val doc = TextDocument(
                    uri = Uri.parse(recent.uriString),
                    displayName = recent.displayName,
                    fileSize = resolvedSize,
                    lines = lines,
                    charsetName = detectedCharset
                )

                // 刷新时间戳
                FileUtils.saveRecentFile(
                    context,
                    recent.copy(
                        fileSize = resolvedSize,
                        lastOpenedTimestamp = System.currentTimeMillis()
                    )
                )

                _uiState.value = ReaderUiState.Reading(
                    document = doc,
                    settings = currentSettings
                )
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(
                        context.applicationContext,
                        "文档不存在或已被移动",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
                val recents = FileUtils.getRecentFiles(context)
                _uiState.value = ReaderUiState.Home(recentFiles = recents)
            }
        }
    }

    /**
     * 通过外部 Intent（文件管理器打开/分享）或系统文件选择器打开文档
     * 打开时自动同步缓存到私有存储，确保持久可读。
     */
    fun openUri(
        context: Context,
        uri: Uri,
        forcedCharset: String? = null
    ) {
        viewModelScope.launch {
            val (name, size) = FileUtils.getFileNameAndSize(context, uri)
            _uiState.value = ReaderUiState.Loading(fileName = name)

            try {
                val (lines, detectedCharset, cachedFile) = withContext(Dispatchers.IO) {
                    // 立即将外部流缓存到私有空间
                    val localCache = FileUtils.cacheUriLocally(context, uri, name)

                    if (localCache != null && localCache.exists() && localCache.length() > 0) {
                        val charset = forcedCharset ?: FileInputStream(localCache).use {
                            EncodingDetector.detectEncoding(it)
                        }
                        val loadedLines = FileUtils.readLinesFromFile(localCache, charset)
                        Triple(loadedLines, charset, localCache)
                    } else {
                        // 兜底：若克隆失败，尝试直接从流中解析
                        val charset = forcedCharset ?: run {
                            val stream = FileUtils.openInputStream(context, uri)
                                ?: throw java.io.FileNotFoundException("无法打开输入流")
                            stream.use { EncodingDetector.detectEncoding(it) }
                        }
                        val loadedLines = FileUtils.readLines(context, uri, charset)
                        if (loadedLines.isEmpty()) {
                            FileUtils.openInputStream(context, uri)?.close()
                                ?: throw java.io.FileNotFoundException("文件为空或不存在")
                        }
                        Triple(loadedLines, charset, null)
                    }
                }

                val finalSize = cachedFile?.length() ?: size
                val doc = TextDocument(
                    uri = uri,
                    displayName = name,
                    fileSize = finalSize,
                    lines = lines,
                    charsetName = detectedCharset
                )

                // 立即持久化记录到最近阅读列表，附带本地安全备份路径
                FileUtils.saveRecentFile(
                    context,
                    RecentFile(
                        uriString = uri.toString(),
                        localCachePath = cachedFile?.absolutePath,
                        displayName = name,
                        fileSize = finalSize,
                        lastOpenedTimestamp = System.currentTimeMillis()
                    )
                )

                _uiState.value = ReaderUiState.Reading(
                    document = doc,
                    settings = currentSettings
                )
            } catch (e: Exception) {
                _uiState.value = ReaderUiState.Error(
                    message = "打开文件失败：${e.localizedMessage ?: "未知错误"}"
                )
            }
        }
    }

    fun openPlainText(context: Context, title: String, text: String) {
        viewModelScope.launch {
            val lines = text.lines()
            val cachedFile = withContext(Dispatchers.IO) {
                FileUtils.cacheTextLocally(context, title, text)
            }
            val size = cachedFile?.length() ?: text.toByteArray().size.toLong()
            val uriString = cachedFile?.let { Uri.fromFile(it).toString() } ?: "memory://$title"

            val doc = TextDocument(
                uri = null,
                displayName = title,
                fileSize = size,
                lines = lines,
                charsetName = "UTF-8"
            )

            FileUtils.saveRecentFile(
                context,
                RecentFile(
                    uriString = uriString,
                    localCachePath = cachedFile?.absolutePath,
                    displayName = title,
                    fileSize = size,
                    lastOpenedTimestamp = System.currentTimeMillis()
                )
            )

            _uiState.value = ReaderUiState.Reading(
                document = doc,
                settings = currentSettings
            )
        }
    }

    fun changeCharset(context: Context, newCharset: String) {
        val state = _uiState.value
        if (state is ReaderUiState.Reading && state.document.uri != null) {
            openUri(context, state.document.uri, forcedCharset = newCharset)
        }
    }

    fun updateFontSize(newSizeSp: Float) {
        currentSettings = currentSettings.copy(fontSizeSp = newSizeSp)
        val state = _uiState.value
        if (state is ReaderUiState.Reading) {
            _uiState.value = state.copy(settings = currentSettings)
        }
    }

    fun toggleLineNumbers() {
        currentSettings = currentSettings.copy(showLineNumbers = !currentSettings.showLineNumbers)
        val state = _uiState.value
        if (state is ReaderUiState.Reading) {
            _uiState.value = state.copy(settings = currentSettings)
        }
    }

    fun toggleWrap() {
        currentSettings = currentSettings.copy(isLineWrap = !currentSettings.isLineWrap)
        val state = _uiState.value
        if (state is ReaderUiState.Reading) {
            _uiState.value = state.copy(settings = currentSettings)
        }
    }

    fun closeDocument(context: Context) {
        val recents = FileUtils.getRecentFiles(context)
        _uiState.value = ReaderUiState.Home(recentFiles = recents)
    }

    fun removeRecentFile(context: Context, uriString: String) {
        FileUtils.removeRecentFile(context, uriString)
        val recents = FileUtils.getRecentFiles(context)
        if (_uiState.value is ReaderUiState.Home) {
            _uiState.value = ReaderUiState.Home(recentFiles = recents)
        }
    }

    fun clearRecents(context: Context) {
        FileUtils.clearRecentFiles(context)
        if (_uiState.value is ReaderUiState.Home) {
            _uiState.value = ReaderUiState.Home(recentFiles = emptyList())
        }
    }
}
