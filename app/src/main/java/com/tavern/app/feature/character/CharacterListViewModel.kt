package com.tavern.app.feature.character

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tavern.app.core.data.local.dao.CharacterCardWithPreview
import com.tavern.app.core.data.local.dao.SessionWithPreview
import com.tavern.app.core.data.local.entity.CharacterCardEntity
import com.tavern.app.core.data.repository.CharacterRepository
import com.tavern.app.core.data.repository.ChatRepository
import com.tavern.app.core.data.repository.PluginRepository
import com.tavern.app.core.model.Plugin
import com.tavern.app.core.parser.PngCardReader
import com.tavern.app.core.parser.RegexScriptParser
import com.tavern.app.core.util.AppSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/**
 * 角色卡列表 ViewModel（角色卡 + 游玩记录）
 */
@HiltViewModel
class CharacterListViewModel @Inject constructor(
    private val repository: CharacterRepository,
    private val chatRepository: ChatRepository,
    private val pluginRepository: PluginRepository,
    private val appSettings: AppSettings,
    @ApplicationContext private val context: Context
) : ViewModel() {

    /** 角色卡列表（含最后互动预览，按最后活跃排序） */
    val cards: StateFlow<List<CharacterCardWithPreview>> =
        repository.getAllCardsWithPreviewFlow()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 游玩记录（已开始的会话，点击直接进入聊天） */
    val records: StateFlow<List<SessionWithPreview>> =
        chatRepository.getSessionsWithPreviewFlow()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 隐私模式（图片模糊） */
    val privacyMode: StateFlow<Boolean> = appSettings.privacyMode

    /** 导入/操作提示消息 */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    /**
     * 从 Uri 导入角色卡
     */
    fun importCard(uri: Uri) {
        viewModelScope.launch {
            val result = runCatching {
                val inputStream = context.contentResolver.openInputStream(uri)
                    ?: throw IllegalStateException("无法读取文件")
                inputStream.use { it.readBytes() }
            }

            result.onSuccess { bytes ->
                // 判断文件类型（扩展名或内容）
                val extension = uri.lastPathSegment?.substringAfterLast('.', "") ?: ""

                // PNG 卡：把原图落到应用私有目录，供视觉小说模式立绘使用
                val savedImagePath = persistPngIfNeeded(bytes)

                val imported = repository.importFromBytes(bytes, extension, savedImagePath)
                if (imported != null) {
                    val entity = repository.getCardEntity(imported)
                    val name = entity?.name?.ifBlank { null }

                    // 实验室开启时：自动导入角色卡内置 regex_scripts
                    if (appSettings.pluginLabEnabled.value) {
                        val card = repository.getCardModel(imported)
                        val rules = card?.let { RegexScriptParser.parseRules(it) }.orEmpty()
                        if (rules.isNotEmpty()) {
                            val pluginName = "「${name ?: "角色"}」角色脚本"
                            pluginRepository.save(
                                Plugin(
                                    name = pluginName,
                                    description = "从角色卡 regex_scripts 自动导入（${rules.size} 条，仅本角色生效）",
                                    enabled = true,
                                    characterCardId = imported,
                                    rules = rules
                                )
                            )
                            _message.value =
                                "已导入「${name ?: "角色"}」+ ${rules.size} 条角色脚本"
                        } else {
                            _message.value = "已导入「${name ?: "角色"}」，点击开始游玩"
                        }
                    } else {
                        _message.value = "已导入「${name ?: "角色"}」，点击开始游玩"
                    }
                } else {
                    _message.value = "导入失败：无法解析角色卡"
                }
            }.onFailure { e ->
                _message.value = "导入失败：${e.message}"
            }
        }
    }

    /**
     * 若是 PNG，持久化到 filesDir/cards/ 并返回绝对路径；否则返回 null
     */
    private suspend fun persistPngIfNeeded(bytes: ByteArray): String? =
        withContext(Dispatchers.IO) {
            if (!PngCardReader.isPng(bytes)) return@withContext null
            runCatching {
                val dir = File(context.filesDir, "cards").apply { mkdirs() }
                val file = File(dir, "card_${System.currentTimeMillis()}.png")
                file.writeBytes(bytes)
                file.absolutePath
            }.getOrNull()
        }

    /**
     * 删除角色卡
     */
    fun deleteCard(id: Long) {
        viewModelScope.launch {
            repository.deleteCard(id)
            _message.value = "已删除"
        }
    }

    /**
     * 删除游玩记录（会话及其消息）
     */
    fun deleteSession(id: Long) {
        viewModelScope.launch {
            chatRepository.deleteSession(id)
            _message.value = "记录已删除"
        }
    }

    /**
     * 切换收藏
     */
    fun toggleFavorite(id: Long, current: Boolean) {
        viewModelScope.launch {
            repository.updateFavorite(id, !current)
        }
    }

    fun clearMessage() {
        _message.value = null
    }
}
