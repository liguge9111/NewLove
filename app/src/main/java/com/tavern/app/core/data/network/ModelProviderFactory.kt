package com.tavern.app.core.data.network

import com.tavern.app.core.model.ModelConfig
import com.tavern.app.core.model.ModelProviderType

/**
 * 大模型 Provider 工厂
 *
 * 根据模型配置的类型返回对应的 Provider 实现。
 */
object ModelProviderFactory {

    /**
     * 获取指定配置对应的 Provider
     */
    fun getProvider(config: ModelConfig): ModelProvider = when (config.providerType) {
        ModelProviderType.OPENAI_COMPATIBLE -> openAiProvider
        ModelProviderType.CLAUDE -> claudeProvider
        ModelProviderType.OLLAMA -> ollamaProvider
    }

    /** 懒加载单例，避免重复创建 OkHttpClient */
    val openAiProvider: ModelProvider by lazy { OpenAiCompatibleProvider() }
    val claudeProvider: ModelProvider by lazy { ClaudeProvider() }
    val ollamaProvider: ModelProvider by lazy { OllamaProvider() }
}
