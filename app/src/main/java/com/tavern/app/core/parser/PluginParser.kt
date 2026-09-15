package com.tavern.app.core.parser

import com.tavern.app.core.model.Plugin
import com.tavern.app.core.model.PluginRule
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 插件定义（可序列化的导入格式，不含自动生成的 id）
 */
@Serializable
data class PluginDef(
    val name: String = "",
    val version: String = "1.0",
    val description: String = "",
    val author: String = "",
    val rules: List<PluginRule> = emptyList()
)

/**
 * 插件解析器
 *
 * 支持两种导入格式：
 * 1. SillyTavern 扩展（.js）：解析 header 注释元数据
 *    ```
 *    // name: 翻译
 *    // description: 自动翻译回复
 *    // author: xxx
 *    // version: 1.0
 *    ```
 * 2. 内置 JSON 格式：完整插件定义（含规则）
 */
object PluginParser {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * 解析 SillyTavern 扩展脚本的 header 注释（仅提取元数据）
     */
    fun parseJs(text: String): Plugin {
        var name = ""
        var description = ""
        var author = ""
        var version = "1.0"

        text.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            if (!line.startsWith("//")) return@forEach
            val content = line.removePrefix("//").trim()
            when {
                content.startsWith("name:") ->
                    name = content.removePrefix("name:").trim()
                content.startsWith("description:") ->
                    description = content.removePrefix("description:").trim()
                content.startsWith("author:") ->
                    author = content.removePrefix("author:").trim()
                content.startsWith("version:") ->
                    version = content.removePrefix("version:").trim()
            }
        }

        return Plugin(
            name = name.ifBlank { "未命名插件" },
            description = description,
            author = author,
            version = version.ifBlank { "1.0" }
        )
    }

    /**
     * 解析内置 JSON 格式的完整插件定义
     */
    fun parseJson(text: String): Plugin? {
        return try {
            val def = json.decodeFromString<PluginDef>(text)
            Plugin(
                name = def.name,
                version = def.version,
                description = def.description,
                author = def.author,
                rules = def.rules
            )
        } catch (e: Exception) {
            null
        }
    }
}
