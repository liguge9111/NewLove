package com.tavern.app.core.util

import kotlin.random.Random

/**
 * 提示词变量替换器
 *
 * 支持 SillyTavern 风格的变量占位符：
 * - {{user}}：用户名
 * - {{char}}：角色名
 * - {{random}}：随机数（0-9）
 * - {{time}}：当前时间
 * - {{date}}：当前日期
 * - {{original}}：原始回复（用于 post_history_instructions）
 * - 自定义变量
 */
object VariableResolver {

    /** 匹配 {{variableName}}（闭合花括号必须转义：Android ICU 正则比 JVM 严格） */
    private val VARIABLE_REGEX = Regex("\\{\\{\\s*([^{}]+?)\\s*\\}\\}")

    /**
     * 替换文本中的所有变量
     *
     * @param text 含变量的文本
     * @param variables 变量映射（不含 {{}} 包裹）
     * @param random 随机数生成器（可注入以便测试）
     * @return 替换后的文本
     */
    fun resolve(
        text: String,
        variables: Map<String, String> = emptyMap(),
        random: Random = Random.Default
    ): String {
        if (text.isBlank() || !text.contains("{{")) return text

        return VARIABLE_REGEX.replace(text) { match ->
            val varName = match.groupValues[1].trim()
            resolveSingle(varName, variables, random)
        }
    }

    /**
     * 解析单个变量
     */
    private fun resolveSingle(
        varName: String,
        variables: Map<String, String>,
        random: Random
    ): String {
        // 自定义变量优先
        if (variables.containsKey(varName)) {
            return variables[varName] ?: ""
        }

        return when (varName) {
            "random" -> random.nextInt(10).toString()
            "time" -> currentTime()
            "date" -> currentDate()
            else -> "" // 未识别的变量置空
        }
    }

    private fun currentTime(): String {
        val now = java.time.LocalTime.now()
        return String.format("%02d:%02d", now.hour, now.minute)
    }

    private fun currentDate(): String {
        val now = java.time.LocalDate.now()
        return String.format("%04d-%02d-%02d", now.year, now.monthValue, now.dayOfMonth)
    }

    /**
     * 创建默认变量集（用户 + 角色）
     */
    fun defaultVariables(userName: String, characterName: String): Map<String, String> =
        mapOf(
            "user" to userName,
            "char" to characterName
        )
}
