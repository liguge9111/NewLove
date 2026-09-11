package com.tavern.app.core.plugin

import com.tavern.app.core.model.Plugin
import com.tavern.app.core.model.PluginAction
import com.tavern.app.core.model.PluginRule
import com.tavern.app.core.model.PluginTrigger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginEngineTest {

    private fun plugin(vararg rules: PluginRule, enabled: Boolean = true) =
        Plugin(name = "test", enabled = enabled, rules = rules.toList())

    @Test
    fun `REPLACE 替换文本`() {
        val rule = PluginRule(
            trigger = PluginTrigger.ON_REPLY,
            action = PluginAction.REPLACE,
            config = mapOf("find" to "你好", "replace" to "您好")
        )
        val result = PluginEngine.applyReply(listOf(plugin(rule)), "你好世界")
        assertEquals("您好世界", result)
    }

    @Test
    fun `正则替换`() {
        val rule = PluginRule(
            trigger = PluginTrigger.ON_REPLY,
            useRegex = true,
            action = PluginAction.REPLACE,
            config = mapOf("find" to "\\d+", "replace" to "#")
        )
        val result = PluginEngine.applyReply(listOf(plugin(rule)), "我有 3 只猫和 10 条狗")
        assertEquals("我有 # 只猫和 # 条狗", result)
    }

    @Test
    fun `前缀与后缀注入`() {
        val prefix = PluginRule(trigger = PluginTrigger.ON_REPLY, action = PluginAction.PREFIX, config = mapOf("text" to "【"))
        val suffix = PluginRule(trigger = PluginTrigger.ON_REPLY, action = PluginAction.SUFFIX, config = mapOf("text" to "】"))
        val result = PluginEngine.applyReply(listOf(plugin(prefix, suffix)), "内容")
        assertEquals("【内容】", result)
    }

    @Test
    fun `关键词触发状态变化`() {
        val rule = PluginRule(
            trigger = PluginTrigger.ON_MESSAGE,
            action = PluginAction.SET_STATUS,
            pattern = "亲亲",
            config = mapOf("affection" to "5", "mood" to "3")
        )
        val delta = PluginEngine.collectStatusDelta(listOf(plugin(rule)), "我想亲亲你")
        assertEquals(5, delta.affection)
        assertEquals(3, delta.mood)
    }

    @Test
    fun `不匹配关键词时不触发`() {
        val rule = PluginRule(
            trigger = PluginTrigger.ON_MESSAGE,
            action = PluginAction.SET_STATUS,
            pattern = "亲亲",
            config = mapOf("affection" to "5")
        )
        val delta = PluginEngine.collectStatusDelta(listOf(plugin(rule)), "你好")
        assertTrue(delta.isEmpty)
    }

    @Test
    fun `禁用插件不生效`() {
        val rule = PluginRule(trigger = PluginTrigger.ON_REPLY, action = PluginAction.REPLACE, config = mapOf("find" to "a", "replace" to "b"))
        val result = PluginEngine.applyReply(listOf(plugin(rule, enabled = false)), "aaa")
        assertEquals("aaa", result)
    }

    @Test
    fun `空 pattern 总是触发`() {
        val rule = PluginRule(trigger = PluginTrigger.ON_SEND, action = PluginAction.PREFIX, config = mapOf("text" to "前缀"))
        val result = PluginEngine.applySend(listOf(plugin(rule)), "消息")
        assertEquals("前缀消息", result)
    }

    @Test
    fun `常驻提示词注入 pattern 为空`() {
        val rule = PluginRule(
            trigger = PluginTrigger.ON_SEND,
            action = PluginAction.INJECT_PROMPT,
            pattern = "",
            config = mapOf("text" to "始终保持卖萌语气")
        )
        val result = PluginEngine.collectPromptInjections(listOf(plugin(rule)), "任意消息")
        assertEquals("始终保持卖萌语气", result)
    }

    @Test
    fun `关键词触发提示词注入`() {
        val rule = PluginRule(
            trigger = PluginTrigger.ON_SEND,
            action = PluginAction.INJECT_PROMPT,
            pattern = "战斗",
            config = mapOf("text" to "战斗场景描述更详细")
        )
        assertEquals("战斗场景描述更详细",
            PluginEngine.collectPromptInjections(listOf(plugin(rule)), "进入战斗！"))
        assertEquals("",
            PluginEngine.collectPromptInjections(listOf(plugin(rule)), "睡觉了"))
    }
}
