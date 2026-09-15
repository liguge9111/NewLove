package com.tavern.app.core.parser

import org.junit.Assert.*
import org.junit.Test
import java.util.Base64

class PngCardReaderTest {

    /**
     * 构造一个最小可用的 PNG 字节数组（含 tEXt "chara" chunk）
     */
    private fun buildPng(tEXtChunks: List<Pair<String, String>>): ByteArray {
        val out = java.io.ByteArrayOutputStream()

        // PNG 签名
        val signature = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
        )
        out.write(signature)

        // IHDR chunk（最小内容）
        val ihdrData = ByteArray(13)
        ihdrData[0] = 0; ihdrData[1] = 0; ihdrData[2] = 0; ihdrData[3] = 1  // width
        ihdrData[4] = 0; ihdrData[5] = 0; ihdrData[6] = 0; ihdrData[7] = 1  // height
        ihdrData[8] = 8   // bit depth
        ihdrData[9] = 6   // color type RGBA
        writeChunk(out, "IHDR", ihdrData)

        // tEXt chunks
        for ((keyword, value) in tEXtChunks) {
            val data = keyword.toByteArray(Charsets.UTF_8) + byteArrayOf(0) + value.toByteArray(Charsets.UTF_8)
            writeChunk(out, "tEXt", data)
        }

        // IEND chunk
        writeChunk(out, "IEND", ByteArray(0))

        return out.toByteArray()
    }

    private fun writeChunk(out: java.io.ByteArrayOutputStream, type: String, data: ByteArray) {
        // length (4 bytes big-endian)
        val length = data.size
        out.write((length ushr 24) and 0xFF)
        out.write((length ushr 16) and 0xFF)
        out.write((length ushr 8) and 0xFF)
        out.write(length and 0xFF)
        // type
        out.write(type.toByteArray(Charsets.US_ASCII))
        // data
        out.write(data)
        // crc (4 bytes, 填 0 即可，解析器不校验)
        out.write(byteArrayOf(0, 0, 0, 0))
    }

    @Test
    fun `从 PNG 提取角色卡 JSON`() {
        val cardJson = """{"name":"PNG角色","description":"从PNG提取"}"""
        val base64 = Base64.getEncoder().encodeToString(cardJson.toByteArray(Charsets.UTF_8))
        val png = buildPng(listOf("chara" to base64))

        val result = PngCardReader.extractCardJson(png)
        assertNotNull(result)
        assertEquals(cardJson, result)
    }

    @Test
    fun `非 chara 字段被忽略`() {
        val png = buildPng(listOf("author" to "somebody"))

        val result = PngCardReader.extractCardJson(png)
        assertNull(result)
    }

    @Test
    fun `非 PNG 文件返回 null`() {
        val result = PngCardReader.extractCardJson("not a png".toByteArray())
        assertNull(result)
    }

    @Test
    fun `端到端：PNG 字节到 CharacterCard`() {
        val cardJson = """
        {
          "spec": "chara_card_v2",
          "spec_version": "2.0",
          "data": {
            "name": "端到端角色",
            "description": "完整流程测试"
          }
        }
        """.trimIndent()
        val base64 = Base64.getEncoder().encodeToString(cardJson.toByteArray(Charsets.UTF_8))
        val png = buildPng(listOf("chara" to base64))

        val card = CharacterCardLoader.loadFromBytes(png, "png")
        assertNotNull(card)
        assertEquals("端到端角色", card!!.name)
        assertEquals("完整流程测试", card.description)
    }

    @Test
    fun `JSON 文件加载角色卡`() {
        val cardJson = """{"name":"JSON角色"}"""
        val card = CharacterCardLoader.loadFromBytes(cardJson.toByteArray(), "json")
        assertNotNull(card)
        assertEquals("JSON角色", card!!.name)
    }

    // ===== 回归：真机导入失败的场景 =====

    @Test
    fun `端到端：V3 卡片含数组形式内嵌世界书`() {
        val cardJson = """
        {
          "spec": "chara_card_v3",
          "spec_version": "3.0",
          "data": {
            "name": "数组世界书角色",
            "description": "复现真机导入失败",
            "character_book": {
              "name": "内嵌书",
              "entries": [
                { "id": 0, "keys": ["魔法"], "content": "第一条", "enabled": true },
                { "id": 1, "keys": ["剑"], "content": "第二条", "enabled": true }
              ]
            }
          }
        }
        """.trimIndent()
        val base64 = Base64.getEncoder().encodeToString(cardJson.toByteArray(Charsets.UTF_8))
        val png = buildPng(listOf("chara" to base64))

        val card = CharacterCardLoader.loadFromBytes(png, "png")
        assertNotNull(card)
        assertEquals("数组世界书角色", card!!.name)
        assertNotNull(card.characterBook)
        assertEquals(2, card.characterBook!!.entries.size)
        assertEquals("第一条", card.characterBook!!.entries["0"]!!.content)
        assertEquals("第二条", card.characterBook!!.entries["1"]!!.content)
    }

    @Test
    fun `base64 含换行仍可解析`() {
        val cardJson = """{"name":"折行角色"}"""
        val b64 = Base64.getEncoder().encodeToString(cardJson.toByteArray(Charsets.UTF_8))
        // 每 8 字符插入换行，模拟部分工具折行的输出
        val wrapped = b64.chunked(8).joinToString("\n")
        val png = buildPng(listOf("chara" to wrapped))

        val card = CharacterCardLoader.loadFromBytes(png, "png")
        assertNotNull(card)
        assertEquals("折行角色", card!!.name)
    }

    @Test
    fun `data 缺失时不抛异常`() {
        val cardJson = """{"spec":"chara_card_v3"}"""
        val base64 = Base64.getEncoder().encodeToString(cardJson.toByteArray(Charsets.UTF_8))
        val png = buildPng(listOf("chara" to base64))

        val card = CharacterCardLoader.loadFromBytes(png, "png")
        assertNotNull(card)
        assertEquals("", card!!.name)
    }
}
