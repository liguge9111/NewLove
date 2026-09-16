package com.tavern.app.core.parser

import com.tavern.app.core.model.CharacterCard
import com.tavern.app.core.model.PluginAction
import com.tavern.app.core.model.PluginRule
import com.tavern.app.core.model.PluginTrigger
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 角色卡 regex_scripts 解析器
 *
 * 将酒馆角色卡 extensions.regex_scripts（JS 风格正则脚本）
 * 转换为本地插件规则（REPLACE）。
 *
 * 支持的脚本字段：
 * - scriptName / name：名称
 * - findRegex："/pattern/flags" 形式（JS），flags 支持 g/m/i
 * - replaceString：替换文本（$1 → Kotlin $1）
 * - target：user_input → 发送前；其余/缺省 → 回复后处理
 * - disabled：跳过
 */
object RegexScriptParser {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * 从角色卡解析 regex_scripts，转换为插件规则列表（无脚本返回空）
     */
    fun parseRules(card: CharacterCard): List<PluginRule> {
        val extensions = card.extensions ?: return emptyList()
        val scripts = extensions["regex_scripts"] as? JsonArray ?: return emptyList()

        return scripts.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            parseRule(obj)
        }
    }

    /** 也支持直接从 rawJson 字符串解析（导入链路用） */
    fun parseRulesFromExtensionsJson(extensionsJson: String): List<PluginRule> {
        return try {
            val root = json.parseToJsonElement(extensionsJson).jsonObject
            val scripts = root["regex_scripts"] as? JsonArray ?: return emptyList()
            scripts.mapNotNull { element ->
                (element as? JsonObject)?.let { parseRule(it) }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun parseRule(obj: JsonObject): PluginRule? {
        val disabled = obj["disabled"]?.jsonPrimitive?.booleanOrNull ?: false
        if (disabled) return null

        val findRegex = obj["findRegex"]?.jsonPrimitive?.contentOrNull
            ?: return null
        val pattern = parseJsRegex(findRegex) ?: return null
        if (pattern.isBlank()) return null

        val replace = obj["replaceString"]?.jsonPrimitive?.contentOrNull ?: ""
        val target = obj["target"]?.jsonPrimitive?.contentOrNull ?: ""
        val trigger = if (target.contains("user_input") || target == "send") {
            PluginTrigger.ON_SEND
        } else {
            PluginTrigger.ON_REPLY
        }

        return PluginRule(
            trigger = trigger,
            useRegex = true,
            pattern = pattern,
            action = PluginAction.REPLACE,
            config = mapOf("find" to pattern, "replace" to replace)
        )
    }

    /**
     * 解析 JS 风格正则 "/pattern/flags" → Kotlin Pattern 字符串
     *
     * 返回 null 表示无法转换（如含 JS 专有语法引用等交由上层兜底）。
     */
    fun parseJsRegex(raw: String): String? {
        val trimmed = raw.trim()
        // /pattern/flags 形式
        if (trimmed.length >= 2 && trimmed.startsWith("/")) {
            val lastSlash = trimmed.lastIndexOf('/')
            if (lastSlash > 0) {
                val pattern = trimmed.substring(1, lastSlash)
                val flags = trimmed.substring(lastSlash + 1)
                val prefix = buildString {
                    if (flags.contains("i")) append("(?i)")
                    if (flags.contains("m")) append("(?m)")
                    if (flags.contains("s")) append("(?s)")
                }
                return prefix + pattern
            }
        }
        // 裸正则
        return trimmed
    }
}
