package com.tavern.app.core.prompt

import org.junit.Assert.*
import org.junit.Test

class OptionsParserTest {

    @Test
    fun `解析标准选项块（旧格式纯字符串）`() {
        val text = """
            你站在十字路口，前方是黑暗的森林。

            [CHOICES]
            {"choices":["走进森林","返回村庄","原地等待"]}
            [/CHOICES]
        """.trimIndent()

        val result = OptionsParser.parse(text)

        assertEquals(3, result.choices.size)
        assertEquals("走进森林", result.choices[0].text)
        assertEquals("返回村庄", result.choices[1].text)
        assertEquals("原地等待", result.choices[2].text)
        assertTrue(result.content.contains("你站在十字路口"))
        assertFalse(result.content.contains("[CHOICES]"))
    }

    @Test
    fun `解析新格式对象选项（含状态增量）`() {
        val text = """
            正文。

            [CHOICES]
            {"choices":[
              {"text":"俯身拾起玉簪借机贴近","delta":{"affection":3,"mood":2}},
              {"text":"假装没看见继续读书","delta":{"affection":-1}},
              {"text":"直接问她为何走神","delta":{"custom":{"理智":-5}}}
            ]}
            [/CHOICES]
        """.trimIndent()

        val result = OptionsParser.parse(text)
        assertEquals(3, result.choices.size)
        assertEquals("俯身拾起玉簪借机贴近", result.choices[0].text)
        assertEquals(3, result.choices[0].delta.affection)
        assertEquals(2, result.choices[0].delta.mood)
        assertEquals(-1, result.choices[1].delta.affection)
        assertEquals(-5, result.choices[2].delta.custom["理智"])
    }

    @Test
    fun `无选项块时原样返回`() {
        val text = "今天天气不错。"
        val result = OptionsParser.parse(text)
        assertEquals(text, result.content)
        assertTrue(result.choices.isEmpty())
    }

    @Test
    fun `非法JSON时返回空选项并剥离块`() {
        val text = "正文\n[CHOICES]\n这不是JSON\n[/CHOICES]"
        val result = OptionsParser.parse(text)
        assertTrue(result.choices.isEmpty())
        assertEquals("正文", result.content)
    }

    @Test
    fun `空choices数组返回空列表`() {
        val text = "正文\n[CHOICES]\n{\"choices\":[]}\n[/CHOICES]"
        val result = OptionsParser.parse(text)
        assertTrue(result.choices.isEmpty())
        assertEquals("正文", result.content)
    }

    @Test
    fun `选项中的空白项被过滤`() {
        val text = "正文\n[CHOICES]\n{\"choices\":[\"选项A\",\"\",\"选项B\"]}\n[/CHOICES]"
        val result = OptionsParser.parse(text)
        assertEquals(2, result.choices.size)
        assertEquals("选项A", result.choices[0].text)
        assertEquals("选项B", result.choices[1].text)
    }

    @Test
    fun `解析 options 竖线格式`() {
        val text = """
            叙事正文。
            <options>俯身拾起玉簪借机贴近|假装没看见继续读书|直接问她为何走神</options>
        """.trimIndent()
        val result = OptionsParser.parse(text)
        assertEquals(3, result.choices.size)
        assertEquals("俯身拾起玉簪借机贴近", result.choices[0].text)
        assertTrue(result.content.contains("叙事正文"))
        assertFalse(result.content.contains("<options>"))
    }
}
