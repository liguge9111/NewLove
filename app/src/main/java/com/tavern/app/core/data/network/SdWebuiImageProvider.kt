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
 * Stable Diffusion WebUI Provider（AUTOMATIC1111 API）
 */
class SdWebuiImageProvider(
    private val client: OkHttpClient = defaultImageClient()
) : ImageProvider {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun generate(
        config: ImageConfig,
        prompt: String,
        negativePrompt: String
    ): Result<ByteArray> = withContext(Dispatchers.IO) {
        try {
            val url = config.baseUrl.trimEnd('/') + "/sdapi/v1/txt2img"
            val requestBody = SdWebuiRequest(
                prompt = prompt.ifBlank { config.defaultPrompt },
                negativePrompt = negativePrompt.ifBlank { config.defaultNegativePrompt },
                steps = config.steps,
                cfgScale = config.cfgScale.toDouble(),
                width = config.width,
                height = config.height,
                samplerName = config.sampler
            )

            val httpRequest = Request.Builder()
                .url(url)
                .addHeader("Content-Type", "application/json")
                .post(
                    json.encodeToString(SdWebuiRequest.serializer(), requestBody)
                        .toRequestBody("application/json".toMediaType())
                )
                .build()

            client.newCall(httpRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    throw ApiException(response.code, response.body?.string() ?: "")
                }
                val body = response.body?.string()
                    ?: throw ApiException(-1, "空响应体")
                val result = json.decodeFromString<SdWebuiResponse>(body)
                val b64 = result.images.firstOrNull()
                    ?: throw ApiException(-1, "无图片返回")
                Result.success(Base64.getDecoder().decode(b64))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
