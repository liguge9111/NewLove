package com.tavern.app.core.parser

import org.junit.Assert.*
import org.junit.Test

class WorldBookParserTest {

    @Test
    fun `解析标准世界书结构`() {
        val json = """
        {
          "entries": {
            "1": {
              "id": 1,
              "keys": ["魔法", "咒语"],
              "secondary_keys": ["古老"],
              "comment": "魔法设定",
              "content": "这个世界存在魔法。",
              "constant": false,
              "selective": false,
              "insertion_order": 10,
              "enabled": true,
              "position": "before_char",
              "extensions": {
                "exclude_recursion": false,
                "probability": 100,
                "useProbability": true,
                "depth": 4,
                "selectiveLogic": 0
              }
            }
          }
        }
        """.trimIndent()

        val book = WorldBookParser.parse(json)
        assertNotNull(book)
        assertEquals(1, book!!.entries.size)

        val entry = book.entries["1"]!!
        assertEquals(1, entry.id)
        assertEquals(listOf("魔法", "咒语"), entry.keys)
        assertEquals(listOf("古老"), entry.secondaryKeys)
        assertEquals("这个世界存在魔法。", entry.content)
        assertEquals(10, entry.insertionOrder)
        assertTrue(entry.enabled)
    }

    @Test
    fun `解析逗号分隔的 keys`() {
        val json = """
        {
          "entries": {
            "1": {
              "keys": "魔法, 咒语, 符文",
              "content": "测试"
            }
          }
        }
        """.trimIndent()

        val book = WorldBookParser.parse(json)!!
        val entry = book.entries["1"]!!
        assertEquals(listOf("魔法", "咒语", "符文"), entry.keys)
    }

    @Test
    fun `从角色卡内嵌世界书解析`() {
        val json = """
        {
          "spec": "chara_card_v2",
          "data": {
            "name": "角色",
            "character_book": {
              "entries": {
                "1": {
                  "keys": ["世界"],
                  "content": "内嵌世界书内容"
                }
              }
            }
          }
        }
        """.trimIndent()

        val card = CharacterCardParser.parse(json)
        assertNotNull(card?.characterBook)
        assertEquals("内嵌世界书内容", card!!.characterBook!!.entries["1"]!!.content)
    }

    @Test
    fun `空世界书返回空 entries`() {
        val json = """{ "entries": {} }"""
        val book = WorldBookParser.parse(json)
        assertNotNull(book)
        assertEquals(0, book!!.entries.size)
    }

    @Test
    fun `无 entries 字段返回 null`() {
        val json = """{ "foo": "bar" }"""
        val book = WorldBookParser.parse(json)
        assertNull(book)
    }

    // ===== 回归：V2/V3 角色卡的 entries 是数组而非对象 =====

    @Test
    fun `解析数组形式 entries（角色卡内嵌书）`() {
        val json = """
        {
          "entries": [
            { "id": 0, "keys": ["甲"], "content": "A" },
            { "id": 1, "keys": ["乙"], "content": "B" }
          ]
        }
        """.trimIndent()

        val book = WorldBookParser.parse(json)
        assertNotNull(book)
        assertEquals(2, book!!.entries.size)
        assertEquals("A", book.entries["0"]!!.content)
        assertEquals("B", book.entries["1"]!!.content)
        assertEquals(0, book.entries["0"]!!.id)
        assertEquals(1, book.entries["1"]!!.id)
    }

    @Test
    fun `从角色卡内嵌数组世界书解析（V3）`() {
        val json = """
        {
          "spec": "chara_card_v3",
          "data": {
            "name": "角色",
            "character_book": {
              "name": "内嵌书",
              "entries": [
                { "id": 0, "keys": ["世界"], "content": "数组内嵌内容" }
              ]
            }
          }
        }
        """.trimIndent()

        val card = CharacterCardParser.parse(json)
        assertNotNull(card)
        assertEquals("角色", card!!.name)
        assertNotNull(card.characterBook)
        assertEquals("数组内嵌内容", card.characterBook!!.entries["0"]!!.content)
    }

    @Test
    fun `entries 数组含非法元素不崩溃`() {
        val json = """{ "entries": [ 123, "x", { "id": 0, "keys": ["x"], "content": "ok" } ] }"""
        val book = WorldBookParser.parse(json)
        assertNotNull(book)
        assertEquals(1, book!!.entries.size)
        assertEquals("ok", book.entries["0"]!!.content)
    }

    @Test
    fun `extensions 非对象时不崩溃`() {
        val json = """
        { "entries": [ { "id": 0, "keys": ["x"], "content": "c", "extensions": "oops" } ] }
        """.trimIndent()
        val book = WorldBookParser.parse(json)
        assertNotNull(book)
        assertEquals(1, book!!.entries.size)
    }

    @Test
    fun `keys 数组含非字符串元素不崩溃`() {
        val json = """{ "entries": [ { "id": 0, "keys": [1, "ok", null], "content": "c" } ] }"""
        val book = WorldBookParser.parse(json)
        assertNotNull(book)
        // 数字key转字符串，null 被过滤
        assertEquals(listOf("1", "ok"), book!!.entries["0"]!!.keys)
    }
}
