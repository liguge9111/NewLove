package com.tavern.app.core.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 会话滚动摘要（前情提要）
 */
@Entity(
    tableName = "chat_summaries",
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
data class ChatSummaryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val sessionId: Long = 0,

    /** 第几代摘要（1 起） */
    val roundIndex: Int = 1,

    val summary: String = "",

    /** 已摘要到的消息水位线（含） */
    val waterMarkMessageId: Long = 0,

    val createdAt: Long = System.currentTimeMillis()
)

/**
 * 角色长期记忆（Mem0 式 ADD-only 事实）
 */
@Entity(
    tableName = "character_memories",
    foreignKeys = [
        ForeignKey(
            entity = CharacterCardEntity::class,
            parentColumns = ["id"],
            childColumns = ["cardId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("cardId"), Index("sessionId")]
)
data class CharacterMemoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val cardId: Long = 0,

    /** 所属会话（0 = 旧版按角色的记忆，兼容保留） */
    val sessionId: Long = 0,

    /** 事实内容（一句话） */
    val content: String = "",

    /** 标签（逗号分隔，可空） */
    val tags: String = "",

    /** auto = 模型抽取；manual = 用户手动添加 */
    val source: String = "auto",

    /** 来源消息 ID（manual 为 -1） */
    val sourceMessageId: Long = -1,

    /** 向量（JSON float 数组，未启用向量检索时为 null） */
    val embedding: String? = null,

    val createdAt: Long = System.currentTimeMillis()
)
