package com.tavern.app.feature.character

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import com.tavern.app.core.model.CharacterCard
import com.tavern.app.core.model.StatusItemDef
import com.tavern.app.core.model.WorldBookEntry

/**
 * 角色卡详情页
 *
 * Tab 分区：基础设定 / 世界书 / 多开局 / 状态栏。
 * 「开始聊天」前弹出开局选择。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CharacterDetailScreen(
    onBack: () -> Unit,
    onStartChat: (Int) -> Unit,
    viewModel: CharacterDetailViewModel = hiltViewModel()
) {
    val card by viewModel.card.collectAsState()
    val statusSchema by viewModel.statusSchema.collectAsState()
    var selectedTab by remember { mutableStateOf(0) }
    var showGreetingPicker by remember { mutableStateOf(false) }
    val tabs = listOf("设定", "世界书", "开局", "状态栏")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(card?.name ?: "角色卡") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
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
                onPick = { index ->
                    showGreetingPicker = false
                    onStartChat(index)
                },
                onDismiss = { showGreetingPicker = false }
            )
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
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var selected by remember { mutableStateOf(0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择开局") },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 360.dp),
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
            TextButton(onClick = { onPick(selected) }) { Text("开始") }
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
