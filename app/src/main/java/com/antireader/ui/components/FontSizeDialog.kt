package com.antireader.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

@Composable
fun FontSizeDialog(
    currentSizeSp: Float,
    onSizeChanged: (Float) -> Unit,
    onDismissRequest: () -> Unit
) {
    var sliderValue by remember { mutableFloatStateOf(currentSizeSp) }
    val presets = listOf(12f, 14f, 16f, 18f, 20f, 24f)

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(
                text = "调整字体大小",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 当前字号数值与快捷加减按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalIconButton(
                        onClick = {
                            if (sliderValue > 10f) {
                                sliderValue = (sliderValue - 1f).coerceAtLeast(10f)
                                onSizeChanged(sliderValue)
                            }
                        }
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "减小")
                    }

                    Text(
                        text = "${sliderValue.roundToInt()} sp",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    FilledTonalIconButton(
                        onClick = {
                            if (sliderValue < 36f) {
                                sliderValue = (sliderValue + 1f).coerceAtMost(36f)
                                onSizeChanged(sliderValue)
                            }
                        }
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "增大")
                    }
                }

                // 连续滑动条
                Slider(
                    value = sliderValue,
                    onValueChange = {
                        sliderValue = it
                        onSizeChanged(it)
                    },
                    valueRange = 10f..36f,
                    steps = 25
                )

                // 常用字号预设标签
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    presets.forEach { preset ->
                        FilterChip(
                            selected = sliderValue.roundToInt() == preset.roundToInt(),
                            onClick = {
                                sliderValue = preset
                                onSizeChanged(preset)
                            },
                            label = { Text("${preset.toInt()}") }
                        )
                    }
                }

                // 预览文字框
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "文本阅读预览：春江水暖鸭先知",
                        fontSize = sliderValue.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text("完成")
            }
        }
    )
}
