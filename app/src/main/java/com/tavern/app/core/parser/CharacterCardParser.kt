package com.tavern.app.core.parser

import com.tavern.app.core.model.CardSpec
import com.tavern.app.core.model.CharacterCard
import kotlinx.serialization.json.*

/**
 * 角色卡解析器
 *
 * 支持解析：
 * - SillyTavern V2（chara_card_v2）
 * - SillyTavern V3（chara_card_v3）
 * - TavernAI V1（无 spec 字段的旧格式）
 *
 * 使用 kotlinx.serialization 的 JsonElement 手动遍历，以兼容字段缺失、
 * 命名差异和未来扩展字段。
 */
object CharacterCardParser {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    /**
     * 解析角色卡 JSON 字符串
     *
     * @param jsonString 角色卡 JSON 内容
     * @return 解析后的 CharacterCard，解析失败返回 null
     */
    fun parse(jsonString: String): CharacterCard? {
        return try {
            val root = json.parseToJsonElement(jsonString.trim())
            if (root !is JsonObject) return null
            parseFromJsonObject(root)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 从 JsonObject 解析角色卡
     */
    fun parseFromJsonObject(root: JsonObject): CharacterCard {
        // 1. 检测规范版本
        val specValue = root["spec"]?.jsonPrimitive?.contentOrNull
        val (spec, specVersion) = when {
            specValue == "chara_card_v3" -> CardSpec.CHARA_CARD_V3 to (root["spec_version"]?.jsonPrimitive?.contentOrNull ?: "3.0")
            specValue == "chara_card_v2" -> CardSpec.CHARA_CARD_V2 to (root["spec_version"]?.jsonPrimitive?.contentOrNull ?: "2.0")
            else -> CardSpec.TAVERN_AI_V1 to ""
        }

        // 2. 确定数据所在的层级（V2/V3 数据在 data 字段下，V1 在顶层）
        val data: JsonObject = when (spec) {
            CardSpec.CHARA_CARD_V2, CardSpec.CHARA_CARD_V3 ->
                root.objectField("data") ?: JsonObject(emptyMap())
            else -> root
        }

        // 3. 解析世界书（V2 在 data.character_book，V1 顶层也可能有）
        val characterBook = parseWorldBook(data) ?: parseWorldBook(root)

        return CharacterCard(
            name = data.stringField("name") ?: "",
            description = data.stringField("description") ?: "",
            personality = data.stringField("personality") ?: "",
            scenario = data.stringField("scenario") ?: "",
            firstMessage = (data.stringField("first_mes")
                ?: data.stringField("firstMessage")) ?: "",
            messageExample = (data.stringField("mes_example")
                ?: data.stringField("mesExample")) ?: "",
            creatorNotes = (data.stringField("creator_notes")
                ?: data.stringField("creatorNotes")
                ?: data.stringField("creator_comment")
                ?: data.stringField("creatorComment")) ?: "",
            systemPrompt = (data.stringField("system_prompt")
                ?: data.stringField("systemPrompt")) ?: "",
            postHistoryInstructions = (data.stringField("post_history_instructions")
                ?: data.stringField("postHistoryInstructions")) ?: "",
            alternateGreetings = (data.stringListField("alternate_greetings")
                ?: data.stringListField("alternateGreetings"))
                ?: emptyList(),
            tags = data.stringListField("tags") ?: emptyList(),
            creator = data.stringField("creator") ?: "",
            characterVersion = (data.stringField("character_version")
                ?: data.stringField("characterVersion")) ?: "",
            spec = spec,
            specVersion = specVersion,
            characterBook = characterBook,
            extensions = data.objectField("extensions"),
            rawJson = root
        )
    }

    /**
     * 解析角色卡中内嵌的世界书
     */
    private fun parseWorldBook(data: JsonObject) =
        WorldBookParser.parseFromJsonObject(data)
}
