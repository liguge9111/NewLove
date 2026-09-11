package com.tavern.app.core.data.network

import com.tavern.app.core.model.AsrConfig
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 语音识别（ASR）Provider 接口
 */
interface AsrProvider {
    /**
     * 将音频转写为文本
     *
     * @param audio 音频字节（m4a/wav/mp3 等）
     * @param fileName 文件名（含扩展名，供服务端判断格式）
     */
    suspend fun transcribe(config: AsrConfig, audio: ByteArray, fileName: String): Result<String>
}

/**
 * OpenAI 兼容 ASR 实现
 *
 * POST {baseUrl}/audio/transcriptions（multipart/form-data）
 * 兼容 OpenAI Whisper 及各类兼容网关（含 MiMo 等中转服务）。
 */
class OpenAiCompatibleAsrProvider : AsrProvider {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    override suspend fun transcribe(
        config: AsrConfig,
        audio: ByteArray,
        fileName: String
    ): Result<String> = runCatching {
        val base = config.baseUrl.trimEnd('/')
        val url = when {
            base.isEmpty() -> throw IllegalStateException("ASR 服务地址为空")
            base.endsWith("/audio/transcriptions") -> base
            base.endsWith("/v1") -> "$base/audio/transcriptions"
            else -> "$base/v1/audio/transcriptions"
        }

        val audioBody = audio.toRequestBody(
            guessMediaType(fileName).toMediaTypeOrNull()
        )
        val multipart = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", fileName, audioBody)
            .addFormDataPart("model", config.modelName.ifBlank { "whisper-1" })
            .apply {
                if (config.language.isNotBlank()) {
                    addFormDataPart("language", config.language)
                }
                addFormDataPart("response_format", "json")
            }
            .build()

        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer ${config.apiKey}")
            .post(multipart)
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException("ASR 请求失败（HTTP ${response.code}）：$body")
            }
            val text = runCatching { JSONObject(body).optString("text") }.getOrDefault("")
            if (text.isBlank() && body.isBlank()) {
                throw IllegalStateException("ASR 返回为空")
            }
            text.ifBlank { body }
        }
    }

    private fun guessMediaType(fileName: String): String = when {
        fileName.endsWith(".wav", true) -> "audio/wav"
        fileName.endsWith(".mp3", true) -> "audio/mpeg"
        fileName.endsWith(".ogg", true) -> "audio/ogg"
        fileName.endsWith(".flac", true) -> "audio/flac"
        else -> "audio/mp4"
    }
}

/**
 * ASR Provider 工厂
 */
object AsrProviderFactory {
    fun getProvider(@Suppress("UNUSED_PARAMETER") config: AsrConfig): AsrProvider =
        OpenAiCompatibleAsrProvider()
}
