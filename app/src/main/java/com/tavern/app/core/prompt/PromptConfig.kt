package com.tavern.app.core.prompt

import com.tavern.app.core.model.MessageRole

/**
 * 提示词消息（统一的中间格式，各 Provider 适配成自己的 API 格式）
 */
data class PromptMessage(
    val role: MessageRole,
    val content: String
)

/**
 * 提示词构建配置
 */
data class PromptConfig(
    /** 是否注入角色定义（description/personality/scenario） */
    val includeDefinition: Boolean = true,

    /** 是否注入消息示例 */
    val includeMessageExample: Boolean = true,

    /** 是否注入世界书激活条目 */
    val includeWorldBook: Boolean = true,

    /** 是否注入首条消息（作为 AI 的第一条消息） */
    val includeFirstMessage: Boolean = true,

    /** 作者注释（Author's Note），空则不注入 */
    val authorNote: String = "",

    /** 作者注释插入深度（距末尾第几条消息后插入，0 表示追加到系统提示词） */
    val authorNoteDepth: Int = 0,

    /** 状态栏协议说明（空则不注入） */
    val statusProtocol: String = "",

    /** 联动旁白协议说明（空则不注入，聊天模式注入） */
    val narrationProtocol: String = "",

    /** 插件提示词注入片段（INJECT_PROMPT 规则收集，追加到系统提示词末尾） */
    val pluginInjections: String = "",

    /** 会话滚动摘要（前情提要） */
    val chatSummary: String = "",

    /** 长期记忆检索片段 */
    val memoryFacts: String = "",

    /** 上下文 token 预算（>0 时裁剪历史；0=不限制） */
    val budgetTokens: Int = 0
)
