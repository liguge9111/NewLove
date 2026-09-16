package com.tavern.app.core.data.local

import androidx.room.TypeConverter
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Room 类型转换器
 *
 * 将 List/Map 等复杂类型转为 JSON 字符串存储。
 */
class Converters {

    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter
    fun fromStringList(value: List<String>): String =
        json.encodeToString(ListSerializer(String.serializer()), value)

    @TypeConverter
    fun toStringList(value: String): List<String> =
        try {
            json.decodeFromString(ListSerializer(String.serializer()), value)
        } catch (e: Exception) {
            emptyList()
        }

    @TypeConverter
    fun fromIntMap(value: Map<String, Int>): String =
        json.encodeToString(MapSerializer(String.serializer(), Int.serializer()), value)

    @TypeConverter
    fun toIntMap(value: String): Map<String, Int> =
        try {
            json.decodeFromString(MapSerializer(String.serializer(), Int.serializer()), value)
        } catch (e: Exception) {
            emptyMap()
        }
}
