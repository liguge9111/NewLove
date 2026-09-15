package com.tavern.app.core.parser

import com.tavern.app.core.model.PluginAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PluginParserTest {

    @Test
    fun `解析 SillyTavern 扩展 header`() {
        val js = """
            // name: 翻译
            // description: 自动翻译角色回复
            // author: 某作者
            // version: 2.0
            console.log("extension body");
        """.trimIndent()

        val plugin = PluginParser.parseJs(js)
        assertEquals("翻译", plugin.name)
        assertEquals("自动翻译角色回复", plugin.description)
        assertEquals("某作者", plugin.author)
        assertEquals("2.0", plugin.version)
    }

    @Test
    fun `无 header 时使用默认名`() {
        val plugin = PluginParser.parseJs("console.log('no header')")
        assertEquals("未命名插件", plugin.name)
        assertEquals("1.0", plugin.version)
    }

    @Test
    fun `解析 JSON 插件定义`() {
        val json = """
            {
                "name": "测试插件",
                "description": "描述",
                "rules": [
                    {"trigger": "ON_REPLY", "action": "REPLACE", "pattern": "a", "config": {"find": "a", "replace": "b"}}
                ]
            }
        """.trimIndent()

        val plugin = PluginParser.parseJson(json)
        assertNotNull(plugin)
        assertEquals("测试插件", plugin!!.name)
        assertEquals(1, plugin.rules.size)
        assertEquals(PluginAction.REPLACE, plugin.rules[0].action)
        assertEquals("a", plugin.rules[0].config["find"])
    }

    @Test
    fun `非法 JSON 返回 null`() {
        assertNull(PluginParser.parseJson("not json"))
    }
}
