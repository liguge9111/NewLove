package com.tavern.app.core.parser

import com.tavern.app.core.model.StatusChange
import com.tavern.app.core.model.StatusDelta
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 状态变化解析器
 *
 * 从 AI 回复中解析 [STATUS]...[/STATUS] 块，提取角色状态变化量，
 * 同时返回剥离状态块后的正文。
 *
 * 协议格式：
 * ```
 * 正文叙述...
 *
 * [STATUS]
 * {"mood": 10, "energy": -5, "affection": 3, "custom": {"好感": 5}}
 * [/STATUS]
 * ```
 *
 * 数值均为增量（正数增加、负数减少）。custom 支持任意命名的自定义状态。
 */
object StatusChangeParser {

    private val json = Json { ignoreUnknownKeys = true }

    private val STATUS_REGEX = Regex(
        "\\[STATUS]\\s*(.*?)\\s*\\[/STATUS]",
        setOf(RegexOption.DOT_MATCHES_ALL)
    )

    /**
     * 解析文本，提取状态变化
     */
    fun parse(text: String): StatusChange {
        val match = STATUS_REGEX.find(text) ?: return StatusChange(text, StatusDelta())
        val delta = parseDeltaJson(match.groupValues[1])
        val content = text.replace(match.value, "").trim()
        return StatusChange(content, delta)
    }

    private fun parseDeltaJson(jsonText: String): StatusDelta {
        return try {
            val root = json.parseToJsonElement(jsonText).jsonObject
            val custom = mutableMapOf<String, Int>()
            root["custom"]?.jsonObject?.forEach { (key, value) ->
                value.jsonPrimitive.intOrNull?.let { custom[key] = it }
            }
            // 顶层任意非内置 key 也视为自定义状态增量
            root.forEach { (key, value) ->
                if (key !in BUILTIN_KEYS && key != "custom") {
                    value.jsonPrimitive.intOrNull?.let { custom[key] = it }
                }
            }

            StatusDelta(
                mood = root["mood"]?.jsonPrimitive?.intOrNull ?: 0,
                energy = root["energy"]?.jsonPrimitive?.intOrNull ?: 0,
                affection = root["affection"]?.jsonPrimitive?.intOrNull ?: 0,
                custom = custom
            )
        } catch (e: Exception) {
            StatusDelta()
        }
    }

    private val BUILTIN_KEYS = setOf("mood", "energy", "affection")
}
