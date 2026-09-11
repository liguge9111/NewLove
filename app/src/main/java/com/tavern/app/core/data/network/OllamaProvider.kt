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
 * Ollama 本地模型 Provider
 *
 * Ollama API 特点：
 * - 端点默认 http://localhost:11434/api/chat
 * - 无需认证
 * - 流式响应为 NDJSON（每行一个 JSON，含 message.content 增量）
 */
class OllamaProvider(
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
                    if (line.isBlank()) continue
                    val delta = parseOllamaDelta(line)
                    if (delta != null && delta.isNotEmpty()) {
                        fullResponse.append(delta)
                        onDelta(delta)
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
                val content = root["message"]?.jsonObject
                    ?.get("content")?.jsonPrimitive?.content ?: ""

                Result.success(content)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun convertMessages(messages: List<PromptMessage>): List<ChatApiMessage> =
        messages.map { msg ->
            ChatApiMessage(
                role = when (msg.role) {
                    MessageRole.SYSTEM -> "system"
                    MessageRole.USER -> "user"
                    MessageRole.ASSISTANT -> "assistant"
                },
                content = msg.content
            )
        }

    private fun buildRequest(
        config: ModelConfig,
        messages: List<PromptMessage>,
        stream: Boolean
    ): Request {
        val body = buildJsonObject {
            put("model", config.modelName)
            put("stream", stream)
            put("options", buildJsonObject {
                put("temperature", config.temperature.toDouble())
                put("top_p", config.topP.toDouble())
            })
            put("messages", buildJsonArray {
                convertMessages(messages).forEach { msg ->
                    add(buildJsonObject {
                        put("role", msg.role)
                        put("content", msg.content)
                    })
                }
            })
        }

        val url = config.baseUrl.trimEnd('/') + "/api/chat"

        return Request.Builder()
            .url(url)
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
    }

    private fun parseOllamaDelta(line: String): String? {
        return try {
            val root = json.parseToJsonElement(line).jsonObject
            root["message"]?.jsonObject
                ?.get("content")?.jsonPrimitive?.content
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(300, TimeUnit.SECONDS)
            .build()
    }
}
