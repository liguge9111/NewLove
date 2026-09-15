package com.tavern.app.core.memory

import com.tavern.app.core.model.MessageRole
import com.tavern.app.core.prompt.PromptMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryEngineTest {

    @Test
    fun `estimateTokens positive for mixed text`() {
        assertTrue(MemoryEngine.estimateTokens("你好世界hello") > 0)
        assertEquals(0, MemoryEngine.estimateTokens(""))
    }

    @Test
    fun `parseFacts extracts list`() {
        val reply = """```json
        {"facts":["玩家怕黑","角色在3月15日答应同居"]}
        ```"""
        val facts = MemoryEngine.parseFacts(reply)
        assertEquals(2, facts.size)
        assertEquals("玩家怕黑", facts[0])
    }

    @Test
    fun `parseFacts empty array`() {
        assertTrue(MemoryEngine.parseFacts("""{"facts":[]}""").isEmpty())
    }

    @Test
    fun `trimToBudget keeps system and recent messages`() {
        val messages = listOf(
            PromptMessage(MessageRole.SYSTEM, "系统提示".repeat(50)),
            PromptMessage(MessageRole.USER, "很长的旧消息内容一二三四五六七八九十".repeat(10)),
            PromptMessage(MessageRole.ASSISTANT, "很长的旧回复内容一二三四五六七八九十".repeat(10)),
            PromptMessage(MessageRole.USER, "最近的消息"),
            PromptMessage(MessageRole.ASSISTANT, "最近的回复")
        )
        // 预算 = system 成本 + 仅够装最后一条短消息的余量
        val lastCost = MemoryEngine.estimateTokens("最近的回复")
        val budget = MemoryEngine.estimateTokens("系统提示".repeat(50)) + lastCost + 2
        val trimmed = MemoryEngine.trimToBudget(messages, budget)
        assertTrue(trimmed.first().role == MessageRole.SYSTEM)
        assertTrue(trimmed.last().content.contains("最近"))
        assertTrue(trimmed.size < messages.size)
    }

    @Test
    fun `trimToBudget no-op when budget huge`() {
        val messages = listOf(
            PromptMessage(MessageRole.SYSTEM, "s"),
            PromptMessage(MessageRole.USER, "u")
        )
        assertEquals(2, MemoryEngine.trimToBudget(messages, 100_000).size)
    }
}
