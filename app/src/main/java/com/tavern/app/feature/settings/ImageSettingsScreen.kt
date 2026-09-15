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
import com.tavern.app.core.model.ImageConfig
import com.tavern.app.core.model.ImageProviderType

/**
 * 生图配置设置页
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ImageSettingsScreen(
    onBack: () -> Unit,
    viewModel: ImageSettingsViewModel = hiltViewModel()
) {
    val configs by viewModel.configs.collectAsState()
    var editingConfig by remember { mutableStateOf<ImageConfig?>(null) }
    var showEditDialog by remember { mutableStateOf(false) }
    var configToDelete by remember { mutableStateOf<ImageConfig?>(null) }

    Scaffold(
        containerColor = WeChatSettingsBg,
        topBar = {
            TopAppBar(
                title = { Text("生图") },
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
                        Icon(Icons.Filled.Add, contentDescription = "添加生图配置")
                    }
                }
            )
        }
    ) { padding ->
        if (configs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("暂无生图配置", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "点击右上角 + 添加生图服务（SD WebUI / NovelAI / DALL-E / ComfyUI）",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                WeChatGroup {
                    configs.forEachIndexed { index, config ->
                        ImageConfigRow(
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
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (showEditDialog) {
        ImageEditDialog(
            initial = editingConfig,
            onDismiss = { showEditDialog = false },
            onSave = { config ->
                viewModel.save(config)
                showEditDialog = false
            }
        )
    }

    configToDelete?.let { config ->
        AlertDialog(
            onDismissRequest = { configToDelete = null },
            title = { Text("删除生图配置") },
            text = { Text("确定删除「${config.name.ifBlank { "未命名" }}」吗？") },
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
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ImageConfigRow(
    config: ImageConfig,
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
                text = "${config.providerType.displayName()} · ${config.width}×${config.height}",
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
 * 生图配置编辑/新增对话框
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ImageEditDialog(
    initial: ImageConfig?,
    onDismiss: () -> Unit,
    onSave: (ImageConfig) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var providerType by remember { mutableStateOf(initial?.providerType ?: ImageProviderType.SD_WEBUI) }
    var baseUrl by remember { mutableStateOf(initial?.baseUrl ?: "") }
    var apiKey by remember { mutableStateOf(initial?.apiKey ?: "") }
    var defaultPrompt by remember { mutableStateOf(initial?.defaultPrompt ?: "") }
    var defaultNegativePrompt by remember { mutableStateOf(initial?.defaultNegativePrompt ?: "") }
    var sampler by remember { mutableStateOf(initial?.sampler ?: "Euler a") }
    var steps by remember { mutableStateOf((initial?.steps ?: 20).toString()) }
    var cfgScale by remember { mutableStateOf((initial?.cfgScale ?: 7.0f).toString()) }
    var width by remember { mutableStateOf((initial?.width ?: 512).toString()) }
    var height by remember { mutableStateOf((initial?.height ?: 512).toString()) }

    WeChatEditSheet(
        title = if (initial == null) "添加生图配置" else "编辑生图配置",
        onDismiss = onDismiss
    ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("配置名称") },
                    placeholder = { Text("如：本地 SD") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(Modifier.height(12.dp))

                Text("服务类型", style = MaterialTheme.typography.labelLarge)
                ImageProviderType.entries.forEach { type ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        RadioButton(
                            selected = providerType == type,
                            onClick = { providerType = type }
                        )
                        Text(type.displayName(), style = MaterialTheme.typography.bodyMedium)
                    }
                }

                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("API 端点") },
                    placeholder = { Text("http://127.0.0.1:7860") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API Key（可选）") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = defaultPrompt,
                    onValueChange = { defaultPrompt = it },
                    label = { Text("默认正向提示词") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = defaultNegativePrompt,
                    onValueChange = { defaultNegativePrompt = it },
                    label = { Text("默认负面提示词") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )

                Spacer(Modifier.height(12.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = width,
                        onValueChange = { width = it },
                        label = { Text("宽度") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = height,
                        onValueChange = { height = it },
                        label = { Text("高度") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                Spacer(Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = steps,
                        onValueChange = { steps = it },
                        label = { Text("步数") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = cfgScale,
                        onValueChange = { cfgScale = it },
                        label = { Text("CFG Scale") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = sampler,
                    onValueChange = { sampler = it },
                    label = { Text("采样器") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("取消") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onSave(
                                ImageConfig(
                                    id = initial?.id ?: 0,
                                    name = name,
                                    providerType = providerType,
                                    baseUrl = baseUrl,
                                    apiKey = apiKey,
                                    defaultPrompt = defaultPrompt,
                                    defaultNegativePrompt = defaultNegativePrompt,
                                    sampler = sampler.ifBlank { "Euler a" },
                                    steps = steps.toIntOrNull() ?: 20,
                                    cfgScale = cfgScale.toFloatOrNull() ?: 7.0f,
                                    width = width.toIntOrNull() ?: 512,
                                    height = height.toIntOrNull() ?: 512,
                                    isDefault = initial?.isDefault ?: false
                                )
                            )
                        }
                    ) { Text("保存") }
                }
    }
}

/**
 * 生图服务类型显示名
 */
fun ImageProviderType.displayName(): String = when (this) {
    ImageProviderType.SD_WEBUI -> "SD WebUI"
    ImageProviderType.NOVELAI -> "NovelAI"
    ImageProviderType.DALL_E -> "DALL-E"
    ImageProviderType.COMFY_UI -> "ComfyUI"
}
