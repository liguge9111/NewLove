package com.tavern.app.core.data.local.dao

import androidx.room.*
import com.tavern.app.core.data.local.entity.ChatMessageEntity
import com.tavern.app.core.data.local.entity.ChatSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {

    // ===== 会话 =====

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: ChatSessionEntity): Long

    @Update
    suspend fun updateSession(session: ChatSessionEntity)

    @Delete
    suspend fun deleteSession(session: ChatSessionEntity)

    @Query("DELETE FROM chat_sessions WHERE id = :id")
    suspend fun deleteSessionById(id: Long)

    @Query("SELECT * FROM chat_sessions WHERE id = :id")
    suspend fun getSession(id: Long): ChatSessionEntity?

    @Query("SELECT * FROM chat_sessions WHERE characterCardId = :cardId AND isArchived = 0 ORDER BY lastActiveAt DESC")
    suspend fun getSessionsForCard(cardId: Long): List<ChatSessionEntity>

    @Query("SELECT * FROM chat_sessions WHERE isArchived = 0 ORDER BY lastActiveAt DESC")
    fun getAllSessionsFlow(): Flow<List<ChatSessionEntity>>

    // ===== 消息 =====

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<ChatMessageEntity>)

    @Update
    suspend fun updateMessage(message: ChatMessageEntity)

    @Delete
    suspend fun deleteMessage(message: ChatMessageEntity)

    @Query("DELETE FROM chat_messages WHERE id = :id")
    suspend fun deleteMessageById(id: Long)

    @Query("DELETE FROM chat_messages WHERE sessionId = :sessionId")
    suspend fun deleteMessagesBySession(sessionId: Long)

    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getMessages(sessionId: Long): List<ChatMessageEntity>

    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getMessagesFlow(sessionId: Long): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages WHERE id = :id")
    suspend fun getMessage(id: Long): ChatMessageEntity?

    @Query("SELECT COUNT(*) FROM chat_messages WHERE sessionId = :sessionId")
    suspend fun getMessageCount(sessionId: Long): Int

    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getLastMessages(sessionId: Long, limit: Int): List<ChatMessageEntity>

    /**
     * 游玩记录：会话 + 角色名/头像 + 最后一条消息，按最后活跃排序
     */
    @Query(
        """
        SELECT s.*, c.name AS characterName, c.filePath AS characterFilePath,
            (SELECT m.content FROM chat_messages m
             WHERE m.sessionId = s.id AND m.content != ''
             ORDER BY m.timestamp DESC LIMIT 1) AS lastMessage
        FROM chat_sessions s
        INNER JOIN character_cards c ON c.id = s.characterCardId
        WHERE s.isArchived = 0
        ORDER BY s.lastActiveAt DESC
        """
    )
    fun getSessionsWithPreviewFlow(): Flow<List<SessionWithPreview>>
}

/**
 * 游玩记录条目（会话 + 角色信息 + 最后消息预览）
 */
data class SessionWithPreview(
    @Embedded
    val session: ChatSessionEntity,

    val characterName: String,

    val characterFilePath: String?,

    val lastMessage: String?
)
