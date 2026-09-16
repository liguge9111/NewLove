package com.tavern.app.feature.character

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.tavern.app.core.model.CharacterCard
import com.tavern.app.core.model.StatusItemDef
import com.tavern.app.core.model.WorldBookEntry
import com.tavern.app.feature.settings.WeChatEditSheet
import com.tavern.app.ui.privacyBlur
import java.io.File

/**
 * 角色卡详情页
 *
 * Tab 分区：基础设定 / 世界书 / 多开局 / 状态栏。
 * 支持改名与自定义头像；「开始游玩」前弹出开局选择。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CharacterDetailScreen(
    onBack: () -> Unit,
    onStartChat: (greetingIndex: Int, newSession: Boolean) -> Unit,
    initialTab: Int = 0,
    viewModel: CharacterDetailViewModel = hiltViewModel()
) {
    val card by viewModel.card.collectAsState()
    val statusSchema by viewModel.statusSchema.collectAsState()
    val avatarPath by viewModel.avatarPath.collectAsState()
    val memories by viewModel.memories.collectAsState()
    val summaries by viewModel.summaries.collectAsState()
    val message by viewModel.message.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedTab by remember { mutableStateOf(initialTab) }
    var showGreetingPicker by remember { mutableStateOf(false) }
    var showEditSheet by remember { mutableStateOf(false) }
    var nameInput by remember(card?.name) { mutableStateOf(card?.name ?: "") }
    val tabs = listOf("设定", "世界书", "开局", "状态栏", "记忆")

    val avatarLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { viewModel.setAvatar(it) } }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(card?.name ?: "角色卡") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        nameInput = card?.name ?: ""
                        showEditSheet = true
                    }) {
                        Icon(Icons.Filled.Edit, contentDescription = "编辑角色")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showGreetingPicker = true },
                icon = { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null) },
                text = { Text("开始游玩") }
            )
        }
    ) { padding ->
        val currentCard = card
        if (currentCard == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                TabRow(selectedTabIndex = selectedTab) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(title) }
                        )
                    }
                }

                when (selectedTab) {
                    0 -> BasicsTab(currentCard)
                    1 -> WorldBookTab(currentCard)
                    2 -> GreetingsTab(currentCard)
                    3 -> StatusSchemaTab(
                        schema = statusSchema,
                        onUpdate = { viewModel.updateSchema(it) }
                    )
                    4 -> MemoriesTab(
                        memories = memories,
                        summaries = summaries,
                        onAdd = { viewModel.addManualMemory(it) },
                        onDelete = { viewModel.deleteMemory(it) },
                        onEdit = { viewModel.updateMemory(it) },
                        onEditSummary = { id, text -> viewModel.updateSummary(id, text) },
                        onDeleteSummary = { viewModel.deleteSummary(it) }
                    )
                }
            }
        }
    }

    // 开局选择
    if (showGreetingPicker) {
        card?.let { currentCard ->
            val greetings = listOf(currentCard.firstMessage) + currentCard.alternateGreetings
            GreetingPickerDialog(
                greetings = greetings,
                onPick = { index, newSession ->
                    showGreetingPicker = false
                    onStartChat(index, newSession)
                },
                onDismiss = { showGreetingPicker = false }
            )
        }
    }

    // 编辑角色（改名 + 自定义头像）
    if (showEditSheet) {
        WeChatEditSheet(
            title = "编辑角色",
            onDismiss = { showEditSheet = false }
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (!avatarPath.isNullOrBlank() && File(avatarPath).exists()) {
                        AsyncImage(
                            model = File(avatarPath),
                            contentDescription = "角色头像",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("无头像", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
                Spacer(Modifier.width(16.dp))
                OutlinedButton(onClick = { avatarLauncher.launch("image/*") }) {
                    Text("上传头像图片")
                }
            }
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = nameInput,
                onValueChange = { nameInput = it },
                label = { Text("角色名称") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    viewModel.rename(nameInput)
                    showEditSheet = false
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = nameInput.isNotBlank()
            ) { Text("保存") }
        }
    }
}

// ===== Tab 1：基础设定 =====

@Composable
private fun BasicsTab(card: CharacterCard) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        DetailSection("描述", card.description)
        DetailSection("性格", card.personality)
        DetailSection("场景", card.scenario)
        DetailSection("首条消息", card.firstMessage)
        DetailSection("系统提示词", card.systemPrompt)

        if (card.messageExample.isNotBlank()) {
            DetailSection("对话示例", card.messageExample)
        }

        if (card.tags.isNotEmpty()) {
            DetailSection("标签", card.tags.joinToString("、"))
        }

        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Text(
            text = buildString {
                if (card.creator.isNotBlank()) appendLine("创建者：${card.creator}")
                if (card.characterVersion.isNotBlank()) appendLine("版本：${card.characterVersion}")
                append("格式：${card.spec.name}")
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(80.dp))
    }
}

// ===== Tab 2：世界书 =====

@Composable
private fun WorldBookTab(card: CharacterCard) {
    val book = card.characterBook
    if (book == null || book.entries.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "该角色卡未携带世界书",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val entries = remember(book) { book.sortedEntries() }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                "共 ${entries.size} 条条目（点击展开内容）",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        itemsIndexed(entries, key = { _, entry -> entry.id }) { index, entry ->
            WorldBookEntryCard(index = index, entry = entry)
        }
        item { Spacer(Modifier.height(80.dp)) }
    }
}

@Composable
private fun WorldBookEntryCard(index: Int, entry: WorldBookEntry) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        onClick = { expanded = !expanded },
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "#${index + 1}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.width(32.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.comment.ifBlank { entry.keys.joinToString(", ").ifBlank { "（无备注）" } },
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (entry.keys.isNotEmpty()) {
                        Text(
                            text = "关键词：${entry.keys.joinToString("、")}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = if (expanded) Int.MAX_VALUE else 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (entry.constant) {
                    Text(
                        "常驻",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                } else if (!entry.enabled) {
                    Text(
                        "停用",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            if (expanded) {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                Text(
                    text = entry.content,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

// ===== Tab 3：多开局 =====

@Composable
private fun GreetingsTab(card: CharacterCard) {
    val greetings = listOf(card.firstMessage) + card.alternateGreetings
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                "共 ${greetings.size} 个开局（开始游玩时可选择）",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        itemsIndexed(greetings) { index, greeting ->
            Card(
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = if (index == 0) "默认开局（first_mes）" else "备选开局 $index",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = greeting.ifBlank { "（空）" },
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        item { Spacer(Modifier.height(80.dp)) }
    }
}

// ===== Tab 4：状态栏模板编辑 =====

@Composable
private fun StatusSchemaTab(
    schema: List<StatusItemDef>,
    onUpdate: (List<StatusItemDef>) -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                "自定义该角色的状态栏项。AI 会按此模板输出状态变化，" +
                    "数值随对话增量更新并长期保存。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        itemsIndexed(schema, key = { _, it -> it.key }) { index, item ->
            Card(
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(item.label, style = MaterialTheme.typography.titleSmall)
                        Text(
                            "key: ${item.key} · 默认 ${item.defaultValue}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = {
                        onUpdate(schema.filterIndexed { i, _ -> i != index })
                    }) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "删除",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
        item {
            OutlinedButton(
                onClick = { showAddDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("添加状态项")
            }
        }
        item { Spacer(Modifier.height(80.dp)) }
    }

    if (showAddDialog) {
        AddStatusItemDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { item ->
                onUpdate(schema + item)
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun AddStatusItemDialog(
    onDismiss: () -> Unit,
    onAdd: (StatusItemDef) -> Unit
) {
    var label by remember { mutableStateOf("") }
    var key by remember { mutableStateOf("") }
    var defaultText by remember { mutableStateOf("50") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加状态项") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("显示名（如：愤怒、理智）") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    label = { Text("键名（英文或与显示名相同）") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = defaultText,
                    onValueChange = { defaultText = it },
                    label = { Text("默认值（0-100）") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val finalKey = key.ifBlank { label }
                    val default = defaultText.toIntOrNull()?.coerceIn(0, 100) ?: 50
                    if (finalKey.isNotBlank() && label.isNotBlank()) {
                        onAdd(StatusItemDef(finalKey, label, default))
                    }
                },
                enabled = label.isNotBlank()
            ) { Text("添加") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

// ===== 开局选择对话框 =====

@Composable
private fun GreetingPickerDialog(
    greetings: List<String>,
    onPick: (greetingIndex: Int, newSession: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var selected by remember { mutableStateOf(0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择开局") },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 340.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                itemsIndexed(greetings) { index, greeting ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selected == index,
                            onClick = { selected = index }
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (index == 0) "默认开局" else "备选开局 $index",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = greeting.ifBlank { "（空）" },
                                style = MaterialTheme.typography.bodySmall,
                                fontStyle = FontStyle.Italic,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = { onPick(selected, true) }) {
                    Text("新开一局")
                }
                TextButton(onClick = { onPick(selected, false) }) {
                    Text("继续上次")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun DetailSection(title: String, content: String) {
    if (content.isBlank()) return
    Column(modifier = Modifier.padding(bottom = 16.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = content,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

// ===== Tab 4：记忆时间线 =====

@Composable
private fun MemoriesTab(
    memories: List<com.tavern.app.core.model.CharacterMemory>,
    summaries: List<com.tavern.app.core.model.ChatSummary>,
    onAdd: (String) -> Unit,
    onDelete: (Long) -> Unit,
    onEdit: (com.tavern.app.core.model.CharacterMemory) -> Unit,
    onEditSummary: (Long, String) -> Unit,
    onDeleteSummary: (Long) -> Unit
) {
    var showAdd by remember { mutableStateOf(false) }
    var addText by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<com.tavern.app.core.model.CharacterMemory?>(null) }
    var editText by remember { mutableStateOf("") }
    var editingSummary by remember { mutableStateOf<com.tavern.app.core.model.ChatSummary?>(null) }
    var editSummaryText by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
        // 前情提要（可编辑）
        if (summaries.isNotEmpty()) {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        "前情提要（${summaries.size} 代）",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(6.dp))
                    summaries.forEach { s ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "第 ${s.roundIndex} 代",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    s.summary,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = if (summaries.size > 1) 2 else 6,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(
                                onClick = {
                                    editSummaryText = s.summary
                                    editingSummary = s
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    Icons.Filled.Edit,
                                    contentDescription = "编辑",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            IconButton(
                                onClick = { onDeleteSummary(s.id) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = "删除",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 操作行
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(onClick = { showAdd = true }, modifier = Modifier.weight(1f)) {
                Text("添加记忆")
            }
            if (memories.isNotEmpty()) {
                OutlinedButton(
                    onClick = { memories.forEach { onDelete(it.id) } },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("清空全部", color = MaterialTheme.colorScheme.error)
                }
            }
        }

        if (memories.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "暂无记忆\n开启「我 → 记忆系统」并多聊几轮后自动积累",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(memories, key = { _, m -> m.id }) { _, memory ->
                    Card(
                        onClick = {
                            editText = memory.content
                            editing = memory
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    if (memory.source == "manual") "手动" else "自动",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (memory.source == "manual") MaterialTheme.colorScheme.tertiary
                                    else MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault())
                                        .format(java.util.Date(memory.createdAt)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = { onDelete(memory.id) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.Delete,
                                        contentDescription = "删除",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(memory.content, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("手动添加记忆") },
            text = {
                OutlinedTextField(
                    value = addText,
                    onValueChange = { addText = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    placeholder = { Text("例如：玩家怕黑，偏好冷静的角色") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onAdd(addText)
                    addText = ""
                    showAdd = false
                }, enabled = addText.isNotBlank()) { Text("添加") }
            },
            dismissButton = {
                TextButton(onClick = { showAdd = false }) { Text("取消") }
            }
        )
    }

    editing?.let { mem ->
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("编辑记忆") },
            text = {
                OutlinedTextField(
                    value = editText,
                    onValueChange = { editText = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onEdit(mem.copy(content = editText))
                    editing = null
                }, enabled = editText.isNotBlank()) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { editing = null }) { Text("取消") }
            }
        )
    }

    editingSummary?.let { s ->
        AlertDialog(
            onDismissRequest = { editingSummary = null },
            title = { Text("编辑前情提要（第 ${s.roundIndex} 代）") },
            text = {
                OutlinedTextField(
                    value = editSummaryText,
                    onValueChange = { editSummaryText = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 4
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onEditSummary(s.id, editSummaryText)
                    editingSummary = null
                }, enabled = editSummaryText.isNotBlank()) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { editingSummary = null }) { Text("取消") }
            }
        )
    }
}
