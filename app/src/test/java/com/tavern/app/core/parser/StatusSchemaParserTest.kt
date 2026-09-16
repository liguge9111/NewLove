package com.tavern.app.core.parser

import com.tavern.app.core.model.CardSpec
import com.tavern.app.core.model.CharacterCard
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class StatusSchemaParserTest {

    private fun cardWithExtensions(json: String): CharacterCard {
        val obj = Json.parseToJsonElement(json).let { it as JsonObject }
        return CharacterCard(
            name = "测试",
            spec = CardSpec.CHARA_CARD_V2,
            extensions = obj
        )
    }

    @Test
    fun `parse reads statusBars array`() {
        val card = cardWithExtensions(
            """
            {"statusBars": [
                {"key":"sanity","label":"理智","default":80},
                {"name":"corruption","displayName":"堕落","value":10}
            ]}
            """.trimIndent()
        )
        val schema = StatusSchemaParser.parse(card)
        assertNotNull(schema)
        assertEquals(2, schema!!.size)
        assertEquals("sanity", schema[0].key)
        assertEquals("理智", schema[0].label)
        assertEquals(80, schema[0].defaultValue)
        assertEquals("corruption", schema[1].key)
        assertEquals("堕落", schema[1].label)
        assertEquals(10, schema[1].defaultValue)
    }

    @Test
    fun `parse returns null without extensions`() {
        assertNull(StatusSchemaParser.parse(CharacterCard(name = "x")))
    }

    @Test
    fun `parse ignores entries without key`() {
        val card = cardWithExtensions(
            """{"status": [{"label":"无效"},{"key":"hp","label":"生命","default":100}]}"""
        )
        val schema = StatusSchemaParser.parse(card)
        assertEquals(1, schema!!.size)
        assertEquals("hp", schema[0].key)
    }

    @Test
    fun `default schema has three builtin items`() {
        val schema = StatusSchemaParser.defaultSchema()
        assertEquals(listOf("mood", "energy", "affection"), schema.map { it.key })
    }
}
