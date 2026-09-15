package com.tavern.app.feature.chat

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tavern.app.core.data.local.entity.ChatMessageEntity
import com.tavern.app.core.data.network.EmbeddingService
import com.tavern.app.core.data.network.ModelProviderFactory
import com.tavern.app.core.data.repository.CharacterRepository
import com.tavern.app.core.data.repository.CharacterStateRepository
import com.tavern.app.core.data.repository.ChatRepository
import com.tavern.app.core.data.repository.MemoryRepository
import com.tavern.app.core.data.repository.PluginRepository
import com.tavern.app.core.data.repository.ProviderRepository
import com.tavern.app.core.data.repository.toModel
import com.tavern.app.core.model.CharacterCard
import com.tavern.app.core.model.CharacterState
import com.tavern.app.core.model.ChatMessage
import com.tavern.app.core.model.InteractiveMode
import com.tavern.app.core.model.MessageRole
import com.tavern.app.core.model.ModelConfig
import com.tavern.app.core.model.StatusItemDef
import com.tavern.app.core.model.VNChoice
import com.tavern.app.core.model.WorldBookActivation
import com.tavern.app.core.memory.MemoryEngine
import com.tavern.app.core.parser.ImProtocolParser
import com.tavern.app.core.parser.NarrationParser
import com.tavern.app.core.parser.StatusBlockParser
import com.tavern.app.core.parser.StatusChangeParser
import com.tavern.app.core.parser.StatusSchemaParser
import com.tavern.app.core.parser.WorldBookScanner
import com.tavern.app.core.plugin.JsSandbox
import com.tavern.app.core.plugin.PluginEngine
import com.tavern.app.core.prompt.ImProtocol
import com.tavern.app.core.prompt.OptionsParser
import com.tavern.app.core.prompt.PromptBuilder
import com.tavern.app.core.prompt.PromptConfig
import com.tavern.app.core.prompt.PromptMessage
import com.tavern.app.core.prompt.StatusProtocol
import com.tavern.app.core.util.AppSettings
import com.tavern.app.core.util.SafetyFilter
import com.tavern.app.core.util.VariableResolver
import com.tavern.app.feature.image.ImageGenerationService
import com.tavern.app.feature.status.StatusNotificationService
import com.tavern.app.feature.voice.SpeechToTextService
import com.tavern.app.feature.voice.VoiceService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 聊天 ViewModel
 *
 * 编排：消息持久化、世界书扫描、提示词构建、模型流式调用、
 * 语音输入输出、互动模式、角色状态与生图。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val characterRepository: CharacterRepository,
    private val providerRepository: ProviderRepository,
    private val voiceService: VoiceService,
    private val speechToText: SpeechToTextService,
    private val stateRepository: CharacterStateRepository,
    private val statusNotification: StatusNotificationService,
    private val pluginRepository: PluginRepository,
    private val imageService: ImageGenerationService,
    private val appSettings: AppSettings,
    private val memoryRepository: MemoryRepository,
    private val embeddingService: EmbeddingService,
    private val appScope: CoroutineScope,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val cardId: Long = savedStateHandle.get<Long>("cardId") ?: -1L

    private companion object {
        const val TAG = "NewLove"
    }

    /** 详情页传入的开局索引（-1 = 默认开局 first_mes；>=0 = alternateGreetings 下标） */
    private val greetingIndex: Int = savedStateHandle.get<Int>("greeting") ?: -1

    /** 指定会话 ID（>0 时直接进入该会话；-1 = 继续/创建首个会话） */
    private val navSessionId: Long = savedStateHandle.get<Long>("sessionId") ?: -1L

    /** 是否强制新开一局（详情页「新开一局」） */
    private val forceNewSession: Boolean = (savedStateHandle.get<Int>("newSession") ?: 0) == 1

    /** 当前会话 ID */
    private val _sessionId = MutableStateFlow<Long?>(null)

    /** 当前会话累计 token 用量（估算） */
    private val _sessionTokenUsage = MutableStateFlow(0L)
    val sessionTokenUsage: StateFlow<Long> = _sessionTokenUsage.asStateFlow()

    /** 会话使用的模型配置 ID（-1 表示跟随全局默认） */
    private val _currentModelConfigId = MutableStateFlow(-1L)

    /** 所有可用模型（供切换器使用） */
    val models: StateFlow<List<ModelConfig>> = providerRepository.getAllModelConfigsFlow()
        .map { list -> list.map { it.toModel() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 当前模型显示名 */
    private val _currentModelName = MutableStateFlow("未配置模型")
    val currentModelName: StateFlow<String> = _currentModelName.asStateFlow()

    /** 当前会话的模型配置 ID（-1 表示跟随默认，供选择器高亮） */
    val currentModelConfigId: StateFlow<Long> = _currentModelConfigId.asStateFlow()

    /** 分支选择：parentMessageId → 当前选中的 branchIndex（默认取最大） */
    private val _branchSelections = MutableStateFlow<Map<Long, Int>>(emptyMap())

    /** 原始消息（未过滤分支，用于计算分支总数） */
    val allMessages: StateFlow<List<ChatMessageEntity>> = _sessionId
        .filterNotNull()
        .flatMapLatest { chatRepository.getMessagesFlow(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 会话消息列表（按当前分支过滤） */
    val messages: StateFlow<List<ChatMessageEntity>> = combine(
        allMessages, _branchSelections
    ) { list, selections ->
        filterBranches(list, selections)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 是否正在生成回复 */
    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    /** 流式输出中的文本（已剥离协议块，供展示） */
    private val _streamingText = MutableStateFlow("")
    val streamingText: StateFlow<String> = _streamingText.asStateFlow()

    /** 角色名 */
    private val _characterName = MutableStateFlow("")
    val characterName: StateFlow<String> = _characterName.asStateFlow()

    /** 角色状态（数值，按卡持久化） */
    val characterState: StateFlow<CharacterState?> = stateRepository.getStateFlow(cardId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** 该卡的状态栏模板 */
    private val _statusSchema = MutableStateFlow<List<StatusItemDef>>(emptyList())
    val statusSchema: StateFlow<List<StatusItemDef>> = _statusSchema.asStateFlow()

    /** 角色头像/立绘路径（PNG 角色卡的文件路径） */
    private val _characterAvatarPath = MutableStateFlow<String?>(null)
    val characterAvatarPath: StateFlow<String?> = _characterAvatarPath.asStateFlow()

    /** 当前互动模式 */
    private val _interactiveMode = MutableStateFlow(InteractiveMode.TEXT_ADVENTURE)
    val interactiveMode: StateFlow<InteractiveMode> = _interactiveMode.asStateFlow()

    /** 当前可选择的选项（AI 回复中解析出的分支，含状态增量） */
    private val _currentChoices = MutableStateFlow<List<VNChoice>>(emptyList())
    val currentChoices: StateFlow<List<VNChoice>> = _currentChoices.asStateFlow()

    /** 视觉小说：选项点击直接发送（否则先预览） */
    val vnDirectSend: StateFlow<Boolean> = appSettings.vnDirectSend

    /** 视觉小说：漏选项自动重试 */
    val vnRetryMissing: StateFlow<Boolean> = appSettings.vnRetryMissing

    fun setVnDirectSend(enabled: Boolean) = appSettings.setVnDirectSend(enabled)
    fun setVnRetryMissing(enabled: Boolean) = appSettings.setVnRetryMissing(enabled)

    /** 是否正在录音（网络 ASR） */
    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    /** 自动朗读开关 */
    val autoSpeak: StateFlow<Boolean> = appSettings.autoSpeak

    /** 文字状态栏格式校验开关（开=未按格式输出时自动重试） */
    val strictStatusFormat: StateFlow<Boolean> = appSettings.strictStatusFormat

    /** 隐私模式（图片模糊） */
    val privacyMode: StateFlow<Boolean> = appSettings.privacyMode

    /** 聊天增强开关 */
    val chatEnhance: StateFlow<Boolean> = appSettings.chatEnhance

    /** 玩家昵称 */
    val playerName: StateFlow<String> = appSettings.playerName

    /** 玩家头像路径 */
    val playerAvatarPath: StateFlow<String> = appSettings.playerAvatarPath

    /** 聊天字体大小（sp） */
    val chatFontSize: StateFlow<Int> = appSettings.chatFontSize

    fun setChatFontSize(sp: Int) {
        appSettings.setChatFontSize(sp)
    }

    /** 该卡是否使用文字状态栏协议（<Status_block>） */
    private val _cardUsesTextStatus = MutableStateFlow(false)

    /** 最新一条文字状态栏内容（固定面板展示用） */
    val latestStatusPanel: StateFlow<String?> = messages
        .map { list -> list.lastOrNull { !it.statusPanel.isNullOrBlank() }?.statusPanel }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** 错误提示 */
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        viewModelScope.launch {
            // 加载角色信息
            val card = characterRepository.getCardModel(cardId)
            _characterName.value = card?.name ?: ""
            _characterAvatarPath.value = characterRepository.getCardEntity(cardId)?.filePath
            _cardUsesTextStatus.value = card?.let { StatusBlockParser.cardUsesProtocol(it) } == true

            // 加载或创建角色状态 + 状态栏模板
            // 仅当卡片自带或用户手动配置过时才展示状态栏（空 = 不显示）
            stateRepository.getOrCreate(cardId)
            val savedSchema = stateRepository.getSchema(cardId)
            if (savedSchema.isNotEmpty()) {
                _statusSchema.value = savedSchema
            } else {
                val parsed = card?.let { StatusSchemaParser.parse(it) }
                if (parsed != null) {
                    _statusSchema.value = parsed
                    stateRepository.updateSchema(cardId, parsed)
                } else {
                    _statusSchema.value = emptyList()
                }
            }

            // 加载或创建会话（默认文字冒险模式）
            // 优先级：指定会话ID > 强制新开 > 继续首个会话 > 创建新会话
            val sessions = chatRepository.getSessionsForCard(cardId)
            val target = when {
                navSessionId > 0 -> sessions.firstOrNull { it.id == navSessionId }
                    ?: chatRepository.getSession(navSessionId)
                forceNewSession -> null
                else -> sessions.firstOrNull()
            }
            if (target != null) {
                _currentModelConfigId.value = target.modelConfigId
                val mode = runCatching {
                    InteractiveMode.valueOf(target.interactiveMode)
                }.getOrDefault(InteractiveMode.TEXT_ADVENTURE)
                // 旧聊天模式会话迁移为文字冒险（聊天已改为 IM 浮窗）
                _interactiveMode.value =
                    if (mode == InteractiveMode.CHAT) InteractiveMode.TEXT_ADVENTURE else mode
                if (mode == InteractiveMode.CHAT) {
                    chatRepository.updateSessionMode(target.id, InteractiveMode.TEXT_ADVENTURE)
                }
                _sessionId.value = target.id

                // 会话从未有过角色消息（含功能上线前的旧会话）：补写开局到最前面
                val existingMessages = chatRepository.getMessages(target.id)
                val hasAssistantMessage = existingMessages.any { it.role == "ASSISTANT" }
                if (!hasAssistantMessage) {
                    val greeting = resolveGreeting(card, greetingIndex)
                    if (greeting.isNotBlank()) {
                        val firstTs = existingMessages.minOfOrNull { it.timestamp }
                            ?: target.createdAt
                        val parsed = StatusBlockParser.parse(greeting)
                        chatRepository.addMessage(
                            sessionId = target.id,
                            role = MessageRole.ASSISTANT,
                            content = parsed.content,
                            statusPanel = parsed.statusPanel,
                            timestamp = firstTs - 1
                        )
                    }
                }
            } else {
                val sessionId = chatRepository.createSession(
                    cardId,
                    mode = InteractiveMode.TEXT_ADVENTURE
                )
                _sessionId.value = sessionId
                // 新会话写入开局消息（作为首条 AI 气泡）
                val greeting = resolveGreeting(card, greetingIndex)
                if (greeting.isNotBlank()) {
                    val parsed = StatusBlockParser.parse(greeting)
                    chatRepository.addMessage(
                        sessionId,
                        MessageRole.ASSISTANT,
                        parsed.content,
                        statusPanel = parsed.statusPanel
                    )
                }
            }
            _sessionId.value?.let { sid ->
                _sessionTokenUsage.value = chatRepository.getSession(sid)?.tokenUsage ?: 0L
            }
            updateCurrentModelName()
        }

        // 监听状态变化，同步到通知栏
        viewModelScope.launch {
            combine(_characterName, characterState) { name, state -> name to state }
                .collect { (name, state) -> statusNotification.updateStatus(name, state) }
        }
    }

    /**
     * 解析开局消息
     * 详情页开局列表为 [first_mes, alt0, alt1...]：
     * index <= 0 取 first_mes，index >= 1 取 alternateGreetings[index - 1]
     */
    private fun resolveGreeting(card: CharacterCard?, index: Int): String {
        if (card == null) return ""
        val variables = VariableResolver.defaultVariables(appSettings.playerName.value, card.name)
        val raw = when {
            index <= 0 -> card.firstMessage
            index - 1 < card.alternateGreetings.size -> card.alternateGreetings[index - 1]
            else -> card.firstMessage
        }
        return VariableResolver.resolve(raw, variables)
    }

    /**
     * 发送消息并流式生成回复
     */
    fun sendMessage(text: String) = sendInternal(text)

    /**
     * 发送手机聊天（IM）消息
     *
     * 聊天增强开启：先纯 IM 生成回复，再自动同步叙事；
     * 否则：实时模式（混入叙事 + [IM] 块回泡）。
     */
    fun sendImMessage(text: String) {
        val sessionId = _sessionId.value ?: return
        if (text.isBlank() || _isGenerating.value) return

        if (appSettings.chatEnhance.value) {
            sendImEnhanced(sessionId, text)
        } else {
            appScope.launch {
                chatRepository.addMessage(
                    sessionId = sessionId,
                    role = MessageRole.USER,
                    content = text,
                    isIm = true
                )
                _currentChoices.value = emptyList()
                generateReply(sessionId)
            }
        }
    }

    /**
     * 聊天增强：IM 纯聊天回复 → 自动同步本轮对话到互动叙事
     */
    private fun sendImEnhanced(sessionId: Long, text: String) {
        appScope.launch {
            // 1. 保存 IM 用户消息
            chatRepository.addMessage(
                sessionId = sessionId,
                role = MessageRole.USER,
                content = text,
                isIm = true
            )

            // 2. IM 纯聊天生成
            _isGenerating.value = true
            _streamingText.value = ""
            val imReply = generateImReply(sessionId, text)
            _isGenerating.value = false

            if (imReply != null) {
                chatRepository.addMessage(
                    sessionId = sessionId,
                    role = MessageRole.ASSISTANT,
                    content = imReply,
                    isIm = true,
                    imContent = imReply
                )
            } else {
                _error.value = "聊天回复生成失败"
                return@launch
            }

            // 3. 自动同步叙事：动作摘要作为用户消息，再生成剧情
            val summary = "你给她发消息：「${text}」，她回复：「${imReply}」。"
            chatRepository.addMessage(
                sessionId = sessionId,
                role = MessageRole.USER,
                content = summary
            )
            _currentChoices.value = emptyList()
            generateReply(sessionId)
        }
    }

    /**
     * IM 纯聊天生成：角色卡全上下文 + 微信语气，无叙事/状态栏协议
     */
    private suspend fun generateImReply(sessionId: Long, userText: String): String? {
        val modelConfig = resolveCurrentModel()
        if (modelConfig == null) {
            _error.value = "请先在设置中配置大模型"
            return null
        }
        val card = characterRepository.getCardModel(cardId)
        if (card == null) {
            _error.value = "角色卡加载失败"
            return null
        }

        val variables = VariableResolver.defaultVariables(appSettings.playerName.value, card.name)
        val activation = scanWorldBook(card, sessionId, userText)

        val systemPrompt = buildString {
            card.buildDefinition().let { if (it.isNotBlank()) append(it) }
            activation?.entries?.forEach { entry ->
                append("\n")
                append(VariableResolver.resolve(entry.content, variables))
            }
            append("\n\n【手机聊天模式】")
            append("你此刻只在聊天软件中与玩家对话。请使用简短、口语化的聊天语气回复，就像微信聊天一样。")
            append("禁止叙事描写、动作描写、场景描写、状态栏、选项或其他任何非聊天内容，只输出你要发出去的聊天文字。")
        }

        // IM 历史（本会话内的手机消息往返）
        val history = chatRepository.getMessages(sessionId)
            .filter { it.isIm }
            .map {
                PromptMessage(
                    role = if (it.role == "USER") MessageRole.USER else MessageRole.ASSISTANT,
                    content = VariableResolver.resolve(it.content, variables)
                )
            }

        val promptMessages = buildList {
            add(PromptMessage(MessageRole.SYSTEM, systemPrompt))
            addAll(history)
            add(PromptMessage(MessageRole.USER, VariableResolver.resolve(userText, variables)))
        }

        return runCatching {
            val provider = ModelProviderFactory.getProvider(modelConfig)
            provider.chatStream(modelConfig, promptMessages) { }.getOrNull()
        }.getOrNull()?.trim()?.takeIf { it.isNotBlank() }
    }

    private fun sendInternal(
        text: String,
        isVoice: Boolean = false,
        voicePath: String? = null,
        voiceDuration: Float? = null
    ) {
        val sessionId = _sessionId.value ?: return
        if (text.isBlank() || _isGenerating.value) return

        // 应用级作用域：退出页面后生成仍会完成并入库
        appScope.launch {
            // 保存用户消息，清空旧选项
            chatRepository.addMessage(
                sessionId = sessionId,
                role = MessageRole.USER,
                content = text,
                isVoice = isVoice,
                voicePath = voicePath,
                voiceDuration = voiceDuration
            )
            _currentChoices.value = emptyList()

            // 插件：关键词状态变化（仅全局 + 当前角色绑定的插件）
            val plugins = pluginRepository.getEnabledPlugins(cardId)
            val keywordDelta = PluginEngine.collectStatusDelta(plugins, text)
            if (!keywordDelta.isEmpty) {
                stateRepository.applyDelta(cardId, keywordDelta)
            }

            generateReply(sessionId)
        }
    }

    /**
     * 编辑用户消息内容
     */
    fun editMessage(messageId: Long, newContent: String) {
        if (newContent.isBlank()) return
        viewModelScope.launch {
            chatRepository.updateMessageContent(messageId, newContent)
        }
    }

    /**
     * 从指定消息重新生成：
     * - 角色消息：删除该条及之后所有消息，基于此前上下文重新生成
     * - 用户消息：删除其后的角色回复，基于（含该条的）历史重新生成
     */
    fun regenerateFrom(messageId: Long) {
        val sessionId = _sessionId.value ?: return
        if (_isGenerating.value) return

        appScope.launch {
            val all = chatRepository.getMessages(sessionId)
            val target = all.firstOrNull { it.id == messageId } ?: return@launch

            if (target.role == "ASSISTANT") {
                // 分支模式：保留本条作为分支0，删除其后的消息，新回复作为新分支
                all.filter { it.id > messageId }
                    .forEach { chatRepository.deleteMessage(it.id) }
                val siblings = all.filter {
                    it.role == "ASSISTANT" && it.parentMessageId == target.parentMessageId
                }
                val nextBranch = (siblings.maxOfOrNull { it.branchIndex } ?: 0) + 1
                _currentChoices.value = emptyList()
                generateReply(
                    sessionId,
                    branchOverride = target.parentMessageId to nextBranch
                )
            } else {
                // 用户消息：保留其后第一条角色回复作为分支0，删除更晚的消息
                val firstReply = all
                    .filter { it.role == "ASSISTANT" && it.id > messageId }
                    .minByOrNull { it.timestamp }
                if (firstReply != null) {
                    all.filter { it.id > firstReply.id }
                        .forEach { chatRepository.deleteMessage(it.id) }
                    val siblings = all.filter {
                        it.role == "ASSISTANT" && it.parentMessageId == messageId
                    }
                    val nextBranch = (siblings.maxOfOrNull { it.branchIndex } ?: 0) + 1
                    _currentChoices.value = emptyList()
                    generateReply(
                        sessionId,
                        branchOverride = messageId to nextBranch
                    )
                } else {
                    all.filter { it.id > messageId }
                        .forEach { chatRepository.deleteMessage(it.id) }
                    _currentChoices.value = emptyList()
                    generateReply(sessionId)
                }
            }
        }
    }

    /** 某个父消息下的角色回复分支总数 */
    fun branchCount(parentMessageId: Long): Int {
        if (parentMessageId <= 0) return 1
        return allMessages.value.count {
            it.role == "ASSISTANT" && it.parentMessageId == parentMessageId
        }
    }

    /** 当前选中的分支序号 */
    fun selectedBranch(parentMessageId: Long): Int {
        if (parentMessageId <= 0) return 0
        val siblings = allMessages.value.filter {
            it.role == "ASSISTANT" && it.parentMessageId == parentMessageId
        }
        return _branchSelections.value[parentMessageId]
            ?: siblings.maxOfOrNull { it.branchIndex } ?: 0
    }

    /** 切换某槽位的分支 */
    fun selectBranch(parentMessageId: Long, branchIndex: Int) {
        _branchSelections.value = _branchSelections.value + (parentMessageId to branchIndex)
    }

    /** 按分支过滤：同父多版本只保留选中；其余消息原样保留 */
    private fun filterBranches(
        list: List<ChatMessageEntity>,
        selections: Map<Long, Int>
    ): List<ChatMessageEntity> {
        val assistantByParent = list
            .filter { it.role == "ASSISTANT" && it.parentMessageId > 0 }
            .groupBy { it.parentMessageId }
        return list.filter { msg ->
            if (msg.role != "ASSISTANT" || msg.parentMessageId <= 0) return@filter true
            val siblings = assistantByParent[msg.parentMessageId] ?: return@filter true
            if (siblings.size <= 1) return@filter true
            val selected = selections[msg.parentMessageId]
                ?: siblings.maxOf { it.branchIndex }
            msg.branchIndex == selected
        }
    }

    /**
     * 基于当前会话历史生成一条角色回复（发送 / 重新生成共用）
     *
     * @param attempt 重试次数（文字状态栏格式校验）
     */
    private suspend fun generateReply(
        sessionId: Long,
        attempt: Int = 0,
        vnRetry: Boolean = false,
        branchOverride: Pair<Long, Int>? = null
    ) {
        val plugins = pluginRepository.getEnabledPlugins(cardId)

        val modelConfig = resolveCurrentModel()
        if (modelConfig == null) {
            _error.value = "请先在设置中配置大模型"
            return
        }

        val card = characterRepository.getCardModel(cardId)
        if (card == null) {
            _error.value = "角色卡加载失败"
            return
        }

        // 世界书扫描以最后一条用户消息为触发文本
        val lastUser = chatRepository.getLastMessages(sessionId, 10)
            .lastOrNull { it.role == "USER" }
        val lastUserText = lastUser?.content ?: ""
        val lastUserIsIm = lastUser?.isIm == true

        val mode = _interactiveMode.value
        val activation = scanWorldBook(card, sessionId, lastUserText)
        val history = chatRepository.getMessages(sessionId).map { it.toChatMessage() }
        val statusProtocol = if (_statusSchema.value.isNotEmpty()) {
            StatusProtocol.build(stateRepository.getState(cardId), _statusSchema.value)
        } else {
            ""
        }
        val enhance = appSettings.chatEnhance.value

        // ===== 记忆系统：摘要 / 事实检索 / 预算（实验室开关控制） =====
        val memOn = appSettings.memoryEnabled.value
        val chatSummary = if (memOn && appSettings.memorySummary.value) {
            memoryRepository.getLatestSummary(sessionId)?.summary ?: ""
        } else ""
        val memoryFacts = if (memOn && appSettings.memoryFacts.value) {
            val queryVector = if (appSettings.memoryVector.value && embeddingService.isConfigured()) {
                embeddingService.embed(listOf(lastUserText)).getOrNull()?.firstOrNull()
            } else null
            memoryRepository.searchMemories(
                cardId, sessionId, lastUserText, appSettings.memoryTopK.value, queryVector
            )
                .joinToString("\n") { "· ${it.content}" }
        } else ""
        val budgetTokens = if (memOn && appSettings.memoryBudget.value) {
            (modelConfig.maxContextTokens * 0.7).toInt()
        } else 0

        val promptMessages = PromptBuilder.buildMessages(
            card = card,
            activation = activation,
            history = history,
            userName = appSettings.playerName.value,
            mode = mode,
            config = PromptConfig(
                // 开局已作为消息写入历史，不再重复注入
                includeFirstMessage = false,
                statusProtocol = statusProtocol,
                // IM 协议仅实时模式使用（聊天增强下 IM 已单独生成，避免重复回泡）
                narrationProtocol = if (enhance) "" else ImProtocol.INSTRUCTION,
                // 插件提示词注入（宏包/模板类）
                pluginInjections = PluginEngine.collectPromptInjections(plugins, lastUserText),
                chatSummary = chatSummary,
                memoryFacts = memoryFacts,
                budgetTokens = budgetTokens,
                // 格式校验重试 / 玩家手机来消息时的强制提醒
                authorNote = buildList {
                    // 状态栏重试提醒（仅非 VN 重试时注入，避免污染不同协议的卡）
                    if (attempt > 0 && !vnRetry && _cardUsesTextStatus.value) {
                        add("重要：你上一次输出缺少状态栏。本次请严格按照角色卡自带的状态栏格式输出（保持该卡原有的状态栏写法，不要改成其他格式）。")
                    }
                    // VN 重试提醒：只强调补选项，不提状态栏格式
                    if (vnRetry && mode == InteractiveMode.VISUAL_NOVEL) {
                        add("重要：你上一次回复没有输出分支选项。本次必须在回复末尾按 [CHOICES] 协议输出 3～4 个选项（每项 30～60 字，含 delta 状态增量）。正文剧情照常完整输出，不要省略。")
                    }
                    if (lastUserIsIm && !enhance) {
                        add("【强制】玩家刚刚通过手机聊天给你发来消息。本回合回复末尾必须输出 [IM]块[/IM]，内容为角色对这条手机消息的口语化回复（简短、像聊天软件）。")
                    }
                }.joinToString("\n")
            )
        )

        // 流式生成
        _isGenerating.value = true
        _streamingText.value = ""

        // Token 估算：提示词部分
        val promptTokens = promptMessages.sumOf { MemoryEngine.estimateTokens(it.content) }

        val provider = ModelProviderFactory.getProvider(modelConfig)
        val result = provider.chatStream(modelConfig, promptMessages) { delta ->
            _streamingText.value = StatusBlockParser.stripForDisplay(
                NarrationParser.stripForDisplay(_streamingText.value + delta)
            )
        }

        // 累计用量（提示词 + 回复）
        val replyTokens = result.getOrNull()?.let { MemoryEngine.estimateTokens(it) } ?: 0
        val totalTokens = promptTokens + replyTokens
        if (totalTokens > 0) {
            chatRepository.addTokenUsage(sessionId, totalTokens)
            _sessionTokenUsage.value += totalTokens
        }

        // 保存 AI 回复（插件 + 剥离状态栏/选项/状态/旁白块）
        result.onSuccess { reply ->
            if (reply.isNotBlank()) {
                // 平台风控拦截：不作为角色回复保存，转为错误提示
                if (SafetyFilter.isRejection(reply)) {
                    Log.w(TAG, "reply rejected by safety filter: ${reply.take(120)}")
                    _error.value = SafetyFilter.rejectionMessage()
                } else {
                    var processed = PluginEngine.applyReply(plugins, reply)
                    // QuickJS 受限沙箱：EXECUTE_JS 规则改写（一期：仅改写类）
                    val jsRules = plugins.filter { it.enabled }
                        .flatMap { it.rules }
                        .filter { it.action == com.tavern.app.core.model.PluginAction.EXECUTE_JS }
                    jsRules.forEach { rule ->
                        val script = rule.config["script"]
                        if (script.isNullOrBlank()) return@forEach
                        val rewritten = JsSandbox.transformReply(
                            script,
                            JsSandbox.SandboxContext(
                                characterName = _characterName.value,
                                userInput = lastUserText,
                                recentMessages = history.takeLast(6).map { it.content },
                                replyText = processed
                            )
                        )
                        if (rewritten != null) {
                            processed = rewritten
                            Log.i(TAG, "js plugin rewrote reply")
                        }
                    }
                    val blockResult = StatusBlockParser.parse(processed)
                    val optionsResult = OptionsParser.parse(blockResult.content)
                    val statusResult = StatusChangeParser.parse(optionsResult.content)
                    val narrationResult = NarrationParser.parse(statusResult.content)
                    val imResult = ImProtocolParser.parse(narrationResult.content)
                    var finalContent = imResult.content
                    val panel = blockResult.statusPanel

                    // 正文兜底：全链路剥离后为空但原始回复非空时，退回轻量剥标签结果
                    if (finalContent.isBlank() && processed.isNotBlank()) {
                        val fallback = StatusBlockParser.stripForDisplay(processed).trim()
                        if (fallback.isNotBlank()) {
                            Log.w(TAG, "content empty after parse, fallback to stripped raw")
                            finalContent = fallback
                        }
                    }

                    // IM 回复：实时模式取 [IM]/Master_Talk/兜底；增强模式不回泡（已单独生成）
                    var imReply: String?
                    if (enhance) {
                        imReply = null
                    } else {
                        imReply = imResult.imReply ?: blockResult.imReply
                        if (imReply == null && lastUserIsIm) {
                            imReply = ImProtocolParser.fallbackReply(finalContent, exclude = lastUserText)
                            if (imReply != null) {
                                Log.w(TAG, "im block missing, fallback to quoted speech")
                            }
                        }
                    }

                    // 格式校验：卡声明了状态栏协议但本次没输出 → 开关打开时重试
                    val strict = appSettings.strictStatusFormat.value
                    if (_cardUsesTextStatus.value && panel == null && strict && attempt < 2 && !vnRetry) {
                        Log.w(TAG, "status block missing, retry ${attempt + 1}/2")
                        _isGenerating.value = false
                        _streamingText.value = ""
                        generateReply(sessionId, attempt + 1)
                        return
                    }

                    // 视觉小说：漏选项且开启重试 → 重生成一次
                    if (mode == InteractiveMode.VISUAL_NOVEL &&
                        optionsResult.choices.isEmpty() &&
                        appSettings.vnRetryMissing.value && attempt < 1
                    ) {
                        Log.w(TAG, "vn choices missing, retry once")
                        _isGenerating.value = false
                        _streamingText.value = ""
                        generateReply(sessionId, attempt + 1, vnRetry = true)
                        return
                    }

                    chatRepository.addMessage(
                        sessionId = sessionId,
                        role = MessageRole.ASSISTANT,
                        content = finalContent,
                        narration = narrationResult.narration,
                        statusPanel = panel,
                        imContent = imReply,
                        parentMessageId = branchOverride?.first ?: lastUser?.id ?: -1,
                        branchIndex = branchOverride?.second ?: 0
                    )
                    _currentChoices.value = optionsResult.choices
                    if (!statusResult.delta.isEmpty) {
                        stateRepository.applyDelta(cardId, statusResult.delta)
                    }
                    // 自动朗读
                    if (appSettings.autoSpeak.value && finalContent.isNotBlank()) {
                        synthesizeAndPlay(finalContent)
                    }
                    // 记忆系统：后台摘要 + 事实抽取（不阻塞 UI）
                    if (appSettings.memoryEnabled.value) {
                        scheduleMemoryTasks(sessionId)
                    }
                }
            }
        }.onFailure { e ->
            Log.w(TAG, "generation failed: ${e.message}")
            // 网络类错误自动重试一次（如 connection abort）
            val msg = e.message ?: ""
            val isNetworkError = msg.contains("connection", ignoreCase = true) ||
                msg.contains("timeout", ignoreCase = true) ||
                msg.contains("reset", ignoreCase = true) ||
                msg.contains("Unable to resolve", ignoreCase = true)
            if (isNetworkError && attempt < 1) {
                Log.w(TAG, "network error, retry once")
                _isGenerating.value = false
                _streamingText.value = ""
                generateReply(sessionId, attempt + 1)
                return
            }
            _error.value = "生成失败：${e.message}"
        }

        _isGenerating.value = false
        _streamingText.value = ""
    }

    /**
     * 扫描世界书，得到激活条目
     */
    private suspend fun scanWorldBook(
        card: CharacterCard,
        sessionId: Long,
        currentText: String
    ): WorldBookActivation? {
        val book = card.characterBook ?: return null
        val recentText = chatRepository.getLastMessages(sessionId, 5)
            .joinToString("\n") { it.content } + "\n" + currentText
        return WorldBookScanner.scan(book, recentText)
    }

    // ===== 记忆系统后台任务 =====

    /** 回复入库后调度：滚动摘要 + 周期事实抽取 */
    private fun scheduleMemoryTasks(sessionId: Long) {
        appScope.launch {
            runCatching {
                if (appSettings.memorySummary.value) {
                    maybeSummarize(sessionId)
                }
                if (appSettings.memoryFacts.value) {
                    maybeExtractFacts(sessionId)
                }
            }.onFailure { e ->
                Log.w(TAG, "memory task failed: ${e.message}")
            }
        }
    }

    /** 未摘要消息 ≥ N 条时增量生成摘要 */
    private suspend fun maybeSummarize(sessionId: Long) {
        val n = appSettings.memorySummaryN.value
        val messages = chatRepository.getMessages(sessionId)
            .filter { it.role == "USER" || it.role == "ASSISTANT" }
        val watermark = memoryRepository.getLatestSummary(sessionId)?.waterMarkMessageId ?: 0L
        val pending = messages.filter { it.id > watermark }
        if (pending.size < n) return

        val model = resolveCurrentModel() ?: return
        val prev = memoryRepository.getLatestSummary(sessionId)?.summary
        val window = pending.joinToString("\n") { msg ->
            val who = if (msg.role == "USER") "玩家" else "角色"
            "$who：${msg.content.take(600)}"
        }
        MemoryEngine.summarize(model, prev, window)
            .onSuccess { text ->
                val nextRound = (memoryRepository.getLatestSummary(sessionId)?.roundIndex ?: 0) + 1
                memoryRepository.saveSummary(sessionId, nextRound, text, pending.last().id)
                Log.i(TAG, "summary updated, round=$nextRound, covered=${pending.size}")
            }
            .onFailure { e ->
                Log.w(TAG, "summarize failed: ${e.message}")
            }
    }

    /** 每 N 轮助手回复后抽取一次长期事实 */
    private suspend fun maybeExtractFacts(sessionId: Long) {
        val everyN = appSettings.memoryFactEveryN.value
        val messages = chatRepository.getMessages(sessionId)
        val assistantCount = messages.count { it.role == "ASSISTANT" }
        if (assistantCount == 0 || assistantCount % everyN != 0) return

        val model = resolveCurrentModel() ?: return
        val recent = messages.takeLast(6)
        val dialog = recent.joinToString("\n") { msg ->
            val who = if (msg.role == "USER") "玩家" else "角色"
            "$who：${msg.content.take(500)}"
        }
        MemoryEngine.extractFacts(model, dialog)
            .onSuccess { facts ->
                // 向量检索开启时为新事实生成 embedding
                val embeddings: List<String?> = if (
                    appSettings.memoryVector.value && embeddingService.isConfigured() && facts.isNotEmpty()
                ) {
                    val vectors = embeddingService.embed(facts).getOrNull()
                    facts.indices.map { i -> vectors?.getOrNull(i)?.let { memoryRepository.encodeEmbedding(it) } }
                } else {
                    facts.map { null }
                }
                var added = 0
                facts.forEachIndexed { i, fact ->
                    if (memoryRepository.addMemory(
                            cardId, sessionId, fact, "auto",
                            recent.lastOrNull { it.role == "ASSISTANT" }?.id ?: -1,
                            embedding = embeddings.getOrNull(i)
                        )
                    ) added++
                }
                if (added > 0) Log.i(TAG, "extracted $added memories (assistant#$assistantCount)")
            }
            .onFailure { e ->
                Log.w(TAG, "extract facts failed: ${e.message}")
            }
    }

    /**
     * 切换当前会话使用的模型（持久化到会话）
     */
    fun selectModel(modelConfigId: Long) {
        viewModelScope.launch {
            val sessionId = _sessionId.value ?: return@launch
            _currentModelConfigId.value = modelConfigId
            chatRepository.updateSessionModelConfig(sessionId, modelConfigId)
            updateCurrentModelName()
        }
    }

    /**
     * 解析当前会话应使用的模型
     */
    private suspend fun resolveCurrentModel(): ModelConfig? {
        val explicitId = _currentModelConfigId.value
        if (explicitId > 0) {
            providerRepository.getModelConfig(explicitId)?.toModel()?.let { return it }
        }
        return providerRepository.getDefaultModelConfig()?.toModel()
    }

    /**
     * 更新当前模型显示名
     */
    private suspend fun updateCurrentModelName() {
        val model = resolveCurrentModel()
        _currentModelName.value = model?.let {
            it.name.ifBlank { it.modelName.ifBlank { "未命名模型" } }
        } ?: "未配置模型"
    }

    /**
     * 切换互动模式（持久化到会话）
     */
    fun switchMode(mode: InteractiveMode) {
        viewModelScope.launch {
            val sessionId = _sessionId.value ?: return@launch
            _interactiveMode.value = mode
            chatRepository.updateSessionMode(sessionId, mode)
        }
    }

    /**
     * 选择一个选项：结算状态增量，作为下一条用户消息发送
     */
    fun chooseOption(choice: VNChoice) {
        _currentChoices.value = emptyList()
        if (!choice.delta.isEmpty) {
            viewModelScope.launch {
                stateRepository.applyDelta(cardId, choice.delta)
            }
        }
        sendMessage(choice.text)
    }

    fun clearError() {
        _error.value = null
    }

    /**
     * 语音输入：
     * - 已配置网络 ASR：首次点击开始录音，再次点击结束并转写，作为语音消息直接发送
     * - 未配置：直接调系统语音识别，结果回填输入框
     */
    fun startOrStopVoiceInput(onSystemResult: (String) -> Unit) {
        viewModelScope.launch {
            if (_isRecording.value) {
                _isRecording.value = false
                speechToText.transcribeRecent()
                    .onSuccess { (text, path, duration) ->
                        if (text.isNotBlank()) {
                            sendInternal(text, isVoice = true, voicePath = path, voiceDuration = duration)
                        } else {
                            _error.value = "语音识别结果为空"
                        }
                    }
                    .onFailure { e ->
                        _error.value = "语音识别失败：${e.message}"
                    }
            } else {
                val hasRemote = speechToText.hasRemoteAsr()
                if (!hasRemote) {
                    // 回退系统识别
                    speechToText.recognizeSystem()
                        .onSuccess { text ->
                            if (text.isNotBlank()) onSystemResult(text)
                        }
                        .onFailure { e ->
                            _error.value = "语音识别失败：${e.message}"
                        }
                    return@launch
                }
                speechToText.startRecording()
                    .onSuccess { _isRecording.value = true }
                    .onFailure { e ->
                        _error.value = "录音失败：${e.message}"
                    }
            }
        }
    }

    fun toggleAutoSpeak(enabled: Boolean) {
        appSettings.setAutoSpeak(enabled)
    }

    /**
     * 合成语音并播放（TTS）
     */
    fun synthesizeAndPlay(text: String) {
        viewModelScope.launch {
            voiceService.synthesizeToFile(text)
                .onSuccess { path -> voiceService.play(path) }
                .onFailure { e ->
                    _error.value = "语音合成失败：${e.message}"
                }
        }
    }

    /**
     * 播放已有的语音文件
     */
    fun playVoiceFile(path: String) {
        voiceService.play(path)
    }

    /**
     * 生成图片并作为图片消息发送
     */
    fun generateImage(prompt: String) {
        val sessionId = _sessionId.value ?: return
        if (prompt.isBlank() || _isGenerating.value) return

        viewModelScope.launch {
            _isGenerating.value = true
            imageService.generate(prompt)
                .onSuccess { path ->
                    chatRepository.addMessage(
                        sessionId = sessionId,
                        role = MessageRole.ASSISTANT,
                        content = "",
                        hasImage = true,
                        imagePath = path
                    )
                }
                .onFailure { e ->
                    _error.value = "生图失败：${e.message}"
                }
            _isGenerating.value = false
        }
    }

    // ===== 实体 ↔ 模型转换 =====

    private fun ChatMessageEntity.toChatMessage(): ChatMessage = ChatMessage(
        id = id,
        sessionId = sessionId,
        role = MessageRole.valueOf(role),
        content = content,
        timestamp = timestamp,
        isVoice = isVoice,
        voicePath = voicePath,
        voiceDuration = voiceDuration,
        hasImage = hasImage,
        imagePath = imagePath,
        isFavorite = isFavorite,
        isBranch = isBranch,
        parentMessageId = parentMessageId,
        isIm = isIm,
        imContent = imContent
    )
}
