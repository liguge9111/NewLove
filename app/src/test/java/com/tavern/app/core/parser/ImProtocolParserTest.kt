package com.tavern.app.core.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ImProtocolParserTest {

    @Test
    fun `parse extracts im block`() {
        val text = "叙事正文。\n\n[IM]\n哈哈哈在追剧呢\n[/IM]"
        val result = ImProtocolParser.parse(text)
        assertEquals("叙事正文。", result.content)
        assertEquals("哈哈哈在追剧呢", result.imReply)
    }

    @Test
    fun `parse without im block keeps content`() {
        val text = "只是叙事。"
        val result = ImProtocolParser.parse(text)
        assertEquals(text, result.content)
        assertNull(result.imReply)
    }

    @Test
    fun `fallbackReply extracts first quoted speech`() {
        val narrative = "她抬起头。\n「在写东西呢，怎么了？」\n她又低下头。"
        assertEquals("在写东西呢，怎么了？", ImProtocolParser.fallbackReply(narrative))
    }

    @Test
    fun `fallbackReply with chinese double quotes`() {
        val narrative = "她笑着说：“刚吃完饭。”"
        assertEquals("刚吃完饭。", ImProtocolParser.fallbackReply(narrative))
    }

    @Test
    fun `fallbackReply no quotes takes head`() {
        val narrative = "暮色把房间染成橘黄。她拿起手机看了看。"
        val fallback = ImProtocolParser.fallbackReply(narrative)
        assertEquals("暮色把房间染成橘黄。她拿起手机看了看。", fallback)
    }

    @Test
    fun `fallbackReply skips user message echo`() {
        val narrative = "你发去消息「妈妈，你在干嘛？」。她笑了笑。\n「在写东西呢。」"
        val reply = ImProtocolParser.fallbackReply(narrative, exclude = "妈妈，你在干嘛？")
        assertEquals("在写东西呢。", reply)
    }
}
