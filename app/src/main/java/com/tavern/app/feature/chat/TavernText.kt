package com.tavern.app.feature.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp

/**
 * 酒馆网页风格的轻量标记渲染（原生 Compose，无 WebView）
 *
 * 支持 SillyTavern 网页版常见的展示效果：
 * - <details><summary>标题</summary>内容</details> → 可折叠区块（默认折叠，点击展开）
 * - <b>/<strong>、<i>/<em>、<u>、<s>/<del>、<code>
 * - Markdown：**粗体**、*斜体*、~~删除线~~、`行内代码`、``` 代码块
 * - <br> 换行；其余未知标签剥离、保留内部文字
 */
@Composable
fun TavernText(
    text: String,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    plain: Boolean = false,
    modifier: Modifier = Modifier
) {
    val blocks = remember(text, plain) { splitBlocks(text) }
    Column(modifier = modifier) {
        blocks.forEachIndexed { index, block ->
            when (block) {
                is MarkupBlock.Plain -> Text(
                    text = if (plain) {
                        AnnotatedString(block.text)
                    } else {
                        buildInline(block.text, style)
                    },
                    style = style
                )

                is MarkupBlock.Code -> CodeBlock(code = block.code, style = style)

                is MarkupBlock.Details -> DetailsBlock(
                    summary = block.summary,
                    content = block.content,
                    style = style,
                    plain = plain
                )
            }
            if (index < blocks.lastIndex) {
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}

// ===== 解析 =====

internal sealed interface MarkupBlock {
    data class Plain(val text: String) : MarkupBlock
    data class Code(val code: String) : MarkupBlock
    data class Details(val summary: String, val content: String) : MarkupBlock
}

private val DETAILS_REGEX = Regex(
    "(?is)<details>\\s*<summary>(.*?)</summary>(.*?)</details>"
)
private val FENCE_REGEX = Regex("```[a-zA-Z]*\\n?(.*?)```", setOf(RegexOption.DOT_MATCHES_ALL))

internal fun splitBlocks(text: String): List<MarkupBlock> {
    val blocks = mutableListOf<MarkupBlock>()
    var pos = 0
    while (pos < text.length) {
        val details = DETAILS_REGEX.find(text, pos)
        val fence = FENCE_REGEX.find(text, pos)
        val next = listOfNotNull(details, fence).minByOrNull { it.range.first }

        if (next == null) {
            val rest = text.substring(pos)
            if (rest.isNotBlank()) blocks.add(MarkupBlock.Plain(rest))
            break
        }
        if (next.range.first > pos) {
            val between = text.substring(pos, next.range.first)
            if (between.isNotBlank()) blocks.add(MarkupBlock.Plain(between))
        }
        if (next === details) {
            blocks.add(
                MarkupBlock.Details(
                    summary = next.groupValues[1].trim(),
                    content = next.groupValues[2].trim()
                )
            )
        } else {
            blocks.add(MarkupBlock.Code(code = next.groupValues[1].trimEnd('\n')))
        }
        pos = next.range.last + 1
    }
    return blocks.ifEmpty { listOf(MarkupBlock.Plain("")) }
}

// ===== 行内样式 =====

/** 微信品牌绿（对话着色） */
private val SpeechColor = Color(0xFF07C160)
/** 想法灰 */
private val ThoughtColor = Color(0xFF8A8A8A)

/** 角色对话：「…」或 “…” */
private val SPEECH_REGEX = Regex("「[^「」\\n]{1,800}」|“[^“”\\n]{1,800}”")
/** 角色内心想法：*…*（单星号，非 **粗体**） */
private val THOUGHT_REGEX = Regex("\\*([^*\\n]{1,800})\\*")

/** 行内标记 token（顺序敏感：** 优先于 *） */
private val TOKEN_PATTERNS = listOf(
    Regex("(?i)</?(?:b|strong)>"),
    Regex("(?i)</?(?:i|em)>"),
    Regex("(?i)</?u>"),
    Regex("(?i)</?(?:s|del|strike)>"),
    Regex("(?i)</?code>"),
    Regex("\\*\\*"),
    Regex("~~"),
    Regex("`"),
    Regex("(?i)<br\\s*/?>"),
    Regex("</?[a-zA-Z][^>]*>") // 其余未知标签：剥离
)

internal fun buildInline(text: String, base: TextStyle): AnnotatedString {
    val builder = AnnotatedString.Builder()
    var pos = 0
    while (pos < text.length) {
        val speech = SPEECH_REGEX.find(text, pos)
        val thought = THOUGHT_REGEX.find(text, pos)
        val next = listOfNotNull(speech, thought).minByOrNull { it.range.first }
        if (next == null) {
            appendRich(text.substring(pos), builder, false, false, false, false, false)
            break
        }
        if (next.range.first > pos) {
            appendRich(
                text.substring(pos, next.range.first),
                builder, false, false, false, false, false
            )
        }
        if (next === speech) {
            // 角色对话：微信绿着色
            builder.withStyle(SpanStyle(color = SpeechColor)) {
                append(next.value)
            }
        } else {
            // 内心想法：斜体 + 灰色（去掉星号）
            builder.withStyle(
                SpanStyle(fontStyle = FontStyle.Italic, color = ThoughtColor)
            ) {
                append(next.groupValues[1])
            }
        }
        pos = next.range.last + 1
    }
    return builder.toAnnotatedString()
}

private fun appendRich(
    text: String,
    builder: AnnotatedString.Builder,
    bold: Boolean,
    italic: Boolean,
    underline: Boolean,
    strike: Boolean,
    code: Boolean
) {
    var i = 0
    var b = bold
    var it = italic
    var u = underline
    var s = strike
    var c = code

    while (i < text.length) {
        var nextIdx = -1
        var nextTok = ""
        for (pattern in TOKEN_PATTERNS) {
            val m = pattern.find(text, i)
            if (m != null && (nextIdx < 0 || m.range.first < nextIdx)) {
                nextIdx = m.range.first
                nextTok = m.value
            }
        }
        if (nextIdx < 0) {
            appendStyled(builder, text.substring(i), b, it, u, s, c)
            return
        }
        if (nextIdx > i) {
            appendStyled(builder, text.substring(i, nextIdx), b, it, u, s, c)
        }

        val lower = nextTok.lowercase()
        when {
            lower == "<b>" || lower == "<strong>" || nextTok == "**" -> b = true
            lower == "</b>" || lower == "</strong>" -> b = false
            lower == "<i>" || lower == "<em>" || nextTok == "*" -> it = true
            lower == "</i>" || lower == "</em>" -> it = false
            lower == "<u>" -> u = true
            lower == "</u>" -> u = false
            lower == "<s>" || lower == "<del>" || lower == "<strike>" || nextTok == "~~" -> s = true
            lower == "</s>" || lower == "</del>" || lower == "</strike>" -> s = false
            lower == "<code>" || nextTok == "`" -> c = true
            lower == "</code>" -> c = false
            lower.startsWith("<br") -> builder.append("\n")
            else -> { /* 未知标签：丢弃 */ }
        }
        i = nextIdx + nextTok.length
    }
}

private fun appendStyled(
    builder: AnnotatedString.Builder,
    chunk: String,
    bold: Boolean,
    italic: Boolean,
    underline: Boolean,
    strike: Boolean,
    code: Boolean
) {
    if (chunk.isEmpty()) return
    var span = SpanStyle()
    if (bold || code) span = span.copy(fontWeight = FontWeight.Bold)
    if (italic) span = span.copy(fontStyle = FontStyle.Italic)
    val decorations = buildList {
        if (underline) add(TextDecoration.Underline)
        if (strike) add(TextDecoration.LineThrough)
    }
    if (decorations.isNotEmpty()) {
        span = span.copy(textDecoration = TextDecoration.combine(decorations))
    }
    if (code) {
        span = span.copy(
            fontFamily = FontFamily.Monospace,
            background = Color.Gray.copy(alpha = 0.25f)
        )
    }
    builder.withStyle(span) { append(chunk) }
}

// ===== 组件 =====

@Composable
private fun CodeBlock(code: String, style: TextStyle) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = code,
            style = style.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Normal
            ),
            modifier = Modifier.padding(10.dp)
        )
    }
}

@Composable
private fun DetailsBlock(
    summary: String,
    content: String,
    style: TextStyle,
    plain: Boolean = false
) {
    // 与网页版一致：默认折叠，点击展开
    var expanded by rememberSaveable(summary, content) { mutableStateOf(false) }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (plain) AnnotatedString(summary)
                    else buildInline(summary, style.merge(MaterialTheme.typography.titleSmall)),
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.primary
                )
                Icon(
                    imageVector = if (expanded) Icons.Filled.KeyboardArrowUp
                    else Icons.Filled.KeyboardArrowDown,
                    contentDescription = if (expanded) "折叠" else "展开",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)) {
                    // 内容里可能还有代码块/行内样式，递归渲染
                    val blocks = remember(content) { splitBlocks(content) }
                    blocks.forEach { block ->
                        when (block) {
                            is MarkupBlock.Plain -> Text(
                                text = if (plain) AnnotatedString(block.text)
                                else buildInline(block.text, style),
                                style = style
                            )
                            is MarkupBlock.Code -> CodeBlock(code = block.code, style = style)
                            is MarkupBlock.Details -> DetailsBlock(
                                summary = block.summary,
                                content = block.content,
                                style = style,
                                plain = plain
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                    }
                }
            }
        }
    }
}
