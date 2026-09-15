package com.tavern.app.core.parser

import kotlinx.serialization.json.*

/**
 * JSON 解析辅助扩展函数
 */

/** 安全读取字符串字段，缺失或类型不符返回 null */
fun JsonObject.stringField(key: String): String? {
    val value = this[key] ?: return null
    return when (value) {
        is JsonPrimitive -> value.contentOrNull
        else -> null
    }
}

/** 安全读取字符串列表字段 */
fun JsonObject.stringListField(key: String): List<String>? {
    val value = this[key] ?: return null
    return when (value) {
        is JsonArray -> value.mapNotNull { it.jsonPrimitive.contentOrNull }
        is JsonPrimitive -> value.contentOrNull?.let { listOf(it) }
        else -> null
    }
}

/** 安全读取整数字段，缺失或类型不符返回 null */
fun JsonObject.intField(key: String): Int? {
    val value = this[key] ?: return null
    return when (value) {
        is JsonPrimitive -> value.contentOrNull?.toIntOrNull()
        else -> null
    }
}

/** 安全读取布尔字段，缺失或类型不符返回 null */
fun JsonObject.booleanField(key: String): Boolean? {
    val value = this[key] ?: return null
    return when (value) {
        is JsonPrimitive -> value.contentOrNull?.toBooleanStrictOrNull()
            ?: value.contentOrNull?.let { it.equals("true", ignoreCase = true) }
        else -> null
    }
}

/** 安全读取浮点字段 */
fun JsonObject.floatField(key: String): Float? {
    val value = this[key] ?: return null
    return when (value) {
        is JsonPrimitive -> value.contentOrNull?.toFloatOrNull()
        else -> null
    }
}

/** 读取 JsonObject 字段 */
fun JsonObject.objectField(key: String): JsonObject? =
    (this[key] as? JsonObject)

/** 读取 JsonArray 字段 */
fun JsonObject.arrayField(key: String): JsonArray? =
    (this[key] as? JsonArray)
