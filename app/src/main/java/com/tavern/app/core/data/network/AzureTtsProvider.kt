package com.tavern.app.core.data.network

import com.tavern.app.core.model.VoiceConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Azure TTS Provider
 *
 * 端点：POST https://{region}.tts.speech.microsoft.com/cognitiveservices/v1
 * 认证：Ocp-Apim-Subscription-Key header
 * 请求体：SSML
 * 响应：音频二进制（MP3）
 */
class AzureTtsProvider(
    private val client: OkHttpClient = defaultClient()
) : TtsProvider {

    override suspend fun synthesize(
        config: VoiceConfig,
        text: String
    ): Result<ByteArray> = withContext(Dispatchers.IO) {
        try {
            // SSML 构建
            val ssml = buildString {
                append("<speak version='1.0' xml:lang='zh-CN'>")
                append("<voice xml:lang='zh-CN' name='")
                append(config.voiceId.ifBlank { "zh-CN-XiaoxiaoNeural" })
                append("'>")
                append(escapeXml(text))
                append("</voice></speak>")
            }

            val url = config.baseUrl.trimEnd('/')

            val request = Request.Builder()
                .url(url)
                .addHeader("Ocp-Apim-Subscription-Key", config.apiKey)
                .addHeader("Content-Type", "application/ssml+xml")
                .addHeader("X-Microsoft-OutputFormat", "audio-24khz-48kbitrate-mono-mp3")
                .addHeader("User-Agent", "TavernApp")
                .post(ssml.toRequestBody("application/ssml+xml".toMediaType()))
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

    private fun escapeXml(text: String): String =
        text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")

    companion object {
        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .build()
    }
}
