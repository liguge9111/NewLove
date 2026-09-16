package com.tavern.app.core.parser

import com.tavern.app.core.model.CharacterCard
import java.io.File
import java.io.InputStream

/**
 * 角色卡加载器（统一入口）
 *
 * 根据文件类型自动路由：
 * - .png → 提取 tEXt chunk 内嵌 JSON 后解析
 * - .json → 直接解析 JSON
 *
 * 也支持直接传入 JSON 字符串。
 */
object CharacterCardLoader {

    /**
     * 从文件加载角色卡
     *
     * @param file 角色卡文件（.png 或 .json）
     * @return 解析结果，失败返回 null
     */
    fun loadFromFile(file: File): CharacterCard? {
        return try {
            val bytes = file.readBytes()
            loadFromBytes(bytes, file.extension)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 从字节数组加载角色卡（根据扩展名路由）
     */
    fun loadFromBytes(bytes: ByteArray, extension: String): CharacterCard? {
        return when (extension.lowercase()) {
            "png" -> {
                val json = PngCardReader.extractCardJson(bytes)
                json?.let { CharacterCardParser.parse(it) }
            }
            "json" -> {
                CharacterCardParser.parse(String(bytes, Charsets.UTF_8))
            }
            else -> {
                // 尝试作为 PNG，失败则尝试作为 JSON
                val json = PngCardReader.extractCardJson(bytes)
                if (json != null) CharacterCardParser.parse(json)
                else CharacterCardParser.parse(String(bytes, Charsets.UTF_8))
            }
        }
    }

    /**
     * 从输入流加载角色卡
     */
    fun loadFromStream(inputStream: InputStream, extension: String = ""): CharacterCard? {
        return try {
            loadFromBytes(inputStream.readBytes(), extension)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 从 JSON 字符串加载角色卡
     */
    fun loadFromJson(jsonString: String): CharacterCard? =
        CharacterCardParser.parse(jsonString)
}
