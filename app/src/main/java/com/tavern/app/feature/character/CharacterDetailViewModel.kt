package com.tavern.app.feature.character

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tavern.app.core.data.repository.CharacterRepository
import com.tavern.app.core.data.repository.CharacterStateRepository
import com.tavern.app.core.model.CharacterCard
import com.tavern.app.core.model.StatusItemDef
import com.tavern.app.core.parser.StatusSchemaParser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 角色卡详情 ViewModel
 *
 * 提供基础设定、世界书、多开局、状态栏模板的展示与编辑。
 */
@HiltViewModel
class CharacterDetailViewModel @Inject constructor(
    private val repository: CharacterRepository,
    private val stateRepository: CharacterStateRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val cardId: Long = savedStateHandle.get<Long>("cardId") ?: -1L

    private val _card = MutableStateFlow<CharacterCard?>(null)
    val card: StateFlow<CharacterCard?> = _card.asStateFlow()

    /** 状态栏模板 */
    private val _statusSchema = MutableStateFlow<List<StatusItemDef>>(emptyList())
    val statusSchema: StateFlow<List<StatusItemDef>> = _statusSchema.asStateFlow()

    init {
        viewModelScope.launch {
            _card.value = repository.getCardModel(cardId)
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

    /** 更新状态栏模板（持久化） */
    fun updateSchema(schema: List<StatusItemDef>) {
        _statusSchema.value = schema
        viewModelScope.launch {
            stateRepository.updateSchema(cardId, schema)
        }
    }

    /** 全部开局消息（默认 + 备选） */
    fun allGreetings(card: CharacterCard): List<String> =
        listOf(card.firstMessage) + card.alternateGreetings
}
