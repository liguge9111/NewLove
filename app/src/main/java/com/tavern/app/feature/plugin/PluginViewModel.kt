package com.tavern.app.feature.plugin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tavern.app.core.data.repository.PluginRepository
import com.tavern.app.core.data.repository.ProviderRepository
import com.tavern.app.core.data.repository.toModel
import com.tavern.app.core.model.Plugin
import com.tavern.app.core.plugin.PluginImporter
import com.tavern.app.core.util.AppSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 插件管理 ViewModel（含实验室导入向导）
 */
@HiltViewModel
class PluginViewModel @Inject constructor(
    private val pluginRepository: PluginRepository,
    private val providerRepository: ProviderRepository,
    private val appSettings: AppSettings
) : ViewModel() {

    private val importer = PluginImporter()

    val plugins: StateFlow<List<Plugin>> = pluginRepository.getAllPluginsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 插件实验室总开关（默认关闭） */
    val labEnabled: StateFlow<Boolean> = appSettings.pluginLabEnabled

    /** 导入向导 UI 状态 */
    sealed interface ImportState {
        data object Idle : ImportState
        data class Loading(val step: String) : ImportState
        data class DraftReady(val draft: PluginImporter.Draft) : ImportState
        data class Error(val message: String) : ImportState
    }

    private val _importState = MutableStateFlow<ImportState>(ImportState.Idle)
    val importState: StateFlow<ImportState> = _importState.asStateFlow()

    fun setLabEnabled(enabled: Boolean) {
        appSettings.setPluginLabEnabled(enabled)
    }

    fun resetImport() {
        _importState.value = ImportState.Idle
    }

    /** 从 URL 导入并转换 */
    fun importFromUrl(url: String, name: String) {
        if (url.isBlank()) {
            _importState.value = ImportState.Error("请输入插件地址")
            return
        }
        viewModelScope.launch {
            _importState.value = ImportState.Loading("正在下载源码…")
            val source = importer.download(url).getOrElse { e ->
                _importState.value = ImportState.Error("下载失败：${e.message}")
                return@launch
            }
            convertSource(source, name.ifBlank { url.substringAfterLast('/') })
        }
    }

    /** 从粘贴源码导入并转换 */
    fun importFromSource(source: String, name: String) {
        if (source.isBlank()) {
            _importState.value = ImportState.Error("请粘贴插件源码")
            return
        }
        viewModelScope.launch {
            convertSource(source, name)
        }
    }

    private suspend fun convertSource(source: String, name: String) {
        // 快速分类：JS 扩展明显 → 直接拒绝
        val kind = importer.classify(source)
        if (kind == PluginImporter.SourceKind.JS_EXTENSION) {
            _importState.value = ImportState.Error(
                "该插件依赖酒馆网页运行时（DOM/事件/API），无法转换为本地规则。" +
                    "仅支持正则改写、文本注入类插件。"
            )
            return
        }

        val model = providerRepository.getDefaultModelConfig()?.toModel()
        if (model == null) {
            _importState.value = ImportState.Error("请先在「我 → 大模型」中配置并设默认模型")
            return
        }

        _importState.value = ImportState.Loading("正在调用模型转换规则…")
        val result = importer.convertViaLlm(model, source, name)
        result.onSuccess { draft ->
            if (draft.rules.isEmpty()) {
                _importState.value = ImportState.Error(
                    draft.warnings.firstOrNull() ?: "未提取到可用规则"
                )
            } else {
                _importState.value = ImportState.DraftReady(draft)
            }
        }.onFailure { e ->
            _importState.value = ImportState.Error("转换失败：${e.message}")
        }
    }

    /** 保存草稿为插件（全局生效） */
    fun saveDraft(draft: PluginImporter.Draft) {
        viewModelScope.launch {
            pluginRepository.save(
                Plugin(
                    name = draft.name,
                    description = draft.description,
                    enabled = true,
                    characterCardId = null,
                    rules = draft.rules
                )
            )
            _importState.value = ImportState.Idle
        }
    }

    fun save(plugin: Plugin) {
        viewModelScope.launch {
            if (plugin.id > 0) {
                pluginRepository.update(plugin)
            } else {
                pluginRepository.save(plugin)
            }
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch {
            pluginRepository.delete(id)
        }
    }

    fun setEnabled(id: Long, enabled: Boolean) {
        viewModelScope.launch {
            pluginRepository.setEnabled(id, enabled)
        }
    }
}
