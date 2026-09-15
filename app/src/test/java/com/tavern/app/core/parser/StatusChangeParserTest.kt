package com.tavern.app.core.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StatusChangeParserTest {

    @Test
    fun `解析状态块并剥离正文`() {
        val text = "正文内容\n\n[STATUS]\n{\"mood\": 10, \"energy\": -5, \"affection\": 3}\n[/STATUS]"
        val result = StatusChangeParser.parse(text)
        assertEquals(10, result.delta.mood)
        assertEquals(-5, result.delta.energy)
        assertEquals(3, result.delta.affection)
        assertEquals("正文内容", result.content)
    }

    @Test
    fun `无状态块时返回空增量`() {
        val result = StatusChangeParser.parse("普通文本")
        assertTrue(result.delta.isEmpty)
        assertEquals("普通文本", result.content)
    }

    @Test
    fun `解析自定义状态`() {
        val text = "[STATUS]\n{\"custom\": {\"好感\": 5, \"信任\": -2}}\n[/STATUS]"
        val result = StatusChangeParser.parse(text)
        assertEquals(5, result.delta.custom["好感"])
        assertEquals(-2, result.delta.custom["信任"])
    }

    @Test
    fun `非法 JSON 时返回空增量`() {
        val text = "[STATUS]\n这不是JSON\n[/STATUS]"
        val result = StatusChangeParser.parse(text)
        assertTrue(result.delta.isEmpty)
    }

    @Test
    fun `缺失字段默认为 0`() {
        val text = "[STATUS]\n{\"mood\": 7}\n[/STATUS]"
        val result = StatusChangeParser.parse(text)
        assertEquals(7, result.delta.mood)
        assertEquals(0, result.delta.energy)
        assertEquals(0, result.delta.affection)
    }
}
