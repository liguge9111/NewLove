package com.tavern.app.core.data.network

import org.junit.Assert.*
import org.junit.Test

class SseParserTest {

    @Test
    fun `解析单个 data 事件`() {
        val parser = SseParser()
        val deltas = parser.feed("data: {\"choices\":[{\"delta\":{\"content\":\"你好\"}}]}\n\n")
        assertEquals(listOf("你好"), deltas)
    }

    @Test
    fun `解析多个连续事件`() {
        val parser = SseParser()
        val input = "data: {\"choices\":[{\"delta\":{\"content\":\"你\"}}]}\n\n" +
                "data: {\"choices\":[{\"delta\":{\"content\":\"好\"}}]}\n\n"
        val deltas = parser.feed(input)
        assertEquals(listOf("你", "好"), deltas)
    }

    @Test
    fun `数据分片到达`() {
        val parser = SseParser()
        // 分三次喂入，模拟网络分片
        val d1 = parser.feed("data: {\"choices\":[{\"delta\":{\"content\":\"你")
        assertTrue(d1.isEmpty())

        val d2 = parser.feed("好\"}}]}\n\n")
        assertEquals(listOf("你好"), d2)
    }

    @Test
    fun `处理 DONE 标记`() {
        val parser = SseParser()
        val input = "data: {\"choices\":[{\"delta\":{\"content\":\"内容\"}}]}\n\n" +
                "data: [DONE]\n\n"
        val deltas = parser.feed(input)
        assertEquals(listOf("内容"), deltas)
    }

    @Test
    fun `忽略非 data 行`() {
        val parser = SseParser()
        val input = ": keep-alive\n\n" +
                "data: {\"choices\":[{\"delta\":{\"content\":\"有效\"}}]}\n\n"
        val deltas = parser.feed(input)
        assertEquals(listOf("有效"), deltas)
    }

    @Test
    fun `空内容增量被忽略`() {
        val parser = SseParser()
        val input = "data: {\"choices\":[{\"delta\":{\"content\":\"\"}}]}\n\n" +
                "data: {\"choices\":[{\"delta\":{\"content\":\"非空\"}}]}\n\n"
        val deltas = parser.feed(input)
        assertEquals(listOf("非空"), deltas)
    }

    @Test
    fun `非法 JSON 不崩溃`() {
        val parser = SseParser()
        val deltas = parser.feed("data: 这不是JSON\n\n")
        assertTrue(deltas.isEmpty())
    }

    @Test
    fun `content 为 null 的分片被跳过而不是字符串null`() {
        val parser = SseParser()
        // 推理模型常见：role 分片 content=null，正文分片在 reasoning_content
        val input = "data: {\"choices\":[{\"delta\":{\"role\":\"assistant\",\"content\":null}}]}\n\n" +
                "data: {\"choices\":[{\"delta\":{\"reasoning_content\":\"思考中\"}}]}\n\n" +
                "data: {\"choices\":[{\"delta\":{\"content\":\"正文\"}}]}\n\n"
        val deltas = parser.feed(input)
        assertEquals(listOf("正文"), deltas)
    }

    @Test
    fun `混合 null 与正文的连续分片`() {
        val parser = SseParser()
        val input = (1..20).joinToString("") {
            "data: {\"choices\":[{\"delta\":{\"content\":null}}]}\n\n"
        } + "data: {\"choices\":[{\"delta\":{\"content\":\"好\"}}]}\n\n"
        val deltas = parser.feed(input)
        assertEquals(listOf("好"), deltas)
    }
}
