package com.tavern.app.core.parser

import com.tavern.app.core.model.NarrationChange

/**
 * 联动旁白解析器
 *
 * 从 AI 回复中解析 [NARRATION]...[/NARRATION] 块：
 * - 剥离后的内容作为聊天模式正文
 * - 旁白内容供互动模式展示（详细场景叙事）
 *
 * 协议格式：
 * ```
 * 聊天正文…
 *
 * [NARRATION]
 * 以第三人称/场景视角的详细叙事…
 * [/NARRATION]
 * ```
 */
object NarrationParser {

    private val NARRATION_REGEX = Regex(
        "\\[NARRATION]\\s*(.*?)\\s*\\[/NARRATION]",
        setOf(RegexOption.DOT_MATCHES_ALL)
    )

    private val BLOCK_REGEX = Regex(
        "\\[(STATUS|CHOICES|NARRATION|IM)]\\s*.*?\\[/\\1]",
        setOf(RegexOption.DOT_MATCHES_ALL)
    )

    /** 开始标签（流式输出中可能只出现一半） */
    private val OPEN_TAG_REGEX = Regex("\\[(STATUS|CHOICES|NARRATION|IM)]\\s*\\{?[^\\]]*$")

    /**
     * 解析文本，提取联动旁白
     */
    fun parse(text: String): NarrationChange {
        val match = NARRATION_REGEX.find(text)
            ?: return NarrationChange(BLOCK_REGEX.replace(text, "").trim(), null)
        val narration = match.groupValues[1].trim()
        val content = text.replace(match.value, "").trim()
        return NarrationChange(content, narration.ifBlank { null })
    }

    /**
     * 实时剥离完整协议块与尾部未闭合的协议标签（用于流式展示）
     */
    fun stripForDisplay(text: String): String =
        BLOCK_REGEX.replace(text, "").replace(OPEN_TAG_REGEX, "").trimEnd()
}
