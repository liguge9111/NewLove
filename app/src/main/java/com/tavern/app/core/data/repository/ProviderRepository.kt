package com.tavern.app.core.data.repository

import com.tavern.app.core.data.local.dao.ProviderDao
import com.tavern.app.core.data.local.entity.*
import com.tavern.app.core.model.*
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provider 配置仓库
 *
 * 管理大模型、生图、语音配置的持久化。
 */
@Singleton
class ProviderRepository @Inject constructor(
    private val dao: ProviderDao
) {

    // ===== 大模型配置 =====

    suspend fun addModelConfig(config: ModelConfig): Long =
        dao.insertModelConfig(config.toEntity())

    suspend fun updateModelConfig(config: ModelConfig) =
        dao.updateModelConfig(config.toEntity())

    suspend fun deleteModelConfig(id: Long) = dao.deleteModelConfigById(id)

    fun getAllModelConfigsFlow(): Flow<List<ModelConfigEntity>> =
        dao.getAllModelConfigsFlow()

    suspend fun getModelConfig(id: Long): ModelConfigEntity? =
        dao.getModelConfig(id)

    suspend fun getDefaultModelConfig(): ModelConfigEntity? =
        dao.getDefaultModelConfig()

    suspend fun setDefaultModel(id: Long) {
        dao.clearDefaultModel()
        val config = dao.getModelConfig(id) ?: return
        dao.updateModelConfig(config.copy(isDefault = true))
    }

    // ===== 生图配置 =====

    suspend fun addImageConfig(config: ImageConfig): Long =
        dao.insertImageConfig(config.toEntity())

    suspend fun updateImageConfig(config: ImageConfig) =
        dao.updateImageConfig(config.toEntity())

    suspend fun deleteImageConfig(id: Long) =
        dao.deleteImageConfigById(id)

    suspend fun setDefaultImage(id: Long) {
        dao.clearDefaultImage()
        val config = dao.getImageConfig(id) ?: return
        dao.updateImageConfig(config.copy(isDefault = true))
    }

    fun getAllImageConfigsFlow(): Flow<List<ImageConfigEntity>> =
        dao.getAllImageConfigsFlow()

    suspend fun getDefaultImageConfig(): ImageConfigEntity? =
        dao.getDefaultImageConfig()

    // ===== 语音配置 =====

    suspend fun addVoiceConfig(config: VoiceConfig): Long =
        dao.insertVoiceConfig(config.toEntity())

    suspend fun updateVoiceConfig(config: VoiceConfig) =
        dao.updateVoiceConfig(config.toEntity())

    suspend fun deleteVoiceConfig(id: Long) =
        dao.deleteVoiceConfigById(id)

    suspend fun setDefaultVoice(id: Long) {
        dao.clearDefaultVoice()
        val config = dao.getVoiceConfig(id) ?: return
        dao.updateVoiceConfig(config.copy(isDefault = true))
    }

    fun getAllVoiceConfigsFlow(): Flow<List<VoiceConfigEntity>> =
        dao.getAllVoiceConfigsFlow()

    suspend fun getDefaultVoiceConfig(): VoiceConfigEntity? =
        dao.getDefaultVoiceConfig()

    // ===== 语音识别（ASR）配置 =====

    suspend fun addAsrConfig(config: AsrConfig): Long =
        dao.insertAsrConfig(config.toEntity())

    suspend fun updateAsrConfig(config: AsrConfig) =
        dao.updateAsrConfig(config.toEntity())

    suspend fun deleteAsrConfig(id: Long) =
        dao.deleteAsrConfigById(id)

    suspend fun setDefaultAsr(id: Long) {
        dao.clearDefaultAsr()
        val config = dao.getAsrConfig(id) ?: return
        dao.updateAsrConfig(config.copy(isDefault = true))
    }

    fun getAllAsrConfigsFlow(): Flow<List<AsrConfigEntity>> =
        dao.getAllAsrConfigsFlow()

    suspend fun getDefaultAsrConfig(): AsrConfigEntity? =
        dao.getDefaultAsrConfig()
}
