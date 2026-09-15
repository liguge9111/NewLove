package com.tavern.app.core.data.repository

import com.tavern.app.core.data.local.dao.MemoryDao
import com.tavern.app.core.data.local.entity.CharacterMemoryEntity
import com.tavern.app.core.data.local.entity.ChatSummaryEntity
import com.tavern.app.core.model.CharacterMemory
import com.tavern.app.core.model.ChatSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 记忆仓库：会话滚动摘要 + 角色长期记忆
 */
@Singleton
class MemoryRepository @Inject constructor(
    private val dao: MemoryDao
) {

    // ===== 摘要 =====

    suspend fun getLatestSummary(sessionId: Long): ChatSummary? =
        dao.getLatestSummary(sessionId)?.toModel()

    fun getSummariesFlow(sessionId: Long): Flow<List<ChatSummary>> =
        dao.getSummariesFlow(sessionId).map { list -> list.map { it.toModel() } }

    suspend fun saveSummary(sessionId: Long, roundIndex: Int, summary: String, waterMarkId: Long): Long =
        dao.insertSummary(
            ChatSummaryEntity(
                sessionId = sessionId,
                roundIndex = roundIndex,
                summary = summary,
                waterMarkMessageId = waterMarkId
            )
        )

    // ===== 长期记忆 =====

    /** 会话记忆时间线（含旧版按角色记忆 sessionId=0 兼容） */
    fun getMemoriesForTimelineFlow(cardId: Long, sessionId: Long): Flow<List<CharacterMemory>> =
        dao.getMemoriesFlow(cardId)
            .map { list ->
                list.map { it.toModel() }
                    .filter { it.sessionId == sessionId || it.sessionId == 0L }
            }

    suspend fun getMemoriesBySession(sessionId: Long): List<CharacterMemory> =
        dao.getMemoriesBySession(sessionId).map { it.toModel() }

    /** 追加记忆（按会话隔离；自动去重），返回是否真正插入 */
    suspend fun addMemory(
        cardId: Long,
        sessionId: Long,
        content: String,
        source: String = "auto",
        sourceMessageId: Long = -1,
        embedding: String? = null
    ): Boolean {
        val trimmed = content.trim()
        if (trimmed.length < 4) return false
        val existing = dao.getMemoriesBySession(sessionId).map { it.toModel() } +
            dao.getMemories(cardId).map { it.toModel() }.filter { it.sessionId == 0L }
        if (existing.any { isSimilar(it.content, trimmed) }) return false
        // 容量上限：超过 200 条淘汰最旧的 auto 记忆
        if (existing.size >= 200) {
            existing.lastOrNull { it.source == "auto" }?.let { dao.deleteMemory(it.id) }
        }
        dao.insertMemory(
            CharacterMemoryEntity(
                cardId = cardId,
                sessionId = sessionId,
                content = trimmed,
                source = source,
                sourceMessageId = sourceMessageId,
                embedding = embedding
            )
        )
        return true
    }

    suspend fun updateMemory(memory: CharacterMemory) {
        dao.updateMemory(memory.toEntity())
    }

    suspend fun deleteMemory(id: Long) = dao.deleteMemory(id)

    /** 清空某会话的全部记忆（含手动） */
    suspend fun clearMemoriesBySession(sessionId: Long) = dao.clearMemoriesBySession(sessionId)

    /** 清空某角色全部记忆（含旧版） */
    suspend fun clearMemories(cardId: Long) = dao.clearMemories(cardId)

    /**
     * 检索记忆：向量可用时按余弦相似度，否则关键词 bigram
     *
     * @param queryVector 可选的查询向量（与记忆 embedding 同空间）
     */
    suspend fun searchMemories(
        cardId: Long,
        sessionId: Long,
        query: String,
        topK: Int,
        queryVector: List<Float>? = null
    ): List<CharacterMemory> {
        val sessionMemories = dao.getMemoriesBySession(sessionId).map { it.toModel() }
        val legacy = dao.getMemories(cardId).map { it.toModel() }.filter { it.sessionId == 0L }
        val memories = sessionMemories + legacy
        if (memories.isEmpty()) return emptyList()

        // 向量路径
        if (queryVector != null) {
            return memories
                .mapNotNull { m ->
                    parseEmbedding(m.embedding)?.let { m to cosine(queryVector, it) }
                }
                .filter { it.second > 0.05f }
                .sortedByDescending { it.second }
                .take(topK)
                .map { it.first }
                .ifEmpty { memories.take(topK) }
        }

        // 关键词回落
        if (query.isBlank()) return memories.take(topK)
        val queryBigrams = bigrams(query)
        if (queryBigrams.isEmpty()) return memories.take(topK)
        return memories
            .map { it to score(it.content, queryBigrams) }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .take(topK)
            .map { it.first }
    }

    /** 解析 JSON 向量 */
    fun parseEmbedding(jsonStr: String?): List<Float>? {
        if (jsonStr.isNullOrBlank()) return null
        return try {
            kotlinx.serialization.json.Json.decodeFromString(
                ListSerializer(Float.serializer()),
                jsonStr
            )
        } catch (e: Exception) {
            null
        }
    }

    fun encodeEmbedding(vector: List<Float>): String =
        kotlinx.serialization.json.Json.encodeToString(
            ListSerializer(Float.serializer()),
            vector
        )

    fun cosine(a: List<Float>, b: List<Float>): Float {
        if (a.isEmpty() || a.size != b.size) return 0f
        var dot = 0f
        var na = 0f
        var nb = 0f
        for (i in a.indices) {
            dot += a[i] * b[i]
            na += a[i] * a[i]
            nb += b[i] * b[i]
        }
        val denom = kotlin.math.sqrt(na) * kotlin.math.sqrt(nb)
        return if (denom == 0f) 0f else dot / denom
    }

    private fun bigrams(text: String): Set<String> {
        val clean = text.filter { it.isLetterOrDigit() || it.code > 0x2E80 }
        if (clean.length < 2) return emptySet()
        return (0 until clean.length - 1).map { clean.substring(it, it + 2) }.toSet()
    }

    private fun score(content: String, queryBigrams: Set<String>): Int {
        val contentBigrams = bigrams(content)
        if (contentBigrams.isEmpty()) return 0
        return contentBigrams.count { it in queryBigrams }
    }

    private fun isSimilar(a: String, b: String): Boolean {
        if (a == b) return true
        if (a.contains(b) || b.contains(a)) return a.length >= 8 && b.length >= 8
        val ba = bigrams(a)
        val bb = bigrams(b)
        if (ba.isEmpty() || bb.isEmpty()) return false
        val overlap = ba.count { it in bb }.toFloat()
        return overlap / maxOf(ba.size, bb.size) > 0.75f
    }

    private fun ChatSummaryEntity.toModel() = ChatSummary(
        id, sessionId, roundIndex, summary, waterMarkMessageId, createdAt
    )

    private fun CharacterMemoryEntity.toModel() = CharacterMemory(
        id, cardId, sessionId, content, tags, source, sourceMessageId, embedding, createdAt
    )

    private fun CharacterMemory.toEntity() = CharacterMemoryEntity(
        id, cardId, sessionId, content, tags, source, sourceMessageId, embedding, createdAt
    )
}
