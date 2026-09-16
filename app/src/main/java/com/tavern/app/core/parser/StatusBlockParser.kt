package com.tavern.app.core.parser

import com.tavern.app.core.model.CharacterCard

/**
 * 文字状态栏解析器
 *
 * 兼容酒馆生态常见的多种状态栏协议：
 *
 * 1. 标准协议（maintext 版）：
 *    <maintext>正文</maintext><Status_block>状态</Status_block>
 * 2. 中文标签版：
 *    <正文>正文</正文>
 * 3. TavernHelper/MVU 版：
 *    <正文>正文</正文>
 *    <style>CSS</style><div class="s">HTML状态面板</div>
 *    <UpdateVariable>_.set(…)</UpdateVariable>
 *    <StatusPlaceHolderImpl/>
 * 4. 传念/评论版（开局可无状态栏，后续回合出现）：
 *    <Comment_of_Master><details>…状态与私信…</details>
 *      <Master_Talk>💬角色传念内容</Master_Talk>
 *    </Comment_of_Master>
 *
 * HTML 面板会被拍平成结构化文本；<details> 等保留给 UI 渲染。
 */
object StatusBlockParser {

    /** 判断卡片是否声明文字状态栏协议的关键词 */
    private val PROTOCOL_MARKERS = listOf(
        "<Status_block>",
        "<StatusPlaceHolder",
        "<UpdateVariable>",
        "<正文>",
        "<Comment_of_Master>",
        "<Master_Talk>",
        "状态面板HTML",
        "状态栏模板"
    )

    private val BLOCK_REGEX = Regex(
        "<Status_block>\\s*(.*?)\\s*</Status_block>",
        setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
    )
    private val MAINTTEXT_REGEX = Regex(
        "<maintext>\\s*(.*?)\\s*</maintext>",
        setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
    )
    private val ZH_NARRATIVE_REGEX = Regex(
        "<正文>\\s*(.*?)\\s*</正文>",
        setOf(RegexOption.DOT_MATCHES_ALL)
    )
    private val UPDATE_VAR_REGEX = Regex(
        "(?is)<UpdateVariable>.*?</UpdateVariable>"
    )
    private val PLACEHOLDER_REGEX = Regex(
        "(?is)<StatusPlaceHolderImpl\\s*/?>"
    )
    private val STYLE_REGEX = Regex("(?is)<style>.*?</style>")
    private val COMMENT_REGEX = Regex(
        "(?is)<Comment_of_Master>(.*?)</Comment_of_Master>"
    )
    private val MASTER_TALK_REGEX = Regex(
        "(?is)<Master_Talk>(.*?)</Master_Talk>"
    )

    /**
     * 解析结果
     *
     * @property content 剥离标签后的正文
     * @property statusPanel 状态面板内容（无则为 null）
     * @property imReply 从传念块提取的 IM 聊天回复（无则为 null）
     */
    data class Result(
        val content: String,
        val statusPanel: String?,
        val imReply: String? = null
    )

    /**
     * 判断角色卡是否声明了文字状态栏协议
     */
    fun cardUsesProtocol(card: CharacterCard): Boolean {
        val haystacks = buildList {
            add(card.description)
            add(card.systemPrompt)
            card.characterBook?.entries?.values?.forEach { add(it.content) }
            card.extensions?.toString()?.let { add(it) }
        }
        return haystacks.any { text -> PROTOCOL_MARKERS.any { text.contains(it) } }
    }

    /**
     * 解析回复，分离正文、状态面板与传念 IM 回复
     */
    fun parse(text: String): Result {
        // 1. 剥离 TavernHelper 变量块与占位符
        var t = text.replace(UPDATE_VAR_REGEX, "")
        t = t.replace(PLACEHOLDER_REGEX, "")

        // 2. 提取 <Master_Talk> 传念（可能位于 Comment_of_Master 内）
        var imReply: String? = null
        MASTER_TALK_REGEX.find(t)?.let { match ->
            imReply = cleanMasterTalk(match.groupValues[1])
            t = t.replace(match.value, "")
        }

        // 3. 标准 <Status_block> 协议
        val blockMatch = BLOCK_REGEX.find(t)
        if (blockMatch != null) {
            val panel = blockMatch.groupValues[1].trim()
                .takeIf { it.isNotEmpty() }
                ?.let { compactBlankLines(it) }
            var body = t.replace(blockMatch.value, "")
            body = findNarrative(body) ?: body.trim()
            return Result(content = body.trim(), statusPanel = panel, imReply = imReply)
        }

        // 4. <Comment_of_Master> 评论/状态块（开局可无，后续回合出现）
        val commentMatch = COMMENT_REGEX.find(t)
        val commentPanel = commentMatch?.groupValues?.get(1)?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { compactBlankLines(it) }
        if (commentMatch != null) {
            t = t.replace(commentMatch.value, "")
        }

        // 5. 叙事标签（<正文> / <maintext>）
        val narrative = findNarrative(t)
        var rest = t
        ZH_NARRATIVE_REGEX.find(rest)?.let { rest = rest.replace(it.value, "") }
        MAINTTEXT_REGEX.find(rest)?.let { rest = rest.replace(it.value, "") }

        // 6. HTML 状态面板（<style> 起始）
        val styleIdx = rest.indexOf("<style>", ignoreCase = true)
        if (styleIdx >= 0) {
            val before = rest.substring(0, styleIdx).trim()
            // HTML 段终点：截到 [CHOICES]/[STATUS] 等下游协议之前，保证选项不被吞进面板
            val tailStart = findTailStart(rest, styleIdx)
            val htmlPart = rest.substring(styleIdx, tailStart)
            val tailPart = rest.substring(tailStart).trim()

            return if (before.isBlank() && narrative == null) {
                // 变体：叙事写在 HTML 卡内部（无正文标签、HTML 打头）
                // → 拍平 HTML 作为正文，[CHOICES] 等保留在正文尾部；不重复进状态栏
                val flat = flattenHtmlPanel(htmlPart)
                val content = listOf(flat, tailPart)
                    .filter { it.isNotBlank() }
                    .joinToString("\n\n")
                // 拍平后可能露出纯文本状态字段 → 再尝试拆入状态栏
                withPlainStatus(
                    Result(content = content.trim(), statusPanel = null, imReply = imReply)
                )
            } else {
                // 常规：正文在前，HTML 面板单独进状态栏
                val panel = flattenHtmlPanel(htmlPart)
                val content = listOf(narrative ?: before, tailPart)
                    .filter { it.isNotBlank() }
                    .joinToString("\n\n")
                Result(
                    content = content.trim(),
                    statusPanel = panel.takeIf { it.isNotBlank() },
                    imReply = imReply
                )
            }
        }

        return withPlainStatus(
            Result(
                content = (narrative ?: rest).trim(),
                statusPanel = commentPanel,
                imReply = imReply
            )
        )
    }

    /** 纯文本状态字段标记（卡片状态栏无 HTML 标签时的识别线索） */
    private val PLAIN_STATUS_MARKERS = listOf(
        "🎭", "📍 地点", "📍地点", "❤️ 好感", "❤️好感", "😈 堕落", "😈堕落",
        "👗", "👚", "🤸", "🧘 修为"
    )

    /**
     * 纯文本状态尾部识别：
     * 卡片有时把状态栏输出为无标签纯文本（如「XX 状态⏰…」+ emoji 字段行），
     * 从正文尾部拆出该段进置顶状态栏。
     */
    private fun withPlainStatus(result: Result): Result {
        if (result.statusPanel != null) return result
        val text = result.content
        if (text.isBlank()) return result

        // 第一个状态字段标记位置
        val markerIdx = PLAIN_STATUS_MARKERS
            .mapNotNull { m -> text.indexOf(m).takeIf { it >= 0 } }
            .minOrNull() ?: return result
        if (markerIdx < text.length / 3) return result // 标记太靠前，不像尾部状态

        // 回退到行首；若上一行是「XX 状态…」短头行则一并纳入
        var start = text.lastIndexOf('\n', markerIdx - 1).let { if (it >= 0) it + 1 else 0 }
        val prevNl = text.lastIndexOf('\n', (start - 2).coerceAtLeast(0))
        if (prevNl >= 0) {
            val prevLine = text.substring(prevNl + 1, start)
            if (prevLine.contains("状态") && prevLine.length <= 40) {
                start = prevNl + 1
            }
        }

        val tailStart = findTailStart(text, start)
        val panel = text.substring(start, tailStart).trim()
        if (panel.length < 20) return result
        val content = (text.substring(0, start) + text.substring(tailStart)).trim()
        return result.copy(content = content, statusPanel = panel)
    }

    /** HTML 段之后的下游协议起始位置（找不到返回 text.length） */
    private fun findTailStart(text: String, from: Int): Int {
        val markers = listOf(
            "[CHOICES]", "[STATUS]", "[IM]", "[NARRATION]",
            "<UpdateVariable>", "<Comment_of_Master>"
        )
        return markers
            .mapNotNull { marker ->
                val i = text.indexOf(marker, startIndex = from, ignoreCase = true)
                if (i > from) i else null
            }
            .minOrNull() ?: text.length
    }

    /**
     * 清洗 <Master_Talk> 内容为 IM 聊天回复：
     * - 去掉内心戏行（💗 开头或含"内心戏"）
     * - 去掉 "💬xx传念之语：" 一类前缀
     * - 去掉首尾引号
     */
    fun cleanMasterTalk(inner: String): String? {
        val lines = inner.lines()
            .filter { line ->
                val trimmed = line.trim()
                !trimmed.startsWith("💗") && !trimmed.contains("内心戏")
            }
            .joinToString("\n")
        val noPrefix = lines.replace(Regex("(?m)^[💬📱].*?[：:]\\s*"), "").trim()
        val unquoted = noPrefix
            .removeSurrounding("「", "」")
            .removeSurrounding("\"", "\"")
            .removeSurrounding("“", "”")
            .trim()
        return unquoted.takeIf { it.isNotBlank() }
    }

    /** 查找叙事标签内容，找不到返回 null */
    private fun findNarrative(text: String): String? =
        ZH_NARRATIVE_REGEX.find(text)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
            ?: MAINTTEXT_REGEX.find(text)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }

    /**
     * 将状态面板 HTML 拍平为结构化纯文本（保留全部信息，供 TavernText 渲染）
     *
     * - 去掉 <style>/<script>
     * - <br>、</div>、</p> 等块级结束 → 换行
     * - 剩余标签剥离
     * - 基础 HTML 实体解码
     */
    fun flattenHtmlPanel(html: String): String {
        var t = html
        t = t.replace(Regex("(?is)<style>.*?</style>"), "")
        t = t.replace(Regex("(?is)<script>.*?</script>"), "")
        t = t.replace(Regex("(?i)<br\\s*/?>"), "\n")
        t = t.replace(Regex("(?i)</(div|p|tr|li|h[1-6]|section|header|footer)>"), "\n")
        t = t.replace(Regex("<[^>]+>"), "")
        t = t
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
        return compactBlankLines(t)
    }

    /** 压缩多余空行（保留标签与结构） */
    fun compactBlankLines(text: String): String = text
        .lines()
        .map { it.trimEnd() }
        .filter { it.isNotBlank() }
        .joinToString("\n")
        .trim()

    /**
     * 流式展示用：剥离状态块/叙事标签/HTML面板/变量块/评论传念（含未闭合的尾部），只留正文
     */
    fun stripForDisplay(text: String): String {
        var t = text

        // 已闭合的块先整块移除/解包
        t = t.replace(UPDATE_VAR_REGEX, "")
        t = t.replace(PLACEHOLDER_REGEX, "")
        t = t.replace(COMMENT_REGEX, "")
        t = t.replace(MASTER_TALK_REGEX, "")
        t = BLOCK_REGEX.replace(t, "")
        t = ZH_NARRATIVE_REGEX.replace(t, "$1")
        t = MAINTTEXT_REGEX.replace(t, "$1")

        // 未闭合的尾部：从开始标签到末尾隐藏
        t = cutOpenTail(t, "<Status_block>", "</Status_block>")
        t = cutOpenTail(t, "<UpdateVariable>", "</UpdateVariable>")
        t = cutOpenTail(t, "<style>", "</style>")
        t = cutOpenTail(t, "<正文>", "</正文>")
        t = cutOpenTail(t, "<Comment_of_Master>", "</Comment_of_Master>")
        t = cutOpenTail(t, "<Master_Talk>", "</Master_Talk>")
        t = cutOpenTail(t, "<options>", "</options>")
        // 已闭合的 options 块也不在正文流式中展示
        t = Regex("(?is)<options>.*?</options>").replace(t, "")

        // HTML 面板段（含闭合 style）在流式中隐藏，保留下游协议（[CHOICES] 等）
        val styleIdx = t.indexOf("<style>", ignoreCase = true)
        if (styleIdx >= 0) {
            val tailStart = findTailStart(t, styleIdx)
            t = (t.substring(0, styleIdx) + t.substring(tailStart)).trim()
        }

        // 纯文本状态尾部在流式中也隐藏
        val markerIdx = PLAIN_STATUS_MARKERS
            .mapNotNull { m -> t.indexOf(m).takeIf { it >= 0 } }
            .minOrNull()
        if (markerIdx != null && markerIdx > t.length / 3) {
            var start = t.lastIndexOf('\n', markerIdx - 1).let { if (it >= 0) it + 1 else 0 }
            val prevNl = t.lastIndexOf('\n', (start - 2).coerceAtLeast(0))
            if (prevNl >= 0) {
                val prevLine = t.substring(prevNl + 1, start)
                if (prevLine.contains("状态") && prevLine.length <= 40) start = prevNl + 1
            }
            val tailStart = findTailStart(t, start)
            t = (t.substring(0, start) + t.substring(tailStart)).trim()
        }

        // 残留标签剥离
        t = t.replace(Regex("</?(?:maintext|正文)>", RegexOption.IGNORE_CASE), "")
        return t.trim()
    }

    private fun cutOpenTail(text: String, openTag: String, closeTag: String): String {
        val idx = text.indexOf(openTag, ignoreCase = true)
        if (idx < 0) return text
        val closed = text.indexOf(closeTag, ignoreCase = true, startIndex = idx + openTag.length)
        if (closed < 0) return text.substring(0, idx)
        return text
    }
}
