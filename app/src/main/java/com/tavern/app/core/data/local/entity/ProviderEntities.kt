package com.tavern.app.core.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 大模型配置实体
 */
@Entity(tableName = "model_configs")
data class ModelConfigEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val name: String = "",

    /** OPENAI_COMPATIBLE / CLAUDE / OLLAMA */
    val providerType: String = "OPENAI_COMPATIBLE",

    val baseUrl: String = "",

    val apiKey: String = "",

    val modelName: String = "",

    val maxContextTokens: Int = 4096,

    val temperature: Float = 0.7f,

    val topP: Float = 1.0f,

    val maxTokens: Int = 4096,

    val frequencyPenalty: Float = 0f,

    val presencePenalty: Float = 0f,

    val streamEnabled: Boolean = true,

    val isDefault: Boolean = false
)

/**
 * 生图配置实体
 */
@Entity(tableName = "image_configs")
data class ImageConfigEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val name: String = "",

    /** SD_WEBUI / NOVELAI / DALL_E / COMFY_UI */
    val providerType: String = "SD_WEBUI",

    val baseUrl: String = "",

    val apiKey: String = "",

    val defaultPrompt: String = "",

    val defaultNegativePrompt: String = "",

    val sampler: String = "Euler a",

    val steps: Int = 20,

    val cfgScale: Float = 7.0f,

    val width: Int = 512,

    val height: Int = 512,

    val isDefault: Boolean = false
)

/**
 * 语音配置实体
 */
@Entity(tableName = "voice_configs")
data class VoiceConfigEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val name: String = "",

    /** ELEVENLABS / AZURE / OPENAI_TTS / SYSTEM_TTS */
    val providerType: String = "SYSTEM_TTS",

    val baseUrl: String = "",

    val apiKey: String = "",

    val voiceId: String = "",

    val speed: Float = 1.0f,

    val pitch: Float = 1.0f,

    val isDefault: Boolean = false
)

/**
 * 角色状态实体（与角色卡一一对应）
 */
@Entity(tableName = "character_states")
data class CharacterStateEntity(
    @PrimaryKey
    val characterCardId: Long = 0,

    /** 心情（0-100） */
    val mood: Int = 50,

    /** 体力（0-100） */
    val energy: Int = 100,

    /** 好感度（0-100） */
    val affection: Int = 0,

    /** 自定义状态（JSON：Map<String, Int>） */
    val customStates: String = "{}",

    /** 状态栏模板（JSON：List<StatusItemDef>，空串表示使用默认三项） */
    val statusSchema: String = "",

    val lastUpdatedAt: Long = System.currentTimeMillis()
)

/**
 * 语音识别（ASR）配置实体
 */
@Entity(tableName = "asr_configs")
data class AsrConfigEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val name: String = "",

    /** OpenAI 兼容 /audio/transcriptions 接口 */
    val baseUrl: String = "",

    val apiKey: String = "",

    /** 模型名（如 whisper-1） */
    val modelName: String = "whisper-1",

    /** 识别语言（如 zh、en，空=自动） */
    val language: String = "zh",

    val isDefault: Boolean = false
)
