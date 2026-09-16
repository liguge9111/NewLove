package com.tavern.app.core.parser

import com.tavern.app.core.model.WorldBook
import com.tavern.app.core.model.WorldBookEntry
import com.tavern.app.core.model.WorldBookExtensions
import org.junit.Assert.*
import org.junit.Test

class WorldBookScannerTest {

    private fun entry(
        id: Int,
        content: String,
        keys: List<String> = emptyList(),
        constant: Boolean = false,
        secondaryKeys: List<String> = emptyList(),
        selectiveLogic: Int = 0,
        scanDepth: Int = 4,
        insertionOrder: Int = id,
        matchWholeWords: Boolean = false
    ) = WorldBookEntry(
        id = id,
        keys = keys,
        secondaryKeys = secondaryKeys,
        content = content,
        constant = constant,
        insertionOrder = insertionOrder,
        extensions = WorldBookExtensions(
            selectiveLogic = selectiveLogic,
            scanDepth = scanDepth,
            matchWholeWords = matchWholeWords
        )
    )

    private fun bookOf(vararg entries: WorldBookEntry): WorldBook =
        WorldBook(entries.associateBy { it.id.toString() })

    @Test
    fun `主关键词命中触发条目`() {
        val book = bookOf(entry(1, "魔法内容", keys = listOf("魔法")))

        val result = WorldBookScanner.scan(book, "这是一个关于魔法的故事")
        assertEquals(1, result.entries.size)
        assertEquals("魔法内容", result.entries[0].content)
        assertEquals("魔法", result.matchedKeys[1])
    }

    @Test
    fun `关键词未命中不触发`() {
        val book = bookOf(entry(1, "魔法内容", keys = listOf("魔法")))

        val result = WorldBookScanner.scan(book, "这是一个普通的故事")
        assertEquals(0, result.entries.size)
    }

    @Test
    fun `常驻条目始终激活`() {
        val book = bookOf(entry(1, "常驻内容", constant = true))

        val result = WorldBookScanner.scan(book, "任意文本")
        assertEquals(1, result.entries.size)
    }

    @Test
    fun `次级关键词 AND 逻辑`() {
        val book = bookOf(
            entry(
                1, "AND逻辑内容",
                keys = listOf("主角"),
                secondaryKeys = listOf("受伤", "哭泣"),
                selectiveLogic = 0 // AND
            )
        )

        // 两个次级关键词都命中
        val result1 = WorldBookScanner.scan(book, "主角受伤后哭泣")
        assertEquals(1, result1.entries.size)

        // 只命中一个次级关键词
        val result2 = WorldBookScanner.scan(book, "主角受伤了")
        assertEquals(0, result2.entries.size)
    }

    @Test
    fun `次级关键词 ANY 逻辑`() {
        val book = bookOf(
            entry(
                1, "ANY逻辑内容",
                keys = listOf("主角"),
                secondaryKeys = listOf("受伤", "哭泣"),
                selectiveLogic = 3 // ANY
            )
        )

        val result = WorldBookScanner.scan(book, "主角受伤了")
        assertEquals(1, result.entries.size)
    }

    @Test
    fun `递归触发`() {
        val book = bookOf(
            entry(1, "这个世界有魔法", keys = listOf("世界")),
            entry(2, "魔法需要使用咒语", keys = listOf("魔法")),
            entry(3, "咒语的力量很强大", keys = listOf("咒语"))
        )

        val result = WorldBookScanner.scan(book, "这是一个世界")
        val ids = result.entries.map { it.id }
        assertTrue("应包含条目1", ids.contains(1))
        assertTrue("应包含条目2（递归）", ids.contains(2))
        assertTrue("应包含条目3（递归）", ids.contains(3))
    }

    @Test
    fun `扫描深度限制递归`() {
        val book = bookOf(
            entry(1, "触发A", keys = listOf("A"), scanDepth = 1),
            entry(2, "触发B", keys = listOf("触发A")),
            entry(3, "触发C", keys = listOf("触发B"))
        )

        val result = WorldBookScanner.scan(book, "A")
        val ids = result.entries.map { it.id }
        assertTrue(ids.contains(1))
        assertFalse("深度限制下不应触发条目2", ids.contains(2))
        assertFalse("深度限制下不应触发条目3", ids.contains(3))
    }

    @Test
    fun `正则表达式关键词`() {
        val book = bookOf(entry(1, "正则内容", keys = listOf("/^你好/")))

        val result1 = WorldBookScanner.scan(book, "你好世界")
        assertEquals(1, result1.entries.size)

        val result2 = WorldBookScanner.scan(book, "他说你好")
        assertEquals(0, result2.entries.size)
    }

    @Test
    fun `整词匹配`() {
        val book = bookOf(
            entry(1, "整词内容", keys = listOf("cat"), matchWholeWords = true)
        )

        val result1 = WorldBookScanner.scan(book, "a cat is here")
        assertEquals(1, result1.entries.size)

        val result2 = WorldBookScanner.scan(book, "we catch fish")
        assertEquals(0, result2.entries.size)
    }

    @Test
    fun `排序按插入顺序`() {
        val book = bookOf(
            entry(3, "条目3", keys = listOf("关键词"), insertionOrder = 3),
            entry(1, "条目1", keys = listOf("关键词"), insertionOrder = 1),
            entry(2, "条目2", keys = listOf("关键词"), insertionOrder = 2)
        )

        val result = WorldBookScanner.scan(book, "关键词")
        val ids = result.entries.map { it.id }
        assertEquals(listOf(1, 2, 3), ids)
    }

    @Test
    fun `禁用条目不触发`() {
        val book = bookOf(
            WorldBookEntry(
                id = 1,
                keys = listOf("关键词"),
                content = "禁用内容",
                enabled = false,
                insertionOrder = 1
            )
        )

        val result = WorldBookScanner.scan(book, "关键词")
        assertEquals(0, result.entries.size)
    }
}
