package com.tavern.app.core.data.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 大模型 API 数据模型（OpenAI 兼容格式）
 */

@Serializable
data class ChatApiMessage(
    val role: String,
    val content: String
)

@Serializable
data class ChatRequest(
    val model: String,
    val messages: List<ChatApiMessage>,
    val temperature: Double = 0.7,
    @SerialName("top_p") val topP: Double = 1.0,
    @SerialName("max_tokens") val maxTokens: Int = 512,
    @SerialName("frequency_penalty") val frequencyPenalty: Double = 0.0,
    @SerialName("presence_penalty") val presencePenalty: Double = 0.0,
    val stream: Boolean = false
)

@Serializable
data class ChatResponse(
    val id: String = "",
    val choices: List<ChatChoice> = emptyList(),
    val usage: ChatUsage? = null
)

@Serializable
data class ChatChoice(
    val index: Int = 0,
    val message: ChatApiMessage? = null,
    val delta: ChatDelta? = null,
    @SerialName("finish_reason") val finishReason: String? = null
)

@Serializable
data class ChatDelta(
    val role: String? = null,
    val content: String? = null
)

@Serializable
data class ChatUsage(
    @SerialName("prompt_tokens") val promptTokens: Int = 0,
    @SerialName("completion_tokens") val completionTokens: Int = 0,
    @SerialName("total_tokens") val totalTokens: Int = 0
)
