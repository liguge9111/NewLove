package com.tavern.app.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.tavern.app.ui.privacyBlur
import java.io.File

/**
 * 设置主页（独立路由版，供聊天页深链）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsHomeScreen(
    onBack: () -> Unit,
    onOpenModelSettings: () -> Unit,
    onOpenImageSettings: () -> Unit,
    onOpenPluginSettings: () -> Unit,
    onOpenVoiceSettings: () -> Unit,
    viewModel: SettingsHomeViewModel = hiltViewModel()
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("我") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        SettingsContent(
            modifier = Modifier.padding(padding),
            viewModel = viewModel,
            onOpenModelSettings = onOpenModelSettings,
            onOpenImageSettings = onOpenImageSettings,
            onOpenPluginSettings = onOpenPluginSettings,
            onOpenVoiceSettings = onOpenVoiceSettings
        )
    }
}

/**
 * 设置内容（微信「我」页样式，可嵌入首页 Tab）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsContent(
    modifier: Modifier = Modifier,
    viewModel: SettingsHomeViewModel = hiltViewModel(),
    onOpenModelSettings: () -> Unit,
    onOpenImageSettings: () -> Unit,
    onOpenPluginSettings: () -> Unit,
    onOpenVoiceSettings: () -> Unit
) {
    val strictStatus by viewModel.strictStatusFormat.collectAsState()
    val privacyMode by viewModel.privacyMode.collectAsState()
    val chatEnhance by viewModel.chatEnhance.collectAsState()
    val playerName by viewModel.playerName.collectAsState()
    val playerAvatarPath by viewModel.playerAvatarPath.collectAsState()
    val chatFontSize by viewModel.chatFontSize.collectAsState()
    val darkMode by viewModel.darkMode.collectAsState()
    val avatarError by viewModel.avatarError.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var nameInput by remember(playerName) { mutableStateOf(playerName) }
    var showNameDialog by remember { mutableStateOf(false) }
    var showDarkSheet by remember { mutableStateOf(false) }
    var showFontSheet by remember { mutableStateOf(false) }
    val avatarLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { viewModel.setPlayerAvatar(it) } }

    LaunchedEffect(avatarError) {
        avatarError?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearAvatarError()
        }
    }

    // 微信灰底
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF2F2F2))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // ===== 玩家信息（微信「我」头部） =====
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showNameDialog = true }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .clickable { avatarLauncher.launch("image/*") },
                        contentAlignment = Alignment.Center
                    ) {
                        if (playerAvatarPath.isNotBlank() && File(playerAvatarPath).exists()) {
                            AsyncImage(
                                model = File(playerAvatarPath),
                                contentDescription = "玩家头像",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .privacyBlur(privacyMode)
                            )
                        } else {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text("头像", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = playerName,
                            style = MaterialTheme.typography.titleLarge
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "点击修改昵称 · 点头像换头像",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ===== 显示 =====
            WeChatSectionLabel("显示")
            WeChatGroup(modifier = Modifier.padding(horizontal = 10.dp)) {
                WeChatValueRow(
                    title = "深色模式",
                    value = if (darkMode == "system") "跟随系统" else "默认（浅色）"
                ) { showDarkSheet = true }
                WeChatRowDivider()
                WeChatValueRow(
                    title = "聊天字体",
                    value = "${chatFontSize}sp"
                ) { showFontSheet = true }
            }

            // ===== 聊天 =====
            WeChatSectionLabel("聊天")
            WeChatGroup(modifier = Modifier.padding(horizontal = 10.dp)) {
                SwitchRow("聊天增强", "IM 纯聊天 + 剧情同步", chatEnhance) {
                    viewModel.setChatEnhance(it)
                }
                WeChatRowDivider()
                SwitchRow("自动朗读", "收到回复后自动播放语音", viewModel.autoSpeak.collectAsState().value) {
                    viewModel.setAutoSpeak(it)
                }
                WeChatRowDivider()
                SwitchRow("隐私模式", "全部图片模糊显示", privacyMode) {
                    viewModel.setPrivacyMode(it)
                }
                WeChatRowDivider()
                SwitchRow("状态栏格式校验", "未按格式输出时自动重试", strictStatus) {
                    viewModel.setStrictStatusFormat(it)
                }
            }

            // ===== 服务 =====
            WeChatSectionLabel("服务")
            WeChatGroup(modifier = Modifier.padding(horizontal = 10.dp)) {
                SettingsEntry(
                    icon = { Icon(Icons.Filled.SmartToy, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    title = "大模型",
                    subtitle = "OpenAI / Claude / Ollama / DeepSeek"
                ) { onOpenModelSettings() }
                WeChatRowDivider()
                SettingsEntry(
                    icon = { Icon(Icons.Filled.Image, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary) },
                    title = "生图",
                    subtitle = "SD WebUI / NovelAI / DALL-E"
                ) { onOpenImageSettings() }
                WeChatRowDivider()
                    SettingsEntry(
                        icon = { Icon(Icons.Filled.RecordVoiceOver, contentDescription = null, tint = MaterialTheme.colorScheme.secondary) },
                        title = "语音",
                        subtitle = "TTS / ASR 服务配置"
                    ) { onOpenVoiceSettings() }
                    HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
                    SettingsEntry(
                        icon = { Icon(Icons.Filled.Extension, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                        title = "插件",
                        subtitle = "规则引擎与扩展管理"
                    ) { onOpenPluginSettings() }
            }

            Spacer(Modifier.height(24.dp))
        }

        // snackbar
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }

    // 修改昵称对话框
    if (showNameDialog) {
        AlertDialog(
            onDismissRequest = { showNameDialog = false },
            title = { Text("修改昵称") },
            text = {
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setPlayerName(nameInput)
                    showNameDialog = false
                }) { Text("确定", color = MaterialTheme.colorScheme.primary) }
            },
            dismissButton = {
                TextButton(onClick = { showNameDialog = false }) { Text("取消") }
            }
        )
    }

    // 深色模式底部选择
    if (showDarkSheet) {
        ModalBottomSheet(onDismissRequest = { showDarkSheet = false }) {
            Text(
                "深色模式",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        viewModel.setDarkMode("light")
                        showDarkSheet = false
                    }
                    .padding(horizontal = 24.dp, vertical = 14.dp)
            ) {
                RadioButton(selected = darkMode == "light", onClick = {
                    viewModel.setDarkMode("light")
                    showDarkSheet = false
                })
                Text("默认（浅色）", style = MaterialTheme.typography.bodyLarge)
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        viewModel.setDarkMode("system")
                        showDarkSheet = false
                    }
                    .padding(horizontal = 24.dp, vertical = 14.dp)
            ) {
                RadioButton(selected = darkMode == "system", onClick = {
                    viewModel.setDarkMode("system")
                    showDarkSheet = false
                })
                Text("跟随系统", style = MaterialTheme.typography.bodyLarge)
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    // 字体大小底部调节
    if (showFontSheet) {
        ModalBottomSheet(onDismissRequest = { showFontSheet = false }) {
            Text(
                "聊天字体大小",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
            Text(
                "她抬起头，微微一笑。",
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = chatFontSize.sp),
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            Row(
                modifier = Modifier.padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("小", style = MaterialTheme.typography.labelMedium)
                Slider(
                    value = chatFontSize.toFloat(),
                    onValueChange = { viewModel.setChatFontSize(it.toInt()) },
                    valueRange = 14f..24f,
                    steps = 9,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                )
                Text("大", style = MaterialTheme.typography.labelMedium)
            }
            Text(
                "${chatFontSize}sp",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** 微信式「标题 + 右侧值 + 箭头」行 */
@Composable
private fun WeChatValueRow(
    title: String,
    value: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(4.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingsEntry(
    icon: (@Composable () -> Unit)? = null,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier.size(28.dp),
                contentAlignment = Alignment.Center
            ) { icon() }
            Spacer(Modifier.width(12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
