package com.tavern.app.core.data.network

import com.tavern.app.core.util.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.float
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Embedding 服务（OpenAI 兼容 /v1/embeddings）
 */
@Singleton
class EmbeddingService @Inject constructor(
    private val appSettings: AppSettings
) {

    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    fun isConfigured(): Boolean =
        appSettings.embBaseUrl.isNotBlank() && appSettings.embApiKey.isNotBlank()

    /** 批量文本转向量 */
    suspend fun embed(texts: List<String>): Result<List<List<Float>>> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (!isConfigured()) throw IllegalStateException("未配置 Embedding 服务")
                val base = appSettings.embBaseUrl.trimEnd('/')
                val url = if (base.endsWith("/v1")) "$base/embeddings" else "$base/v1/embeddings"
                val bodyJson = json.encodeToString(
                    JsonElement.serializer(),
                    buildJsonObject {
                        put("model", JsonPrimitive(appSettings.embModel))
                        put("input", JsonArray(texts.map { JsonPrimitive(it) }))
                    }
                )
                val request = Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer ${appSettings.embApiKey}")
                    .post(bodyJson.toRequestBody("application/json".toMediaTypeOrNull()))
                    .build()
                client.newCall(request).execute().use { resp ->
                    val body = resp.body?.string() ?: ""
                    if (!resp.isSuccessful) {
                        throw IllegalStateException("Embedding HTTP ${resp.code}: ${body.take(200)}")
                    }
                    val root = json.parseToJsonElement(body).jsonObject
                    root["data"]?.jsonArray
                        ?.map { item ->
                            item.jsonObject["embedding"]!!.jsonArray
                                .map { it.jsonPrimitive.float }
                        }
                        ?: throw IllegalStateException("Embedding 响应格式异常")
                }
            }
        }
}
