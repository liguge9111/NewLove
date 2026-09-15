package com.tavern.app.core.prompt

import com.tavern.app.core.model.CharacterCard
import com.tavern.app.core.model.ChatMessage
import com.tavern.app.core.model.InteractiveMode
import com.tavern.app.core.model.MessageRole
import com.tavern.app.core.model.WorldBookActivation
import com.tavern.app.core.model.WorldBookEntry
import com.tavern.app.core.util.VariableResolver

/**
 * 提示词构建器
 *
 * 将角色卡、世界书、聊天历史组装成发送给大模型的消息列表。
 *
 * 系统提示词组装顺序（参考 SillyTavern）：
 * 1. 世界书 before_char 条目
 * 2. 角色定义（description + personality + scenario）
 * 3. 世界书 after_char 条目
 * 4. 消息示例（mes_example）
 * 5. 自定义系统提示词（system_prompt）
 * 6. 后置指令（post_history_instructions）
 */
object PromptBuilder {

    /**
     * 构建完整的消息列表
     *
     * @param card 角色卡
     * @param activation 世界书激活结果（可为空）
     * @param history 聊天历史（按时间正序）
     * @param userName 用户名
     * @param config 构建配置
     * @return 消息列表（system 开头，含历史）
     */
    fun buildMessages(
        card: CharacterCard,
        activation: WorldBookActivation? = null,
        history: List<ChatMessage> = emptyList(),
        userName: String = "用户",
        config: PromptConfig = PromptConfig(),
        mode: InteractiveMode = InteractiveMode.CHAT
    ): List<PromptMessage> {
        val variables = VariableResolver.defaultVariables(userName, card.name)
        val messages = mutableListOf<PromptMessage>()

        // 1. 系统提示词
        val systemPrompt = buildSystemPrompt(card, activation, config, variables, mode)
        if (systemPrompt.isNotBlank()) {
            messages.add(PromptMessage(MessageRole.SYSTEM, systemPrompt))
        }

        // 2. 首条消息（作为 AI 的开场白）
        if (config.includeFirstMessage && card.firstMessage.isNotBlank()) {
            val firstMes = VariableResolver.resolve(card.firstMessage, variables)
            messages.add(PromptMessage(MessageRole.ASSISTANT, firstMes))
        }

        // 3. 聊天历史（可选预算裁剪：协议恒保留，从最旧历史丢弃）
        val historyMessages = buildHistory(history, config, variables)
        val trimmedHistory = if (config.budgetTokens > 0) {
            com.tavern.app.core.memory.MemoryEngine.trimToBudget(historyMessages, config.budgetTokens)
        } else {
            historyMessages
        }
        messages.addAll(trimmedHistory)

        return messages
    }

    /**
     * 构建系统提示词
     */
    fun buildSystemPrompt(
        card: CharacterCard,
        activation: WorldBookActivation? = null,
        config: PromptConfig = PromptConfig(),
        variables: Map<String, String> = emptyMap(),
        mode: InteractiveMode = InteractiveMode.CHAT
    ): String {
        val parts = mutableListOf<String>()

        // 世界书 before_char 条目
        if (config.includeWorldBook && activation != null) {
            val beforeChar = activation.entries.filter { it.position == "before_char" }
            beforeChar.forEach { parts.add(resolve(it.content, variables)) }
        }

        // 角色定义
        if (config.includeDefinition) {
            val definition = card.buildDefinition()
            if (definition.isNotBlank()) {
                parts.add(resolve(definition, variables))
            }
        }

        // 前情提要（滚动摘要）
        if (config.chatSummary.isNotBlank()) {
            parts.add("【前情提要】\n${config.chatSummary}")
        }

        // 长期记忆
        if (config.memoryFacts.isNotBlank()) {
            parts.add("【角色记忆】\n${config.memoryFacts}")
        }

        // 世界书 after_char 条目
        if (config.includeWorldBook && activation != null) {
            val afterChar = activation.entries.filter { it.position == "after_char" }
            afterChar.forEach { parts.add(resolve(it.content, variables)) }
        }

        // 消息示例
        if (config.includeMessageExample && card.messageExample.isNotBlank()) {
            parts.add(resolve(card.messageExample, variables))
        }

        // 自定义系统提示词
        if (card.systemPrompt.isNotBlank()) {
            parts.add(resolve(card.systemPrompt, variables))
        }

        // 后置指令
        if (card.postHistoryInstructions.isNotBlank()) {
            parts.add(resolve(card.postHistoryInstructions, variables))
        }

        // 作者注释（depth=0 时追加到系统提示词）
        if (config.authorNote.isNotBlank() && config.authorNoteDepth == 0) {
            parts.add(resolve(config.authorNote, variables))
        }

        // 互动模式指令（放在最后，优先级最高）
        val modeInstruction = InteractiveModePrompts.instructionFor(mode)
        if (modeInstruction.isNotBlank()) {
            parts.add(resolve(modeInstruction, variables))
        }

        // 状态栏协议说明（追加在模式指令之后）
        if (config.statusProtocol.isNotBlank()) {
            parts.add(resolve(config.statusProtocol, variables))
        }

        // 联动旁白协议（聊天模式，追加在状态协议之后）
        if (config.narrationProtocol.isNotBlank()) {
            parts.add(resolve(config.narrationProtocol, variables))
        }

        // 插件提示词注入
        if (config.pluginInjections.isNotBlank()) {
            parts.add(config.pluginInjections)
        }

        return parts.joinToString("\n\n")
    }

    /**
     * 构建聊天历史消息列表（含作者注释插入）
     */
    private fun buildHistory(
        history: List<ChatMessage>,
        config: PromptConfig,
        variables: Map<String, String>
    ): List<PromptMessage> {
        val messages = mutableListOf<PromptMessage>()

        for (message in history) {
            val role = when (message.role) {
                MessageRole.SYSTEM -> MessageRole.SYSTEM
                MessageRole.USER -> MessageRole.USER
                MessageRole.ASSISTANT -> MessageRole.ASSISTANT
            }
            val resolved = resolve(message.content, variables)
            // IM 消息在叙事提示词中改写为手机动作描述
            val text = if (message.role == MessageRole.USER && message.isIm) {
                "{{user}}给{{char}}发去手机消息：「$resolved」"
            } else {
                resolved
            }
            messages.add(PromptMessage(role, resolve(text, variables)))
        }

        // 作者注释插入（depth > 0 时插入到距末尾 depth 条消息之后）
        if (config.authorNote.isNotBlank() && config.authorNoteDepth > 0) {
            val insertIndex = (messages.size - config.authorNoteDepth).coerceAtLeast(0)
            val noteMessage = PromptMessage(
                MessageRole.SYSTEM,
                resolve(config.authorNote, variables)
            )
            messages.add(insertIndex, noteMessage)
        }

        return messages
    }

    private fun resolve(text: String, variables: Map<String, String>): String =
        VariableResolver.resolve(text, variables)
}
