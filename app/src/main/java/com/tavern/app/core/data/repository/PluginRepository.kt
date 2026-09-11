package com.tavern.app.core.data.repository

import com.tavern.app.core.data.local.dao.PluginDao
import com.tavern.app.core.data.local.entity.PluginEntity
import com.tavern.app.core.model.Plugin
import com.tavern.app.core.model.PluginRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 插件仓库
 *
 * 管理插件的持久化与转换。
 */
@Singleton
class PluginRepository @Inject constructor(
    private val dao: PluginDao
) {

    private val json = Json { ignoreUnknownKeys = true }

    /** 所有插件（实时） */
    fun getAllPluginsFlow(): Flow<List<Plugin>> =
        dao.getAllFlow().map { list -> list.map { it.toModel() } }

    /**
     * 启用的插件（作用于指定角色卡）：
     * 全局插件 + 绑定该卡的插件
     */
    suspend fun getEnabledPlugins(characterCardId: Long): List<Plugin> =
        dao.getEnabled()
            .map { it.toModel() }
            .filter { it.characterCardId == null || it.characterCardId == characterCardId }

    /** 保存插件（返回 id） */
    suspend fun save(plugin: Plugin): Long = dao.insert(plugin.toEntity())

    /** 更新插件 */
    suspend fun update(plugin: Plugin) = dao.update(plugin.toEntity())

    /** 删除插件 */
    suspend fun delete(id: Long) = dao.deleteById(id)

    /** 启停插件 */
    suspend fun setEnabled(id: Long, enabled: Boolean) {
        val entity = dao.getById(id) ?: return
        dao.update(entity.copy(enabled = enabled))
    }

    // ===== 转换 =====

    private fun Plugin.toEntity(): PluginEntity = PluginEntity(
        id = id,
        name = name,
        version = version,
        description = description,
        author = author,
        enabled = enabled,
        isBuiltin = isBuiltin,
        characterCardId = characterCardId,
        rulesJson = json.encodeToString(ListSerializer(PluginRule.serializer()), rules)
    )

    private fun PluginEntity.toModel(): Plugin = Plugin(
        id = id,
        name = name,
        version = version,
        description = description,
        author = author,
        enabled = enabled,
        isBuiltin = isBuiltin,
        characterCardId = characterCardId,
        rules = try {
            json.decodeFromString(ListSerializer(PluginRule.serializer()), rulesJson)
        } catch (e: Exception) {
            emptyList()
        }
    )
}
