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
 * OpenAI 兼容 TTS Provider
 *
 * 端点：POST /v1/audio/speech
 * 认证：Authorization Bearer
 * 响应：音频二进制
 */
class OpenAiTtsProvider(
    private val client: OkHttpClient = defaultClient()
) : TtsProvider {

    override suspend fun synthesize(
        config: VoiceConfig,
        text: String
    ): Result<ByteArray> = withContext(Dispatchers.IO) {
        try {
            val body = buildJsonObject {
                put("model", config.voiceId.ifBlank { "tts-1" })
                put("input", text)
                put("voice", "alloy")
                put("speed", config.speed.toDouble())
            }

            val url = config.baseUrl.trimEnd('/') + "/v1/audio/speech"

            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer ${config.apiKey}")
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

/**
 * TTS Provider 工厂
 */
object TtsProviderFactory {

    fun getProvider(config: VoiceConfig): TtsProvider = when (config.providerType) {
        com.tavern.app.core.model.VoiceProviderType.ELEVENLABS -> elevenLabs
        com.tavern.app.core.model.VoiceProviderType.AZURE -> azure
        com.tavern.app.core.model.VoiceProviderType.OPENAI_TTS -> openAi
        // 系统 TTS 走 Android TextToSpeech，不在此工厂处理
        com.tavern.app.core.model.VoiceProviderType.SYSTEM_TTS -> throw IllegalArgumentException("系统 TTS 应使用 Android TextToSpeech")
    }

    val elevenLabs: TtsProvider by lazy { ElevenLabsTtsProvider() }
    val azure: TtsProvider by lazy { AzureTtsProvider() }
    val openAi: TtsProvider by lazy { OpenAiTtsProvider() }
}
