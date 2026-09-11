package com.tavern.app.core.data.network

import com.tavern.app.core.model.ModelConfig
import com.tavern.app.core.model.MessageRole
import com.tavern.app.core.prompt.PromptMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * OpenAI 兼容格式 Provider
 *
 * 适配所有采用 OpenAI Chat Completions 格式的第三方服务，
 * 支持自定义端点、Bearer Token 认证和流式响应。
 */
class OpenAiCompatibleProvider(
    private val client: OkHttpClient = defaultClient()
) : ModelProvider {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

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

                val parser = SseParser()
                while (!source.exhausted()) {
                    val line = source.readUtf8Line() ?: break
                    if (line.startsWith("data:")) {
                        // 构造完整行（含换行）喂给解析器
                        val deltas = parser.feed(line + "\n")
                        for (delta in deltas) {
                            fullResponse.append(delta)
                            onDelta(delta)
                        }
                    }
                }

                if (fullResponse.isBlank()) {
                    throw ApiException(
                        -2,
                        "模型返回内容为空。若使用推理模型（如 deepseek 系列），" +
                            "思考过程会占用「最大生成长度」额度，请在模型设置中调大该值（建议 ≥ 4096）"
                    )
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

                val chatResponse = json.decodeFromString<ChatResponse>(body)
                val content = chatResponse.choices.firstOrNull()
                    ?.message?.content
                    ?: ""

                if (content.isBlank()) {
                    throw ApiException(
                        -2,
                        "模型返回内容为空。若使用推理模型（如 deepseek 系列），" +
                            "思考过程会占用「最大生成长度」额度，请在模型设置中调大该值（建议 ≥ 4096）"
                    )
                }

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

    /**
     * 构建 HTTP 请求
     */
    private fun buildRequest(
        config: ModelConfig,
        messages: List<PromptMessage>,
        stream: Boolean
    ): Request {
        val requestBody = ChatRequest(
            model = config.modelName,
            messages = convertMessages(messages),
            temperature = config.temperature.toDouble(),
            topP = config.topP.toDouble(),
            maxTokens = config.maxTokens,
            frequencyPenalty = config.frequencyPenalty.toDouble(),
            presencePenalty = config.presencePenalty.toDouble(),
            stream = stream
        )

        val jsonBody = json.encodeToString(ChatRequest.serializer(), requestBody)

        val url = config.baseUrl.trimEnd('/') + "/chat/completions"

        return Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer ${config.apiKey}")
            .addHeader("Content-Type", "application/json")
            .post(jsonBody.toRequestBody("application/json".toMediaType()))
            .build()
    }

    companion object {
        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(300, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }
}

/**
 * API 请求异常
 */
class ApiException(val statusCode: Int, val body: String) :
    Exception("API 请求失败（$statusCode）：$body")
