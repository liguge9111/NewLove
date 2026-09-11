package com.tavern.app.core.plugin

import com.tavern.app.core.model.Plugin
import com.tavern.app.core.model.PluginAction
import com.tavern.app.core.model.PluginRule
import com.tavern.app.core.model.PluginTrigger
import com.tavern.app.core.model.StatusDelta

/**
 * 插件引擎（声明式规则执行，纯 Kotlin，无 JS 运行时依赖）
 *
 * 处理三类动作：
 * - REPLACE / PREFIX / SUFFIX：文本变换（作用于 AI 回复或用户输入）
 * - SET_STATUS：关键词匹配时改变角色状态（增量）
 */
object PluginEngine {

    /**
     * 处理 AI 回复文本（ON_REPLY 规则）
     */
    fun applyReply(plugins: List<Plugin>, reply: String): String {
        var result = reply
        plugins.filter { it.enabled }.forEach { plugin ->
            plugin.rules
                .filter { it.trigger == PluginTrigger.ON_REPLY }
                .forEach { rule ->
                    if (matches(rule, reply)) {
                        result = applyTextAction(rule, result)
                    }
                }
        }
        return result
    }

    /**
     * 处理用户输入文本（ON_SEND 规则）
     */
    fun applySend(plugins: List<Plugin>, input: String): String {
        var result = input
        plugins.filter { it.enabled }.forEach { plugin ->
            plugin.rules
                .filter { it.trigger == PluginTrigger.ON_SEND }
                .forEach { rule ->
                    if (matches(rule, input)) {
                        result = applyTextAction(rule, result)
                    }
                }
        }
        return result
    }

    /**
     * 收集关键词状态变化（ON_MESSAGE + SET_STATUS 规则）
     */
    fun collectStatusDelta(plugins: List<Plugin>, text: String): StatusDelta {
        var mood = 0
        var energy = 0
        var affection = 0

        plugins.filter { it.enabled }.forEach { plugin ->
            plugin.rules
                .filter {
                    it.trigger == PluginTrigger.ON_MESSAGE &&
                        it.action == PluginAction.SET_STATUS
                }
                .forEach { rule ->
                    if (matches(rule, text)) {
                        mood += rule.config["mood"]?.toIntOrNull() ?: 0
                        energy += rule.config["energy"]?.toIntOrNull() ?: 0
                        affection += rule.config["affection"]?.toIntOrNull() ?: 0
                    }
                }
        }

        return StatusDelta(mood = mood, energy = energy, affection = affection)
    }

    /**
     * 收集提示词注入（INJECT_PROMPT 规则）
     *
     * - pattern 为空：常驻注入（宏包/模板类常见用法）
     * - pattern 非空：仅当匹配当前用户消息时注入
     * @return 拼接后的提示词片段（无则空串）
     */
    fun collectPromptInjections(plugins: List<Plugin>, userText: String): String =
        plugins.filter { it.enabled }
            .flatMap { plugin ->
                plugin.rules.filter {
                    it.action == PluginAction.INJECT_PROMPT &&
                        (it.trigger == PluginTrigger.ON_SEND || it.trigger == PluginTrigger.ON_MESSAGE)
                }
            }
            .filter { matches(it, userText) }
            .mapNotNull { it.config["text"]?.takeIf { t -> t.isNotBlank() } }
            .joinToString("\n\n")

    /**
     * 判断规则是否匹配文本
     */
    private fun matches(rule: PluginRule, text: String): Boolean {
        if (rule.pattern.isBlank()) return true
        return if (rule.useRegex) {
            runCatching { Regex(rule.pattern).containsMatchIn(text) }.getOrDefault(false)
        } else {
            text.contains(rule.pattern)
        }
    }

    /**
     * 应用文本变换动作
     */
    private fun applyTextAction(rule: PluginRule, text: String): String {
        return when (rule.action) {
            PluginAction.REPLACE -> {
                val find = rule.config["find"] ?: return text
                val replace = rule.config["replace"] ?: ""
                if (rule.useRegex) {
                    runCatching { Regex(find).replace(text, replace) }.getOrDefault(text)
                } else {
                    text.replace(find, replace)
                }
            }

            PluginAction.PREFIX -> (rule.config["text"] ?: "") + text

            PluginAction.SUFFIX -> text + (rule.config["text"] ?: "")

            PluginAction.SET_STATUS -> text

            PluginAction.INJECT_PROMPT -> text // 提示词注入由 collectPromptInjections 处理
        }
    }
}
