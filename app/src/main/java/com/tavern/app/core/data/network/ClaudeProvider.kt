package com.tavern.app.core.data.network

import com.tavern.app.core.model.ModelConfig
import com.tavern.app.core.model.MessageRole
import com.tavern.app.core.prompt.PromptMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Anthropic Claude Provider
 *
 * Claude API 特点：
 * - system 提示词为独立字段（非 messages 数组内）
 * - 认证用 x-api-key + anthropic-version header
 * - 流式响应为 SSE 事件流（content_block_delta 事件携带文本增量）
 */
class ClaudeProvider(
    private val client: OkHttpClient = defaultClient()
) : ModelProvider {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override suspend fun chatStream(
        config: ModelConfig,
        messages: List<PromptMessage>,
        onDelta: (String) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val request = buildRequest(config, messages, stream = true)
            val fullResponse = StringBuilder()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw ApiException(response.code, response.body?.string() ?: "")
                }
                val source = response.body?.source()
                    ?: throw ApiException(-1, "空响应体")

                while (!source.exhausted()) {
                    val line = source.readUtf8Line() ?: break
                    if (line.startsWith("data:")) {
                        val data = line.substring(5).trim()
                        val delta = parseClaudeDelta(data)
                        if (delta != null && delta.isNotEmpty()) {
                            fullResponse.append(delta)
                            onDelta(delta)
                        }
                    }
                }
            }

            Result.success(fullResponse.toString())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun chat(
        config: ModelConfig,
        messages: List<PromptMessage>
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val request = buildRequest(config, messages, stream = false)

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw ApiException(response.code, response.body?.string() ?: "")
                }
                val body = response.body?.string()
                    ?: throw ApiException(-1, "空响应体")

                val root = json.parseToJsonElement(body).jsonObject
                val content = root["content"]?.jsonArray
                    ?.mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.content }
                    ?.joinToString("")
                    ?: ""

                Result.success(content)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun convertMessages(messages: List<PromptMessage>): List<ChatApiMessage> =
        // Claude 的 system 消息不在此列表（由 buildRequest 单独处理）
        messages.filter { it.role != MessageRole.SYSTEM }.map { msg ->
            ChatApiMessage(
                role = if (msg.role == MessageRole.USER) "user" else "assistant",
                content = msg.content
            )
        }

    /**
     * 构建 Claude 请求
     */
    private fun buildRequest(
        config: ModelConfig,
        messages: List<PromptMessage>,
        stream: Boolean
    ): Request {
        // 提取 system 消息
        val systemPrompt = messages
            .filter { it.role == MessageRole.SYSTEM }
            .joinToString("\n\n") { it.content }

        val body = buildJsonObject {
            put("model", config.modelName)
            put("max_tokens", config.maxTokens)
            put("temperature", config.temperature.toDouble())
            put("top_p", config.topP.toDouble())
            if (systemPrompt.isNotBlank()) {
                put("system", systemPrompt)
            }
            put("stream", stream)
            put("messages", buildJsonArray {
                convertMessages(messages).forEach { msg ->
                    add(buildJsonObject {
                        put("role", msg.role)
                        put("content", msg.content)
                    })
                }
            })
        }

        val url = config.baseUrl.trimEnd('/') + "/messages"

        return Request.Builder()
            .url(url)
            .addHeader("x-api-key", config.apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
    }

    /**
     * 解析 Claude 流式 delta（content_block_delta 事件）
     */
    private fun parseClaudeDelta(data: String): String? {
        return try {
            val root = json.parseToJsonElement(data).jsonObject
            val type = root["type"]?.jsonPrimitive?.content ?: return null
            if (type != "content_block_delta") return null
            val delta = root["delta"]?.jsonObject ?: return null
            if (delta["type"]?.jsonPrimitive?.content != "text_delta") return null
            delta["text"]?.jsonPrimitive?.content
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(300, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }
}
