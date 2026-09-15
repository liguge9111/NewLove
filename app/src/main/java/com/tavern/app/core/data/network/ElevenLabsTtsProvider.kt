package com.tavern.app.core.data.network

import com.tavern.app.core.model.VoiceConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * ElevenLabs TTS Provider
 *
 * 端点：POST /v1/text-to-speech/{voice_id}
 * 认证：xi-api-key header
 * 响应：音频二进制（MP3）
 */
class ElevenLabsTtsProvider(
    private val client: OkHttpClient = defaultClient()
) : TtsProvider {

    override suspend fun synthesize(
        config: VoiceConfig,
        text: String
    ): Result<ByteArray> = withContext(Dispatchers.IO) {
        try {
            val body = buildJsonObject {
                put("text", text)
                put("model_id", "eleven_multilingual_v2")
                put("voice_settings", buildJsonObject {
                    put("stability", 0.5)
                    put("similarity_boost", 0.75)
                })
            }

            val voiceId = config.voiceId.ifBlank { "default" }
            val url = config.baseUrl.trimEnd('/') + "/v1/text-to-speech/$voiceId"

            val request = Request.Builder()
                .url(url)
                .addHeader("xi-api-key", config.apiKey)
                .addHeader("Content-Type", "application/json")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw ApiException(response.code, response.body?.string() ?: "")
                }
                val audio = response.body?.bytes()
                    ?: throw ApiException(-1, "空响应体")
                Result.success(audio)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    companion object {
        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .build()
    }
}
