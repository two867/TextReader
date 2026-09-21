package com.antireader.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antireader.model.ReaderSettings
import com.antireader.model.TextDocument
import com.antireader.ui.components.EncodingDialog
import com.antireader.ui.components.FontSizeDialog
import com.antireader.ui.components.JumpLineDialog
import com.antireader.ui.theme.*
import com.antireader.utils.FileUtils
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    document: TextDocument,
    settings: ReaderSettings,
    initialScrollLine: Int = 0,
    onBackClick: (lastLineIndex: Int) -> Unit,
    onProgressChanged: (lineIndex: Int, totalLines: Int) -> Unit,
    onToggleLineNumbers: () -> Unit,
    onToggleWrap: () -> Unit,
    onFontSizeChanged: (Float) -> Unit,
    onCharsetChanged: (String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val verticalListState = rememberLazyListState(
        initialFirstVisibleItemIndex = initialScrollLine.coerceIn(0, maxOf(0, document.totalLines - 1))
    )
    val horizontalScrollState = rememberScrollState()

    // 自动恢复上次阅读进度提示
    LaunchedEffect(initialScrollLine) {
        if (initialScrollLine > 0) {
            Toast.makeText(context, "已恢复至上次阅读位置（第 ${initialScrollLine + 1} 行）", Toast.LENGTH_SHORT).show()
        }
    }

    // 监听滚动位置，实时/防抖保存当前阅读进度
    LaunchedEffect(verticalListState) {
        snapshotFlow { verticalListState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { lineIndex ->
                onProgressChanged(lineIndex, document.totalLines)
            }
    }

    var showFontSizeDialog by remember { mutableStateOf(false) }
    var showEncodingDialog by remember { mutableStateOf(false) }
    var showJumpDialog by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }

    // 计算行号所需占用的字符宽度
    val digits = remember(document.totalLines) {
        maxOf(2, document.totalLines.toString().length)
    }
    val gutterWidth = remember(digits, settings.fontSizeSp) {
        (digits * (settings.fontSizeSp * 0.65f) + 20).dp
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = document.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${document.totalLines} 行 | ${FileUtils.formatFileSize(document.fileSize)} | ${document.charsetName}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { onBackClick(verticalListState.firstVisibleItemIndex) }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                actions = {
                    // 1. 切换行号按钮
                    IconButton(
                        onClick = onToggleLineNumbers,
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = if (settings.showLineNumbers) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                        )
                    ) {
                        Icon(
                            Icons.Default.FormatListNumbered,
                            contentDescription = "切换行号",
                            tint = if (settings.showLineNumbers) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // 2. 自动换行 / 水平不换行切换按钮
                    IconButton(
                        onClick = onToggleWrap,
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = if (settings.isLineWrap) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                        )
                    ) {
                        Icon(
                            imageVector = if (settings.isLineWrap) Icons.Default.WrapText else Icons.Default.TableRows,
                            contentDescription = if (settings.isLineWrap) "当前自动换行" else "当前单行横向滚动",
                            tint = if (settings.isLineWrap) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // 3. 字体大小按钮
                    IconButton(onClick = { showFontSizeDialog = true }) {
                        Icon(
                            Icons.Default.FormatSize,
                            contentDescription = "字体大小"
                        )
                    }

                    // 4. 更多菜单（跳转、编码切换、全选复制）
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "更多选项")
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("跳转到指定行...") },
                            leadingIcon = { Icon(Icons.Default.Navigation, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                showJumpDialog = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("切换文件编码 (${document.charsetName})") },
                            leadingIcon = { Icon(Icons.Default.Translate, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                showEncodingDialog = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("复制全文内容") },
                            leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("TextReader", document.lines.joinToString("\n"))
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "已将全文复制到剪贴板", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (settings.isLineWrap) {
                // 自动换行模式：普通垂直滚动 LazyColumn
                LazyColumn(
                    state = verticalListState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 32.dp)
                ) {
                    itemsIndexed(document.lines) { index, line ->
                        LineRow(
                            lineNumber = index + 1,
                            text = line,
                            settings = settings,
                            gutterWidth = gutterWidth
                        )
                    }
                }
            } else {
                // 单行横向滚动模式：外层包裹水平滚动
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .horizontalScroll(horizontalScrollState)
                ) {
                    LazyColumn(
                        state = verticalListState,
                        modifier = Modifier
                            .fillMaxHeight()
                            .wrapContentWidth(align = Alignment.Start),
                        contentPadding = PaddingValues(bottom = 32.dp, end = 32.dp)
                    ) {
                        itemsIndexed(document.lines) { index, line ->
                            LineRow(
                                lineNumber = index + 1,
                                text = line,
                                settings = settings,
                                gutterWidth = gutterWidth
                            )
                        }
                    }
                }
            }
        }
    }

    // 字体大小调节弹窗
    if (showFontSizeDialog) {
        FontSizeDialog(
            currentSizeSp = settings.fontSizeSp,
            onSizeChanged = onFontSizeChanged,
            onDismissRequest = { showFontSizeDialog = false }
        )
    }

    // 编码切换弹窗
    if (showEncodingDialog) {
        EncodingDialog(
            currentCharset = document.charsetName,
            onCharsetSelected = onCharsetChanged,
            onDismissRequest = { showEncodingDialog = false }
        )
    }

    // 快速跳转指定行弹窗
    if (showJumpDialog) {
        JumpLineDialog(
            totalLines = document.totalLines,
            onJump = { lineIndex ->
                coroutineScope.launch {
                    verticalListState.scrollToItem(lineIndex)
                }
            },
            onDismissRequest = { showJumpDialog = false }
        )
    }
}

@Composable
private fun LineRow(
    lineNumber: Int,
    text: String,
    settings: ReaderSettings,
    gutterWidth: androidx.compose.ui.unit.Dp
) {
    val lineHeight = (settings.fontSizeSp * 1.55f).sp

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp),
        verticalAlignment = Alignment.Top
    ) {
        // 行号区域
        if (settings.showLineNumbers) {
            Box(
                modifier = Modifier
                    .width(gutterWidth)
                    .padding(end = 8.dp),
                contentAlignment = Alignment.TopEnd
            ) {
                Text(
                    text = lineNumber.toString(),
                    fontFamily = FontFamily.Monospace,
                    fontSize = (settings.fontSizeSp * 0.8f).sp,
                    lineHeight = lineHeight,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // 行号与文本之间的竖向浅色分隔线
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height((settings.fontSizeSp * 1.5f).dp)
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            )
            Spacer(modifier = Modifier.width(8.dp))
        } else {
            Spacer(modifier = Modifier.width(12.dp))
        }

        // 文本内容区域
        Text(
            text = text.ifEmpty { " " },
            fontSize = settings.fontSizeSp.sp,
            lineHeight = lineHeight,
            softWrap = settings.isLineWrap,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = if (settings.isLineWrap) Modifier.weight(1f).padding(end = 12.dp) else Modifier
        )
    }
}
