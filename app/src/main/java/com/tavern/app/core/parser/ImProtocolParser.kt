package com.tavern.app.core.parser

/**
 * IM 聊天协议解析器
 *
 * 互动模式下，模型可在回复末尾输出角色在手机聊天中的回复：
 * ```
 * 叙事正文…
 *
 * [IM]
 * 角色的聊天回复（口语化）
 * [/IM]
 * ```
 *
 * IM 块是否出现由模型根据剧情自由决定。
 */
object ImProtocolParser {

    private val IM_REGEX = Regex(
        "\\[IM]\\s*(.*?)\\s*\\[/IM]",
        setOf(RegexOption.DOT_MATCHES_ALL)
    )

    /**
     * 解析结果
     *
     * @property content 剥离 IM 块后的叙事正文
     * @property imReply 角色的聊天回复（无则为 null）
     */
    data class Result(
        val content: String,
        val imReply: String?
    )

    fun parse(text: String): Result {
        val match = IM_REGEX.find(text) ?: return Result(text.trim(), null)
        val reply = match.groupValues[1].trim()
        val content = text.replace(match.value, "").trim()
        return Result(content, reply.ifBlank { null })
    }

    private val QUOTE_PATTERNS = listOf(
        Regex("「([^」]{2,150})」"),
        Regex("“([^”]{2,150})”"),
        Regex("\"([^\"]{2,150})\"")
    )

    /**
     * 兜底提取：模型未输出 [IM] 块时，从叙事中抽第一句对白作为 IM 回复
     *
     * @param exclude 玩家刚发送的消息（叙事常会复述它，需跳过，避免当成角色回复）
     */
    fun fallbackReply(narrative: String, exclude: String? = null): String? {
        val excluded = exclude?.trim()
        for (pattern in QUOTE_PATTERNS) {
            for (match in pattern.findAll(narrative)) {
                val speech = match.groupValues[1].trim()
                if (speech.isBlank()) continue
                // 跳过与玩家消息相同（或为其子串）的复述
                if (excluded != null && excluded.isNotBlank() &&
                    (speech == excluded || speech.contains(excluded) || excluded.contains(speech))
                ) {
                    continue
                }
                return speech
            }
        }
        // 无合适对白则取正文前 60 字（去空白）
        val head = narrative.replace(Regex("\\s+"), "").take(60)
        return head.takeIf { it.length >= 4 }
    }
}
