package com.tavern.app.core.data.local.dao

import androidx.room.*
import com.tavern.app.core.data.local.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ProviderDao {

    // ===== 大模型配置 =====

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertModelConfig(config: ModelConfigEntity): Long

    @Update
    suspend fun updateModelConfig(config: ModelConfigEntity)

    @Delete
    suspend fun deleteModelConfig(config: ModelConfigEntity)

    @Query("DELETE FROM model_configs WHERE id = :id")
    suspend fun deleteModelConfigById(id: Long)

    @Query("SELECT * FROM model_configs WHERE id = :id")
    suspend fun getModelConfig(id: Long): ModelConfigEntity?

    @Query("SELECT * FROM model_configs ORDER BY id ASC")
    fun getAllModelConfigsFlow(): Flow<List<ModelConfigEntity>>

    @Query("SELECT * FROM model_configs WHERE isDefault = 1 LIMIT 1")
    suspend fun getDefaultModelConfig(): ModelConfigEntity?

    @Query("UPDATE model_configs SET isDefault = 0")
    suspend fun clearDefaultModel()

    // ===== 生图配置 =====

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertImageConfig(config: ImageConfigEntity): Long

    @Update
    suspend fun updateImageConfig(config: ImageConfigEntity)

    @Delete
    suspend fun deleteImageConfig(config: ImageConfigEntity)

    @Query("SELECT * FROM image_configs WHERE id = :id")
    suspend fun getImageConfig(id: Long): ImageConfigEntity?

    @Query("SELECT * FROM image_configs ORDER BY id ASC")
    fun getAllImageConfigsFlow(): Flow<List<ImageConfigEntity>>

    @Query("SELECT * FROM image_configs WHERE isDefault = 1 LIMIT 1")
    suspend fun getDefaultImageConfig(): ImageConfigEntity?

    @Query("DELETE FROM image_configs WHERE id = :id")
    suspend fun deleteImageConfigById(id: Long)

    @Query("UPDATE image_configs SET isDefault = 0")
    suspend fun clearDefaultImage()

    // ===== 语音配置 =====

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVoiceConfig(config: VoiceConfigEntity): Long

    @Update
    suspend fun updateVoiceConfig(config: VoiceConfigEntity)

    @Delete
    suspend fun deleteVoiceConfig(config: VoiceConfigEntity)

    @Query("SELECT * FROM voice_configs ORDER BY id ASC")
    fun getAllVoiceConfigsFlow(): Flow<List<VoiceConfigEntity>>

    @Query("SELECT * FROM voice_configs WHERE isDefault = 1 LIMIT 1")
    suspend fun getDefaultVoiceConfig(): VoiceConfigEntity?

    @Query("SELECT * FROM voice_configs WHERE id = :id")
    suspend fun getVoiceConfig(id: Long): VoiceConfigEntity?

    @Query("DELETE FROM voice_configs WHERE id = :id")
    suspend fun deleteVoiceConfigById(id: Long)

    @Query("UPDATE voice_configs SET isDefault = 0")
    suspend fun clearDefaultVoice()

    // ===== 语音识别（ASR）配置 =====

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAsrConfig(config: AsrConfigEntity): Long

    @Update
    suspend fun updateAsrConfig(config: AsrConfigEntity)

    @Delete
    suspend fun deleteAsrConfig(config: AsrConfigEntity)

    @Query("SELECT * FROM asr_configs ORDER BY id ASC")
    fun getAllAsrConfigsFlow(): Flow<List<AsrConfigEntity>>

    @Query("SELECT * FROM asr_configs WHERE isDefault = 1 LIMIT 1")
    suspend fun getDefaultAsrConfig(): AsrConfigEntity?

    @Query("SELECT * FROM asr_configs WHERE id = :id")
    suspend fun getAsrConfig(id: Long): AsrConfigEntity?

    @Query("DELETE FROM asr_configs WHERE id = :id")
    suspend fun deleteAsrConfigById(id: Long)

    @Query("UPDATE asr_configs SET isDefault = 0")
    suspend fun clearDefaultAsr()
}

@Dao
interface CharacterStateDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(state: CharacterStateEntity)

    @Update
    suspend fun update(state: CharacterStateEntity)

    @Query("SELECT * FROM character_states WHERE characterCardId = :cardId")
    suspend fun getState(cardId: Long): CharacterStateEntity?

    @Query("SELECT * FROM character_states WHERE characterCardId = :cardId")
    fun getStateFlow(cardId: Long): Flow<CharacterStateEntity?>
}
