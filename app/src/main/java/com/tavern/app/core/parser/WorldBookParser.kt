package com.tavern.app.core.parser

import com.tavern.app.core.model.WorldBook
import com.tavern.app.core.model.WorldBookEntry
import com.tavern.app.core.model.WorldBookExtensions
import kotlinx.serialization.json.*

/**
 * 世界书解析器
 *
 * 解析 SillyTavern 世界书结构，兼容两种 entries 形式：
 *
 * 1) 旧版独立世界书（对象 / map 形式）：
 * ```json
 * { "entries": { "0": { "keys": [...], "content": "..." } } }
 * ```
 * 2) SillyTavern V2/V3 角色卡内嵌世界书（数组形式，每个条目自带 id）：
 * ```json
 * { "entries": [ { "id": 0, "keys": [...], "content": "..." } ] }
 * ```
 *
 * 兼容多种字段来源：character_book / characterBook / world_book / worldBook / lorebook
 */
object WorldBookParser {

    /** 世界书可能出现的字段名 */
    private val BOOK_KEYS = listOf("character_book", "characterBook", "world_book", "worldBook", "lorebook")

    /**
     * 从 JSON 字符串解析世界书
     */
    fun parse(jsonString: String): WorldBook? {
        return try {
            val json = Json {
                ignoreUnknownKeys = true
                isLenient = true
            }
            val root = json.parseToJsonElement(jsonString.trim())
            if (root !is JsonObject) return null
            parseFromJsonObject(root)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 从 JsonObject 解析世界书（自动识别世界书字段位置）
     */
    fun parseFromJsonObject(root: JsonObject): WorldBook? {
        // 1. 先尝试直接找到 entries 字段（root 可能本身就是世界书结构）
        var entriesElement: JsonElement? = root["entries"]

        // 2. 遍历已知的世界书字段名（注意用安全转型，entries 可能是数组而非对象）
        if (entriesElement == null) {
            for (key in BOOK_KEYS) {
                val bookObj = root[key] as? JsonObject ?: continue
                val e = bookObj["entries"]
                if (e != null) {
                    entriesElement = e
                    break
                }
            }
        }

        val element = entriesElement ?: return null
        return WorldBook(entries = parseEntries(element))
    }

    /**
     * 解析 entries 容器，兼容对象与数组两种形式。
     *
     * - JsonObject（旧版）：key 作为条目 ID
     * - JsonArray（V2/V3）：条目自身 `id` 字段优先，缺失时用下标
     */
    private fun parseEntries(element: JsonElement): Map<String, WorldBookEntry> {
        val result = LinkedHashMap<String, WorldBookEntry>()
        when (element) {
            is JsonArray -> element.forEachIndexed { index, value ->
                val obj = value as? JsonObject ?: return@forEachIndexed
                val id = obj.intField("id") ?: index
                result[id.toString()] = parseEntry(id, obj)
            }

            is JsonObject -> element.forEach { (key, value) ->
                val obj = value as? JsonObject ?: return@forEach
                val id = obj.intField("id") ?: key.toIntOrNull() ?: 0
                result[id.toString()] = parseEntry(id, obj)
            }

            else -> Unit
        }
        return result
    }

    /**
     * 解析单条世界书条目
     */
    private fun parseEntry(id: Int, obj: JsonObject): WorldBookEntry {
        // keys 字段：可能是数组或逗号分隔字符串
        val keys = parseStringList(obj["keys"])

        // secondary_keys
        val secondaryKeys = parseStringList(obj["secondary_keys"])
            ?: parseStringList(obj["secondaryKeys"])
            ?: emptyList()

        // extensions（安全转型，非对象时按空处理）
        val extensionsObj = obj["extensions"] as? JsonObject ?: JsonObject(emptyMap())
        val extensions = WorldBookExtensions(
            position = extensionsObj.intField("position") ?: 0,
            excludeRecursion = extensionsObj.booleanField("exclude_recursion")
                ?: extensionsObj.booleanField("excludeRecursion") ?: false,
            preventRecursion = extensionsObj.booleanField("prevent_recursion")
                ?: extensionsObj.booleanField("preventRecursion") ?: false,
            scanDepth = extensionsObj.intField("scan_depth")
                ?: extensionsObj.intField("scanDepth") ?: 4,
            probability = extensionsObj.intField("probability") ?: 100,
            useProbability = extensionsObj.booleanField("useProbability")
                ?: extensionsObj.booleanField("use_probability") ?: true,
            matchWholeWords = extensionsObj.booleanField("match_whole_words")
                ?: extensionsObj.booleanField("matchWholeWords") ?: false,
            caseSensitive = extensionsObj.booleanField("case_sensitive")
                ?: extensionsObj.booleanField("caseSensitive") ?: false,
            selectiveLogic = extensionsObj.intField("selectiveLogic")
                ?: extensionsObj.intField("selective_logic") ?: 0,
            group = extensionsObj.stringField("group") ?: "",
            groupOverride = extensionsObj.booleanField("group_override")
                ?: extensionsObj.booleanField("groupOverride") ?: false,
            groupWeight = extensionsObj.intField("group_weight")
                ?: extensionsObj.intField("groupWeight") ?: 100,
            injectionDepth = extensionsObj.intField("injection_depth")
                ?: extensionsObj.intField("injectionDepth") ?: 4,
            injectionPosition = extensionsObj.intField("injection_position")
                ?: extensionsObj.intField("injectionPosition") ?: 0,
            role = extensionsObj.intField("role") ?: 0,
            excluded = extensionsObj.booleanField("excluded") ?: false,
            delayUntilRecursion = extensionsObj.booleanField("delay_until_recursion")
                ?: extensionsObj.booleanField("delayUntilRecursion") ?: false
        )

        return WorldBookEntry(
            id = id,
            keys = keys ?: emptyList(),
            secondaryKeys = secondaryKeys,
            comment = obj.stringField("comment") ?: "",
            content = obj.stringField("content") ?: "",
            constant = obj.booleanField("constant") ?: false,
            selective = obj.booleanField("selective") ?: false,
            insertionOrder = obj.intField("insertion_order")
                ?: obj.intField("insertionOrder") ?: id,
            enabled = obj.booleanField("enabled") ?: true,
            position = obj.stringField("position") ?: "before_char",
            extensions = extensions
        )
    }

    /**
     * 解析字符串列表（数组 或 逗号分隔字符串），对非字符串元素容错
     */
    private fun parseStringList(value: JsonElement?): List<String>? {
        return when (value) {
            is JsonArray -> value.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            is JsonPrimitive -> value.contentOrNull?.let { text ->
                text.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            }
            else -> null
        }
    }
}
