package com.tavern.app.core.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NarrationParserTest {

    @Test
    fun `parse extracts narration block and strips it from content`() {
        val clean = "她微微一笑。\n\n[NARRATION]\n少女侧过身，阳光洒在她的发梢。\n[/NARRATION]"
        val result = NarrationParser.parse(clean)
        assertEquals("她微微一笑。", result.content)
        assertEquals("少女侧过身，阳光洒在她的发梢。", result.narration)
    }

    @Test
    fun `parse without narration keeps content intact`() {
        val text = "只是普通回复。"
        val result = NarrationParser.parse(text)
        assertEquals(text, result.content)
        assertNull(result.narration)
    }

    @Test
    fun `parse strips status and choices blocks when no narration`() {
        val text = "正文\n[STATUS]\n{\"mood\":5}\n[/STATUS]"
        val result = NarrationParser.parse(text)
        assertEquals("正文", result.content)
        assertNull(result.narration)
    }

    @Test
    fun `stripForDisplay removes complete blocks`() {
        val text = "流式文本[NARRATION]\n旁白内容[/NARRATION][STATUS]\n{}[/STATUS]"
        assertEquals("流式文本", NarrationParser.stripForDisplay(text))
    }

    @Test
    fun `stripForDisplay removes trailing open tag`() {
        val text = "流式文本[NARRATION]\n旁白开始…"
        assertEquals("流式文本", NarrationParser.stripForDisplay(text))
    }

    @Test
    fun `empty narration block treated as absent`() {
        val text = "正文[NARRATION]\n\n[/NARRATION]"
        val result = NarrationParser.parse(text)
        assertEquals("正文", result.content)
        assertNull(result.narration)
    }
}
