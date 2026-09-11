package com.tavern.app.core.data.network

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * SSE（Server-Sent Events）流式响应解析器
 *
 * 解析 OpenAI 兼容的流式响应格式：
 * ```
 * data: {"choices":[{"delta":{"content":"你"}}]}
 * data: {"choices":[{"delta":{"content":"好"}}]}
 * data: [DONE]
 * ```
 *
 * 支持数据分片到达（缓冲累积，按完整行解析）。
 * 推理类模型（如 MiMo）会发送大量 content 为 null 的分片
 * （正文在 reasoning_content 等字段），必须跳过而非当成字符串 "null"。
 */
class SseParser {

    private val buffer = StringBuilder()
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * 喂入新的数据块，返回本次解析出的文本增量列表
     *
     * @param chunk 新增的字节/字符串数据
     * @return 解析出的内容增量（可为空）
     */
    fun feed(chunk: String): List<String> {
        buffer.append(chunk)
        val deltas = mutableListOf<String>()

        while (true) {
            val lineEnd = buffer.indexOf('\n')
            if (lineEnd < 0) break

            val line = buffer.substring(0, lineEnd).trimEnd('\r')
            buffer.delete(0, lineEnd + 1)

            if (line.startsWith("data:")) {
                val data = line.substring(5).trim()
                if (data == "[DONE]") {
                    continue
                }
                if (data.isEmpty()) continue
                val content = parseDeltaContent(data)
                if (content != null && content.isNotEmpty()) {
                    deltas.add(content)
                }
            }
        }

        return deltas
    }

    /**
     * 解析单个 data 事件，提取 delta.content
     *
     * content 为 JSON null 或缺失时返回 null（跳过该分片）。
     */
    private fun parseDeltaContent(data: String): String? {
        return try {
            val root = json.parseToJsonElement(data).jsonObject
            val choices = root["choices"]?.jsonArray ?: return null
            if (choices.isEmpty()) return null
            val delta = choices[0].jsonObject["delta"]?.jsonObject ?: return null
            val contentElement = delta["content"] ?: return null
            // JsonNull 是显式的 JSON null：不能取 .content（会得到字符串 "null"）
            if (contentElement is JsonNull) return null
            contentElement.jsonPrimitive.content
        } catch (e: Exception) {
            null
        }
    }
}
