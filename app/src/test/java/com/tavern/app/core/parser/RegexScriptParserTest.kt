package com.tavern.app.core.parser

import com.tavern.app.core.model.CharacterCard
import com.tavern.app.core.model.PluginAction
import com.tavern.app.core.model.PluginTrigger
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RegexScriptParserTest {

    @Test
    fun `parse js regex with flags`() {
        val result = RegexScriptParser.parseJsRegex("/foo(bar)/gi")
        assertNotNull(result)
        assertTrue(result!!.startsWith("(?i)"))
        assertTrue(result.contains("foo(bar)"))
    }

    @Test
    fun `parse bare regex passes through`() {
        assertEquals("abc\\d+", RegexScriptParser.parseJsRegex("abc\\d+"))
    }

    @Test
    fun `parse rules from extensions json`() {
        val extensions = buildJsonObject {
            putJsonArray("regex_scripts") {
                add(
                    buildJsonObject {
                        put("scriptName", "剥离示例")
                        put("findRegex", "/<example>[\\s\\S]*?<\\/example>/g")
                        put("replaceString", "")
                        put("disabled", false)
                    }
                )
                add(
                    buildJsonObject {
                        put("scriptName", "已禁用")
                        put("findRegex", "/x/g")
                        put("disabled", true)
                    }
                )
            }
        }.toString()

        val rules = RegexScriptParser.parseRulesFromExtensionsJson(extensions)
        assertEquals(1, rules.size)
        val rule = rules[0]
        assertEquals(PluginTrigger.ON_REPLY, rule.trigger)
        assertEquals(PluginAction.REPLACE, rule.action)
        assertTrue(rule.useRegex)
        assertTrue(rule.pattern.contains("example"))
    }

    @Test
    fun `user_input target maps to ON_SEND`() {
        val extensions = buildJsonObject {
            putJsonArray("regex_scripts") {
                add(
                    buildJsonObject {
                        put("findRegex", "/bad/")
                        put("replaceString", "good")
                        put("target", "user_input")
                    }
                )
            }
        }.toString()
        val rules = RegexScriptParser.parseRulesFromExtensionsJson(extensions)
        assertEquals(PluginTrigger.ON_SEND, rules[0].trigger)
        assertEquals("good", rules[0].config["replace"])
    }

    @Test
    fun `empty extensions returns empty`() {
        assertTrue(RegexScriptParser.parseRulesFromExtensionsJson("{}").isEmpty())
        assertTrue(
            RegexScriptParser.parseRules(CharacterCard(name = "x")).isEmpty()
        )
    }
}
