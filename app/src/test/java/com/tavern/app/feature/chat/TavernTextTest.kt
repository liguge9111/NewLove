package com.tavern.app.feature.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TavernTextTest {

    @Test
    fun `splitBlocks extracts details block`() {
        val text = "前文\n<details><summary>[角色状态]</summary>\n- 身份：弟子\n</details>\n后文"
        val blocks = splitBlocks(text)
        assertEquals(3, blocks.size)
        assertTrue(blocks[0] is MarkupBlock.Plain)
        val details = blocks[1] as MarkupBlock.Details
        assertEquals("[角色状态]", details.summary)
        assertTrue(details.content.contains("身份：弟子"))
        assertTrue(blocks[2] is MarkupBlock.Plain)
    }

    @Test
    fun `splitBlocks extracts code fence`() {
        val text = "说明\n```\ncode line\n```\n结束"
        val blocks = splitBlocks(text)
        assertEquals(3, blocks.size)
        val code = blocks[1] as MarkupBlock.Code
        assertEquals("code line", code.code)
    }

    @Test
    fun `splitBlocks plain text only`() {
        val blocks = splitBlocks("只有普通文字")
        assertEquals(1, blocks.size)
        assertTrue(blocks[0] is MarkupBlock.Plain)
    }
}
