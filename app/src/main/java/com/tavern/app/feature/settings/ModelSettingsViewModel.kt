package com.tavern.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tavern.app.core.data.network.ModelProviderFactory
import com.tavern.app.core.data.repository.ProviderRepository
import com.tavern.app.core.data.repository.toModel
import com.tavern.app.core.model.MessageRole
import com.tavern.app.core.model.ModelConfig
import com.tavern.app.core.prompt.PromptMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 模型设置 ViewModel
 *
 * 管理大模型配置的增删改、默认标记与连接测试。
 */
@HiltViewModel
class ModelSettingsViewModel @Inject constructor(
    private val providerRepository: ProviderRepository
) : ViewModel() {

    /** 所有模型配置 */
    val models: StateFlow<List<ModelConfig>> = providerRepository.getAllModelConfigsFlow()
        .map { list -> list.map { it.toModel() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 一次性提示消息 */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    /** 正在测试的配置 ID（null 表示无） */
    private val _testingId = MutableStateFlow<Long?>(null)
    val testingId: StateFlow<Long?> = _testingId.asStateFlow()

    /**
     * 保存模型配置（id == 0 新增，否则更新）
     */
    fun save(config: ModelConfig) {
        viewModelScope.launch {
            val id = if (config.id == 0L) {
                providerRepository.addModelConfig(config)
            } else {
                providerRepository.updateModelConfig(config)
                config.id
            }
            // 若尚不存在默认模型，则把刚保存的设为默认
            if (providerRepository.getDefaultModelConfig() == null) {
                providerRepository.setDefaultModel(id)
            }
            _message.value = "已保存"
        }
    }

    /**
     * 删除模型配置
     */
    fun delete(id: Long) {
        viewModelScope.launch {
            providerRepository.deleteModelConfig(id)
            _message.value = "已删除"
        }
    }

    /**
     * 设为默认模型
     */
    fun setDefault(id: Long) {
        viewModelScope.launch {
            providerRepository.setDefaultModel(id)
            _message.value = "已设为默认"
        }
    }

    /**
     * 测试模型连通性（非流式短回复）
     */
    fun test(config: ModelConfig) {
        viewModelScope.launch {
            _testingId.value = config.id
            _message.value = "正在测试连接…"
            val testConfig = config.copy(maxTokens = 16, streamEnabled = false)
            runCatching {
                val provider = ModelProviderFactory.getProvider(testConfig)
                provider.chat(
                    testConfig,
                    listOf(PromptMessage(MessageRole.USER, "你好，请只回复四个字：连接成功"))
                )
            }.onSuccess { result ->
                result.onSuccess { text ->
                    _message.value = "连接成功 ✓ ${text.take(20)}"
                }.onFailure { e ->
                    _message.value = "测试失败：${e.message?.take(150) ?: "未知错误"}"
                }
            }.onFailure { e ->
                _message.value = "测试失败：${e.message?.take(150) ?: "未知错误"}"
            }
            _testingId.value = null
        }
    }

    fun clearMessage() {
        _message.value = null
    }
}
