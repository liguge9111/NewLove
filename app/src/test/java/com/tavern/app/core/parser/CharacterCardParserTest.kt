package com.tavern.app.core.parser

import com.tavern.app.core.model.CardSpec
import org.junit.Assert.*
import org.junit.Test

class CharacterCardParserTest {

    @Test
    fun `解析 SillyTavern V2 格式`() {
        val json = """
        {
          "spec": "chara_card_v2",
          "spec_version": "2.0",
          "data": {
            "name": "爱丽丝",
            "description": "一个温柔的少女",
            "personality": "温柔、善良",
            "scenario": "在森林里相遇",
            "first_mes": "你好呀，旅行者！",
            "mes_example": "<START>\n{{user}}: 你好\n{{char}}: 你好！",
            "creator_notes": "测试角色",
            "system_prompt": "你是{{char}}，请扮演她。",
            "post_history_instructions": "保持角色",
            "alternate_greetings": ["你好", "嗨"],
            "tags": ["少女", "温柔"],
            "creator": "test",
            "character_version": "1.0"
          }
        }
        """.trimIndent()

        val card = CharacterCardParser.parse(json)
        assertNotNull(card)

        assertEquals("爱丽丝", card!!.name)
        assertEquals("一个温柔的少女", card.description)
        assertEquals("温柔、善良", card.personality)
        assertEquals("在森林里相遇", card.scenario)
        assertEquals("你好呀，旅行者！", card.firstMessage)
        assertEquals("<START>\n{{user}}: 你好\n{{char}}: 你好！", card.messageExample)
        assertEquals("测试角色", card.creatorNotes)
        assertEquals("你是{{char}}，请扮演她。", card.systemPrompt)
        assertEquals("保持角色", card.postHistoryInstructions)
        assertEquals(listOf("你好", "嗨"), card.alternateGreetings)
        assertEquals(listOf("少女", "温柔"), card.tags)
        assertEquals("test", card.creator)
        assertEquals("1.0", card.characterVersion)
        assertEquals(CardSpec.CHARA_CARD_V2, card.spec)
        assertEquals("2.0", card.specVersion)
    }

    @Test
    fun `解析 SillyTavern V3 格式`() {
        val json = """
        {
          "spec": "chara_card_v3",
          "spec_version": "3.0",
          "data": {
            "name": "V3角色",
            "description": "V3 格式角色"
          }
        }
        """.trimIndent()

        val card = CharacterCardParser.parse(json)
        assertNotNull(card)
        assertEquals(CardSpec.CHARA_CARD_V3, card!!.spec)
        assertEquals("V3角色", card.name)
    }

    @Test
    fun `解析 TavernAI V1 格式`() {
        val json = """
        {
          "name": "老式角色",
          "description": "V1 格式",
          "personality": "沉稳",
          "scenario": "酒吧",
          "first_mes": "欢迎光临",
          "mes_example": "示例",
          "tags": ["经典"]
        }
        """.trimIndent()

        val card = CharacterCardParser.parse(json)
        assertNotNull(card)
        assertEquals(CardSpec.TAVERN_AI_V1, card!!.spec)
        assertEquals("老式角色", card.name)
        assertEquals("V1 格式", card.description)
        assertEquals("沉稳", card.personality)
        assertEquals("欢迎光临", card.firstMessage)
        assertEquals(listOf("经典"), card.tags)
    }

    @Test
    fun `解析缺失字段的角色卡`() {
        val json = """{ "name": "极简角色" }"""

        val card = CharacterCardParser.parse(json)
        assertNotNull(card)
        assertEquals("极简角色", card!!.name)
        assertEquals("", card.description)
        assertEquals("", card.firstMessage)
        assertEquals(emptyList<String>(), card.tags)
        assertEquals(CardSpec.TAVERN_AI_V1, card.spec)
    }

    @Test
    fun `解析非法 JSON 返回 null`() {
        val card = CharacterCardParser.parse("这不是 JSON")
        assertNull(card)
    }

    @Test
    fun `解析空字符串返回 null`() {
        val card = CharacterCardParser.parse("")
        assertNull(card)
    }

    @Test
    fun `解析含内嵌世界书的角色卡`() {
        val json = """
        {
          "spec": "chara_card_v2",
          "spec_version": "2.0",
          "data": {
            "name": "带世界书角色",
            "description": "测试",
            "character_book": {
              "entries": {
                "1": {
                  "id": 1,
                  "keys": ["魔法"],
                  "content": "这个世界存在魔法。"
                }
              }
            }
          }
        }
        """.trimIndent()

        val card = CharacterCardParser.parse(json)
        assertNotNull(card)
        val book = card!!.characterBook
        assertNotNull(book)
        assertEquals(1, book!!.entries.size)
        assertEquals("这个世界存在魔法。", book.entries["1"]!!.content)
    }

    @Test
    fun `buildDefinition 组合角色定义`() {
        val json = """
        {
          "name": "组合测试",
          "description": "描述部分",
          "personality": "性格部分",
          "scenario": "场景部分"
        }
        """.trimIndent()

        val card = CharacterCardParser.parse(json)!!
        val definition = card.buildDefinition()
        assertTrue(definition.contains("描述部分"))
        assertTrue(definition.contains("性格部分"))
        assertTrue(definition.contains("场景部分"))
    }
}
