package com.tavern.app.core.util

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class VariableResolverTest {

    @Test
    fun `替换用户和角色变量`() {
        val text = "你好，{{user}}，我是{{char}}"
        val vars = mapOf("user" to "小明", "char" to "爱丽丝")
        val result = VariableResolver.resolve(text, vars)
        assertEquals("你好，小明，我是爱丽丝", result)
    }

    @Test
    fun `替换随机数变量`() {
        val text = "随机数：{{random}}"
        val result = VariableResolver.resolve(text, emptyMap(), Random(42))
        assertTrue(result.startsWith("随机数："))
        val num = result.substringAfter("随机数：")
        assertTrue(num.toIntOrNull() in 0..9)
    }

    @Test
    fun `未知变量替换为空`() {
        val text = "前{{unknown}}后"
        val result = VariableResolver.resolve(text)
        assertEquals("前后", result)
    }

    @Test
    fun `无变量文本原样返回`() {
        val text = "没有变量的文本"
        val result = VariableResolver.resolve(text)
        assertEquals(text, result)
    }

    @Test
    fun `变量带空格也能解析`() {
        val text = "{{ user }}"
        val result = VariableResolver.resolve(text, mapOf("user" to "测试"))
        assertEquals("测试", result)
    }

    @Test
    fun `defaultVariables 提供用户角色映射`() {
        val vars = VariableResolver.defaultVariables("小明", "爱丽丝")
        assertEquals("小明", vars["user"])
        assertEquals("爱丽丝", vars["char"])
    }
}
