package com.antireader.model

sealed interface ReaderUiState {
    // 初始状态 / 首页：未打开文档，展示最近历史列表与引导
    data class Home(
        val recentFiles: List<RecentFile> = emptyList()
    ) : ReaderUiState

    // 正在加载中
    data class Loading(
        val fileName: String = "加载中..."
    ) : ReaderUiState

    // 阅读中文档
    data class Reading(
        val document: TextDocument,
        val settings: ReaderSettings = ReaderSettings(),
        val currentScrollLine: Int = 0
    ) : ReaderUiState

    // 加载错误
    data class Error(
        val message: String
    ) : ReaderUiState
}
