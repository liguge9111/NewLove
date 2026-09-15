package com.tavern.app.core.data.repository

import com.tavern.app.core.data.local.dao.CharacterCardDao
import com.tavern.app.core.data.local.dao.CharacterCardWithPreview
import com.tavern.app.core.data.local.entity.CharacterCardEntity
import com.tavern.app.core.model.CharacterCard
import com.tavern.app.core.parser.CharacterCardLoader
import com.tavern.app.core.parser.CharacterCardParser
import kotlinx.coroutines.flow.Flow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 角色卡仓库
 *
 * 组合解析器与 DAO，提供角色卡的导入、查询、删除等操作。
 */
@Singleton
class CharacterRepository @Inject constructor(
    private val dao: CharacterCardDao
) {

    /**
     * 从 JSON 字符串导入角色卡
     *
     * @return 导入后的卡片 ID，失败返回 null
     */
    suspend fun importFromJson(jsonString: String, filePath: String? = null): Long? {
        val card = CharacterCardParser.parse(jsonString) ?: return null
        return insertCard(card, filePath)
    }

    /**
     * 从文件导入角色卡（.png 或 .json）
     */
    suspend fun importFromFile(file: File): Long? {
        val card = CharacterCardLoader.loadFromFile(file) ?: return null
        return insertCard(card, file.absolutePath)
    }

    /**
     * 从字节数组导入角色卡（根据扩展名路由）
     *
     * @param filePath 可选：已持久化的原图路径（PNG 卡用于视觉小说模式立绘）
     */
    suspend fun importFromBytes(
        bytes: ByteArray,
        extension: String,
        filePath: String? = null
    ): Long? {
        val card = CharacterCardLoader.loadFromBytes(bytes, extension) ?: return null
        return insertCard(card, filePath)
    }

    private suspend fun insertCard(card: CharacterCard, filePath: String?): Long {
        val entity = card.toEntity(filePath)
        return dao.insert(entity)
    }

    /**
     * 获取所有角色卡（Flow）
     */
    fun getAllCardsFlow(): Flow<List<CharacterCardEntity>> = dao.getAllFlow()

    /**
     * 角色卡 + 最后互动预览（按最后活跃排序，列表页用）
     */
    fun getAllCardsWithPreviewFlow(): Flow<List<CharacterCardWithPreview>> =
        dao.getAllWithLastMessageFlow()

    /**
     * 获取所有角色卡（一次性）
     */
    suspend fun getAllCards(): List<CharacterCardEntity> = dao.getAll()

    /**
     * 获取角色卡实体（含 filePath 等元信息）
     */
    suspend fun getCardEntity(id: Long): CharacterCardEntity? = dao.getById(id)

    /**
     * 获取角色卡完整模型（解析 rawJson）
     */
    suspend fun getCardModel(id: Long): CharacterCard? {
        val entity = dao.getById(id) ?: return null
        return entity.toModel()
    }

    /**
     * 搜索角色卡
     */
    suspend fun search(query: String): List<CharacterCardEntity> = dao.search(query)

    /**
     * 获取收藏的角色卡
     */
    suspend fun getFavorites(): List<CharacterCardEntity> = dao.getFavorites()

    /**
     * 删除角色卡
     */
    suspend fun deleteCard(id: Long) = dao.deleteById(id)

    /**
     * 更新收藏状态
     */
    suspend fun updateFavorite(id: Long, isFavorite: Boolean) {
        val entity = dao.getById(id) ?: return
        dao.update(entity.copy(isFavorite = isFavorite))
    }

    /** 重命名角色卡（仅改显示名，不影响 rawJson 其他内容） */
    suspend fun renameCard(id: Long, newName: String) {
        val entity = dao.getById(id) ?: return
        dao.update(entity.copy(name = newName.trim().ifBlank { entity.name }))
    }

    /** 设置自定义头像（JSON 卡无头像时可用；filePath 指向本地图片） */
    suspend fun setAvatarPath(id: Long, path: String) {
        val entity = dao.getById(id) ?: return
        dao.update(entity.copy(filePath = path))
    }

    // ===== 实体与模型转换 =====

    fun CharacterCard.toEntity(filePath: String? = null): CharacterCardEntity =
        CharacterCardEntity(
            name = name,
            description = description,
            tags = tags.joinToString(","),
            creator = creator,
            spec = spec.name,
            rawJson = rawJson?.toString() ?: "",
            filePath = filePath
        )

    fun CharacterCardEntity.toModel(): CharacterCard? {
        if (rawJson.isBlank()) return null
        val parsed = CharacterCardParser.parse(rawJson) ?: return null
        // 显示名以实体列为准（支持用户改名）
        return parsed.copy(name = name.ifBlank { parsed.name })
    }
}
