package com.tavern.app.core.prompt

import com.tavern.app.core.model.StatusDelta
import com.tavern.app.core.model.VNChoice
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 互动选项解析器
 *
 * 从 AI 回复中提取可点击的选项列表，同时剥离选项块。
 *
 * 支持三种协议格式：
 * 1. 旧格式（纯文本数组）：
 *    [CHOICES]{"choices":["选项1","选项2"]}[/CHOICES]
 * 2. 新格式（带状态增量）：
 *    [CHOICES]{"choices":[{"text":"…","delta":{"affection":3}}]}[/CHOICES]
 * 3. 酒馆 <options> 格式（竖线/换行分隔，部分角色卡使用）：
 *    <options>选项A|选项B|选项C</options>
 */
object OptionsParser {

    private val json = Json { ignoreUnknownKeys = true }

    private val CHOICES_REGEX = Regex(
        "\\[CHOICES]\\s*(.*?)\\s*\\[/CHOICES]",
        setOf(RegexOption.DOT_MATCHES_ALL)
    )

    private val HTML_OPTIONS_REGEX = Regex(
        "(?is)<options>\\s*(.*?)\\s*</options>"
    )

    /**
     * 解析结果
     *
     * @property content 剥离选项块后的正文
     * @property choices 选项列表（无选项时为空）
     */
    data class Result(
        val content: String,
        val choices: List<VNChoice>
    )

    /**
     * 解析文本，提取选项
     */
    fun parse(text: String): Result {
        // 优先 [CHOICES] JSON 协议
        val match = CHOICES_REGEX.find(text)
        if (match != null) {
            val choices = parseChoicesJson(match.groupValues[1])
            val content = text.replace(match.value, "").trim()
            return Result(content, choices)
        }
        // 回退：<options> 竖线/换行分隔格式
        val htmlMatch = HTML_OPTIONS_REGEX.find(text)
        if (htmlMatch != null) {
            val raw = htmlMatch.groupValues[1]
            val choices = raw
                .split('|', '\n')
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .map { VNChoice(text = it) }
            val content = text.replace(htmlMatch.value, "").trim()
            return Result(content, choices)
        }
        return Result(text, emptyList())
    }

    private fun parseChoicesJson(jsonText: String): List<VNChoice> {
        return try {
            val root = json.parseToJsonElement(jsonText).jsonObject
            val array = root["choices"]?.jsonArray ?: return emptyList()
            array.mapNotNull { element ->
                // 新格式：对象 {text, delta}
                runCatching {
                    val obj = element.jsonObject
                    val text = obj["text"]?.jsonPrimitive?.contentOrNull
                        ?: return@runCatching null
                    if (text.isBlank()) return@runCatching null
                    val delta = obj["delta"]?.jsonObject?.let { parseDelta(it) } ?: StatusDelta()
                    VNChoice(text = text, delta = delta)
                }.getOrNull() ?: runCatching {
                    // 旧格式：纯字符串
                    val text = element.jsonPrimitive.contentOrNull
                    if (text.isNullOrBlank()) null else VNChoice(text = text)
                }.getOrNull()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun parseDelta(obj: kotlinx.serialization.json.JsonObject): StatusDelta {
        val custom = obj["custom"]?.jsonObject
            ?.mapNotNull { (key, value) ->
                value.jsonPrimitive.intOrNull?.let { key to it }
            }
            ?.toMap()
            ?: emptyMap()
        return StatusDelta(
            mood = obj["mood"]?.jsonPrimitive?.intOrNull ?: 0,
            energy = obj["energy"]?.jsonPrimitive?.intOrNull ?: 0,
            affection = obj["affection"]?.jsonPrimitive?.intOrNull ?: 0,
            custom = custom
        )
    }
}
