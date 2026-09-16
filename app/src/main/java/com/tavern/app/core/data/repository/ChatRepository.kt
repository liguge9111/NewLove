package com.tavern.app.core.data.repository

import com.tavern.app.core.data.local.dao.ChatDao
import com.tavern.app.core.data.local.dao.SessionWithPreview
import com.tavern.app.core.data.local.entity.ChatMessageEntity
import com.tavern.app.core.data.local.entity.ChatSessionEntity
import com.tavern.app.core.model.InteractiveMode
import com.tavern.app.core.model.MessageRole
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 聊天仓库
 *
 * 管理会话和消息的持久化。
 */
@Singleton
class ChatRepository @Inject constructor(
    private val chatDao: ChatDao
) {

    // ===== 会话 =====

    suspend fun createSession(
        cardId: Long,
        title: String = "",
        mode: InteractiveMode = InteractiveMode.TEXT_ADVENTURE
    ): Long {
        val session = ChatSessionEntity(
            characterCardId = cardId,
            title = title.ifBlank { "新会话" },
            interactiveMode = mode.name
        )
        return chatDao.insertSession(session)
    }

    suspend fun getSession(id: Long): ChatSessionEntity? = chatDao.getSession(id)

    suspend fun getSessionsForCard(cardId: Long): List<ChatSessionEntity> =
        chatDao.getSessionsForCard(cardId)

    fun getAllSessionsFlow(): Flow<List<ChatSessionEntity>> = chatDao.getAllSessionsFlow()

    /** 游玩记录（会话+角色+最后消息预览），按最后活跃排序 */
    fun getSessionsWithPreviewFlow(): Flow<List<SessionWithPreview>> =
        chatDao.getSessionsWithPreviewFlow()

    suspend fun renameSession(id: Long, newTitle: String) {
        val session = chatDao.getSession(id) ?: return
        chatDao.updateSession(session.copy(title = newTitle))
    }

    suspend fun updateSessionMode(id: Long, mode: InteractiveMode) {
        val session = chatDao.getSession(id) ?: return
        chatDao.updateSession(session.copy(interactiveMode = mode.name))
    }

    /**
     * 设置会话使用的模型（modelConfigId < 0 表示跟随全局默认）
     */
    suspend fun updateSessionModelConfig(id: Long, modelConfigId: Long) {
        val session = chatDao.getSession(id) ?: return
        chatDao.updateSession(session.copy(modelConfigId = modelConfigId))
    }

    suspend fun touchSession(id: Long) {
        val session = chatDao.getSession(id) ?: return
        chatDao.updateSession(session.copy(lastActiveAt = System.currentTimeMillis()))
    }

    /** 累加会话 token 用量 */
    suspend fun addTokenUsage(sessionId: Long, tokens: Int) {
        if (tokens <= 0) return
        val session = chatDao.getSession(sessionId) ?: return
        chatDao.updateSession(
            session.copy(
                tokenUsage = session.tokenUsage + tokens,
                lastActiveAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun archiveSession(id: Long, archived: Boolean = true) {
        val session = chatDao.getSession(id) ?: return
        chatDao.updateSession(session.copy(isArchived = archived))
    }

    suspend fun deleteSession(id: Long) = chatDao.deleteSessionById(id)

    // ===== 消息 =====

    suspend fun addMessage(
        sessionId: Long,
        role: MessageRole,
        content: String,
        isVoice: Boolean = false,
        voicePath: String? = null,
        voiceDuration: Float? = null,
        hasImage: Boolean = false,
        imagePath: String? = null,
        narration: String? = null,
        statusPanel: String? = null,
        isIm: Boolean = false,
        imContent: String? = null,
        parentMessageId: Long = -1,
        branchIndex: Int = 0,
        isProactive: Boolean = false,
        timestamp: Long = System.currentTimeMillis()
    ): Long {
        val message = ChatMessageEntity(
            sessionId = sessionId,
            role = role.name,
            content = content,
            isVoice = isVoice,
            voicePath = voicePath,
            voiceDuration = voiceDuration,
            hasImage = hasImage,
            imagePath = imagePath,
            narration = narration,
            statusPanel = statusPanel,
            isIm = isIm,
            imContent = imContent,
            parentMessageId = parentMessageId,
            branchIndex = branchIndex,
            isProactive = isProactive,
            timestamp = timestamp
        )
        val id = chatDao.insertMessage(message)
        touchSession(sessionId)
        return id
    }

    suspend fun updateMessageContent(id: Long, newContent: String) {
        val message = chatDao.getMessage(id) ?: return
        chatDao.updateMessage(message.copy(content = newContent))
    }

    suspend fun deleteMessage(id: Long) = chatDao.deleteMessageById(id)

    suspend fun getMessages(sessionId: Long): List<ChatMessageEntity> =
        chatDao.getMessages(sessionId)

    fun getMessagesFlow(sessionId: Long): Flow<List<ChatMessageEntity>> =
        chatDao.getMessagesFlow(sessionId)

    suspend fun getLastMessages(sessionId: Long, limit: Int): List<ChatMessageEntity> =
        chatDao.getLastMessages(sessionId, limit)
}
