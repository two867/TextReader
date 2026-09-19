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

class ReaderViewModel : ViewModel() {

    private val _uiState = MutableStateFlow<ReaderUiState>(ReaderUiState.Home())
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    // 独立维护阅读偏好，跨文档保持
    private var currentSettings = ReaderSettings(
        fontSizeSp = 16f,
        showLineNumbers = true,
        isLineWrap = true
    )

    fun loadRecentFiles(context: Context) {
        val recents = FileUtils.getRecentFiles(context)
        if (_uiState.value is ReaderUiState.Home) {
            _uiState.value = ReaderUiState.Home(recentFiles = recents)
        }
    }

    fun openUri(
        context: Context,
        uri: Uri,
        isFromRecent: Boolean = false,
        forcedCharset: String? = null
    ) {
        viewModelScope.launch {
            val (name, size) = FileUtils.getFileNameAndSize(context, uri)
            _uiState.value = ReaderUiState.Loading(fileName = name)

            try {
                val (lines, detectedCharset) = withContext(Dispatchers.IO) {
                    val charset = forcedCharset ?: run {
                        val stream = context.contentResolver.openInputStream(uri)
                            ?: throw java.io.FileNotFoundException("无法打开输入流")
                        stream.use {
                            EncodingDetector.detectEncoding(it)
                        }
                    }

                    val loadedLines = FileUtils.readLines(context, uri, charset)
                    if (loadedLines.isEmpty()) {
                        // 确认是否能正常打开
                        context.contentResolver.openInputStream(uri)?.close()
                            ?: throw java.io.FileNotFoundException("文件为空或不存在")
                    }
                    Pair(loadedLines, charset)
                }

                val doc = TextDocument(
                    uri = uri,
                    displayName = name,
                    fileSize = size,
                    lines = lines,
                    charsetName = detectedCharset
                )

                // 立即同步记录到最近打开列表，确保立即返回也能立即刷新
                FileUtils.saveRecentFile(
                    context,
                    RecentFile(
                        uriString = uri.toString(),
                        displayName = name,
                        fileSize = size,
                        lastOpenedTimestamp = System.currentTimeMillis()
                    )
                )

                _uiState.value = ReaderUiState.Reading(
                    document = doc,
                    settings = currentSettings
                )
            } catch (e: Exception) {
                if (isFromRecent) {
                    // 如果是从历史记录点击打开失败（文件被移动或删除），通过 Toast 提示，不弹窗打扰
                    withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(
                            context.applicationContext,
                            "文档不存在或已被移动",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                    val recents = FileUtils.getRecentFiles(context)
                    _uiState.value = ReaderUiState.Home(recentFiles = recents)
                } else {
                    _uiState.value = ReaderUiState.Error(
                        message = "打开文件失败：${e.localizedMessage ?: "未知错误"}"
                    )
                }
            }
        }
    }

    fun removeRecentFile(context: Context, uriString: String) {
        FileUtils.removeRecentFile(context, uriString)
        val recents = FileUtils.getRecentFiles(context)
        if (_uiState.value is ReaderUiState.Home) {
            _uiState.value = ReaderUiState.Home(recentFiles = recents)
        }
    }

    fun openPlainText(title: String, text: String) {
        val lines = text.lines()
        val doc = TextDocument(
            uri = null,
            displayName = title,
            fileSize = text.toByteArray().size.toLong(),
            lines = lines,
            charsetName = "UTF-8"
        )
        _uiState.value = ReaderUiState.Reading(
            document = doc,
            settings = currentSettings
        )
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

    fun clearRecents(context: Context) {
        FileUtils.clearRecentFiles(context)
        if (_uiState.value is ReaderUiState.Home) {
            _uiState.value = ReaderUiState.Home(recentFiles = emptyList())
        }
    }
}
