package com.tavern.app.core.data.local.dao

import androidx.room.*
import com.tavern.app.core.data.local.entity.CharacterMemoryEntity
import com.tavern.app.core.data.local.entity.ChatSummaryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoryDao {

    // ===== 会话摘要 =====

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSummary(summary: ChatSummaryEntity): Long

    @Query("SELECT * FROM chat_summaries WHERE sessionId = :sessionId ORDER BY roundIndex DESC LIMIT 1")
    suspend fun getLatestSummary(sessionId: Long): ChatSummaryEntity?

    @Query("SELECT * FROM chat_summaries WHERE sessionId = :sessionId ORDER BY roundIndex DESC")
    fun getSummariesFlow(sessionId: Long): Flow<List<ChatSummaryEntity>>

    @Query("SELECT * FROM chat_summaries WHERE sessionId = :sessionId ORDER BY roundIndex DESC")
    suspend fun getSummaries(sessionId: Long): List<ChatSummaryEntity>

    @Query("DELETE FROM chat_summaries WHERE sessionId = :sessionId")
    suspend fun deleteSummariesBySession(sessionId: Long)

    // ===== 角色记忆 =====

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemory(memory: CharacterMemoryEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemories(memories: List<CharacterMemoryEntity>)

    @Update
    suspend fun updateMemory(memory: CharacterMemoryEntity)

    @Query("DELETE FROM character_memories WHERE id = :id")
    suspend fun deleteMemory(id: Long)

    @Query("DELETE FROM character_memories WHERE cardId = :cardId")
    suspend fun clearMemories(cardId: Long)

    @Query("SELECT * FROM character_memories WHERE cardId = :cardId ORDER BY createdAt DESC")
    fun getMemoriesFlow(cardId: Long): Flow<List<CharacterMemoryEntity>>

    @Query("SELECT * FROM character_memories WHERE cardId = :cardId ORDER BY createdAt DESC")
    suspend fun getMemories(cardId: Long): List<CharacterMemoryEntity>

    @Query("SELECT * FROM character_memories WHERE sessionId = :sessionId ORDER BY createdAt DESC")
    fun getMemoriesBySessionFlow(sessionId: Long): Flow<List<CharacterMemoryEntity>>

    @Query("SELECT * FROM character_memories WHERE sessionId = :sessionId ORDER BY createdAt DESC")
    suspend fun getMemoriesBySession(sessionId: Long): List<CharacterMemoryEntity>

    @Query("DELETE FROM character_memories WHERE sessionId = :sessionId")
    suspend fun clearMemoriesBySession(sessionId: Long)

    @Query("SELECT COUNT(*) FROM character_memories WHERE cardId = :cardId")
    suspend fun countMemories(cardId: Long): Int
}
