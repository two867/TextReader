package com.antireader

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.activity.compose.BackHandler
import androidx.compose.ui.unit.dp
import com.antireader.model.ReaderUiState
import com.antireader.ui.HomeScreen
import com.antireader.ui.ReaderScreen
import com.antireader.ui.theme.TextReaderTheme

class MainActivity : ComponentActivity() {

    private val viewModel: ReaderViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 处理打开 App 时的 Intent
        handleIntent(intent)

        setContent {
            TextReaderTheme {
                val context = LocalContext.current
                val uiState by viewModel.uiState.collectAsState()

                // 拦截手机系统返回手势/物理返回键：如果在阅读页面，返回到首页并刷新
                BackHandler(enabled = uiState is ReaderUiState.Reading) {
                    viewModel.closeDocument(context)
                }

                // 文件选择器（从 HomeScreen 点击打开本地文件时唤起）
                val filePickerLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument()
                ) { uri: Uri? ->
                    if (uri != null) {
                        try {
                            // 保持长久读取权限（如果支持）
                            val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                            contentResolver.takePersistableUriPermission(uri, takeFlags)
                        } catch (_: Exception) {}
                        viewModel.openUri(context, uri)
                    }
                }

                LaunchedEffect(Unit) {
                    viewModel.loadRecentFiles(context)
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    when (val state = uiState) {
                        is ReaderUiState.Home -> {
                            HomeScreen(
                                recentFiles = state.recentFiles,
                                onOpenFileClick = {
                                    filePickerLauncher.launch(
                                        arrayOf("text/plain", "text/*", "*/*")
                                    )
                                },
                                onRecentFileClick = { recent ->
                                    val uri = Uri.parse(recent.uriString)
                                    viewModel.openUri(context, uri, isFromRecent = true)
                                },
                                onDeleteRecentClick = { recent ->
                                    viewModel.removeRecentFile(context, recent.uriString)
                                },
                                onClearRecentsClick = {
                                    viewModel.clearRecents(context)
                                }
                            )
                        }

                        is ReaderUiState.Loading -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.background),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator()
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = "正在载入「${state.fileName}」...",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }

                        is ReaderUiState.Reading -> {
                            ReaderScreen(
                                document = state.document,
                                settings = state.settings,
                                onBackClick = {
                                    viewModel.closeDocument(context)
                                },
                                onToggleLineNumbers = {
                                    viewModel.toggleLineNumbers()
                                },
                                onToggleWrap = {
                                    viewModel.toggleWrap()
                                },
                                onFontSizeChanged = { newSize ->
                                    viewModel.updateFontSize(newSize)
                                },
                                onCharsetChanged = { newCharset ->
                                    viewModel.changeCharset(context, newCharset)
                                }
                            )
                        }

                        is ReaderUiState.Error -> {
                            AlertDialog(
                                onDismissRequest = { viewModel.closeDocument(context) },
                                title = {
                                    Text(
                                        text = "读取失败",
                                        fontWeight = FontWeight.Bold
                                    )
                                },
                                text = { Text(text = state.message) },
                                confirmButton = {
                                    Button(onClick = { viewModel.closeDocument(context) }) {
                                        Text("返回首页")
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadRecentFiles(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return

        val action = intent.action
        val type = intent.type

        when (action) {
            Intent.ACTION_VIEW -> {
                val uri = intent.data
                if (uri != null) {
                    viewModel.openUri(this, uri)
                }
            }

            Intent.ACTION_SEND -> {
                // 处理分享文本或流文件
                if (intent.hasExtra(Intent.EXTRA_STREAM)) {
                    val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
                    }
                    if (uri != null) {
                        viewModel.openUri(this, uri)
                    }
                } else if (type == "text/plain") {
                    val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
                    if (!sharedText.isNullOrEmpty()) {
                        val title = intent.getStringExtra(Intent.EXTRA_SUBJECT) ?: "分享文本.txt"
                        viewModel.openPlainText(title, sharedText)
                    }
                }
            }
        }
    }
}
