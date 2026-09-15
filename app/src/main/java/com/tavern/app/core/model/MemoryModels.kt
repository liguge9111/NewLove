package com.tavern.app.core.model

/**
 * 会话滚动摘要（前情提要）
 */
data class ChatSummary(
    val id: Long = 0,
    val sessionId: Long = 0,
    val roundIndex: Int = 1,
    val summary: String = "",
    val waterMarkMessageId: Long = 0,
    val createdAt: Long = 0
)

/**
 * 长期记忆（ADD-only 事实，按会话隔离）
 */
data class CharacterMemory(
    val id: Long = 0,
    val cardId: Long = 0,
    /** 所属会话（0 = 旧版按角色的记忆） */
    val sessionId: Long = 0,
    val content: String = "",
    val tags: String = "",
    /** auto / manual */
    val source: String = "auto",
    val sourceMessageId: Long = -1,
    /** 向量（JSON，可空） */
    val embedding: String? = null,
    val createdAt: Long = 0
)
