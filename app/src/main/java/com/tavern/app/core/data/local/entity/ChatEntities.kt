package com.tavern.app.core.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 聊天会话实体
 */
@Entity(
    tableName = "chat_sessions",
    indices = [Index("characterCardId")]
)
data class ChatSessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** 关联角色卡 ID */
    val characterCardId: Long = 0,

    /** 会话标题 */
    val title: String = "",

    /** 创建时间 */
    val createdAt: Long = System.currentTimeMillis(),

    /** 最后活跃时间 */
    val lastActiveAt: Long = System.currentTimeMillis(),

    /** 互动模式（CHAT/TEXT_ADVENTURE/VISUAL_NOVEL/FREE_ROLEPLAY），默认文字冒险 */
    val interactiveMode: String = "TEXT_ADVENTURE",

    /** 会话使用的模型配置 ID（-1 表示跟随全局默认模型） */
    val modelConfigId: Long = -1,

    /** 是否归档 */
    val isArchived: Boolean = false,

    /** 会话累计 token 用量（估算值） */
    val tokenUsage: Long = 0
)

/**
 * 聊天消息实体
 */
@Entity(
    tableName = "chat_messages",
    foreignKeys = [
        ForeignKey(
            entity = ChatSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("sessionId")]
)
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** 所属会话 ID */
    val sessionId: Long = 0,

    /** 消息角色（SYSTEM/USER/ASSISTANT） */
    val role: String = "USER",

    /** 消息内容 */
    val content: String = "",

    /** 联动旁白（CHAT 模式下模型输出的 [NARRATION] 块，供互动模式展示） */
    val narration: String? = null,

    /** 文字状态栏（模型输出的 <Status_block> 内容，供固定状态面板展示） */
    val statusPanel: String? = null,

    /** 是否为手机聊天（IM）消息 */
    val isIm: Boolean = false,

    /** IM 聊天回复（互动回复中 [IM] 块的内容，供微信视图展示） */
    val imContent: String? = null,

    /** 时间戳 */
    val timestamp: Long = System.currentTimeMillis(),

    /** 是否语音消息 */
    val isVoice: Boolean = false,

    /** 语音文件路径 */
    val voicePath: String? = null,

    /** 语音时长（秒） */
    val voiceDuration: Float? = null,

    /** 是否含图片 */
    val hasImage: Boolean = false,

    /** 图片文件路径 */
    val imagePath: String? = null,

    /** 是否收藏 */
    val isFavorite: Boolean = false,

    /** 是否分支消息 */
    val isBranch: Boolean = false,

    /** 分支父消息 ID（-1 表示无） */
    val parentMessageId: Long = -1,

    /** 同一槽位的分支序号（0 起；重生成递增） */
    val branchIndex: Int = 0,

    /** 是否为角色主动消息 */
    val isProactive: Boolean = false
)
