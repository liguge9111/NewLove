package com.tavern.app.core.data.repository

import com.tavern.app.core.data.local.dao.CharacterStateDao
import com.tavern.app.core.data.local.entity.CharacterStateEntity
import com.tavern.app.core.model.CharacterState
import com.tavern.app.core.model.StatusDelta
import com.tavern.app.core.model.StatusItemDef
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 角色状态仓库
 *
 * 管理角色状态（按卡状态模板 + 数值）的读取与更新。
 * 数值只被 [STATUS] 增量修改，绝不随对话重新生成。
 */
@Singleton
class CharacterStateRepository @Inject constructor(
    private val dao: CharacterStateDao
) {

    /** 监听角色状态（实时） */
    fun getStateFlow(cardId: Long): Flow<CharacterState?> =
        dao.getStateFlow(cardId).map { it?.toModel() }

    /** 获取角色状态 */
    suspend fun getState(cardId: Long): CharacterState? =
        dao.getState(cardId)?.toModel()

    /** 获取或创建默认状态 */
    suspend fun getOrCreate(cardId: Long): CharacterState {
        val existing = dao.getState(cardId)
        if (existing != null) return existing.toModel()
        val state = CharacterStateEntity(characterCardId = cardId)
        dao.insert(state)
        return state.toModel()
    }

    /** 直接更新状态（保留状态栏模板） */
    suspend fun updateState(state: CharacterState) {
        val entity = dao.getState(state.characterCardId)
            ?: CharacterStateEntity(characterCardId = state.characterCardId)
        val updated = state.toEntity()
        dao.update(entity.copy(
            mood = updated.mood,
            energy = updated.energy,
            affection = updated.affection,
            customStates = updated.customStates,
            lastUpdatedAt = updated.lastUpdatedAt
        ))
    }

    /** 获取状态栏模板（空列表表示尚未定义） */
    suspend fun getSchema(cardId: Long): List<StatusItemDef> =
        dao.getState(cardId)?.statusSchema?.decodeSchema() ?: emptyList()

    /** 保存状态栏模板 */
    suspend fun updateSchema(cardId: Long, schema: List<StatusItemDef>) {
        val entity = dao.getState(cardId) ?: CharacterStateEntity(characterCardId = cardId)
        dao.update(entity.copy(statusSchema = schema.encodeSchema()))
    }

    /**
     * 应用状态增量（自动钳制到 0-100 区间），返回更新后的状态
     */
    suspend fun applyDelta(cardId: Long, delta: StatusDelta): CharacterState {
        val entity = dao.getState(cardId) ?: CharacterStateEntity(characterCardId = cardId)
        if (delta.isEmpty) return entity.toModel()

        val current = entity.toModel()
        val updatedCustom = current.customStates.toMutableMap()
        delta.custom.forEach { (key, value) ->
            updatedCustom[key] = ((updatedCustom[key] ?: 0) + value).coerceIn(0, 100)
        }

        val mood = (current.mood + delta.mood).coerceIn(0, 100)
        val energy = (current.energy + delta.energy).coerceIn(0, 100)
        val affection = (current.affection + delta.affection).coerceIn(0, 100)
        val customJson = updatedCustom.encodeToJson()

        dao.update(entity.copy(
            mood = mood,
            energy = energy,
            affection = affection,
            customStates = customJson,
            lastUpdatedAt = System.currentTimeMillis()
        ))
        return entity.copy(
            mood = mood,
            energy = energy,
            affection = affection,
            customStates = customJson,
            lastUpdatedAt = System.currentTimeMillis()
        ).toModel()
    }

    private fun Map<String, Int>.encodeToJson(): String =
        CharacterState(customStates = this).toEntity().customStates
}
