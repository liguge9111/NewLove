package com.tavern.app.core.parser

import com.tavern.app.core.model.WorldBook
import com.tavern.app.core.model.WorldBookActivation
import com.tavern.app.core.model.WorldBookEntry
import java.util.regex.Pattern
import kotlin.random.Random

/**
 * 世界书扫描器
 *
 * 根据对话文本扫描世界书，返回应激活的条目。
 *
 * 核心逻辑：
 * 1. 常驻条目（constant）始终激活
 * 2. 选择性条目（selective）仅当手动激活时才触发
 * 3. 主关键词（keys）任意命中即触发（OR）
 * 4. 次级关键词（secondaryKeys）按 selectiveLogic 组合：
 *    - 0 = AND：全部命中
 *    - 1 = NOT ANY：全部未命中
 *    - 2 = NOT ALL：不全命中
 *    - 3 = ANY：任一命中
 * 5. 支持正则表达式（用 /.../ 包裹）和整词匹配
 * 6. 递归扫描：激活条目的内容中若命中其他条目关键词，递归触发
 * 7. 扫描深度（scanDepth）限制递归层数
 */
object WorldBookScanner {

    /**
     * 扫描世界书，返回激活的条目列表
     *
     * @param worldBook 世界书
     * @param text 用于触发的文本（通常是最近的对话消息）
     * @param manuallyActivatedIds 手动激活的条目 ID 集合（用于 selective 条目）
     * @param random 随机数生成器（用于概率判定，可注入以便测试）
     * @return 激活结果
     */
    fun scan(
        worldBook: WorldBook,
        text: String,
        manuallyActivatedIds: Set<Int> = emptySet(),
        random: Random = Random.Default
    ): WorldBookActivation {
        val activated = LinkedHashMap<Int, WorldBookEntry>()
        val matchedKeys = mutableMapOf<Int, String>()

        // 1. 常驻条目始终激活
        worldBook.entries.values
            .filter { it.enabled && it.constant }
            .forEach { activated[it.id] = it }

        // 2. 第一轮：扫描主文本触发
        scanPass(worldBook, text, manuallyActivatedIds, activated, matchedKeys, random)
            .forEach { activated[it.id] = it }

        // 3. 递归扫描：激活条目的内容作为新的触发文本
        var currentDepth = 1
        var frontier = activated.values.toList()
        while (frontier.isNotEmpty()) {
            val nextFrontier = mutableListOf<WorldBookEntry>()
            for (entry in frontier) {
                // 阻止递归的条目跳过
                if (entry.excludeRecursion || entry.preventRecursion) continue
                // 深度限制（用条目自身的 scanDepth 或全局深度）
                if (currentDepth >= entry.scanDepth) continue

                val newlyActivated = scanPass(
                    worldBook, entry.content, manuallyActivatedIds,
                    activated, matchedKeys, random
                )
                nextFrontier.addAll(newlyActivated)
            }
            frontier = nextFrontier
            currentDepth++
        }

        // 4. 排序：先按 position 分组，再按 insertionOrder
        val sorted = activated.values.sortedWith(
            compareBy<WorldBookEntry> { positionRank(it.position) }
                .thenBy { it.insertionOrder }
        )

        return WorldBookActivation(entries = sorted, matchedKeys = matchedKeys)
    }

    /**
     * 单轮扫描：在给定文本中查找所有触发条目
     * 返回新激活的条目（不在已激活集合中的）
     */
    private fun scanPass(
        worldBook: WorldBook,
        text: String,
        manuallyActivatedIds: Set<Int>,
        activated: MutableMap<Int, WorldBookEntry>,
        matchedKeys: MutableMap<Int, String>,
        random: Random
    ): List<WorldBookEntry> {
        val newlyActivated = mutableListOf<WorldBookEntry>()

        for (entry in worldBook.entries.values) {
            if (!entry.enabled) continue
            if (activated.containsKey(entry.id)) continue
            if (entry.content.isBlank() && entry.keys.isEmpty()) continue

            // 选择性条目：必须手动激活
            if (entry.selective && entry.id !in manuallyActivatedIds) continue

            // 概率判定
            if (entry.useProbability && entry.probability < 100) {
                if (random.nextInt(100) >= entry.probability) continue
            }

            // 关键词触发判定
            val matchedKey = matchesEntry(entry, text) ?: continue

            activated[entry.id] = entry
            matchedKeys[entry.id] = matchedKey
            newlyActivated.add(entry)
        }

        return newlyActivated
    }

    /**
     * 判断条目是否被文本触发
     *
     * @return 命中的关键词（用于调试），未命中返回 null
     */
    fun matchesEntry(entry: WorldBookEntry, text: String): String? {
        // 主关键词匹配
        val primaryMatch = entry.keys.firstOrNull { key -> matchKey(key, text, entry) }
        val secondaryMatches = entry.secondaryKeys.filter { key -> matchKey(key, text, entry) }

        return when {
            // 无主键无次级键：只有常驻或内容为空才走到这里
            entry.keys.isEmpty() && entry.secondaryKeys.isEmpty() -> ""

            // 有主键：主键命中 + 次级键逻辑判断
            entry.keys.isNotEmpty() -> {
                if (primaryMatch == null) return null
                if (entry.secondaryKeys.isEmpty()) return primaryMatch
                if (checkSecondaryLogic(entry, secondaryMatches)) primaryMatch else null
            }

            // 只有次级键：按 selectiveLogic 单独判断
            else -> {
                if (checkSecondaryLogic(entry, secondaryMatches))
                    secondaryMatches.firstOrNull() ?: ""
                else null
            }
        }
    }

    /**
     * 检查次级关键词逻辑
     */
    private fun checkSecondaryLogic(entry: WorldBookEntry, secondaryMatches: List<String>): Boolean {
        val all = entry.secondaryKeys.size
        val matched = secondaryMatches.size
        return when (entry.selectiveLogic) {
            0 -> matched == all && all > 0              // AND：全部命中
            1 -> matched == 0                            // NOT ANY：全部未命中
            2 -> matched < all                           // NOT ALL：不全命中
            3 -> matched > 0                             // ANY：任一命中
            else -> true
        }
    }

    /**
     * 匹配单个关键词（支持正则和整词匹配）
     */
    private fun matchKey(key: String, text: String, entry: WorldBookEntry): Boolean {
        if (key.isBlank()) return false

        val target = if (entry.caseSensitive) text else text.lowercase()

        // 正则表达式：/pattern/ 或 /pattern/flags
        if (key.startsWith("/") && key.length > 2) {
            val regex = parseRegex(key)
            return regex?.containsMatchIn(target) ?: false
        }

        val needle = if (entry.caseSensitive) key else key.lowercase()

        return if (entry.matchWholeWords) {
            // 整词匹配
            val pattern = Pattern.compile("(?<![\\w])${Pattern.quote(needle)}(?![\\w])")
            pattern.matcher(target).find()
        } else {
            // 子串匹配
            target.contains(needle)
        }
    }

    /**
     * 解析 /pattern/flags 形式的正则表达式
     */
    private fun parseRegex(key: String): Regex? {
        return try {
            // 找到最后一个未转义的 /
            val lastSlash = key.lastIndexOf('/')
            if (lastSlash <= 0) return null
            val pattern = key.substring(1, lastSlash)
            val flags = key.substring(lastSlash + 1)
            val options = mutableSetOf<RegexOption>()
            if (flags.contains('i')) options.add(RegexOption.IGNORE_CASE)
            if (flags.contains('m')) options.add(RegexOption.MULTILINE)
            if (flags.contains('s')) options.add(RegexOption.DOT_MATCHES_ALL)
            Regex(pattern, options)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 插入位置排序权重
     */
    private fun positionRank(position: String): Int = when (position) {
        "before_char" -> 0
        "after_char" -> 1
        "before_anchor" -> 2
        "after_anchor" -> 3
        "anchor_top" -> 4
        "anchor_bottom" -> 5
        else -> 6
    }
}
