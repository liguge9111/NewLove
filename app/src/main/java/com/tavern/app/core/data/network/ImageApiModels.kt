package com.tavern.app.core.data.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 生图 API 数据模型（各 Provider 请求/响应）
 */

// ===== Stable Diffusion WebUI =====

@Serializable
data class SdWebuiRequest(
    val prompt: String,
    @SerialName("negative_prompt") val negativePrompt: String = "",
    val steps: Int = 20,
    @SerialName("cfg_scale") val cfgScale: Double = 7.0,
    val width: Int = 512,
    val height: Int = 512,
    @SerialName("sampler_name") val samplerName: String = "Euler a"
)

@Serializable
data class SdWebuiResponse(
    val images: List<String> = emptyList()
)

// ===== DALL-E / OpenAI 兼容 =====

@Serializable
data class DallERequest(
    val prompt: String,
    val size: String = "1024x1024",
    val n: Int = 1
)

@Serializable
data class DallEResponse(
    val data: List<DallEImage> = emptyList()
)

@Serializable
data class DallEImage(
    @SerialName("b64_json") val b64Json: String? = null,
    val url: String? = null
)

// ===== NovelAI =====

@Serializable
data class NovelAiRequest(
    val input: String,
    val model: String = "nai-diffusion-3",
    val parameters: NovelAiParameters = NovelAiParameters()
)

@Serializable
data class NovelAiParameters(
    val width: Int = 832,
    val height: Int = 1216,
    @SerialName("negative_prompt") val negativePrompt: String = "",
    @SerialName("n_samples") val nSamples: Int = 1,
    val steps: Int = 28,
    val scale: Double = 5.0,
    val sampler: String = "k_euler_ancestral"
)
