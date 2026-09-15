package com.tavern.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tavern.app.core.data.repository.ProviderRepository
import com.tavern.app.core.data.repository.toModel
import com.tavern.app.core.model.AsrConfig
import com.tavern.app.core.model.VoiceConfig
import com.tavern.app.core.util.AppSettings
import com.tavern.app.feature.voice.VoiceService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 语音设置 ViewModel
 *
 * 管理 TTS 配置、ASR 配置的增删改、默认标记、试听与自动朗读开关。
 */
@HiltViewModel
class VoiceSettingsViewModel @Inject constructor(
    private val providerRepository: ProviderRepository,
    private val voiceService: VoiceService,
    private val appSettings: AppSettings
) : ViewModel() {

    /** 所有 TTS 语音配置 */
    val configs: StateFlow<List<VoiceConfig>> = providerRepository.getAllVoiceConfigsFlow()
        .map { list -> list.map { it.toModel() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** ASR 配置列表 */
    val asrConfigs: StateFlow<List<AsrConfig>> = providerRepository.getAllAsrConfigsFlow()
        .map { list -> list.map { it.toModel() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 自动朗读开关 */
    val autoSpeak: StateFlow<Boolean> = appSettings.autoSpeak

    /** 一次性提示消息 */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    /**
     * 保存语音配置（id == 0 新增，否则更新）
     */
    fun save(config: VoiceConfig) {
        viewModelScope.launch {
            if (config.id > 0) {
                providerRepository.updateVoiceConfig(config)
            } else {
                // 第一个配置自动设为默认
                val isFirst = providerRepository.getAllVoiceConfigsFlow().first().isEmpty()
                providerRepository.addVoiceConfig(
                    config.copy(isDefault = config.isDefault || isFirst)
                )
            }
            _message.value = "已保存"
        }
    }

    /**
     * 删除语音配置
     */
    fun delete(id: Long) {
        viewModelScope.launch {
            providerRepository.deleteVoiceConfig(id)
            _message.value = "已删除"
        }
    }

    /**
     * 设为默认语音
     */
    fun setDefault(id: Long) {
        viewModelScope.launch {
            providerRepository.setDefaultVoice(id)
            _message.value = "已设为默认"
        }
    }

    /**
     * 试听指定配置的合成效果
     */
    fun test(config: VoiceConfig, text: String) {
        viewModelScope.launch {
            _message.value = "正在合成试听…"
            val result = voiceService.synthesizeToFile(text, config)
            result.onSuccess { path ->
                voiceService.play(path)
                _message.value = "试听已播放"
            }.onFailure { e ->
                _message.value = "试听失败：${e.message ?: "未知错误"}"
            }
        }
    }

    // ===== ASR =====

    fun saveAsr(config: AsrConfig) {
        viewModelScope.launch {
            if (config.id > 0) {
                providerRepository.updateAsrConfig(config)
            } else {
                val isFirst = providerRepository.getAllAsrConfigsFlow().first().isEmpty()
                providerRepository.addAsrConfig(
                    config.copy(isDefault = config.isDefault || isFirst)
                )
            }
            _message.value = "已保存语音识别配置"
        }
    }

    fun deleteAsr(id: Long) {
        viewModelScope.launch {
            providerRepository.deleteAsrConfig(id)
            _message.value = "已删除"
        }
    }

    fun setDefaultAsr(id: Long) {
        viewModelScope.launch {
            providerRepository.setDefaultAsr(id)
            _message.value = "已设为默认识别服务"
        }
    }

    fun setAutoSpeak(enabled: Boolean) {
        appSettings.setAutoSpeak(enabled)
    }

    fun clearMessage() {
        _message.value = null
    }
}
