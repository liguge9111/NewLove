package com.tavern.app.core.data.network

import com.tavern.app.core.model.ImageConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.Base64

/**
 * OpenAI DALL-E Provider（兼容 /v1/images/generations 格式）
 */
class DallEImageProvider(
    private val client: OkHttpClient = defaultImageClient()
) : ImageProvider {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun generate(
        config: ImageConfig,
        prompt: String,
        negativePrompt: String
    ): Result<ByteArray> = withContext(Dispatchers.IO) {
        try {
            val url = config.baseUrl.trimEnd('/') + "/images/generations"
            val requestBody = DallERequest(
                prompt = prompt.ifBlank { config.defaultPrompt },
                size = "${config.width}x${config.height}"
            )

            val httpRequest = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer ${config.apiKey}")
                .addHeader("Content-Type", "application/json")
                .post(
                    json.encodeToString(DallERequest.serializer(), requestBody)
                        .toRequestBody("application/json".toMediaType())
                )
                .build()

            client.newCall(httpRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    throw ApiException(response.code, response.body?.string() ?: "")
                }
                val body = response.body?.string()
                    ?: throw ApiException(-1, "空响应体")
                val result = json.decodeFromString<DallEResponse>(body)
                val image = result.data.firstOrNull()
                    ?: throw ApiException(-1, "无图片返回")

                val bytes = when {
                    !image.b64Json.isNullOrBlank() ->
                        Base64.getDecoder().decode(image.b64Json)
                    !image.url.isNullOrBlank() ->
                        downloadImage(image.url, config.apiKey)
                    else -> throw ApiException(-1, "无图片数据")
                }
                Result.success(bytes)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun downloadImage(url: String, apiKey: String): ByteArray {
        val builder = Request.Builder().url(url)
        if (apiKey.isNotBlank()) {
            builder.addHeader("Authorization", "Bearer $apiKey")
        }
        client.newCall(builder.build()).execute().use { response ->
            if (!response.isSuccessful) {
                throw ApiException(response.code, "图片下载失败")
            }
            return response.body?.bytes() ?: throw ApiException(-1, "图片下载为空")
        }
    }
}
