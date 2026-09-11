package com.tavern.app.core.model

/**
 * 消息角色
 */
enum class MessageRole {
    /** 系统消息 */
    SYSTEM,

    /** 用户消息 */
    USER,

    /** AI 助手消息 */
    ASSISTANT
}

/**
 * 聊天消息
 */
data class ChatMessage(
    /** 消息 ID */
    val id: Long = 0,

    /** 所属会话 ID */
    val sessionId: Long = 0,

    /** 消息角色 */
    val role: MessageRole = MessageRole.USER,

    /** 消息内容（纯文本） */
    val content: String = "",

    /** 时间戳 */
    val timestamp: Long = System.currentTimeMillis(),

    /** 是否为语音消息 */
    val isVoice: Boolean = false,

    /** 语音文件路径（isVoice 为 true 时有效） */
    val voicePath: String? = null,

    /** 语音时长（秒） */
    val voiceDuration: Float? = null,

    /** 是否包含图片 */
    val hasImage: Boolean = false,

    /** 图片文件路径（hasImage 为 true 时有效） */
    val imagePath: String? = null,

    /** 消息令牌数（可选，用于上下文管理） */
    val tokenCount: Int = 0,

    /** 是否被收藏 */
    val isFavorite: Boolean = false,

    /** 是否为分支消息 */
    val isBranch: Boolean = false,

    /** 分支父消息 ID（-1 表示无） */
    val parentMessageId: Long = -1,

    /** 是否为手机聊天（IM）消息 */
    val isIm: Boolean = false,

    /** IM 聊天回复内容 */
    val imContent: String? = null
)

/**
 * 聊天会话
 */
data class ChatSession(
    /** 会话 ID */
    val id: Long = 0,

    /** 关联角色卡 ID */
    val characterCardId: Long = 0,

    /** 会话标题 */
    val title: String = "",

    /** 创建时间 */
    val createdAt: Long = System.currentTimeMillis(),

    /** 最后活跃时间 */
    val lastActiveAt: Long = System.currentTimeMillis(),

    /** 当前互动模式 */
    val interactiveMode: InteractiveMode = InteractiveMode.CHAT,

    /** 是否归档 */
    val isArchived: Boolean = false
)

/**
 * 互动模式枚举
 */
enum class InteractiveMode {
    /** 聊天模式 */
    CHAT,

    /** 文字冒险模式 */
    TEXT_ADVENTURE,

    /** 视觉小说模式 */
    VISUAL_NOVEL,

    /** 自由角色扮演模式 */
    FREE_ROLEPLAY
}

/**
 * 角色状态（状态栏数据）
 */
data class CharacterState(
    /** 关联角色卡 ID */
    val characterCardId: Long = 0,

    /** 心情（0-100） */
    val mood: Int = 50,

    /** 体力（0-100） */
    val energy: Int = 100,

    /** 好感度（0-100） */
    val affection: Int = 0,

    /** 自定义状态（key: 状态名, value: 数值 0-100） */
    val customStates: Map<String, Int> = emptyMap(),

    /** 最后更新时间 */
    val lastUpdatedAt: Long = System.currentTimeMillis()
)
