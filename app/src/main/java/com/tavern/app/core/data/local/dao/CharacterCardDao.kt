package com.tavern.app.core.data.local.dao

import androidx.room.*
import com.tavern.app.core.data.local.entity.CharacterCardEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CharacterCardDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(card: CharacterCardEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(cards: List<CharacterCardEntity>): List<Long>

    @Update
    suspend fun update(card: CharacterCardEntity)

    @Delete
    suspend fun delete(card: CharacterCardEntity)

    @Query("SELECT * FROM character_cards WHERE id = :id")
    suspend fun getById(id: Long): CharacterCardEntity?

    @Query("SELECT * FROM character_cards ORDER BY importedAt DESC")
    suspend fun getAll(): List<CharacterCardEntity>

    @Query("SELECT * FROM character_cards ORDER BY importedAt DESC")
    fun getAllFlow(): Flow<List<CharacterCardEntity>>

    @Query("SELECT * FROM character_cards WHERE name LIKE '%' || :query || '%' OR creator LIKE '%' || :query || '%' ORDER BY importedAt DESC")
    suspend fun search(query: String): List<CharacterCardEntity>

    @Query("SELECT * FROM character_cards WHERE tags LIKE '%' || :tag || '%'")
    suspend fun getByTag(tag: String): List<CharacterCardEntity>

    @Query("SELECT * FROM character_cards WHERE isFavorite = 1")
    suspend fun getFavorites(): List<CharacterCardEntity>

    @Query("DELETE FROM character_cards WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM character_cards")
    suspend fun deleteAll()

    /**
     * 角色卡 + 最后互动信息（列表页预览用）
     * 按最后活跃时间倒序，无会话的按导入时间
     */
    @Query(
        """
        SELECT c.*,
            (SELECT m.content FROM chat_messages m
             INNER JOIN chat_sessions s ON m.sessionId = s.id
             WHERE s.characterCardId = c.id AND m.content != ''
             ORDER BY m.timestamp DESC LIMIT 1) AS lastMessage,
            (SELECT MAX(s2.lastActiveAt) FROM chat_sessions s2
             WHERE s2.characterCardId = c.id) AS lastActiveAt
        FROM character_cards c
        ORDER BY COALESCE(lastActiveAt, c.importedAt) DESC
        """
    )
    fun getAllWithLastMessageFlow(): Flow<List<CharacterCardWithPreview>>
}

/**
 * 角色卡 + 最后一条消息预览（列表页展示用）
 */
data class CharacterCardWithPreview(
    @Embedded
    val card: CharacterCardEntity,

    /** 最后一条消息内容（无会话时为 null） */
    val lastMessage: String?,

    /** 最后活跃时间（无会话时为 null） */
    val lastActiveAt: Long?
)
