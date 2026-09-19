package com.antireader.utils

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

object EncodingDetector {

    val SUPPORTED_CHARSETS = listOf(
        "UTF-8",
        "GB18030",
        "GBK",
        "UTF-16LE",
        "UTF-16BE",
        "Big5",
        "ISO-8859-1"
    )

    /**
     * 智能探测输入流编码：
     * 1. 优先检查标准 BOM 标头；
     * 2. 读取前 64KB 字节，通过严格 UTF-8 解码校验；
     * 3. 若 UTF-8 校验失败，默认推荐 GB18030（完全向下兼容 GBK 和 GB2312，是绝大多数中文 txt 乱码的根源）。
     */
    fun detectEncoding(inputStream: InputStream): String {
        val sampleSize = 64 * 1024
        val buffer = ByteArray(sampleSize)
        val bytesRead = inputStream.read(buffer)
        if (bytesRead <= 0) return "UTF-8"

        // 1. 检查常见 BOM
        if (bytesRead >= 3 &&
            buffer[0] == 0xEF.toByte() &&
            buffer[1] == 0xBB.toByte() &&
            buffer[2] == 0xBF.toByte()
        ) {
            return "UTF-8"
        }
        if (bytesRead >= 2) {
            if (buffer[0] == 0xFF.toByte() && buffer[1] == 0xFE.toByte()) {
                return "UTF-16LE"
            }
            if (buffer[0] == 0xFE.toByte() && buffer[1] == 0xFF.toByte()) {
                return "UTF-16BE"
            }
        }

        // 2. 严格校验 UTF-8
        if (isValidUtf8(buffer, 0, bytesRead)) {
            return "UTF-8"
        }

        // 3. 中文小说或 Windows 保存的默认 ANSI 文件一般是 GBK / GB18030
        return "GB18030"
    }

    private fun isValidUtf8(bytes: ByteArray, offset: Int, length: Int): Boolean {
        return try {
            val decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
            val byteBuffer = ByteBuffer.wrap(bytes, offset, length)
            decoder.decode(byteBuffer)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun getCharset(charsetName: String): Charset {
        return try {
            Charset.forName(charsetName)
        } catch (e: Exception) {
            StandardCharsets.UTF_8
        }
    }
}
