package com.tavern.app.core.prompt

import com.tavern.app.core.model.*
import org.junit.Assert.*
import org.junit.Test

class PromptBuilderTest {

    private val card = CharacterCard(
        name = "爱丽丝",
        description = "温柔的少女",
        personality = "善良",
        scenario = "在森林中",
        firstMessage = "你好呀，{{user}}！",
        systemPrompt = "你是{{char}}"
    )

    @Test
    fun `构建系统提示词包含角色定义`() {
        val prompt = PromptBuilder.buildSystemPrompt(card)
        assertTrue(prompt.contains("温柔的少女"))
        assertTrue(prompt.contains("善良"))
        assertTrue(prompt.contains("在森林中"))
    }

    @Test
    fun `系统提示词变量替换`() {
        val prompt = PromptBuilder.buildSystemPrompt(
            card,
            variables = mapOf("char" to "爱丽丝")
        )
        assertTrue(prompt.contains("你是爱丽丝"))
    }

    @Test
    fun `构建消息列表含系统消息和首条消息`() {
        val messages = PromptBuilder.buildMessages(card, userName = "小明")
        assertTrue(messages.isNotEmpty())
        assertEquals(MessageRole.SYSTEM, messages[0].role)
        // 首条消息
        val firstMes = messages.first { it.role == MessageRole.ASSISTANT }
        assertEquals("你好呀，小明！", firstMes.content)
    }

    @Test
    fun `构建消息列表含聊天历史`() {
        val history = listOf(
            ChatMessage(id = 1, role = MessageRole.USER, content = "你好"),
            ChatMessage(id = 2, role = MessageRole.ASSISTANT, content = "你好呀")
        )
        val messages = PromptBuilder.buildMessages(card, history = history, userName = "小明")

        val userMsg = messages.filter { it.role == MessageRole.USER }
        assertEquals(1, userMsg.size)
        assertEquals("你好", userMsg[0].content)

        val assistantMsg = messages.filter { it.role == MessageRole.ASSISTANT }
        // 首条消息 + 历史 assistant
        assertEquals(2, assistantMsg.size)
    }

    @Test
    fun `世界书条目注入系统提示词`() {
        val activation = WorldBookActivation(
            entries = listOf(
                WorldBookEntry(
                    id = 1,
                    content = "这个世界存在魔法",
                    position = "before_char"
                )
            )
        )
        val prompt = PromptBuilder.buildSystemPrompt(card, activation)
        assertTrue(prompt.contains("这个世界存在魔法"))
    }

    @Test
    fun `作者注释插入历史`() {
        val history = listOf(
            ChatMessage(id = 1, role = MessageRole.USER, content = "第一条"),
            ChatMessage(id = 2, role = MessageRole.ASSISTANT, content = "第二条"),
            ChatMessage(id = 3, role = MessageRole.USER, content = "第三条")
        )
        val config = PromptConfig(authorNote = "[作者注释]", authorNoteDepth = 1)

        val messages = PromptBuilder.buildMessages(card, history = history, config = config)

        val noteMsg = messages.filter { it.content == "[作者注释]" }
        assertEquals(1, noteMsg.size)
        assertEquals(MessageRole.SYSTEM, noteMsg[0].role)
    }

    @Test
    fun `作者注释 depth=0 追加到系统提示词`() {
        val config = PromptConfig(authorNote = "[作者注释]", authorNoteDepth = 0)
        val prompt = PromptBuilder.buildSystemPrompt(card, config = config)
        assertTrue(prompt.contains("[作者注释]"))
    }

    @Test
    fun `聊天模式不注入互动指令`() {
        val prompt = PromptBuilder.buildSystemPrompt(card, mode = InteractiveMode.CHAT)
        assertFalse(prompt.contains("互动模式"))
        assertFalse(prompt.contains("[CHOICES]"))
    }

    @Test
    fun `文字冒险模式注入叙事指令且不引导选项`() {
        val prompt = PromptBuilder.buildSystemPrompt(card, mode = InteractiveMode.TEXT_ADVENTURE)
        assertTrue(prompt.contains("文字冒险"))
        // 文字冒险不再主动引导输出选项（视觉小说专属；卡片自带选项仍会解析显示）
        assertFalse(prompt.contains("[CHOICES]"))
    }

    @Test
    fun `视觉小说模式注入视觉小说指令`() {
        val prompt = PromptBuilder.buildSystemPrompt(card, mode = InteractiveMode.VISUAL_NOVEL)
        assertTrue(prompt.contains("视觉小说"))
    }

    @Test
    fun `自由角色扮演模式注入角色扮演指令`() {
        val prompt = PromptBuilder.buildSystemPrompt(card, mode = InteractiveMode.FREE_ROLEPLAY)
        assertTrue(prompt.contains("自由角色扮演"))
    }

    @Test
    fun `互动模式指令注入到消息列表系统提示词`() {
        val messages = PromptBuilder.buildMessages(card, mode = InteractiveMode.TEXT_ADVENTURE)
        val systemMsg = messages.first { it.role == MessageRole.SYSTEM }
        assertTrue(systemMsg.content.contains("文字冒险"))
    }
}
