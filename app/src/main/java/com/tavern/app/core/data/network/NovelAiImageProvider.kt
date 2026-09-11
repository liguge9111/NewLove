package com.tavern.app.core.data.network

import com.tavern.app.core.model.ImageConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * NovelAI Provider
 *
 * 提交文生图请求，返回图片二进制（NovelAI 返回 ZIP 包时，
 * 直接透传原始字节，由调用方保存）。
 */
class NovelAiImageProvider(
    private val client: OkHttpClient = defaultImageClient()
) : ImageProvider {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun generate(
        config: ImageConfig,
        prompt: String,
        negativePrompt: String
    ): Result<ByteArray> = withContext(Dispatchers.IO) {
        try {
            val url = config.baseUrl.trimEnd('/') + "/ai/generate-image"
            val requestBody = NovelAiRequest(
                input = prompt.ifBlank { config.defaultPrompt },
                parameters = NovelAiParameters(
                    width = config.width,
                    height = config.height,
                    negativePrompt = negativePrompt.ifBlank { config.defaultNegativePrompt },
                    steps = config.steps,
                    scale = config.cfgScale.toDouble(),
                    sampler = config.sampler.ifBlank { "k_euler_ancestral" }
                )
            )

            val httpRequest = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer ${config.apiKey}")
                .addHeader("Content-Type", "application/json")
                .post(
                    json.encodeToString(NovelAiRequest.serializer(), requestBody)
                        .toRequestBody("application/json".toMediaType())
                )
                .build()

            client.newCall(httpRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    throw ApiException(response.code, response.body?.string() ?: "")
                }
                val bytes = response.body?.bytes()
                    ?: throw ApiException(-1, "空响应体")
                Result.success(bytes)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
