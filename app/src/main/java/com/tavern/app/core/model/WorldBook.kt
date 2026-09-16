package com.tavern.app.core.model

import kotlinx.serialization.Serializable

/**
 * 世界书（World Info / Lorebook）
 *
 * 兼容 SillyTavern 的世界书结构，支持完整解析：
 * 关键词触发、正则、逻辑运算、优先级、递归引用、扫描深度控制。
 */
@Serializable
data class WorldBook(
    /** 条目集合，key 为条目 ID（字符串形式） */
    val entries: Map<String, WorldBookEntry> = emptyMap()
) {
    /** 按条目 ID（数字）排序后的条目列表 */
    fun sortedEntries(): List<WorldBookEntry> =
        entries.values.sortedBy { it.id }
}

/**
 * 世界书单一条目
 */
@Serializable
data class WorldBookEntry(
    /** 数字 ID */
    val id: Int = 0,

    /** 触发关键词（主关键词） */
    val keys: List<String> = emptyList(),

    /** 次级关键词（与主关键词逻辑组合） */
    val secondaryKeys: List<String> = emptyList(),

    /** 备注 */
    val comment: String = "",

    /** 条目内容（注入到提示词的文本） */
    val content: String = "",

    /** 是否常驻（始终注入，无需触发） */
    val constant: Boolean = false,

    /** 是否选择性（需要手动激活才触发） */
    val selective: Boolean = false,

    /** 插入顺序（数值越大越靠后/越重要） */
    val insertionOrder: Int = 0,

    /** 是否启用 */
    val enabled: Boolean = true,

    /** 插入位置：before_char / after_char / before_anchor */
    val position: String = "before_char",

    /** 扩展配置 */
    val extensions: WorldBookExtensions = WorldBookExtensions()
) {
    /** 是否排除递归 */
    val excludeRecursion: Boolean get() = extensions.excludeRecursion

    /** 是否阻止递归 */
    val preventRecursion: Boolean get() = extensions.preventRecursion

    /** 扫描深度 */
    val scanDepth: Int get() = extensions.scanDepth

    /** 触发概率（0-100） */
    val probability: Int get() = extensions.probability

    /** 是否使用概率 */
    val useProbability: Boolean get() = extensions.useProbability

    /** 是否整词匹配 */
    val matchWholeWords: Boolean get() = extensions.matchWholeWords

    /** 是否大小写敏感 */
    val caseSensitive: Boolean get() = extensions.caseSensitive

    /** 选择性逻辑：0=AND, 1=NOT ANY, 2=NOT ALL, 3=ANY */
    val selectiveLogic: Int get() = extensions.selectiveLogic
}

/**
 * 世界书条目扩展配置（SillyTavern extensions 字段）
 */
@Serializable
data class WorldBookExtensions(
    /** 相对 anchor 的位置偏移 */
    val position: Int = 0,

    /** 排除递归 */
    val excludeRecursion: Boolean = false,

    /** 阻止递归 */
    val preventRecursion: Boolean = false,

    /** 扫描深度（递归扫描的最大深度，默认 4） */
    val scanDepth: Int = 4,

    /** 触发概率（0-100，默认 100） */
    val probability: Int = 100,

    /** 是否使用概率 */
    val useProbability: Boolean = true,

    /** 是否整词匹配 */
    val matchWholeWords: Boolean = false,

    /** 是否大小写敏感 */
    val caseSensitive: Boolean = false,

    /** 选择性逻辑：0=AND, 1=NOT ANY, 2=NOT ALL, 3=ANY */
    val selectiveLogic: Int = 0,

    /** 分组名 */
    val group: String = "",

    /** 是否覆盖分组 */
    val groupOverride: Boolean = false,

    /** 分组权重 */
    val groupWeight: Int = 100,

    /** 注入深度 */
    val injectionDepth: Int = 4,

    /** 注入位置 */
    val injectionPosition: Int = 0,

    /** 角色限制：0=任意, 1=system, 2=user, 3=assistant */
    val role: Int = 0,

    /** 是否已排除 */
    val excluded: Boolean = false,

    /** 延迟到递归 */
    val delayUntilRecursion: Boolean = false
)

/**
 * 世界书扫描结果（解析后的激活条目）
 */
data class WorldBookActivation(
    /** 已激活的条目 */
    val entries: List<WorldBookEntry>,

    /** 激活时命中的关键词（用于调试） */
    val matchedKeys: Map<Int, String> = emptyMap()
)
