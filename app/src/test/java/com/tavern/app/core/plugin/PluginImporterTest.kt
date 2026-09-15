package com.tavern.app.core.plugin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginImporterTest {

    private val importer = PluginImporter()

    @Test
    fun `classify detects js extension`() {
        val source = """
            SillyTavern.getContext().eventSource.on('MESSAGE_SENT', () => {});
            document.querySelector('.chat').addEventListener(...);
        """.trimIndent()
        assertEquals(PluginImporter.SourceKind.JS_EXTENSION, importer.classify(source))
    }

    @Test
    fun `classify detects regex like`() {
        val source = """[{"findRegex":"/foo/g","replaceString":"bar"}]"""
        assertEquals(PluginImporter.SourceKind.REGEX_LIKE, importer.classify(source))
    }

    @Test
    fun `parseDraft tolerates markdown fence`() {
        val reply = """
            ```json
            {"name":"测试插件","description":"desc","rules":[
              {"trigger":"ON_REPLY","useRegex":true,"pattern":"abc","action":"REPLACE","config":{"find":"abc","replace":"xyz"}}
            ]}
            ```
        """.trimIndent()
        val draft = importer.parseDraft(reply, "fallback")
        assertEquals("测试插件", draft.name)
        assertEquals(1, draft.rules.size)
        assertEquals("abc", draft.rules[0].pattern)
    }

    @Test
    fun `parseDraft empty rules`() {
        val draft = importer.parseDraft("""{"name":"x","description":"不支持","rules":[]}""", "")
        assertTrue(draft.rules.isEmpty())
        assertTrue(draft.warnings.isNotEmpty())
    }
}
