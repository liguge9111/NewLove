package com.tavern.app.core.model

import kotlinx.serialization.Serializable

/**
 * 插件规则触发时机
 */
enum class PluginTrigger {
    /** AI 回复后处理回复文本 */
    ON_REPLY,

    /** 用户发送消息前处理输入文本 */
    ON_SEND,

    /** 消息产生时（用于状态变化等副作用） */
    ON_MESSAGE
}

/**
 * 插件规则动作类型
 */
enum class PluginAction {
    /** 文本替换（config: find → replace，支持正则） */
    REPLACE,

    /** 前缀注入（config: text） */
    PREFIX,

    /** 后缀注入（config: text） */
    SUFFIX,

    /** 状态变化（config: mood/energy/affection 增量） */
    SET_STATUS,

    /** 提示词注入（config: text；pattern 空=常驻，否则命中时注入） */
    INJECT_PROMPT,

    /** 执行 JS 脚本（config: script；QuickJS 受限沙箱，仅改写类） */
    EXECUTE_JS
}

/**
 * 插件规则
 *
 * 声明式规则：触发时机 + 匹配条件 + 动作。
 */
@Serializable
data class PluginRule(
    val trigger: PluginTrigger = PluginTrigger.ON_REPLY,

    /** 是否按正则匹配（否则按包含关键词匹配） */
    val useRegex: Boolean = false,

    /** 匹配模式（正则或关键词，空表示总是触发） */
    val pattern: String = "",

    val action: PluginAction = PluginAction.REPLACE,

    /** 动作参数（key-value） */
    val config: Map<String, String> = emptyMap()
)

/**
 * 插件
 *
 * 一组规则的集合，可启用/停用。
 */
data class Plugin(
    val id: Long = 0,

    val name: String = "",

    val version: String = "1.0",

    val description: String = "",

    val author: String = "",

    /** 是否启用 */
    val enabled: Boolean = true,

    /** 是否为内置插件（不可删除） */
    val isBuiltin: Boolean = false,

    /** 绑定的角色卡 ID（null = 全局生效；非空 = 仅该角色聊天时生效） */
    val characterCardId: Long? = null,

    /** 规则列表 */
    val rules: List<PluginRule> = emptyList()
)
