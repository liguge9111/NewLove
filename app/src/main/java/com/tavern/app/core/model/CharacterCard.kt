package com.tavern.app.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * 角色卡规范版本
 */
enum class CardSpec {
    /** SillyTavern V2 格式（chara_card_v2） */
    CHARA_CARD_V2,

    /** SillyTavern V3 格式（chara_card_v3） */
    CHARA_CARD_V3,

    /** TavernAI V1 格式（无 spec 字段） */
    TAVERN_AI_V1,

    /** 未知/无法识别 */
    UNKNOWN
}

/**
 * 角色卡完整数据模型
 *
 * 兼容 SillyTavern V2/V3 与 TavernAI V1 格式。
 * 所有字段统一以 V2 语义为准，解析器负责从 V1 映射。
 */
@Serializable
data class CharacterCard(
    /** 角色名 */
    val name: String = "",

    /** 角色描述（核心人设） */
    val description: String = "",

    /** 性格描述 */
    val personality: String = "",

    /** 场景/世界观设定 */
    val scenario: String = "",

    /** 首条消息（角色开场白） */
    val firstMessage: String = "",

    /** 对话示例 */
    val messageExample: String = "",

    /** 创建者备注 */
    val creatorNotes: String = "",

    /** 自定义系统提示词 */
    val systemPrompt: String = "",

    /** 历史后置指令 */
    val postHistoryInstructions: String = "",

    /** 备选开场白 */
    val alternateGreetings: List<String> = emptyList(),

    /** 标签 */
    val tags: List<String> = emptyList(),

    /** 创建者 */
    val creator: String = "",

    /** 角色卡版本号 */
    val characterVersion: String = "",

    /** 角色卡规范 */
    val spec: CardSpec = CardSpec.UNKNOWN,

    /** 规范版本字符串（如 "2.0"、"3.0"） */
    val specVersion: String = "",

    /** 内嵌世界书（角色卡自带的世界书） */
    val characterBook: WorldBook? = null,

    /** 扩展数据（Lore 等 SillyTavern 扩展字段，原样保留） */
    val extensions: JsonObject? = null,

    /** 原始 JSON（保底：解析失败或未来字段时可用） */
    val rawJson: JsonObject? = null
) {
    /** 组合后的完整角色定义（用于注入提示词） */
    fun buildDefinition(): String = buildString {
        if (description.isNotBlank()) {
            append(description)
        }
        if (personality.isNotBlank()) {
            if (isNotEmpty()) append("\n")
            append("性格：").append(personality)
        }
        if (scenario.isNotBlank()) {
            if (isNotEmpty()) append("\n")
            append("场景：").append(scenario)
        }
    }
}
