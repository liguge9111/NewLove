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
import com.tavern.app.core.parser.CharacterCardLoader
import com.tavern.app.core.parser.PngCardReader
import com.tavern.app.core.parser.RegexScriptParser
import com.tavern.app.core.util.AppSettings
import com.tavern.app.core.util.CardFingerprint
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

    /** 待确认的重复导入（单张导入遇到重复时弹窗询问） */
    private val _pendingDuplicate = MutableStateFlow<Pair<ByteArray, String>?>(null)
    val pendingDuplicate: StateFlow<Pair<ByteArray, String>?> = _pendingDuplicate.asStateFlow()

    /** 多选模式选中的卡片 ID */
    private val _selectedIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedIds: StateFlow<Set<Long>> = _selectedIds.asStateFlow()

    /** 是否处于多选模式 */
    private val _selectionMode = MutableStateFlow(false)
    val selectionMode: StateFlow<Boolean> = _selectionMode.asStateFlow()

    /** 已有卡片的指纹集合（导入时用于去重） */
    private suspend fun existingFingerprints(): Set<String> =
        repository.getAllCards().mapNotNull { entity ->
            runCatching { repository.getCardModel(entity.id) }.getOrNull()
        }.mapNotNull { card ->
            runCatching { CardFingerprint.of(card.name, card.firstMessage) }.getOrNull()
        }.toSet()

    private fun fingerprintOf(bytes: ByteArray, extension: String): String? =
        runCatching {
            val card = CharacterCardLoader.loadFromBytes(bytes, extension) ?: return@runCatching null
            CardFingerprint.of(card.name, card.firstMessage)
        }.getOrNull()

    /**
     * 批量导入角色卡。
     * - 单张且重复 → 弹窗询问（pendingDuplicate）
     * - 批量且重复 → 自动跳过，不影响后续导入
     */
    fun importCards(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            var success = 0
            var failed = 0
            var skippedDup = 0
            var scriptCount = 0
            val existing = existingFingerprints()
            var dupFoundForSingle: Pair<ByteArray, String>? = null

            uris.forEachIndexed { index, uri ->
                val bytes = runCatching {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                }.getOrNull()
                if (bytes == null) {
                    failed++
                    return@forEachIndexed
                }
                val extension = uri.lastPathSegment?.substringAfterLast('.', "") ?: ""
                val fp = fingerprintOf(bytes, extension)

                // 单张导入且命中重复 → 暂存等确认
                if (uris.size == 1 && fp != null && fp in existing) {
                    dupFoundForSingle = bytes to extension
                    return@forEachIndexed
                }
                // 批量或已确认：重复直接跳过
                if (fp != null && fp in existing) {
                    skippedDup++
                    return@forEachIndexed
                }

                val ok = importBytes(bytes, extension)
                if (ok.first) {
                    success++
                    scriptCount += ok.second
                } else {
                    failed++
                }
            }

            // 单张重复：交给弹窗
            dupFoundForSingle?.let {
                _pendingDuplicate.value = it
                return@launch
            }

            _message.value = buildString {
                append("已导入 $success 张角色卡")
                if (scriptCount > 0) append("（含 $scriptCount 条角色脚本）")
                if (skippedDup > 0) append("，跳过重复 $skippedDup 张")
                if (failed > 0) append("，失败 $failed 张")
            }
        }
    }

    /** 用户确认后导入重复卡 */
    fun confirmDuplicateImport() {
        val pending = _pendingDuplicate.value ?: return
        _pendingDuplicate.value = null
        viewModelScope.launch {
            val ok = importBytes(pending.first, pending.second)
            _message.value = if (ok.first) {
                "已导入（重复卡已确认导入）" +
                    if (ok.second > 0) "，含 ${ok.second} 条角色脚本" else ""
            } else {
                "导入失败：无法解析角色卡"
            }
        }
    }

    fun cancelDuplicateImport() {
        _pendingDuplicate.value = null
        _message.value = "已取消导入"
    }

    // ===== 多选管理 =====

    fun toggleSelectionMode() {
        _selectionMode.value = !_selectionMode.value
        if (!_selectionMode.value) _selectedIds.value = emptySet()
    }

    fun toggleSelected(id: Long) {
        _selectedIds.value = _selectedIds.value.let {
            if (id in it) it - id else it + id
        }
    }

    fun selectAllCards() {
        viewModelScope.launch {
            _selectedIds.value = repository.getAllCards().map { it.id }.toSet()
        }
    }

    /** 批量删除选中的卡片 */
    fun deleteSelectedCards() {
        viewModelScope.launch {
            val ids = _selectedIds.value
            ids.forEach { repository.deleteCard(it) }
            _selectionMode.value = false
            _selectedIds.value = emptySet()
            _message.value = "已删除 ${ids.size} 张角色卡"
        }
    }

    /** 单张导入字节；返回 (是否成功, 附带脚本数) */
    private suspend fun importBytes(bytes: ByteArray, extension: String): Pair<Boolean, Int> {
        val savedImagePath = persistPngIfNeeded(bytes)
        val imported = repository.importFromBytes(bytes, extension, savedImagePath)
            ?: return false to 0

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
        return true to scripts
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
