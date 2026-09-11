package com.tavern.app.core.parser

import com.tavern.app.core.model.CharacterCard
import com.tavern.app.core.model.StatusItemDef
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 状态栏模板解析器
 *
 * 从角色卡 extensions 中提取按卡定制的状态栏定义。
 * 兼容常见命名：statusBars / status_schema / status / stats。
 * 每项支持字段：key|name、label|displayName、default|value|initial。
 *
 * 提取失败时返回 null，由调用方回落到默认三项（心情/体力/好感）。
 */
object StatusSchemaParser {

    private val SCHEMA_KEYS = listOf("statusBars", "status_schema", "statusSchema", "status", "stats")

    /** 默认状态栏模板 */
    fun defaultSchema(): List<StatusItemDef> = listOf(
        StatusItemDef("mood", "心情", 50),
        StatusItemDef("energy", "体力", 100),
        StatusItemDef("affection", "好感", 0)
    )

    /**
     * 从角色卡解析状态栏模板，解析不到返回 null
     */
    fun parse(card: CharacterCard): List<StatusItemDef>? {
        val extensions = card.extensions ?: return null
        for (schemaKey in SCHEMA_KEYS) {
            val element = extensions[schemaKey] ?: continue
            val items = parseArray(element) ?: continue
            if (items.isNotEmpty()) return items
        }
        return null
    }

    private fun parseArray(element: kotlinx.serialization.json.JsonElement): List<StatusItemDef>? {
        val array = element as? JsonArray ?: return null
        val items = array.mapNotNull { item ->
            val obj = item as? JsonObject ?: return@mapNotNull null
            val key = pickString(obj, "key", "name", "id") ?: return@mapNotNull null
            if (key.isBlank()) return@mapNotNull null
            val label = pickString(obj, "label", "displayName", "title") ?: key
            val default = pickInt(obj, "default", "value", "initial") ?: 50
            StatusItemDef(key = key, label = label, defaultValue = default.coerceIn(0, 100))
        }
        return items.takeIf { it.isNotEmpty() }
    }

    private fun pickString(obj: JsonObject, vararg keys: String): String? =
        keys.firstNotNullOfOrNull { key ->
            obj[key]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
        }

    private fun pickInt(obj: JsonObject, vararg keys: String): Int? =
        keys.firstNotNullOfOrNull { key ->
            obj[key]?.jsonPrimitive?.intOrNull
        }
}
