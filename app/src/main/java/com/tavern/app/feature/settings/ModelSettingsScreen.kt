package com.tavern.app.feature.settings

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import com.tavern.app.core.model.ModelConfig
import com.tavern.app.core.model.ModelProviderType

/**
 * 模型配置设置页
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ModelSettingsScreen(
    onBack: () -> Unit,
    viewModel: ModelSettingsViewModel = hiltViewModel()
) {
    val models by viewModel.models.collectAsState()
    val message by viewModel.message.collectAsState()
    val testingId by viewModel.testingId.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var editingModel by remember { mutableStateOf<ModelConfig?>(null) }
    var showEditDialog by remember { mutableStateOf(false) }
    var modelToDelete by remember { mutableStateOf<ModelConfig?>(null) }

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
                title = { Text("大模型") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        editingModel = null
                        showEditDialog = true
                    }) {
                        Icon(Icons.Filled.Add, contentDescription = "添加模型")
                    }
                }
            )
        }
    ) { padding ->
        if (models.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("暂无模型配置", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "点击右上角 + 添加模型，配置后即可在聊天中切换",
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
                    models.forEachIndexed { index, model ->
                        ModelRow(
                            model = model,
                            isTesting = testingId == model.id,
                            onClick = {
                                editingModel = model
                                showEditDialog = true
                            },
                            onTest = { viewModel.test(model) },
                            onSetDefault = { viewModel.setDefault(model.id) },
                            onDelete = { modelToDelete = model }
                        )
                        if (index < models.lastIndex) WeChatRowDivider()
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    // 编辑/新增对话框
    if (showEditDialog) {
        ModelEditDialog(
            initial = editingModel,
            onDismiss = { showEditDialog = false },
            onSave = { config ->
                viewModel.save(config)
                showEditDialog = false
            }
        )
    }

    // 删除确认对话框
    modelToDelete?.let { model ->
        AlertDialog(
            onDismissRequest = { modelToDelete = null },
            title = { Text("删除模型") },
            text = { Text("确定删除「${model.name.ifBlank { "未命名" }}」吗？此操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(model.id)
                    modelToDelete = null
                }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { modelToDelete = null }) { Text("取消") }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ModelRow(
    model: ModelConfig,
    isTesting: Boolean = false,
    onClick: () -> Unit,
    onTest: () -> Unit = {},
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
                    text = model.name.ifBlank { "未命名" },
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (model.isDefault) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "默认",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF07C160)
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = "${model.providerType.displayName()} · ${model.modelName.ifBlank { "未填模型名" }}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // 连接测试
        TextButton(onClick = onTest, enabled = !isTesting) {
            if (isTesting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Text("测试", style = MaterialTheme.typography.labelMedium, color = Color(0xFF07C160))
            }
        }
        // 设为默认
        IconButton(onClick = onSetDefault) {
            Icon(
                imageVector = if (model.isDefault) Icons.Filled.Star else Icons.Outlined.StarBorder,
                contentDescription = "设为默认",
                tint = if (model.isDefault) Color(0xFFFFC300)
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 模型编辑/新增对话框
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ModelEditDialog(
    initial: ModelConfig?,
    onDismiss: () -> Unit,
    onSave: (ModelConfig) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var providerType by remember { mutableStateOf(initial?.providerType ?: ModelProviderType.OPENAI_COMPATIBLE) }
    var baseUrl by remember { mutableStateOf(initial?.baseUrl ?: "") }
    var apiKey by remember { mutableStateOf(initial?.apiKey ?: "") }
    var modelName by remember { mutableStateOf(initial?.modelName ?: "") }
    var contextTokens by remember { mutableStateOf(tokensToKDisplay(initial?.maxContextTokens ?: 4096)) }
    var temperature by remember { mutableStateOf((initial?.temperature ?: 0.7f).toString()) }
    var topP by remember { mutableStateOf((initial?.topP ?: 1.0f).toString()) }
    var maxTokens by remember { mutableStateOf(tokensToKDisplay(initial?.maxTokens ?: 512)) }
    var streamEnabled by remember { mutableStateOf(initial?.streamEnabled ?: true) }

    WeChatEditSheet(
        title = if (initial == null) "添加模型" else "编辑模型",
        onDismiss = onDismiss
    ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("配置名称") },
                    placeholder = { Text("如：GPT-4o 主力") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(Modifier.height(12.dp))

                // 服务类型选择
                Text("服务类型", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(4.dp))
                ModelProviderType.entries.forEach { type ->
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

                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("API 端点地址") },
                    placeholder = { Text("https://api.openai.com/v1") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API Key") },
                    placeholder = { Text("sk-...") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = modelName,
                    onValueChange = { modelName = it },
                    label = { Text("模型名称") },
                    placeholder = { Text("gpt-4o / claude-3-5-sonnet / llama3") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(Modifier.height(12.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = contextTokens,
                        onValueChange = { contextTokens = it },
                        label = { Text("上下文长度（K）") },
                        placeholder = { Text("32 = 32K") },
                        supportingText = { Text("单位 K，1K = 1024 tokens") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = maxTokens,
                        onValueChange = { maxTokens = it },
                        label = { Text("最大生成（K）") },
                        placeholder = { Text("4 = 4K") },
                        supportingText = { Text("支持小数，0.5 = 512") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                Spacer(Modifier.height(12.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = temperature,
                        onValueChange = { temperature = it },
                        label = { Text("温度") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = topP,
                        onValueChange = { topP = it },
                        label = { Text("Top P") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                Spacer(Modifier.height(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("流式输出", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.weight(1f))
                    Switch(checked = streamEnabled, onCheckedChange = { streamEnabled = it })
                }

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
                                ModelConfig(
                                    id = initial?.id ?: 0,
                                    name = name,
                                    providerType = providerType,
                                    baseUrl = baseUrl,
                                    apiKey = apiKey,
                                    modelName = modelName,
                                    maxContextTokens = kToTokens(contextTokens, default = 4096),
                                    temperature = temperature.toFloatOrNull() ?: 0.7f,
                                    topP = topP.toFloatOrNull() ?: 1.0f,
                                    maxTokens = kToTokens(maxTokens, default = 512),
                                    streamEnabled = streamEnabled,
                                    isDefault = initial?.isDefault ?: false
                                )
                            )
                        }
                    ) { Text("保存") }
                }
    }
}

/** tokens → K 显示（整数去尾零，支持 0.5 这类小数） */
private fun tokensToKDisplay(tokens: Int): String {
    val k = tokens / 1024f
    return if (k == k.toInt().toFloat()) k.toInt().toString()
    else ((k * 10).toInt() / 10f).toString()
}

/** K 输入 → tokens（支持小数；非法输入回退默认值） */
private fun kToTokens(k: String, default: Int): Int {
    val value = k.trim().toDoubleOrNull() ?: return default
    return (value * 1024).toInt().coerceIn(256, 2_097_152)
}

/**
 * 服务类型显示名
 */
fun ModelProviderType.displayName(): String = when (this) {
    ModelProviderType.OPENAI_COMPATIBLE -> "OpenAI 兼容"
    ModelProviderType.CLAUDE -> "Claude"
    ModelProviderType.OLLAMA -> "Ollama"
}
