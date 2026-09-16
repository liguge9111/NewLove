package com.tavern.app.core.data.repository

import com.tavern.app.core.data.local.entity.*
import com.tavern.app.core.model.*
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * 实体 ↔ 模型转换（顶层扩展函数，供各层复用）
 */

fun ModelConfigEntity.toModel(): ModelConfig = ModelConfig(
    id = id,
    name = name,
    providerType = ModelProviderType.valueOf(providerType),
    baseUrl = baseUrl,
    apiKey = apiKey,
    modelName = modelName,
    maxContextTokens = maxContextTokens,
    temperature = temperature,
    topP = topP,
    maxTokens = maxTokens,
    frequencyPenalty = frequencyPenalty,
    presencePenalty = presencePenalty,
    streamEnabled = streamEnabled,
    isDefault = isDefault
)

fun ModelConfig.toEntity(): ModelConfigEntity = ModelConfigEntity(
    id = id,
    name = name,
    providerType = providerType.name,
    baseUrl = baseUrl,
    apiKey = apiKey,
    modelName = modelName,
    maxContextTokens = maxContextTokens,
    temperature = temperature,
    topP = topP,
    maxTokens = maxTokens,
    frequencyPenalty = frequencyPenalty,
    presencePenalty = presencePenalty,
    streamEnabled = streamEnabled,
    isDefault = isDefault
)

fun ImageConfig.toEntity(): ImageConfigEntity = ImageConfigEntity(
    id = id,
    name = name,
    providerType = providerType.name,
    baseUrl = baseUrl,
    apiKey = apiKey,
    defaultPrompt = defaultPrompt,
    defaultNegativePrompt = defaultNegativePrompt,
    sampler = sampler,
    steps = steps,
    cfgScale = cfgScale,
    width = width,
    height = height,
    isDefault = isDefault
)

fun ImageConfigEntity.toModel(): ImageConfig = ImageConfig(
    id = id,
    name = name,
    providerType = ImageProviderType.valueOf(providerType),
    baseUrl = baseUrl,
    apiKey = apiKey,
    defaultPrompt = defaultPrompt,
    defaultNegativePrompt = defaultNegativePrompt,
    sampler = sampler,
    steps = steps,
    cfgScale = cfgScale,
    width = width,
    height = height,
    isDefault = isDefault
)

fun VoiceConfig.toEntity(): VoiceConfigEntity = VoiceConfigEntity(
    id = id,
    name = name,
    providerType = providerType.name,
    baseUrl = baseUrl,
    apiKey = apiKey,
    voiceId = voiceId,
    speed = speed,
    pitch = pitch,
    isDefault = isDefault
)

fun VoiceConfigEntity.toModel(): VoiceConfig = VoiceConfig(
    id = id,
    name = name,
    providerType = VoiceProviderType.valueOf(providerType),
    baseUrl = baseUrl,
    apiKey = apiKey,
    voiceId = voiceId,
    speed = speed,
    pitch = pitch,
    isDefault = isDefault
)

// ===== 角色状态 =====

private val stateJson = Json { ignoreUnknownKeys = true }

fun CharacterState.toEntity(): CharacterStateEntity = CharacterStateEntity(
    characterCardId = characterCardId,
    mood = mood,
    energy = energy,
    affection = affection,
    customStates = stateJson.encodeToString(
        MapSerializer(String.serializer(), Int.serializer()),
        customStates
    ),
    lastUpdatedAt = lastUpdatedAt
)

fun CharacterStateEntity.toModel(): CharacterState = CharacterState(
    characterCardId = characterCardId,
    mood = mood,
    energy = energy,
    affection = affection,
    customStates = try {
        stateJson.decodeFromString(
            MapSerializer(String.serializer(), Int.serializer()),
            customStates
        )
    } catch (e: Exception) {
        emptyMap()
    },
    lastUpdatedAt = lastUpdatedAt
)

// ===== 状态栏模板 =====

fun List<StatusItemDef>.encodeSchema(): String =
    stateJson.encodeToString(kotlinx.serialization.builtins.ListSerializer(StatusItemDef.serializer()), this)

fun String.decodeSchema(): List<StatusItemDef> = try {
    if (isBlank()) emptyList()
    else stateJson.decodeFromString(
        kotlinx.serialization.builtins.ListSerializer(StatusItemDef.serializer()),
        this
    )
} catch (e: Exception) {
    emptyList()
}

// ===== 语音识别（ASR）配置 =====

fun AsrConfig.toEntity(): AsrConfigEntity = AsrConfigEntity(
    id = id,
    name = name,
    baseUrl = baseUrl,
    apiKey = apiKey,
    modelName = modelName,
    language = language,
    isDefault = isDefault
)

fun AsrConfigEntity.toModel(): AsrConfig = AsrConfig(
    id = id,
    name = name,
    baseUrl = baseUrl,
    apiKey = apiKey,
    modelName = modelName,
    language = language,
    isDefault = isDefault
)
