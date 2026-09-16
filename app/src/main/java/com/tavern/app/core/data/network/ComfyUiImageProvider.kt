package com.tavern.app.core.data.network

import com.tavern.app.core.model.ImageConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID

/**
 * ComfyUI Provider
 *
 * 提交标准 SD1.5 txt2img 工作流，轮询 history 获取结果图片。
 * 注意：checkpoint 名称固定为常见默认值，高级用户可自行调整。
 */
class ComfyUiImageProvider(
    private val client: OkHttpClient = defaultImageClient()
) : ImageProvider {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun generate(
        config: ImageConfig,
        prompt: String,
        negativePrompt: String
    ): Result<ByteArray> = withContext(Dispatchers.IO) {
        try {
            val base = config.baseUrl.trimEnd('/')
            val workflow = buildWorkflow(
                prompt = prompt.ifBlank { config.defaultPrompt },
                negative = negativePrompt.ifBlank { config.defaultNegativePrompt },
                config = config
            )

            val promptId = submitPrompt(base, workflow)

            // 轮询 history 直到生图完成
            repeat(120) {
                delay(1000)
                val filename = queryHistory(base, promptId)
                if (filename != null) {
                    val bytes = downloadImage(base, filename)
                    return@withContext Result.success(bytes)
                }
            }

            throw ApiException(-1, "ComfyUI 生图超时")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 提交工作流，返回 prompt_id
     */
    private fun submitPrompt(base: String, workflow: JsonObject): String {
        val payload = buildJsonObject {
            put("prompt", workflow)
            put("client_id", UUID.randomUUID().toString())
        }
        val request = Request.Builder()
            .url("$base/prompt")
            .addHeader("Content-Type", "application/json")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw ApiException(response.code, response.body?.string() ?: "")
            }
            val body = response.body?.string()
                ?: throw ApiException(-1, "空响应体")
            val result = json.parseToJsonElement(body).jsonObject
            return result["prompt_id"]?.jsonPrimitive?.content
                ?: throw ApiException(-1, "未返回 prompt_id")
        }
    }

    /**
     * 查询 history，若生图完成返回图片文件名，否则返回 null
     */
    private fun queryHistory(base: String, promptId: String): String? {
        val request = Request.Builder()
            .url("$base/history/$promptId")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string() ?: return null
            return parseFilename(body, promptId)
        }
    }

    private fun parseFilename(historyJson: String, promptId: String): String? {
        return runCatching {
            val root = json.parseToJsonElement(historyJson).jsonObject
            val entry = root[promptId]?.jsonObject ?: return null
            val outputs = entry["outputs"]?.jsonObject ?: return null
            val saveNode = outputs["9"]?.jsonObject ?: return null
            val images = saveNode["images"]?.jsonArray ?: return null
            images.firstOrNull()?.jsonObject?.get("filename")?.jsonPrimitive?.content
        }.getOrNull()
    }

    private fun downloadImage(base: String, filename: String): ByteArray {
        val request = Request.Builder()
            .url("$base/view?filename=$filename&subfolder=&type=output")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw ApiException(response.code, "图片下载失败")
            }
            return response.body?.bytes() ?: throw ApiException(-1, "图片下载为空")
        }
    }

    /**
     * 构造标准 SD1.5 txt2img 工作流
     */
    private fun buildWorkflow(
        prompt: String,
        negative: String,
        config: ImageConfig
    ): JsonObject = buildJsonObject {
        put("3", buildJsonObject {
            put("class_type", "KSampler")
            put("inputs", buildJsonObject {
                put("seed", 0)
                put("steps", config.steps)
                put("cfg", config.cfgScale)
                put("sampler_name", config.sampler.ifBlank { "euler" })
                put("scheduler", "normal")
                put("denoise", 1.0)
                put("model", buildJsonArray { add("4"); add(0) })
                put("positive", buildJsonArray { add("6"); add(0) })
                put("negative", buildJsonArray { add("7"); add(0) })
                put("latent_image", buildJsonArray { add("5"); add(0) })
            })
        })
        put("4", buildJsonObject {
            put("class_type", "CheckpointLoaderSimple")
            put("inputs", buildJsonObject {
                put("ckpt_name", "v1-5-pruned-emaonly-fp16.safetensors")
            })
        })
        put("5", buildJsonObject {
            put("class_type", "EmptyLatentImage")
            put("inputs", buildJsonObject {
                put("width", config.width)
                put("height", config.height)
                put("batch_size", 1)
            })
        })
        put("6", buildJsonObject {
            put("class_type", "CLIPTextEncode")
            put("inputs", buildJsonObject {
                put("text", prompt)
                put("clip", buildJsonArray { add("4"); add(1) })
            })
        })
        put("7", buildJsonObject {
            put("class_type", "CLIPTextEncode")
            put("inputs", buildJsonObject {
                put("text", negative)
                put("clip", buildJsonArray { add("4"); add(1) })
            })
        })
        put("8", buildJsonObject {
            put("class_type", "VAEDecode")
            put("inputs", buildJsonObject {
                put("samples", buildJsonArray { add("3"); add(0) })
                put("vae", buildJsonArray { add("4"); add(2) })
            })
        })
        put("9", buildJsonObject {
            put("class_type", "SaveImage")
            put("inputs", buildJsonObject {
                put("filename_prefix", "tavern")
                put("images", buildJsonArray { add("8"); add(0) })
            })
        })
    }
}
