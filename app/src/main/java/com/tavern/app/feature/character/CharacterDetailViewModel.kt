package com.tavern.app.feature.character

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tavern.app.core.data.repository.CharacterRepository
import com.tavern.app.core.data.repository.CharacterStateRepository
import com.tavern.app.core.data.repository.ChatRepository
import com.tavern.app.core.data.repository.MemoryRepository
import com.tavern.app.core.model.CharacterCard
import com.tavern.app.core.model.CharacterMemory
import com.tavern.app.core.model.ChatSummary
import com.tavern.app.core.model.StatusItemDef
import com.tavern.app.core.parser.StatusSchemaParser
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/**
 * 角色卡详情 ViewModel
 *
 * 提供基础设定、世界书、多开局、状态栏模板的展示与编辑，支持改名与自定义头像。
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class CharacterDetailViewModel @Inject constructor(
    private val repository: CharacterRepository,
    private val stateRepository: CharacterStateRepository,
    private val memoryRepository: MemoryRepository,
    private val chatRepository: ChatRepository,
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val cardId: Long = savedStateHandle.get<Long>("cardId") ?: -1L

    private val _card = MutableStateFlow<CharacterCard?>(null)
    val card: StateFlow<CharacterCard?> = _card.asStateFlow()

    /** 当前头像路径（JSON 卡可为空） */
    private val _avatarPath = MutableStateFlow<String?>(null)
    val avatarPath: StateFlow<String?> = _avatarPath.asStateFlow()

    /** 状态栏模板 */
    private val _statusSchema = MutableStateFlow<List<StatusItemDef>>(emptyList())
    val statusSchema: StateFlow<List<StatusItemDef>> = _statusSchema.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    /** 长期记忆时间线（最近会话 + 旧版按角色记忆） */
    private val _memories = MutableStateFlow<List<CharacterMemory>>(emptyList())
    val memories: StateFlow<List<CharacterMemory>> = _memories.asStateFlow()

    /** 最近会话的前情提要 */
    private val _summaries = MutableStateFlow<List<ChatSummary>>(emptyList())
    val summaries: StateFlow<List<ChatSummary>> = _summaries.asStateFlow()

    private val _latestSessionId = MutableStateFlow(-1L)

    init {
        reload()
        viewModelScope.launch {
            chatRepository.getSessionsForCard(cardId).firstOrNull()?.let { session ->
                _latestSessionId.value = session.id
                memoryRepository.getSummariesFlow(session.id)
                    .collect { _summaries.value = it }
            }
        }
        viewModelScope.launch {
            // 跟踪最近会话的记忆（新会话创建后 latestSessionId 更新时自动切换）
            _latestSessionId.filter { it > 0 }.flatMapLatest { sid ->
                memoryRepository.getMemoriesForTimelineFlow(cardId, sid)
            }.collect { _memories.value = it }
        }
    }

    private fun reload() {
        viewModelScope.launch {
            _card.value = repository.getCardModel(cardId)
            _avatarPath.value = repository.getCardEntity(cardId)?.filePath
            stateRepository.getOrCreate(cardId)
            val saved = stateRepository.getSchema(cardId)
            if (saved.isNotEmpty()) {
                _statusSchema.value = saved
            } else {
                val parsed = _card.value?.let { StatusSchemaParser.parse(it) }
                if (parsed != null) {
                    _statusSchema.value = parsed
                    stateRepository.updateSchema(cardId, parsed)
                } else {
                    // 卡片不带状态栏：界面展示默认三项供编辑，但不落库（聊天页不显示状态栏）
                    _statusSchema.value = StatusSchemaParser.defaultSchema()
                }
            }
        }
    }

    // ===== 记忆管理 =====

    fun addManualMemory(content: String) {
        if (content.isBlank()) return
        viewModelScope.launch {
            memoryRepository.addMemory(
                cardId,
                _latestSessionId.value.takeIf { it > 0 } ?: 0L,
                content,
                "manual"
            )
            _message.value = "已添加记忆"
        }
    }

    fun updateMemory(memory: CharacterMemory) {
        viewModelScope.launch {
            memoryRepository.updateMemory(memory)
            _message.value = "已更新"
        }
    }

    fun deleteMemory(id: Long) {
        viewModelScope.launch {
            memoryRepository.deleteMemory(id)
        }
    }

    fun clearAllMemories() {
        viewModelScope.launch {
            memoryRepository.clearMemories(cardId)
            _message.value = "已清空记忆"
        }
    }

    /** 编辑某代前情提要 */
    fun updateSummary(id: Long, text: String) {
        viewModelScope.launch {
            memoryRepository.updateSummary(id, text)
            _message.value = "前情提要已更新"
        }
    }

    /** 删除某代前情提要 */
    fun deleteSummary(id: Long) {
        viewModelScope.launch {
            memoryRepository.deleteSummary(id)
            _message.value = "已删除该代摘要"
        }
    }

    /** 更新状态栏模板（持久化） */
    fun updateSchema(schema: List<StatusItemDef>) {
        _statusSchema.value = schema
        viewModelScope.launch {
            stateRepository.updateSchema(cardId, schema)
        }
    }

    /** 重命名角色卡 */
    fun rename(newName: String) {
        viewModelScope.launch {
            repository.renameCard(cardId, newName)
            reload()
            _message.value = "已改名"
        }
    }

    /** 从相册设置自定义头像（复制到应用私有目录） */
    fun setAvatar(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                val input = context.contentResolver.openInputStream(uri)
                    ?: throw IllegalStateException("无法读取图片")
                val bytes = input.use { it.readBytes() }
                val dir = File(context.filesDir, "cards").apply { mkdirs() }
                val file = File(dir, "avatar_${cardId}_${System.currentTimeMillis()}.png")
                file.writeBytes(bytes)
                repository.setAvatarPath(cardId, file.absolutePath)
            }.onSuccess {
                reload()
                _message.value = "头像已更新"
            }.onFailure { e ->
                _message.value = "头像设置失败：${e.message}"
            }
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    /** 全部开局消息（默认 + 备选） */
    fun allGreetings(card: CharacterCard): List<String> =
        listOf(card.firstMessage) + card.alternateGreetings
}
