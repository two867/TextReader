package com.antireader.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
fun JumpLineDialog(
    totalLines: Int,
    onJump: (Int) -> Unit,
    onDismissRequest: () -> Unit
) {
    var inputText by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(text = "快速跳转到指定行")
        },
        text = {
            Column {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = {
                        inputText = it.filter { char -> char.isDigit() }
                        isError = false
                    },
                    label = { Text("行号 (1 ~ $totalLines)") },
                    singleLine = true,
                    isError = isError,
                    supportingText = {
                        if (isError) {
                            Text("请输入 1 到 $totalLines 之间的数字")
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val line = inputText.toIntOrNull()
                    if (line != null && line in 1..totalLines) {
                        onJump(line - 1) // 转为 0-based 索引
                        onDismissRequest()
                    } else {
                        isError = true
                    }
                }
            ) {
                Text("跳转")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text("取消")
            }
        }
    )
}
