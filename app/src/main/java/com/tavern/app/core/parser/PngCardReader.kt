package com.tavern.app.core.parser

import java.io.InputStream
import java.util.Base64
import java.util.zip.Inflater

/**
 * PNG 角色卡读取器
 *
 * 手动解析 PNG 文件结构，提取 tEXt / iTXt chunk 中内嵌的角色卡 JSON。
 * SillyTavern 角色卡将 JSON 数据以 base64 编码存储在 tEXt chunk 的 "chara" 字段中。
 *
 * 手动解析而非依赖 ImageIO，因为 Android 平台无完整 ImageIO。
 *
 * PNG 文件结构：
 * - 8 字节签名：89 50 4E 47 0D 0A 1A 0A
 * - 多个 chunk：4字节长度 + 4字节类型 + 数据 + 4字节CRC
 */
object PngCardReader {

    private val PNG_SIGNATURE = byteArrayOf(
        0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    )

    /** 角色卡在 tEXt chunk 中的 keyword */
    private const val CHARA_KEYWORD = "chara"

    /**
     * 从 PNG 输入流提取角色卡 JSON
     *
     * @return JSON 字符串，未找到返回 null
     */
    fun extractCardJson(inputStream: InputStream): String? {
        val data = inputStream.readBytes()
        return extractCardJson(data)
    }

    /**
     * 从 PNG 字节数组提取角色卡 JSON
     */
    fun extractCardJson(data: ByteArray): String? {
        if (!isPng(data)) return null

        var offset = 8 // 跳过签名
        while (offset + 8 <= data.size) {
            // 读取 chunk 长度（4 字节 big-endian）
            val length = readInt(data, offset)
            offset += 4

            // 读取 chunk 类型（4 字节 ASCII）
            val type = String(data, offset, 4, Charsets.US_ASCII)
            offset += 4

            // 数据长度越界保护
            if (offset + length > data.size) break

            // 读取 chunk 数据
            val chunkData = data.copyOfRange(offset, offset + length)

            // 处理 tEXt 和 iTXt chunk
            if (type == "tEXt") {
                val json = parseTextChunk(chunkData)
                if (json != null) return json
            } else if (type == "iTXt") {
                val json = parseITextChunk(chunkData)
                if (json != null) return json
            }

            // 移动到下一个 chunk（跳过 CRC）
            offset += length + 4

            // IEND chunk 结束
            if (type == "IEND") break
        }
        return null
    }

    /**
     * 解析 tEXt chunk：keyword\0text
     */
    private fun parseTextChunk(data: ByteArray): String? {
        val nullIndex = data.indexOf(0)
        if (nullIndex <= 0) return null
        val keyword = String(data, 0, nullIndex, Charsets.UTF_8)
        if (keyword != CHARA_KEYWORD) return null
        val value = String(data, nullIndex + 1, data.size - nullIndex - 1, Charsets.UTF_8)
        return decodeBase64(value)
    }

    /**
     * 解析 iTXt chunk（国际文本，可能 zlib 压缩）
     */
    private fun parseITextChunk(data: ByteArray): String? {
        var pos = 0
        // keyword\0
        val kwEnd = indexOfByte(data, 0, pos)
        if (kwEnd <= 0) return null
        val keyword = String(data, pos, kwEnd - pos, Charsets.UTF_8)
        pos = kwEnd + 1

        // 需要至少 2 字节：compression flag + compression method
        if (pos + 2 > data.size) return null
        val compressionFlag = data[pos]
        val compressionMethod = data[pos + 1]
        pos += 2

        // language tag\0
        val langEnd = indexOfByte(data, 0, pos)
        if (langEnd < 0) return null
        pos = langEnd + 1

        // translated keyword\0
        val transEnd = indexOfByte(data, 0, pos)
        if (transEnd < 0) return null
        pos = transEnd + 1

        // 剩余为文本数据
        val textBytes = data.copyOfRange(pos, data.size)
        val text: String = if (compressionFlag.toInt() == 1 && compressionMethod.toInt() == 0) {
            inflate(textBytes) ?: return null
        } else {
            String(textBytes, Charsets.UTF_8)
        }

        if (keyword != CHARA_KEYWORD) return null
        return decodeBase64(text)
    }

    /**
     * base64 解码
     *
     * - 忽略空白/换行（部分工具生成的卡片会把 base64 折行，标准解码器会直接抛错）
     * - 含 `-`/`_` 时按 URL-safe 变体解码
     */
    private fun decodeBase64(value: String): String? {
        val cleaned = value.filterNot { it.isWhitespace() }
        if (cleaned.isEmpty()) return null
        val decoder = if (cleaned.contains('-') || cleaned.contains('_')) {
            Base64.getUrlDecoder()
        } else {
            Base64.getMimeDecoder()
        }
        return try {
            String(decoder.decode(cleaned), Charsets.UTF_8)
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    /**
     * zlib 解压
     */
    private fun inflate(data: ByteArray): String? {
        return try {
            val inflater = Inflater()
            inflater.setInput(data)
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(4096)
            while (!inflater.finished()) {
                val count = inflater.inflate(buffer)
                if (count == 0) break
                output.write(buffer, 0, count)
            }
            inflater.end()
            String(output.toByteArray(), Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 检查是否为 PNG 文件
     */
    fun isPng(data: ByteArray): Boolean {
        if (data.size < 8) return false
        for (i in 0 until 8) {
            if (data[i] != PNG_SIGNATURE[i]) return false
        }
        return true
    }

    /**
     * 读取 4 字节 big-endian 整数
     */
    private fun readInt(data: ByteArray, offset: Int): Int {
        return ((data[offset].toInt() and 0xFF) shl 24) or
                ((data[offset + 1].toInt() and 0xFF) shl 16) or
                ((data[offset + 2].toInt() and 0xFF) shl 8) or
                (data[offset + 3].toInt() and 0xFF)
    }

    /**
     * 在字节数组中从指定位置查找目标字节
     */
    private fun indexOfByte(data: ByteArray, target: Int, fromIndex: Int): Int {
        for (i in fromIndex until data.size) {
            if (data[i].toInt() == target) return i
        }
        return -1
    }
}
