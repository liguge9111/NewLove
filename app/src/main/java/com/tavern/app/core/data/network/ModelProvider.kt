package com.tavern.app.core.data.network

import com.tavern.app.core.model.ModelConfig
import com.tavern.app.core.prompt.PromptMessage

/**
 * 大模型 Provider 统一接口
 *
 * 各服务（OpenAI 兼容 / Claude / Ollama）实现此接口，
 * 向上层提供统一的聊天能力。
 */
interface ModelProvider {

    /**
     * 流式聊天
     *
     * @param config 模型配置
     * @param messages 提示词消息（统一中间格式）
     * @param onDelta 每个文本增量的回调
     * @return 完整回复（失败返回 Result.failure）
     */
    suspend fun chatStream(
        config: ModelConfig,
        messages: List<PromptMessage>,
        onDelta: (String) -> Unit
    ): Result<String>

    /**
     * 非流式聊天
     *
     * @return 完整回复
     */
    suspend fun chat(
        config: ModelConfig,
        messages: List<PromptMessage>
    ): Result<String>

    /**
     * 将统一中间格式消息转换为该 Provider 的 API 消息格式
     */
    fun convertMessages(messages: List<PromptMessage>): List<ChatApiMessage>
}
