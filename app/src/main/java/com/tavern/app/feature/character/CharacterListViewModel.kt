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
import com.tavern.app.core.data.repository.MemoryRepository
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
    private val memoryRepository: MemoryRepository,
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
     * 批量导入角色卡（多选）
     */
    fun importCards(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            var success = 0
            var failed = 0
            var scriptCount = 0

            uris.forEach { uri ->
                val ok = importOne(uri)
                if (ok.first) {
                    success++
                    scriptCount += ok.second
                } else {
                    failed++
                }
            }

            _message.value = buildString {
                append("已导入 $success 张角色卡")
                if (scriptCount > 0) append("（含 $scriptCount 条角色脚本）")
                if (failed > 0) append("，失败 $failed 张")
            }
        }
    }

    /** 单张导入；返回 (是否成功, 附带脚本数) */
    private suspend fun importOne(uri: Uri): Pair<Boolean, Int> {
        val result = runCatching {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: throw IllegalStateException("无法读取文件")
            inputStream.use { it.readBytes() }
        }

        return result.fold(onSuccess = { bytes ->
            val extension = uri.lastPathSegment?.substringAfterLast('.', "") ?: ""
            val savedImagePath = persistPngIfNeeded(bytes)
            val imported = repository.importFromBytes(bytes, extension, savedImagePath)
                ?: return@fold false to 0

            // 实验室开启时：自动导入角色卡内置 regex_scripts
            var scripts = 0
            if (appSettings.pluginLabEnabled.value) {
                val card = repository.getCardModel(imported)
                val rules = card?.let { RegexScriptParser.parseRules(it) }.orEmpty()
                if (rules.isNotEmpty()) {
                    val name = repository.getCardEntity(imported)?.name?.ifBlank { null }
                    pluginRepository.save(
                        Plugin(
                            name = "「${name ?: "角色"}」角色脚本",
                            description = "从角色卡 regex_scripts 自动导入（${rules.size} 条，仅本角色生效）",
                            enabled = true,
                            characterCardId = imported,
                            rules = rules
                        )
                    )
                    scripts = rules.size
                }
            }
            true to scripts
        }, onFailure = {
            false to 0
        })
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
     * 删除游玩记录（会话及其消息、该会话的记忆）
     */
    fun deleteSession(id: Long) {
        viewModelScope.launch {
            memoryRepository.clearMemoriesBySession(id)
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
