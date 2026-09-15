package com.tavern.app.core.memory

import com.tavern.app.core.data.network.ModelProviderFactory
import com.tavern.app.core.model.MessageRole
import com.tavern.app.core.model.ModelConfig
import com.tavern.app.core.model.ChatMessage
import com.tavern.app.core.prompt.PromptMessage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 记忆引擎：滚动摘要 + 事实抽取 + token 估算（纯逻辑可测）
 */
object MemoryEngine {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    // ===== token 粗估（中英混合，宁大勿小） =====

    fun estimateTokens(text: String): Int {
        if (text.isBlank()) return 0
        var cjk = 0
        var other = 0
        for (ch in text) {
            if (ch.code > 0x2E80) cjk++ else other++
        }
        return (cjk / 1.6 + other / 4).toInt().coerceAtLeast(1)
    }

    // ===== 滚动摘要 =====

    /**
     * 构建摘要提示词（历史对话 → 前情提要）
     *
     * @param previousSummary 上一代摘要（增量合并）
     * @param window 待压缩的消息文本（role: content 拼接）
     */
    fun buildSummaryPrompt(previousSummary: String?, window: String): List<PromptMessage> {
        val system = """
            你是剧情摘要器。把给定的对话压缩为「前情提要」，供后续剧情续写时快速回忆。
            要求：
            1. 用第三人称，200～400字
            2. 必须保留：人名、关键事件、重要决定、关系变化、未完成的目标
            3. 丢弃：寒暄、重复、无关细节
            4. 只输出摘要正文，不要标题、不要解释、不要列表符号
        """.trimIndent()
        val user = buildString {
            if (!previousSummary.isNullOrBlank()) {
                appendLine("【已有前情提要】")
                appendLine(previousSummary)
                appendLine()
            }
            appendLine("【新对话片段】")
            append(window.take(24_000))
        }
        return listOf(
            PromptMessage(MessageRole.SYSTEM, system),
            PromptMessage(MessageRole.USER, user)
        )
    }

    /** 调用模型生成摘要 */
    suspend fun summarize(
        modelConfig: ModelConfig,
        previousSummary: String?,
        window: String
    ): Result<String> = runCatching {
        val provider = ModelProviderFactory.getProvider(modelConfig)
        val reply = provider.chat(
            modelConfig.copy(maxTokens = 1024, streamEnabled = false),
            buildSummaryPrompt(previousSummary, window)
        ).getOrThrow()
        reply.trim().takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("摘要为空")
    }

    // ===== 事实抽取 =====

    /** 从本轮对话抽取事实的提示词 */
    fun buildFactsPrompt(dialog: String): List<PromptMessage> {
        val system = """
            你是记忆抽取器。从对话中提取值得长期记住的事实（关于玩家、角色、世界的关键信息与事件）。
            要求：
            1. 每条事实一句话，≤50字，主语明确（用「玩家」称呼用户一方）
            2. 只记稳定事实与关键事件（喜好、关系进展、重要约定/事件、秘密、设定），不记闲聊寒暄
            3. 最多 5 条；没有值得记的就输出 []
            4. 只输出 JSON：{"facts":["……","……"]}
        """.trimIndent()
        return listOf(
            PromptMessage(MessageRole.SYSTEM, system),
            PromptMessage(MessageRole.USER, dialog.take(12_000))
        )
    }

    /** 解析模型输出的 facts JSON */
    fun parseFacts(reply: String): List<String> {
        var text = reply.trim()
        val fence = Regex("(?s)```(?:json)?\\s*(.*?)\\s*```")
        fence.find(text)?.let { text = it.groupValues[1].trim() }
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start >= 0 && end > start) text = text.substring(start, end + 1)
        return try {
            val root = json.parseToJsonElement(text).jsonObject
            root["facts"]?.jsonArray
                ?.mapNotNull { it.jsonPrimitive.contentOrNull?.trim() }
                ?.filter { it.length in 4..80 }
                ?.take(5)
                ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** 调用模型抽取事实 */
    suspend fun extractFacts(
        modelConfig: ModelConfig,
        dialog: String
    ): Result<List<String>> = runCatching {
        val provider = ModelProviderFactory.getProvider(modelConfig)
        val reply = provider.chat(
            modelConfig.copy(maxTokens = 512, streamEnabled = false),
            buildFactsPrompt(dialog)
        ).getOrThrow()
        parseFacts(reply)
    }

    // ===== 上下文预算 =====

    /**
     * 按 token 预算裁剪消息列表：协议消息恒保留，从最旧的历史开始丢弃。
     *
     * @param budgetTokens 可用 token 预算
     * @return 保留的消息（system 恒在 + 尽量多的最近消息）
     */
    fun trimToBudget(
        messages: List<PromptMessage>,
        budgetTokens: Int
    ): List<PromptMessage> {
        if (budgetTokens <= 0) return messages
        val system = messages.filter { it.role == MessageRole.SYSTEM }
        val rest = messages.filter { it.role != MessageRole.SYSTEM }
        val systemCost = system.sumOf { estimateTokens(it.content) }
        var budget = budgetTokens - systemCost
        if (budget <= 0) return system + rest.takeLast(1)

        // 从最新往回装
        val kept = ArrayList<PromptMessage>()
        for (i in rest.indices.reversed()) {
            val cost = estimateTokens(rest[i].content)
            if (cost > budget) break
            budget -= cost
            kept.add(0, rest[i])
        }
        return system + kept
    }
}
