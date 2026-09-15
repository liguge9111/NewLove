package com.tavern.app.feature.plugin

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import com.tavern.app.core.model.Plugin
import com.tavern.app.core.model.PluginAction
import com.tavern.app.core.model.PluginRule
import com.tavern.app.core.model.PluginTrigger
import com.tavern.app.feature.settings.WeChatGroup
import com.tavern.app.feature.settings.WeChatRowDivider
import com.tavern.app.feature.settings.WeChatSectionLabel
import com.tavern.app.feature.settings.WeChatSettingsBg
import com.tavern.app.feature.settings.WeChatEditSheet

/**
 * 插件管理页
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PluginScreen(
    onBack: () -> Unit,
    viewModel: PluginViewModel = hiltViewModel()
) {
    val plugins by viewModel.plugins.collectAsState()
    val labEnabled by viewModel.labEnabled.collectAsState()
    val importState by viewModel.importState.collectAsState()
    var editingPlugin by remember { mutableStateOf<Plugin?>(null) }
    var showEditDialog by remember { mutableStateOf(false) }
    var pluginToDelete by remember { mutableStateOf<Plugin?>(null) }
    var showImportSheet by remember { mutableStateOf(false) }
    var importName by remember { mutableStateOf("") }
    var importUrl by remember { mutableStateOf("") }
    var importSource by remember { mutableStateOf("") }

    Scaffold(
        containerColor = WeChatSettingsBg,
        topBar = {
            TopAppBar(
                title = { Text("插件") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        editingPlugin = null
                        showEditDialog = true
                    }) {
                        Icon(Icons.Filled.Add, contentDescription = "添加插件")
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
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            // ===== 实验室（插件导入向导，默认关闭） =====
            WeChatSectionLabel("实验室")
            WeChatGroup {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("插件导入向导", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "开启后可从 URL/源码导入规则类插件；导入角色卡时自动转换内置脚本。默认关闭。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = labEnabled, onCheckedChange = { viewModel.setLabEnabled(it) })
                }
                if (labEnabled) {
                    WeChatRowDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showImportSheet = true }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "从 URL / 源码导入插件",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            if (plugins.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Spacer(Modifier.height(32.dp))
                        Text("暂无插件", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "点击右上角 + 创建插件，可配置文本替换、前后缀注入、状态变化等规则",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 32.dp)
                        )
                    }
                }
            } else {
                WeChatGroup {
                    plugins.forEachIndexed { index, plugin ->
                        PluginRow(
                            plugin = plugin,
                            onClick = {
                                editingPlugin = plugin
                                showEditDialog = true
                            },
                            onToggle = { viewModel.setEnabled(plugin.id, it) },
                            onDelete = { pluginToDelete = plugin }
                        )
                        if (index < plugins.lastIndex) WeChatRowDivider()
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    // 编辑/新增对话框
    if (showEditDialog) {
        PluginEditDialog(
            initial = editingPlugin,
            onDismiss = { showEditDialog = false },
            onSave = { plugin ->
                viewModel.save(plugin)
                showEditDialog = false
            }
        )
    }

    // 删除确认
    pluginToDelete?.let { plugin ->
        AlertDialog(
            onDismissRequest = { pluginToDelete = null },
            title = { Text("删除插件") },
            text = { Text("确定删除「${plugin.name}」吗？") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(plugin.id)
                    pluginToDelete = null
                }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pluginToDelete = null }) { Text("取消") }
            }
        )
    }

    // ===== 导入向导：输入表单 =====
    if (showImportSheet) {
        WeChatEditSheet(
            title = "导入插件",
            onDismiss = {
                showImportSheet = false
                viewModel.resetImport()
            }
        ) {
            when (val st = importState) {
                is PluginViewModel.ImportState.Loading -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 24.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(st.step)
                    }
                }
                is PluginViewModel.ImportState.Error -> {
                    Text(
                        st.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = { viewModel.resetImport() },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("重新输入") }
                }
                is PluginViewModel.ImportState.DraftReady -> {
                    // 草稿预览
                    Text("转换草稿预览", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        st.draft.name,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        st.draft.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    st.draft.warnings.forEach {
                        Text(
                            "⚠ $it",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    st.draft.rules.forEachIndexed { i, rule ->
                        Text(
                            "#${i + 1} [${rule.trigger}] ${rule.action} · ${rule.pattern.take(60)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = {
                            viewModel.saveDraft(st.draft)
                            showImportSheet = false
                            importName = ""
                            importUrl = ""
                            importSource = ""
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("保存为插件（全局生效）") }
                    Spacer(Modifier.height(8.dp))
                    TextButton(
                        onClick = { viewModel.resetImport() },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("取消") }
                }
                else -> {
                    OutlinedTextField(
                        value = importName,
                        onValueChange = { importName = it },
                        label = { Text("插件名称（可选）") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = importUrl,
                        onValueChange = { importUrl = it },
                        label = { Text("插件下载地址 URL") },
                        placeholder = { Text("https://github.com/.../plugin.js") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "或直接粘贴源码（二选一，URL 优先）",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    OutlinedTextField(
                        value = importSource,
                        onValueChange = { importSource = it },
                        label = { Text("插件源码") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = {
                            if (importUrl.isNotBlank()) {
                                viewModel.importFromUrl(importUrl, importName)
                            } else {
                                viewModel.importFromSource(importSource, importName)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = importUrl.isNotBlank() || importSource.isNotBlank()
                    ) { Text("解析并转换") }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "将调用默认大模型转换，消耗少量 token；仅支持正则改写/注入类插件。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PluginRow(
    plugin: Plugin,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onDelete)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = plugin.name.ifBlank { "未命名插件" },
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (plugin.characterCardId != null) "角色" else "全局",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (plugin.characterCardId != null) Color(0xFF07C160)
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (plugin.description.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = plugin.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(2.dp))
            val meta = listOfNotNull(
                "${plugin.rules.size} 条规则".takeIf { plugin.rules.isNotEmpty() },
                plugin.version.takeIf { it.isNotBlank() },
                plugin.author.takeIf { it.isNotBlank() }
            ).joinToString(" · ")
            if (meta.isNotBlank()) {
                Text(
                    text = meta,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (plugin.enabled) Color(0xFF07C160)
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(checked = plugin.enabled, onCheckedChange = onToggle)
    }
}

/**
 * 插件编辑/新增对话框（含规则管理）
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PluginEditDialog(
    initial: Plugin?,
    onDismiss: () -> Unit,
    onSave: (Plugin) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var description by remember { mutableStateOf(initial?.description ?: "") }
    var rules by remember { mutableStateOf(initial?.rules?.toMutableList() ?: mutableListOf()) }
    var editingRule by remember { mutableStateOf<PluginRule?>(null) }
    var showRuleDialog by remember { mutableStateOf(false) }
    var ruleIndex by remember { mutableStateOf(-1) }

    WeChatEditSheet(
        title = if (initial == null) "添加插件" else "编辑插件",
        onDismiss = onDismiss
    ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("插件名称") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("描述") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )

                Spacer(Modifier.height(16.dp))

                // 规则列表
                Text("规则（${rules.size}）", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(8.dp))

                rules.forEachIndexed { index, rule ->
                    RuleItem(
                        rule = rule,
                        onClick = {
                            ruleIndex = index
                            editingRule = rule
                            showRuleDialog = true
                        },
                        onDelete = { rules = rules.toMutableList().apply { removeAt(index) } }
                    )
                }

                TextButton(
                    onClick = {
                        ruleIndex = -1
                        editingRule = PluginRule()
                        showRuleDialog = true
                    }
                ) {
                    Text("+ 添加规则")
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
                                Plugin(
                                    id = initial?.id ?: 0,
                                    name = name.ifBlank { "未命名插件" },
                                    version = initial?.version ?: "1.0",
                                    description = description,
                                    author = initial?.author ?: "",
                                    enabled = initial?.enabled ?: true,
                                    isBuiltin = initial?.isBuiltin ?: false,
                                    rules = rules
                                )
                            )
                        }
                    ) { Text("保存") }
                }
    }

    // 规则编辑对话框
    if (showRuleDialog) {
        RuleEditDialog(
            initial = editingRule,
            onDismiss = { showRuleDialog = false },
            onSave = { rule ->
                rules = rules.toMutableList().apply {
                    if (ruleIndex >= 0) set(ruleIndex, rule) else add(rule)
                }
                showRuleDialog = false
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RuleItem(
    rule: PluginRule,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onClick, onLongClick = onDelete)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${rule.trigger.displayName()} → ${rule.action.displayName()}",
                    style = MaterialTheme.typography.bodyMedium
                )
                val detail = rule.pattern.ifBlank { "（始终触发）" }
                Text(
                    text = "匹配：$detail",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text("删除", color = MaterialTheme.colorScheme.error)
        }
    }
}

/**
 * 规则编辑对话框
 */
@Composable
private fun RuleEditDialog(
    initial: PluginRule?,
    onDismiss: () -> Unit,
    onSave: (PluginRule) -> Unit
) {
    var trigger by remember { mutableStateOf(initial?.trigger ?: PluginTrigger.ON_REPLY) }
    var action by remember { mutableStateOf(initial?.action ?: PluginAction.REPLACE) }
    var pattern by remember { mutableStateOf(initial?.pattern ?: "") }
    var useRegex by remember { mutableStateOf(initial?.useRegex ?: false) }
    var config by remember { mutableStateOf(initial?.config?.toMutableMap() ?: mutableMapOf()) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                Text("规则", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(16.dp))

                Text("触发时机", style = MaterialTheme.typography.labelLarge)
                PluginTrigger.entries.forEach { t ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        RadioButton(selected = trigger == t, onClick = { trigger = t })
                        Text(t.displayName())
                    }
                }

                Spacer(Modifier.height(12.dp))

                Text("动作", style = MaterialTheme.typography.labelLarge)
                PluginAction.entries.forEach { a ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        RadioButton(selected = action == a, onClick = { action = a })
                        Text(a.displayName())
                    }
                }

                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = pattern,
                    onValueChange = { pattern = it },
                    label = { Text("匹配内容（留空则始终触发）") },
                    placeholder = { Text("关键词或正则") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("按正则匹配", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.weight(1f))
                    Switch(checked = useRegex, onCheckedChange = { useRegex = it })
                }

                Spacer(Modifier.height(12.dp))

                // 动作参数
                when (action) {
                    PluginAction.REPLACE -> {
                        OutlinedTextField(
                            value = config["find"] ?: "",
                            onValueChange = { config = config.toMutableMap().apply { put("find", it) } },
                            label = { Text("查找") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = config["replace"] ?: "",
                            onValueChange = { config = config.toMutableMap().apply { put("replace", it) } },
                            label = { Text("替换为") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }

                    PluginAction.PREFIX, PluginAction.SUFFIX -> {
                        OutlinedTextField(
                            value = config["text"] ?: "",
                            onValueChange = { config = config.toMutableMap().apply { put("text", it) } },
                            label = { Text("文本") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }

                    PluginAction.SET_STATUS -> {
                        Text("状态增量（正数增加、负数减少）", style = MaterialTheme.typography.labelLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = config["mood"] ?: "",
                                onValueChange = { config = config.toMutableMap().apply { put("mood", it) } },
                                label = { Text("心情") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = config["energy"] ?: "",
                                onValueChange = { config = config.toMutableMap().apply { put("energy", it) } },
                                label = { Text("体力") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = config["affection"] ?: "",
                                onValueChange = { config = config.toMutableMap().apply { put("affection", it) } },
                                label = { Text("好感") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                        }
                    }

                    PluginAction.INJECT_PROMPT -> {
                        OutlinedTextField(
                            value = config["text"] ?: "",
                            onValueChange = { config = config.toMutableMap().apply { put("text", it) } },
                            label = { Text("注入的提示词片段") },
                            placeholder = { Text("留空 pattern = 常驻注入；否则命中关键词时注入") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3
                        )
                    }

                    PluginAction.EXECUTE_JS -> {
                        OutlinedTextField(
                            value = config["script"] ?: "",
                            onValueChange = { config = config.toMutableMap().apply { put("script", it) } },
                            label = { Text("JS 脚本（QuickJS 受限沙箱）") },
                            placeholder = { Text("function transformReply(text) { return text + '…'; }") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 4
                        )
                    }
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
                                PluginRule(
                                    trigger = trigger,
                                    useRegex = useRegex,
                                    pattern = pattern,
                                    action = action,
                                    config = config
                                )
                            )
                        }
                    ) { Text("确定") }
                }
            }
        }
    }
}

private fun PluginTrigger.displayName(): String = when (this) {
    PluginTrigger.ON_REPLY -> "AI 回复后"
    PluginTrigger.ON_SEND -> "发送前"
    PluginTrigger.ON_MESSAGE -> "消息产生时"
}

private fun PluginAction.displayName(): String = when (this) {
    PluginAction.REPLACE -> "文本替换"
    PluginAction.PREFIX -> "前缀注入"
    PluginAction.SUFFIX -> "后缀注入"
    PluginAction.SET_STATUS -> "状态变化"
    PluginAction.INJECT_PROMPT -> "提示词注入"
    PluginAction.EXECUTE_JS -> "JS脚本(受限)"
}
