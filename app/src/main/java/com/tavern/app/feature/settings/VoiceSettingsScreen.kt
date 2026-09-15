package com.tavern.app.feature.settings

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import com.tavern.app.core.model.AsrConfig
import com.tavern.app.core.model.VoiceConfig
import com.tavern.app.core.model.VoiceProviderType
import kotlin.math.roundToInt

/**
 * 语音配置设置页
 *
 * 配置 TTS 服务（系统 TTS / ElevenLabs / Azure / OpenAI）、
 * 网络 ASR（Whisper 兼容）与自动朗读开关。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun VoiceSettingsScreen(
    onBack: () -> Unit,
    viewModel: VoiceSettingsViewModel = hiltViewModel()
) {
    val configs by viewModel.configs.collectAsState()
    val asrConfigs by viewModel.asrConfigs.collectAsState()
    val autoSpeak by viewModel.autoSpeak.collectAsState()
    val message by viewModel.message.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var editingConfig by remember { mutableStateOf<VoiceConfig?>(null) }
    var showEditDialog by remember { mutableStateOf(false) }
    var configToDelete by remember { mutableStateOf<VoiceConfig?>(null) }

    var editingAsr by remember { mutableStateOf<AsrConfig?>(null) }
    var showAsrDialog by remember { mutableStateOf(false) }
    var asrToDelete by remember { mutableStateOf<AsrConfig?>(null) }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = WeChatSettingsBg,
        topBar = {
            TopAppBar(
                title = { Text("语音") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        editingConfig = null
                        showEditDialog = true
                    }) {
                        Icon(Icons.Filled.Add, contentDescription = "添加语音配置")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // ===== 自动朗读 =====
            WeChatGroup {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("自动朗读 AI 回复", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "每次生成回复后自动播放语音",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = autoSpeak, onCheckedChange = { viewModel.setAutoSpeak(it) })
                }
            }

            // ===== TTS 配置 =====
            Text(
                "语音合成（TTS）",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp)
            )

            if (configs.isEmpty()) {
                WeChatGroup {
                    Text(
                        "点击右上角 + 添加语音服务（系统 TTS / ElevenLabs / Azure / OpenAI）",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            } else {
                WeChatGroup {
                    configs.forEachIndexed { index, config ->
                        VoiceConfigRow(
                            config = config,
                            onClick = {
                                editingConfig = config
                                showEditDialog = true
                            },
                            onSetDefault = { viewModel.setDefault(config.id) },
                            onDelete = { configToDelete = config }
                        )
                        if (index < configs.lastIndex) WeChatRowDivider()
                    }
                }
            }

            // ===== ASR 配置 =====
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "语音识别（ASR）",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f).padding(start = 4.dp)
                )
                IconButton(onClick = {
                    editingAsr = null
                    showAsrDialog = true
                }) {
                    Icon(Icons.Filled.Add, contentDescription = "添加 ASR 配置")
                }
            }
            Text(
                "OpenAI 兼容 Whisper 格式，未配置时回退系统识别",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp)
            )

            if (asrConfigs.isEmpty()) {
                WeChatGroup {
                    Text(
                        "未配置网络语音识别",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            } else {
                WeChatGroup {
                    asrConfigs.forEachIndexed { index, config ->
                        AsrConfigRow(
                            config = config,
                            onClick = {
                                editingAsr = config
                                showAsrDialog = true
                            },
                            onSetDefault = { viewModel.setDefaultAsr(config.id) },
                            onDelete = { asrToDelete = config }
                        )
                        if (index < asrConfigs.lastIndex) WeChatRowDivider()
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    if (showEditDialog) {
        VoiceEditDialog(
            initial = editingConfig,
            onDismiss = { showEditDialog = false },
            onSave = { config ->
                viewModel.save(config)
                showEditDialog = false
            },
            onTest = { config -> viewModel.test(config, "你好，这是一段语音测试。") }
        )
    }

    configToDelete?.let { config ->
        AlertDialog(
            onDismissRequest = { configToDelete = null },
            title = { Text("删除语音配置") },
            text = { Text("确定删除「${config.name.ifBlank { "未命名" }}」吗？此操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(config.id)
                    configToDelete = null
                }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { configToDelete = null }) { Text("取消") }
            }
        )
    }

    if (showAsrDialog) {
        AsrEditDialog(
            initial = editingAsr,
            onDismiss = { showAsrDialog = false },
            onSave = { config ->
                viewModel.saveAsr(config)
                showAsrDialog = false
            }
        )
    }

    asrToDelete?.let { config ->
        AlertDialog(
            onDismissRequest = { asrToDelete = null },
            title = { Text("删除识别配置") },
            text = { Text("确定删除「${config.name.ifBlank { "未命名" }}」吗？") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteAsr(config.id)
                    asrToDelete = null
                }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { asrToDelete = null }) { Text("取消") }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AsrConfigRow(
    config: AsrConfig,
    onClick: () -> Unit,
    onSetDefault: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onDelete)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = config.name.ifBlank { "未命名" },
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (config.isDefault) {
                    Spacer(Modifier.width(6.dp))
                    Text("默认", style = MaterialTheme.typography.labelSmall, color = Color(0xFF07C160))
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = "${config.modelName} · ${config.baseUrl}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onSetDefault) {
            Icon(
                imageVector = if (config.isDefault) Icons.Filled.Star else Icons.Outlined.StarBorder,
                contentDescription = "设为默认",
                tint = if (config.isDefault) Color(0xFFFFC300)
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * ASR 配置编辑对话框
 */
@Composable
private fun AsrEditDialog(
    initial: AsrConfig?,
    onDismiss: () -> Unit,
    onSave: (AsrConfig) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var baseUrl by remember { mutableStateOf(initial?.baseUrl ?: "") }
    var apiKey by remember { mutableStateOf(initial?.apiKey ?: "") }
    var modelName by remember { mutableStateOf(initial?.modelName ?: "whisper-1") }
    var language by remember { mutableStateOf(initial?.language ?: "zh") }

    WeChatEditSheet(
        title = if (initial == null) "添加语音识别配置" else "编辑语音识别配置",
        onDismiss = onDismiss
    ) {
                Text(
                    "OpenAI 兼容格式：POST {地址}/v1/audio/transcriptions",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("配置名称") },
                    placeholder = { Text("如：MiMo 语音识别") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("API 端点") },
                    placeholder = { Text("https://api.openai.com/v1") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API Key") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = modelName,
                    onValueChange = { modelName = it },
                    label = { Text("模型名") },
                    placeholder = { Text("whisper-1") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = language,
                    onValueChange = { language = it },
                    label = { Text("语言（zh / en，留空自动）") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("取消") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onSave(
                                AsrConfig(
                                    id = initial?.id ?: 0,
                                    name = name,
                                    baseUrl = baseUrl,
                                    apiKey = apiKey,
                                    modelName = modelName.ifBlank { "whisper-1" },
                                    language = language,
                                    isDefault = initial?.isDefault ?: false
                                )
                            )
                        },
                        enabled = baseUrl.isNotBlank() && apiKey.isNotBlank()
                    ) { Text("保存") }
                }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VoiceConfigRow(
    config: VoiceConfig,
    onClick: () -> Unit,
    onSetDefault: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onDelete)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = config.name.ifBlank { "未命名" },
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (config.isDefault) {
                    Spacer(Modifier.width(6.dp))
                    Text("默认", style = MaterialTheme.typography.labelSmall, color = Color(0xFF07C160))
                }
            }
            Spacer(Modifier.height(2.dp))
            val voiceIdPart = if (config.providerType != VoiceProviderType.SYSTEM_TTS &&
                config.voiceId.isNotBlank()
            ) " · ${config.voiceId}" else ""
            Text(
                text = "${config.providerType.displayName()}$voiceIdPart · 语速 ${config.speed}x",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onSetDefault) {
            Icon(
                imageVector = if (config.isDefault) Icons.Filled.Star else Icons.Outlined.StarBorder,
                contentDescription = "设为默认",
                tint = if (config.isDefault) Color(0xFFFFC300)
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 语音配置编辑/新增对话框
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VoiceEditDialog(
    initial: VoiceConfig?,
    onDismiss: () -> Unit,
    onSave: (VoiceConfig) -> Unit,
    onTest: (VoiceConfig) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var providerType by remember { mutableStateOf(initial?.providerType ?: VoiceProviderType.SYSTEM_TTS) }
    var baseUrl by remember { mutableStateOf(initial?.baseUrl ?: "") }
    var apiKey by remember { mutableStateOf(initial?.apiKey ?: "") }
    var voiceId by remember { mutableStateOf(initial?.voiceId ?: "") }
    var speed by remember { mutableStateOf(initial?.speed ?: 1.0f) }
    var pitch by remember { mutableStateOf(initial?.pitch ?: 1.0f) }

    val isNetwork = providerType != VoiceProviderType.SYSTEM_TTS

    WeChatEditSheet(
        title = if (initial == null) "添加语音配置" else "编辑语音配置",
        onDismiss = onDismiss
    ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("配置名称") },
                    placeholder = { Text("如：默认女声") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(Modifier.height(12.dp))

                Text("服务类型", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(4.dp))
                VoiceProviderType.entries.forEach { type ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(onClick = { providerType = type })
                    ) {
                        RadioButton(
                            selected = providerType == type,
                            onClick = { providerType = type }
                        )
                        Text(type.displayName(), style = MaterialTheme.typography.bodyMedium)
                    }
                }

                Spacer(Modifier.height(8.dp))

                if (isNetwork) {
                    OutlinedTextField(
                        value = baseUrl,
                        onValueChange = { baseUrl = it },
                        label = { Text("API 端点") },
                        placeholder = { Text(providerType.defaultBaseUrl()) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        label = { Text("API Key") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = voiceId,
                        onValueChange = { voiceId = it },
                        label = { Text(providerType.voiceIdLabel()) },
                        placeholder = { Text(providerType.voiceIdPlaceholder()) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                } else {
                    // 系统 TTS 无需网络配置
                    Text(
                        text = "系统 TTS 使用设备本地语音引擎，无需额外配置。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(16.dp))

                Text(
                    text = "语速：${(speed * 10).roundToInt() / 10f}x",
                    style = MaterialTheme.typography.labelLarge
                )
                Slider(
                    value = speed,
                    onValueChange = { speed = it },
                    valueRange = 0.5f..2.0f
                )

                Text(
                    text = "音调：${(pitch * 10).roundToInt() / 10f}x",
                    style = MaterialTheme.typography.labelLarge
                )
                Slider(
                    value = pitch,
                    onValueChange = { pitch = it },
                    valueRange = 0.5f..2.0f
                )

                Spacer(Modifier.height(16.dp))

                // 试听当前配置（无需先保存）
                OutlinedButton(
                    onClick = {
                        onTest(
                            VoiceConfig(
                                id = initial?.id ?: 0,
                                name = name,
                                providerType = providerType,
                                baseUrl = baseUrl,
                                apiKey = apiKey,
                                voiceId = voiceId,
                                speed = speed,
                                pitch = pitch,
                                isDefault = initial?.isDefault ?: false
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("试听")
                }

                Spacer(Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("取消") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onSave(
                                VoiceConfig(
                                    id = initial?.id ?: 0,
                                    name = name,
                                    providerType = providerType,
                                    baseUrl = baseUrl,
                                    apiKey = apiKey,
                                    voiceId = voiceId,
                                    speed = speed,
                                    pitch = pitch,
                                    isDefault = initial?.isDefault ?: false
                                )
                            )
                        }
                    ) { Text("保存") }
                }
    }
}

/**
 * 语音服务类型显示名
 */
fun VoiceProviderType.displayName(): String = when (this) {
    VoiceProviderType.SYSTEM_TTS -> "系统 TTS"
    VoiceProviderType.ELEVENLABS -> "ElevenLabs"
    VoiceProviderType.AZURE -> "Azure TTS"
    VoiceProviderType.OPENAI_TTS -> "OpenAI TTS"
}

private fun VoiceProviderType.defaultBaseUrl(): String = when (this) {
    VoiceProviderType.ELEVENLABS -> "https://api.elevenlabs.io"
    VoiceProviderType.AZURE -> "https://{region}.tts.speech.microsoft.com/cognitiveservices/v1"
    VoiceProviderType.OPENAI_TTS -> "https://api.openai.com/v1"
    VoiceProviderType.SYSTEM_TTS -> ""
}

private fun VoiceProviderType.voiceIdLabel(): String = when (this) {
    VoiceProviderType.ELEVENLABS -> "Voice ID"
    VoiceProviderType.AZURE -> "音色名称"
    VoiceProviderType.OPENAI_TTS -> "模型名称"
    VoiceProviderType.SYSTEM_TTS -> ""
}

private fun VoiceProviderType.voiceIdPlaceholder(): String = when (this) {
    VoiceProviderType.ELEVENLABS -> "21m00Tcm4TlvDq8ikWAM"
    VoiceProviderType.AZURE -> "zh-CN-XiaoxiaoNeural"
    VoiceProviderType.OPENAI_TTS -> "tts-1"
    VoiceProviderType.SYSTEM_TTS -> ""
}
