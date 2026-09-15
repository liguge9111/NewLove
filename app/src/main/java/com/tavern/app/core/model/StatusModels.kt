package com.tavern.app.core.model

import kotlinx.serialization.Serializable

/**
 * 角色状态变化量（增量，正数增加、负数减少）
 *
 * 由 AI 回复中的 [STATUS] 块解析得到，叠加到当前状态。
 */
data class StatusDelta(
    /** 心情变化量 */
    val mood: Int = 0,

    /** 体力变化量 */
    val energy: Int = 0,

    /** 好感度变化量 */
    val affection: Int = 0,

    /** 自定义状态变化量（key: 状态名, value: 增量） */
    val custom: Map<String, Int> = emptyMap()
) {
    /** 是否有任何变化 */
    val isEmpty: Boolean
        get() = mood == 0 && energy == 0 && affection == 0 && custom.isEmpty()
}

/**
 * 状态变化解析结果
 */
data class StatusChange(
    /** 剥离状态块后的正文 */
    val content: String,

    /** 状态变化量（无变化时为空增量） */
    val delta: StatusDelta
)

/**
 * 状态栏单个状态项定义（按角色卡定制）
 *
 * key 为内置项（mood/energy/affection）时读写对应内置列，
 * 其余 key 存取 customStates。
 */
@Serializable
data class StatusItemDef(
    /** 状态键（mood / energy / affection 或自定义名） */
    val key: String = "",

    /** 显示名 */
    val label: String = "",

    /** 默认值（0-100） */
    val defaultValue: Int = 50
)

/**
 * 联动旁白解析结果
 */
data class NarrationChange(
    /** 剥离旁白块后的正文 */
    val content: String,

    /** 互动模式用的叙事旁白（无则为 null） */
    val narration: String?
)
