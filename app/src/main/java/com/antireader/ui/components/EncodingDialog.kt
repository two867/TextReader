package com.antireader.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.antireader.utils.EncodingDetector

@Composable
fun EncodingDialog(
    currentCharset: String,
    onCharsetSelected: (String) -> Unit,
    onDismissRequest: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Column {
                Text(
                    text = "选择文本编码",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "提示：中文内容若乱码，请选择 GB18030 或 GBK",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(EncodingDetector.SUPPORTED_CHARSETS) { charset ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onCharsetSelected(charset)
                                onDismissRequest()
                            }
                            .padding(vertical = 12.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = charset.equals(currentCharset, ignoreCase = true),
                            onClick = {
                                onCharsetSelected(charset)
                                onDismissRequest()
                            }
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = charset,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (charset.equals(currentCharset, ignoreCase = true)) FontWeight.Bold else FontWeight.Normal
                            )
                            val note = when (charset) {
                                "GB18030" -> "兼容 GBK、GB2312（国内小说推荐）"
                                "UTF-8" -> "国际通用标准编码"
                                "Big5" -> "繁体中文编码"
                                "UTF-16LE" -> "Windows 宽字符/Unicode"
                                else -> null
                            }
                            if (note != null) {
                                Text(
                                    text = note,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text("取消")
            }
        }
    )
}
