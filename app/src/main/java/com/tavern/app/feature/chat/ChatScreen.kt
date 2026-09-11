package com.tavern.app.feature.chat

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.tavern.app.core.data.local.entity.ChatMessageEntity
import com.tavern.app.core.model.InteractiveMode
import com.tavern.app.core.model.ModelConfig
import com.tavern.app.core.model.StatusDelta
import com.tavern.app.core.model.StatusItemDef
import com.tavern.app.core.model.VNChoice
import com.tavern.app.ui.LocalChatFontSize
import com.tavern.app.ui.LocalPrivacyMode
import com.tavern.app.ui.privacyBlur
import com.tavern.app.core.prompt.displayName as modeDisplayName
import com.tavern.app.feature.settings.displayName as providerDisplayName
import com.tavern.app.feature.status.StatusBar

/**
 * 聊天页
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: ChatViewModel = hiltViewModel()
) {
    val messages by viewModel.messages.collectAsState()
    val isGenerating by viewModel.isGenerating.collectAsState()
    val streamingText by viewModel.streamingText.collectAsState()
    val characterName by viewModel.characterName.collectAsState()
    val error by viewModel.error.collectAsState()
    val models by viewModel.models.collectAsState()
    val currentModelName by viewModel.currentModelName.collectAsState()
    val currentModelConfigId by viewModel.currentModelConfigId.collectAsState()
    val interactiveMode by viewModel.interactiveMode.collectAsState()
    val currentChoices by viewModel.currentChoices.collectAsState()
    val characterAvatarPath by viewModel.characterAvatarPath.collectAsState()
    val characterState by viewModel.characterState.collectAsState()
    val statusSchema by viewModel.statusSchema.collectAsState()
    val latestStatusPanel by viewModel.latestStatusPanel.collectAsState()
    val privacyMode by viewModel.privacyMode.collectAsState()
    val playerAvatarPath by viewModel.playerAvatarPath.collectAsState()
    val chatFontSize by viewModel.chatFontSize.collectAsState()
    val isRecording by viewModel.isRecording.collectAsState()
    val autoSpeak by viewModel.autoSpeak.collectAsState()
    val vnDirectSend by viewModel.vnDirectSend.collectAsState()
    val vnRetryMissing by viewModel.vnRetryMissing.collectAsState()

    val listState = rememberLazyListState()
    var inputText by remember { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }
    var showModelPicker by remember { mutableStateOf(false) }
    var showModePicker by remember { mutableStateOf(false) }
    var showImageDialog by remember { mutableStateOf(false) }
    var showImChat by remember { mutableStateOf(false) }
    var imInputText by remember { mutableStateOf("") }
    var messageMenu by remember { mutableStateOf<ChatMessageEntity?>(null) }
    var editingMessage by remember { mutableStateOf<ChatMessageEntity?>(null) }
    var showFontDialog by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }

    // 录音权限
    val context = LocalContext.current
    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.startOrStopVoiceInput { text -> inputText = text }
        }
    }

    // 自动跟随到底部：仅当用户本就位于底部附近时跟随，
    // 阅读历史时不会被流式输出强行拽走
    val shouldFollowBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val total = info.totalItemsCount
            if (total == 0) true
            else (info.visibleItemsInfo.lastOrNull()?.index ?: 0) >= total - 2
        }
    }
    LaunchedEffect(messages.size, streamingText, isGenerating) {
        if (shouldFollowBottom) {
            val total = listState.layoutInfo.totalItemsCount
            if (total > 0) listState.scrollToItem(total - 1)
        }
    }

    // 错误提示
    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    CompositionLocalProvider(
        LocalPrivacyMode provides privacyMode
    ) {
    CompositionLocalProvider(LocalChatFontSize provides chatFontSize.sp) {
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.surface,
        floatingActionButton = {
            ImFloatingBall(
                name = characterName,
                filePath = characterAvatarPath,
                privacyMode = privacyMode,
                overlayOpen = showImChat,
                onClick = { showImChat = true }
            )
        },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = characterName.ifBlank { "聊天" },
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${interactiveMode.modeDisplayName()} · $currentModelName",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { showMoreMenu = true }) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = "更多"
                        )
                    }
                }
            )
        },
        bottomBar = {
            MessageInputBar(
                value = inputText,
                onValueChange = { inputText = it },
                onSend = {
                    viewModel.sendMessage(inputText)
                    inputText = ""
                },
                onVoiceInput = {
                    val granted = ContextCompat.checkSelfPermission(
                        context, Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED
                    if (granted) {
                        viewModel.startOrStopVoiceInput { text -> inputText = text }
                    } else {
                        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                isRecording = isRecording,
                onGenerateImage = { showImageDialog = true },
                enabled = !isGenerating
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // 角色状态栏：固定在头部（不随聊天滚动），仅卡片自带/手动配置时显示
            if (statusSchema.isNotEmpty()) {
                StatusBar(
                    state = characterState,
                    schema = statusSchema,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            // 文字状态栏面板：模型输出的 <Status_block> 内容，固定可折叠
            latestStatusPanel?.let { panel ->
                TextStatusPanel(
                    panel = panel,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 视觉小说模式：顶部角色立绘
                if (interactiveMode == InteractiveMode.VISUAL_NOVEL) {
                    item {
                        CharacterPortrait(
                            name = characterName,
                            filePath = characterAvatarPath,
                            privacyMode = privacyMode
                        )
                    }
                }

            items(messages, key = { it.id }) { message ->
                if (message.isIm) {
                    // IM 消息在叙事视图中显示为手机动作气泡
                    ImActionBubble(
                        content = message.content,
                        isFromUser = message.role == "USER"
                    )
                } else {
                    val isInteractive = interactiveMode != InteractiveMode.CHAT
                    val displayContent = if (isInteractive && message.role == "ASSISTANT") {
                        // 互动模式优先展示联动旁白，无旁白则展示正文
                        message.narration?.takeIf { it.isNotBlank() } ?: message.content
                    } else {
                        message.content
                    }
                    val showNarrativeStyle = isInteractive &&
                        message.role == "ASSISTANT" &&
                        !message.narration.isNullOrBlank()

                    MessageBubble(
                        message = message,
                        contentOverride = displayContent,
                        narrativeStyle = showNarrativeStyle,
                        onLongClick = { messageMenu = message },
                        onPlay = {
                            if (message.isVoice) {
                                message.voicePath?.let { viewModel.playVoiceFile(it) }
                            } else if (message.role == "ASSISTANT") {
                                viewModel.synthesizeAndPlay(message.content)
                            }
                        }
                    )
                }
            }

            // 流式输出中的文本
            if (isGenerating && streamingText.isNotEmpty()) {
                item {
                    MessageBubble(
                        role = "ASSISTANT",
                        content = streamingText,
                        isStreaming = true
                    )
                }
            }

            // 等待首个 token 的占位
            if (isGenerating && streamingText.isEmpty()) {
                item {
                    TypingIndicator()
                }
            }

            // 可选项按钮
            if (!isGenerating && currentChoices.isNotEmpty()) {
                item {
                    ChoicesColumn(
                        choices = currentChoices,
                        directSend = vnDirectSend,
                        onChoose = { viewModel.chooseOption(it) }
                    )
                }
            }
            }
        }
    }

    // 微信式 IM 聊天浮层（翻转动画开合）
    val imFlipTransition = updateTransition(showImChat, label = "imFlip")
    if (imFlipTransition.currentState || imFlipTransition.targetState) {
        val rotationY by imFlipTransition.animateFloat(label = "rotationY") { visible ->
            if (visible) 0f else 95f
        }
        val imAlpha by imFlipTransition.animateFloat(label = "imAlpha") { visible ->
            if (visible) 1f else 0f
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    this.rotationY = rotationY
                    this.alpha = imAlpha
                    // 加大 cameraDistance 才有 3D 翻转感
                    cameraDistance = 14f * this.density
                }
        ) {
            ImChatOverlay(
                characterName = characterName,
                filePath = characterAvatarPath,
                playerAvatarPath = playerAvatarPath,
                privacyMode = privacyMode,
                messages = messages,
                isGenerating = isGenerating,
                streamingText = streamingText,
                inputText = imInputText,
                onInputTextChange = { imInputText = it },
                onSend = {
                    viewModel.sendImMessage(imInputText)
                    imInputText = ""
                },
                onBack = { showImChat = false }
            )
        }
    }
    } // LocalChatFontSize
    } // LocalPrivacyMode

    // 消息长按菜单
    messageMenu?.let { msg ->
        AlertDialog(
            onDismissRequest = { messageMenu = null },
            title = {
                Text(if (msg.role == "USER") "操作：我的消息" else "操作：角色消息")
            },
            text = {
                Column {
                    if (msg.role == "USER") {
                        TextButton(onClick = {
                            editingMessage = msg
                            messageMenu = null
                        }) { Text("编辑内容") }
                        TextButton(onClick = {
                            viewModel.regenerateFrom(msg.id)
                            messageMenu = null
                        }) { Text("重新生成本轮回复") }
                    } else {
                        TextButton(onClick = {
                            viewModel.regenerateFrom(msg.id)
                            messageMenu = null
                        }) { Text("重新生成这条回复") }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { messageMenu = null }) { Text("取消") }
            }
        )
    }

    // 编辑用户消息
    editingMessage?.let { msg ->
        EditMessageDialog(
            initial = msg.content,
            onDismiss = { editingMessage = null },
            onSave = { newContent ->
                viewModel.editMessage(msg.id, newContent)
                editingMessage = null
            }
        )
    }

    // 字体大小调节
    if (showFontDialog) {
        FontSizeDialog(
            current = chatFontSize,
            onDismiss = { showFontDialog = false },
            onConfirm = { sp ->
                viewModel.setChatFontSize(sp)
                showFontDialog = false
            }
        )
    }

    // 模型选择器
    if (showModelPicker) {
        ModelPickerSheet(
            models = models,
            currentModelConfigId = currentModelConfigId,
            onSelect = { id ->
                viewModel.selectModel(id)
                showModelPicker = false
            },
            onOpenSettings = {
                showModelPicker = false
                onOpenSettings()
            },
            onDismiss = { showModelPicker = false }
        )
    }

    // 互动模式选择器
    if (showModePicker) {
        ModePickerSheet(
            currentMode = interactiveMode,
            onSelect = { mode ->
                viewModel.switchMode(mode)
                showModePicker = false
            },
            vnDirectSend = vnDirectSend,
            vnRetryMissing = vnRetryMissing,
            onVnDirectSendChange = { viewModel.setVnDirectSend(it) },
            onVnRetryChange = { viewModel.setVnRetryMissing(it) },
            onDismiss = { showModePicker = false }
        )
    }

    // 更多菜单（微信式底部弹层）
    if (showMoreMenu) {
        ModalBottomSheet(onDismissRequest = { showMoreMenu = false }) {
            Text(
                text = "更多",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
            MoreMenuItem(Icons.Filled.SwapHoriz, "切换模式（当前：${interactiveMode.modeDisplayName()}）") {
                showMoreMenu = false
                showModePicker = true
            }
            MoreMenuItem(Icons.Filled.SmartToy, "切换模型（$currentModelName）") {
                showMoreMenu = false
                showModelPicker = true
            }
            MoreMenuItem(Icons.Filled.TextFields, "字体大小（${chatFontSize}sp）") {
                showMoreMenu = false
                showFontDialog = true
            }
            MoreMenuItem(
                icon = if (autoSpeak) Icons.AutoMirrored.Filled.VolumeUp else Icons.Filled.VolumeOff,
                title = if (autoSpeak) "自动朗读：开" else "自动朗读：关"
            ) {
                viewModel.toggleAutoSpeak(!autoSpeak)
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    // 生图提示词对话框
    if (showImageDialog) {
        ImagePromptDialog(
            onDismiss = { showImageDialog = false },
            onGenerate = { prompt ->
                viewModel.generateImage(prompt)
                showImageDialog = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelPickerSheet(
    models: List<ModelConfig>,
    currentModelConfigId: Long,
    onSelect: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            text = "选择模型",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
        )

        // 跟随默认模型
        ModelPickerItem(
            title = "跟随默认模型",
            subtitle = null,
            selected = currentModelConfigId <= 0,
            onClick = { onSelect(-1L) }
        )

        HorizontalDivider()

        if (models.isEmpty()) {
            Text(
                text = "尚未配置模型，点击下方「管理模型」添加",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
            )
        } else {
            models.forEach { model ->
                ModelPickerItem(
                    title = model.name.ifBlank { model.modelName.ifBlank { "未命名模型" } },
                    subtitle = "${model.providerType.providerDisplayName()} · ${model.modelName}",
                    selected = currentModelConfigId == model.id,
                    onClick = { onSelect(model.id) }
                )
            }
        }

        TextButton(
            onClick = onOpenSettings,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
        ) {
            Text("管理模型…")
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ModelPickerItem(
    title: String,
    subtitle: String?,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        RadioButton(selected = selected, onClick = onClick)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModePickerSheet(
    currentMode: InteractiveMode,
    onSelect: (InteractiveMode) -> Unit,
    vnDirectSend: Boolean = true,
    vnRetryMissing: Boolean = true,
    onVnDirectSendChange: (Boolean) -> Unit = {},
    onVnRetryChange: (Boolean) -> Unit = {},
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            text = "选择互动模式",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
        )

        InteractiveMode.entries
            .filter { it != InteractiveMode.CHAT }
            .forEach { mode ->
            ModePickerItem(
                title = mode.modeDisplayName(),
                subtitle = modeDescription(mode),
                selected = currentMode == mode,
                onClick = { onSelect(mode) }
            )
        }

        // 视觉小说子设置
        HorizontalDivider(modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp))
        Text(
            text = "视觉小说设置",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onVnDirectSendChange(!vnDirectSend) }
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("选项点击直接发送", style = MaterialTheme.typography.bodyLarge)
                Text(
                    if (vnDirectSend) "点击选项立即作为回复发送" else "点击选项先预览全文再确认发送",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = vnDirectSend, onCheckedChange = onVnDirectSendChange)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onVnRetryChange(!vnRetryMissing) }
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("漏选项自动重试", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "模型未输出选项时自动重新生成一次",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = vnRetryMissing, onCheckedChange = onVnRetryChange)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ModePickerItem(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        RadioButton(selected = selected, onClick = onClick)
    }
}

private fun modeDescription(mode: InteractiveMode): String = when (mode) {
    InteractiveMode.CHAT -> "传统对话"
    InteractiveMode.TEXT_ADVENTURE -> "AI 主导叙事，输入动作推进故事"
    InteractiveMode.VISUAL_NOVEL -> "角色台词 + 分支选项，日式 AVG 体验"
    InteractiveMode.FREE_ROLEPLAY -> "沉浸式扮演角色，与 AI 角色自由互动"
}

@Composable
private fun CharacterPortrait(name: String, filePath: String?, privacyMode: Boolean = false) {
    val bitmap = remember(filePath) {
        filePath?.let { path ->
            runCatching { BitmapFactory.decodeFile(path) }.getOrNull()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = name,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .privacyBlur(privacyMode)
            )
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(name, style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "（无立绘）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ChoicesColumn(
    choices: List<VNChoice>,
    directSend: Boolean = true,
    onChoose: (VNChoice) -> Unit
) {
    var previewChoice by remember { mutableStateOf<VNChoice?>(null) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "请选择行动：",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        choices.forEachIndexed { index, choice ->
            Surface(
                onClick = {
                    if (directSend) onChoose(choice) else previewChoice = choice
                },
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF95EC69),
                contentColor = Color(0xFF1B1B1B),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${index + 1}. ${choice.text}",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = LocalChatFontSize.current
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (!directSend) {
                        Spacer(Modifier.width(8.dp))
                        Text("👁", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }

    // 预览弹层（非直接发送模式）
    previewChoice?.let { choice ->
        AlertDialog(
            onDismissRequest = { previewChoice = null },
            title = { Text("选择行动") },
            text = {
                Column {
                    Text(
                        choice.text,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = LocalChatFontSize.current
                        )
                    )
                    if (!choice.delta.isEmpty) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "预估影响：${describeDelta(choice.delta)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(0xFF07C160)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val c = choice
                    previewChoice = null
                    onChoose(c)
                }) { Text("选择此项") }
            },
            dismissButton = {
                TextButton(onClick = { previewChoice = null }) { Text("取消") }
            }
        )
    }
}

/** 状态增量的可读描述 */
private fun describeDelta(delta: StatusDelta): String {
    val parts = buildList {
        if (delta.mood != 0) add("心情${signed(delta.mood)}")
        if (delta.energy != 0) add("体力${signed(delta.energy)}")
        if (delta.affection != 0) add("好感${signed(delta.affection)}")
        delta.custom.forEach { (k, v) -> add("$k${signed(v)}") }
    }
    return parts.joinToString("  ").ifBlank { "无" }
}

private fun signed(v: Int): String = if (v > 0) "+$v" else "$v"

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    message: ChatMessageEntity,
    contentOverride: String? = null,
    narrativeStyle: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onPlay: (() -> Unit)? = null
) {
    MessageBubble(
        role = message.role,
        content = contentOverride ?: message.content,
        isStreaming = false,
        isVoice = message.isVoice,
        hasImage = message.hasImage,
        imagePath = message.imagePath,
        narrativeStyle = narrativeStyle,
        onLongClick = onLongClick,
        onPlay = onPlay
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    role: String,
    content: String,
    isStreaming: Boolean,
    isVoice: Boolean = false,
    hasImage: Boolean = false,
    imagePath: String? = null,
    narrativeStyle: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onPlay: (() -> Unit)? = null
) {
    val isUser = role == "USER"
    // 流式输出时在末尾显示光标
    val displayContent = if (isStreaming) content + "▌" else content

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        val bubbleModifier = if (onLongClick != null) {
            Modifier
                .fillMaxWidth(0.8f)
                .combinedClickable(onClick = {}, onLongClick = onLongClick)
        } else {
            Modifier.fillMaxWidth(0.8f)
        }

        Surface(
            color = when {
                // 微信绿用户气泡
                isUser -> Color(0xFF95EC69)
                narrativeStyle -> MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                else -> MaterialTheme.colorScheme.surface
            },
            contentColor = when {
                isUser -> Color(0xFF1B1B1B)
                narrativeStyle -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                else -> MaterialTheme.colorScheme.onSurface
            },
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            ),
            shadowElevation = if (isUser) 0.dp else 1.5.dp,
            modifier = bubbleModifier
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                if (narrativeStyle) {
                    Text(
                        text = "◇ 旁白",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
                // 图片消息
                if (hasImage && imagePath != null) {
                    MessageImage(filePath = imagePath)
                    if (content.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                    }
                }
                if (isVoice) {
                    // 语音消息：播放按钮
                    IconButton(onClick = onPlay ?: {}, modifier = Modifier.size(24.dp)) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = "播放语音",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (content.isNotBlank()) {
                        Text(
                            text = displayContent,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                } else {
                    // 玩家消息固定纯黑文本（禁用引号绿色着色）
                    TavernText(
                        text = displayContent,
                        plain = isUser,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = LocalChatFontSize.current,
                            color = if (isUser) Color(0xFF111111)
                            else Color.Unspecified,
                            fontStyle = if (!isUser && narrativeStyle) FontStyle.Italic
                            else FontStyle.Normal
                        )
                    )
                }
            }
        }

        // AI 消息的朗读按钮
        if (!isUser && onPlay != null && !isVoice) {
            IconButton(onClick = onPlay) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = "朗读",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun TypingIndicator() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(
                text = "正在输入…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(12.dp)
            )
        }
    }
}

@Composable
private fun MessageInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onVoiceInput: () -> Unit,
    isRecording: Boolean = false,
    onGenerateImage: () -> Unit,
    enabled: Boolean
) {
    // 微信输入栏：浅灰底 + 白色圆角输入框 + 绿色圆形发送键
    Surface(
        color = Color(0xFFF7F7F7),
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            IconButton(onClick = onVoiceInput, enabled = enabled || isRecording) {
                Icon(
                    imageVector = Icons.Filled.Mic,
                    contentDescription = if (isRecording) "结束录音" else "语音输入",
                    tint = if (isRecording) MaterialTheme.colorScheme.error
                    else Color(0xFF181818)
                )
            }

            IconButton(onClick = onGenerateImage, enabled = enabled) {
                Icon(
                    imageVector = Icons.Filled.AddPhotoAlternate,
                    contentDescription = "生图",
                    tint = Color(0xFF181818)
                )
            }

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color.White,
                modifier = Modifier.weight(1f)
            ) {
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    enabled = enabled,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = Color(0xFF111111),
                        fontSize = LocalChatFontSize.current
                    ),
                    cursorBrush = SolidColor(WeChatGreenChat),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    maxLines = 4
                )
            }
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (enabled && value.isNotBlank()) WeChatGreenChat
                        else Color(0xFFB2EBD0)
                    )
                    .clickable(enabled = enabled && value.isNotBlank()) { onSend() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "发送",
                    tint = Color.White
                )
            }
        }
    }
}

private val WeChatGreenChat = Color(0xFF07C160)

/** 更多菜单条目（底部弹层） */
@Composable
private fun MoreMenuItem(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.width(16.dp))
        Text(title, style = MaterialTheme.typography.bodyLarge)
    }
}

/**
 * 图片消息显示
 */
@Composable
private fun MessageImage(filePath: String) {
    val bitmap = remember(filePath) {
        runCatching { BitmapFactory.decodeFile(filePath) }.getOrNull()
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 320.dp)
                .privacyBlur(LocalPrivacyMode.current)
        )
    }
}

/**
 * 生图提示词对话框
 */
@Composable
private fun ImagePromptDialog(
    onDismiss: () -> Unit,
    onGenerate: (String) -> Unit
) {
    var prompt by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("生成图片") },
        text = {
            OutlinedTextField(
                value = prompt,
                onValueChange = { prompt = it },
                label = { Text("图片描述") },
                placeholder = { Text("描述想生成的画面…") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onGenerate(prompt) },
                enabled = prompt.isNotBlank()
            ) { Text("生成") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

/**
 * 编辑消息对话框
 */
@Composable
private fun EditMessageDialog(
    initial: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var text by remember(initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑消息") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 10
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(text) },
                enabled = text.isNotBlank()
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

/**
 * 字体大小调节对话框
 */
@Composable
private fun FontSizeDialog(
    current: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var value by remember(current) { mutableStateOf(current.toFloat()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("聊天字体大小") },
        text = {
            Column {
                Text(
                    "示例：她抬起头，微微一笑。",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = value.sp)
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("小", style = MaterialTheme.typography.labelMedium)
                    Slider(
                        value = value,
                        onValueChange = { value = it },
                        valueRange = 14f..24f,
                        steps = 9,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp)
                    )
                    Text("大", style = MaterialTheme.typography.labelMedium)
                }
                Text(
                    "${value.toInt()}sp",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value.toInt()) }) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

/**
 * 文字状态栏面板（<Status_block> 内容）
 *
 * 固定在聊天列表上方，可折叠，随每次角色回复自动更新。
 */
@Composable
private fun TextStatusPanel(
    panel: String,
    modifier: Modifier = Modifier
) {
    var expanded by rememberSaveable { mutableStateOf(true) }
    if (panel.isBlank()) return

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "状态栏",
                    style = MaterialTheme.typography.titleSmall,
                    color = WeChatGreenChat,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = if (expanded) Icons.Filled.KeyboardArrowUp
                    else Icons.Filled.KeyboardArrowDown,
                    contentDescription = if (expanded) "折叠状态栏" else "展开状态栏",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 240.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(top = 4.dp)
                ) {
                    TavernText(
                        text = panel,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = with(LocalChatFontSize.current) {
                                (value - 2f).coerceAtLeast(12f)
                            }.sp
                        )
                    )
                }
            }
        }
    }
}

// ===== IM 手机聊天 =====

/** 右下角 IM 悬浮球（带头像，开合带缩放动画） */
@Composable
private fun ImFloatingBall(
    name: String,
    filePath: String?,
    privacyMode: Boolean,
    overlayOpen: Boolean = false,
    onClick: () -> Unit
) {
    val bitmap = remember(filePath) {
        filePath?.let { runCatching { BitmapFactory.decodeFile(it) }.getOrNull() }
    }
    val border = MaterialTheme.colorScheme.primary
    // 浮层打开时悬浮球轻微缩小后仰，形成呼应
    val scale by animateFloatAsState(
        targetValue = if (overlayOpen) 0.85f else 1f,
        animationSpec = tween(durationMillis = 280),
        label = "ballScale"
    )
    val rot by animateFloatAsState(
        targetValue = if (overlayOpen) -12f else 0f,
        animationSpec = tween(durationMillis = 280),
        label = "ballRot"
    )

    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier
            .size(56.dp)
            .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                rotationZ = rot
            }
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "打开与${name}的手机聊天",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(3.dp)
                        .clip(CircleShape)
                        .privacyBlur(privacyMode)
                )
            } else {
                Text(
                    text = name.take(1),
                    style = MaterialTheme.typography.titleLarge,
                    color = border
                )
            }
        }
    }
}

/** 叙事视图中的手机消息动作气泡 */
@Composable
private fun ImActionBubble(content: String, isFromUser: Boolean = true) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        Surface(
            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f),
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(0.85f)
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    text = "📱 手机消息",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = if (isFromUser) "你：「$content」" else "TA：「$content」",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

/** 微信式 IM 聊天浮层（全屏覆盖） */
@Composable
private fun ImChatOverlay(
    characterName: String,
    filePath: String?,
    playerAvatarPath: String? = null,
    privacyMode: Boolean,
    messages: List<ChatMessageEntity>,
    isGenerating: Boolean,
    streamingText: String,
    inputText: String,
    onInputTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onBack: () -> Unit
) {
    val imItems = remember(messages) {
        messages.filter { it.isIm || !it.imContent.isNullOrBlank() }
    }
    val listState = rememberLazyListState()
    val charBitmap = remember(filePath) {
        filePath?.let { runCatching { BitmapFactory.decodeFile(it) }.getOrNull() }
    }
    val playerBitmap = remember(playerAvatarPath) {
        playerAvatarPath?.takeIf { it.isNotBlank() }
            ?.let { runCatching { BitmapFactory.decodeFile(it) }.getOrNull() }
    }

    // 有新内容时滚到底
    LaunchedEffect(imItems.size, streamingText) {
        if (imItems.isNotEmpty()) {
            listState.animateScrollToItem(imItems.size - 1)
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 顶栏（微信式：返回 + 居中标题）
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp
            ) {
                Column {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .height(48.dp)
                    ) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.align(Alignment.CenterStart)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                        Row(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(horizontal = 56.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            val bitmap = remember(filePath) {
                                filePath?.let { runCatching { BitmapFactory.decodeFile(it) }.getOrNull() }
                            }
                            if (bitmap != null) {
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .privacyBlur(privacyMode)
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(
                                text = characterName.ifBlank { "聊天" },
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    HorizontalDivider(color = Color(0xFFE5E5E5))
                }
            }

            // 消息区
            if (imItems.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "给TA发第一条消息，开始手机聊天吧",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(imItems, key = { it.id }) { msg ->
                        when {
                            msg.isIm && msg.role == "USER" -> ImUserBubble(
                                content = msg.content,
                                avatar = playerBitmap,
                                privacyMode = privacyMode
                            )
                            msg.isIm && msg.role == "ASSISTANT" -> ImCharacterBubble(
                                content = msg.content,
                                avatar = charBitmap,
                                privacyMode = privacyMode
                            )
                            else -> msg.imContent?.let {
                                ImCharacterBubble(
                                    content = it,
                                    avatar = charBitmap,
                                    privacyMode = privacyMode
                                )
                            }
                        }
                    }
                    if (isGenerating && streamingText.isBlank()) {
                        item { TypingIndicator() }
                    }
                }
            }

            // 输入栏（微信风）
            Surface(
                color = Color(0xFFF7F7F7),
                tonalElevation = 0.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color.White,
                        modifier = Modifier.weight(1f)
                    ) {
                        BasicTextField(
                            value = inputText,
                            onValueChange = onInputTextChange,
                            enabled = !isGenerating,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                color = Color(0xFF111111),
                                fontSize = LocalChatFontSize.current
                            ),
                            cursorBrush = SolidColor(WeChatGreenChat),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            maxLines = 4
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(
                                if (inputText.isNotBlank() && !isGenerating) WeChatGreenChat
                                else Color(0xFFB2EBD0)
                            )
                            .clickable(enabled = inputText.isNotBlank() && !isGenerating) {
                                onSend()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "发送",
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }
}

/** IM 用户气泡（右侧，微信绿风格 + 玩家头像） */
@Composable
private fun ImUserBubble(
    content: String,
    avatar: android.graphics.Bitmap? = null,
    privacyMode: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.Top
    ) {
        Surface(
            color = Color(0xFF95EC69),
            contentColor = Color(0xFF1B1B1B),
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 4.dp),
            modifier = Modifier.fillMaxWidth(0.72f)
        ) {
            Text(
                text = content,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = LocalChatFontSize.current
                ),
                modifier = Modifier.padding(12.dp)
            )
        }
        Spacer(Modifier.width(8.dp))
        ImAvatar(avatar = avatar, privacyMode = privacyMode)
    }
}

/** IM 角色气泡（左侧，白色 + 角色头像） */
@Composable
private fun ImCharacterBubble(
    content: String,
    avatar: android.graphics.Bitmap? = null,
    privacyMode: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        ImAvatar(avatar = avatar, privacyMode = privacyMode)
        Spacer(Modifier.width(8.dp))
        Surface(
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 16.dp),
            shadowElevation = 1.dp,
            modifier = Modifier.fillMaxWidth(0.72f)
        ) {
            Text(
                text = content,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = LocalChatFontSize.current
                ),
                modifier = Modifier.padding(12.dp)
            )
        }
    }
}

/** IM 气泡头像（微信风圆角方形） */
@Composable
private fun ImAvatar(
    avatar: android.graphics.Bitmap?,
    privacyMode: Boolean
) {
    if (avatar != null) {
        Image(
            bitmap = avatar.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .privacyBlur(privacyMode)
        )
    } else {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.size(40.dp)
        ) {}
    }
}
