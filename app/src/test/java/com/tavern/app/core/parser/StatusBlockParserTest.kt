package com.tavern.app.core.parser

import com.tavern.app.core.model.CharacterCard
import com.tavern.app.core.model.WorldBook
import com.tavern.app.core.model.WorldBookEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StatusBlockParserTest {

    @Test
    fun `parse splits maintext and status block`() {
        val text = """
            <maintext>
            她微微一笑。
            </maintext>
            <Status_block>
            📅 秦武阳十五年春 | 📍 云山宗后山
            - 👤 身份：杂役弟子
            </Status_block>
        """.trimIndent()
        val result = StatusBlockParser.parse(text)
        assertEquals("她微微一笑。", result.content)
        assertTrue(result.statusPanel!!.contains("云山宗后山"))
        assertTrue(result.statusPanel!!.contains("杂役弟子"))
    }

    @Test
    fun `parse without tags keeps content and null panel`() {
        val text = "普通回复，没有状态栏。"
        val result = StatusBlockParser.parse(text)
        assertEquals(text, result.content)
        assertNull(result.statusPanel)
    }

    @Test
    fun `parse status block only treats rest as content`() {
        val text = "正文内容<Status_block>\n面板\n</Status_block>"
        val result = StatusBlockParser.parse(text)
        assertEquals("正文内容", result.content)
        assertEquals("面板", result.statusPanel)
    }

    @Test
    fun `cardUsesProtocol detects marker in world book`() {
        val card = CharacterCard(
            name = "x",
            characterBook = WorldBook(
                entries = mapOf(
                    "1" to WorldBookEntry(id = 1, content = "输出 <Status_block> 格式")
                )
            )
        )
        assertTrue(StatusBlockParser.cardUsesProtocol(card))
    }

    @Test
    fun `cardUsesProtocol false without marker`() {
        val card = CharacterCard(
            name = "x",
            characterBook = WorldBook(
                entries = mapOf("1" to WorldBookEntry(id = 1, content = "普通条目"))
            )
        )
        assertFalse(StatusBlockParser.cardUsesProtocol(card))
    }

    @Test
    fun `stripForDisplay removes open tags while streaming`() {
        val text = "正文<Status_block>\n📅 日期"
        assertEquals("正文", StatusBlockParser.stripForDisplay(text))
    }

    @Test
    fun `parse keeps details tags in panel for UI rendering`() {
        val text = "正文<Status_block><details><summary>[状态]</summary>\n内容\n</details></Status_block>"
        val result = StatusBlockParser.parse(text)
        assertEquals("正文", result.content)
        assertTrue(result.statusPanel!!.contains("<details>"))
        assertTrue(result.statusPanel!!.contains("[状态]"))
        assertTrue(result.statusPanel!!.contains("内容"))
    }

    @Test
    fun `parse tavernhelper style with chinese narrative and html panel`() {
        val text = """
            <正文>
            她推门而入。
            </正文>
            <style>.s{background:#f5fafa}</style>
            <div class="s">
            <div class="h"><b>唐婉清 状态</b><span>⏰ 18:32</span></div>
            <div><span class="f">🎭 阶段</span><br>🌸师生距离🌸</div>
            ❤️ 好感 <b>0</b>/100
            </div>
            <UpdateVariable>
            _.set('唐婉清.好感', 0, 0);
            </UpdateVariable>
            <StatusPlaceHolderImpl/>
        """.trimIndent()
        val result = StatusBlockParser.parse(text)
        assertEquals("她推门而入。", result.content)
        val panel = result.statusPanel!!
        assertTrue(panel.contains("唐婉清 状态"))
        assertTrue(panel.contains("🌸师生距离🌸"))
        assertTrue(panel.contains("好感"))
        assertFalse(panel.contains("<style>"))
        assertFalse(panel.contains("<div"))
        assertFalse(panel.contains("UpdateVariable"))
    }

    @Test
    fun `flattenHtmlPanel converts br and div to lines`() {
        val html = "<div class=\"h\"><b>标题</b><span>⏰ 18:32</span></div><div><span class=\"f\">地点</span><br>教室</div>"
        val flat = StatusBlockParser.flattenHtmlPanel(html)
        assertTrue(flat.contains("标题"))
        assertTrue(flat.contains("教室"))
        assertFalse(flat.contains("<div"))
        assertFalse(flat.contains("<br"))
    }

    @Test
    fun `parse comment of master extracts panel and master talk im`() {
        val text = """
            叙事正文内容。
            <Comment_of_Master>
            <details><summary>⭐传念私信</summary>
            <Master_Talk>
            💬师尊传念之语："徒儿这般晚了还惦记着本座。"
            💗内心戏：*啊啊啊他叫我师尊了！*
            </Master_Talk>
            >📍地点：寒月宫
            </details>
            </Comment_of_Master>
        """.trimIndent()
        val result = StatusBlockParser.parse(text)
        assertEquals("叙事正文内容。", result.content)
        assertTrue(result.statusPanel!!.contains("⭐传念私信"))
        assertTrue(result.statusPanel!!.contains("寒月宫"))
        // IM 提取：保留传念语，去掉内心戏与前缀
        assertEquals("徒儿这般晚了还惦记着本座。", result.imReply)
    }

    @Test
    fun `greeting without panel leaves statusPanel null`() {
        val result = StatusBlockParser.parse("*你睁开了双眼，这里是哪？*")
        assertEquals("*你睁开了双眼，这里是哪？*", result.content)
        assertNull(result.statusPanel)
        assertNull(result.imReply)
    }

    @Test
    fun `html-first variant with choices keeps choices in content`() {
        val text = """
            <style>.card{color:#333}</style>
            <div class="nova-card-body">她抬眼看向你，唇角带笑。</div>
            [CHOICES]
            {"choices":["选项A","选项B"]}
            [/CHOICES]
        """.trimIndent()
        val result = StatusBlockParser.parse(text)
        // 叙事在 HTML 内 → 拍平为正文，不进状态栏
        assertTrue(result.content.contains("她抬眼看向你"))
        assertTrue(result.content.contains("[CHOICES]"))
        assertNull(result.statusPanel)
        // 选项能被 OptionsParser 继续解析
        val options = com.tavern.app.core.prompt.OptionsParser.parse(result.content)
        assertEquals(2, options.choices.size)
        assertEquals("选项A", options.choices[0].text)
    }

    @Test
    fun `plain text status tail is extracted to panel`() {
        val text = """
            教室里鸦雀无声。她慢慢走上讲台，指尖敲了敲桌面，目光扫过全班，最后停在你身上，唇角勾起一丝冷笑，那笑意冷得像冬日里的冰碴，却又带着说不清道不明的意味，让全班同学大气都不敢出。

            唐婉清 状态⏰ 17:52 | 📅 9月15日
            🎭 阶段
            🌸师生距离🌸
            📍 地点
            教学楼走廊拐角
            ❤️ 好感 5/100
            +3点, 被看穿的异样感
        """.trimIndent()
        val result = StatusBlockParser.parse(text)
        assertTrue(result.content.contains("教室里鸦雀无声"))
        assertFalse(result.content.contains("🎭"))
        val panel = result.statusPanel
        assertNotNull(panel)
        assertTrue(panel!!.contains("唐婉清 状态"))
        assertTrue(panel.contains("师生距离"))
        assertTrue(panel.contains("好感 5/100"))
    }
}
