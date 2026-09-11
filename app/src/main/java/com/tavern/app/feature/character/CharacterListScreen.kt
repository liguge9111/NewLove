package com.tavern.app.feature.character

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.tavern.app.core.data.local.dao.CharacterCardWithPreview
import com.tavern.app.core.data.local.dao.SessionWithPreview
import com.tavern.app.core.data.local.entity.CharacterCardEntity
import com.tavern.app.feature.settings.SettingsContent
import com.tavern.app.ui.privacyBlur
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 微信品牌绿 */
private val WeChatGreen = Color(0xFF07C160)

/**
 * 首页（微信式三 Tab）：聊天记录 / 好友 / 我
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun CharacterListScreen(
    onCardClick: (Long) -> Unit,
    onOpenSession: (Long) -> Unit,
    onOpenModelSettings: () -> Unit,
    onOpenImageSettings: () -> Unit,
    onOpenPluginSettings: () -> Unit,
    onOpenVoiceSettings: () -> Unit,
    viewModel: CharacterListViewModel = hiltViewModel()
) {
    val cards by viewModel.cards.collectAsState()
    val records by viewModel.records.collectAsState()
    val privacyMode by viewModel.privacyMode.collectAsState()
    val message by viewModel.message.collectAsState()
    var cardToDelete by remember { mutableStateOf<CharacterCardEntity?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    // 0 = 聊天（默认），1 = 好友，2 = 我（rememberSaveable：从二级页返回时保持当前 Tab）
    var selectedTab by rememberSaveable { mutableStateOf(0) }
    var recordToDelete by remember { mutableStateOf<SessionWithPreview?>(null) }
    var chatQuery by rememberSaveable { mutableStateOf("") }
    var friendQuery by rememberSaveable { mutableStateOf("") }

    // 搜索过滤
    val filteredRecords = remember(records, chatQuery) {
        if (chatQuery.isBlank()) records
        else records.filter {
            it.characterName.contains(chatQuery, ignoreCase = true) ||
                (it.lastMessage?.contains(chatQuery, ignoreCase = true) == true)
        }
    }
    val filteredCards = remember(cards, friendQuery) {
        if (friendQuery.isBlank()) cards
        else cards.filter {
            it.card.name.contains(friendQuery, ignoreCase = true) ||
                it.card.description.contains(friendQuery, ignoreCase = true) ||
                it.card.tags.contains(friendQuery, ignoreCase = true)
        }
    }

    // 文件选择器（PNG / JSON）
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.importCard(it) }
    }

    // 提示消息
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
                title = {
                    Text(
                        when (selectedTab) {
                            0 -> "聊天"
                            1 -> "好友"
                            else -> "我"
                        }
                    )
                },
                actions = {
                    if (selectedTab == 1) {
                        IconButton(onClick = {
                            launcher.launch(arrayOf("image/png", "application/json"))
                        }) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = "添加好友（导入角色卡）"
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Filled.ChatBubbleOutline, contentDescription = null) },
                    label = { Text("聊天") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = WeChatGreen,
                        selectedTextColor = WeChatGreen,
                        indicatorColor = MaterialTheme.colorScheme.surface
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Filled.PersonOutline, contentDescription = null) },
                    label = { Text("好友") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = WeChatGreen,
                        selectedTextColor = WeChatGreen,
                        indicatorColor = MaterialTheme.colorScheme.surface
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                    label = { Text("我") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = WeChatGreen,
                        selectedTextColor = WeChatGreen,
                        indicatorColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        }
    ) { padding ->
        when (selectedTab) {
            0 -> Column(modifier = Modifier.padding(padding)) {
                SearchField(
                    query = chatQuery,
                    onQueryChange = { chatQuery = it },
                    placeholder = "搜索聊天"
                )
                RecordsTab(
                    records = filteredRecords,
                    onOpen = { onOpenSession(it.session.characterCardId) },
                    onLongPress = { recordToDelete = it },
                    privacyMode = privacyMode
                )
            }
            1 -> Column(modifier = Modifier.padding(padding)) {
                SearchField(
                    query = friendQuery,
                    onQueryChange = { friendQuery = it },
                    placeholder = "搜索好友"
                )
                CharactersTab(
                    cards = filteredCards,
                    onCardClick = onCardClick,
                    onDelete = { cardToDelete = it },
                    privacyMode = privacyMode
                )
            }
            2 -> SettingsContent(
                modifier = Modifier.padding(padding),
                onOpenModelSettings = onOpenModelSettings,
                onOpenImageSettings = onOpenImageSettings,
                onOpenPluginSettings = onOpenPluginSettings,
                onOpenVoiceSettings = onOpenVoiceSettings
            )
        }
    }

    // 删除聊天记录确认
    recordToDelete?.let { record ->
        AlertDialog(
            onDismissRequest = { recordToDelete = null },
            title = { Text("删除聊天记录") },
            text = {
                Text("确定删除与「${record.characterName}」的聊天记录吗？全部对话将被清空。")
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteSession(record.session.id)
                    recordToDelete = null
                }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { recordToDelete = null }) { Text("取消") }
            }
        )
    }

    // 删除确认对话框
    cardToDelete?.let { card ->
        AlertDialog(
            onDismissRequest = { cardToDelete = null },
            title = { Text("删除角色卡") },
            text = { Text("确定删除「${card.name}」吗？此操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteCard(card.id)
                    cardToDelete = null
                }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { cardToDelete = null }) {
                    Text("取消")
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CharactersTab(
    cards: List<CharacterCardWithPreview>,
    modifier: Modifier = Modifier,
    privacyMode: Boolean = false,
    onCardClick: (Long) -> Unit,
    onDelete: (CharacterCardEntity) -> Unit
) {
    if (cards.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "暂无好友",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "点击右上角 + 导入 PNG / JSON 角色卡添加好友",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        // 微信通讯录式列表
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface),
            contentPadding = PaddingValues(vertical = 4.dp)
        ) {
            items(cards, key = { it.card.id }) { item ->
                val card = item.card
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = { onCardClick(card.id) },
                                onLongClick = { onDelete(card) }
                            )
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val filePath = card.filePath
                        if (!filePath.isNullOrBlank() && File(filePath).exists()) {
                            AsyncImage(
                                model = File(filePath),
                                contentDescription = card.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .privacyBlur(privacyMode)
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(6.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = card.name.take(1),
                                            style = MaterialTheme.typography.titleMedium,
                                            color = WeChatGreen
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = card.name.ifBlank { "未命名" },
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (card.isFavorite) {
                            Text("★", color = WeChatGreen)
                        }
                    }
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 72.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecordsTab(
    records: List<SessionWithPreview>,
    modifier: Modifier = Modifier,
    privacyMode: Boolean = false,
    onOpen: (SessionWithPreview) -> Unit,
    onLongPress: (SessionWithPreview) -> Unit
) {
    if (records.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "暂无聊天记录",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "在好友页选择角色开始互动后，记录会出现在这里\n长按记录可删除",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface),
            contentPadding = PaddingValues(vertical = 4.dp)
        ) {
            items(records, key = { it.session.id }) { record ->
                RecordItem(
                    record = record,
                    privacyMode = privacyMode,
                    onClick = { onOpen(record) },
                    onLongClick = { onLongPress(record) }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecordItem(
    record: SessionWithPreview,
    privacyMode: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {}
) {
    // 微信会话列表样式：扁平行 + 分隔线
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 角色头像（圆角方形）
            val filePath = record.characterFilePath
            if (!filePath.isNullOrBlank() && File(filePath).exists()) {
                AsyncImage(
                    model = File(filePath),
                    contentDescription = record.characterName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .privacyBlur(privacyMode)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = record.characterName.take(1),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = record.characterName,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                        ),
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = formatLastActive(record.session.lastActiveAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = record.lastMessage ?: "开始你的第一次互动吧",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        HorizontalDivider(
            modifier = Modifier.padding(start = 76.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CharacterCardItem(
    item: CharacterCardWithPreview,
    privacyMode: Boolean = false,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val card = item.card
    // 头像高度按 id 微调，营造瀑布流错落感
    val avatarHeight = (160 + (card.id % 3).toInt() * 28).dp

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onDelete
                )
        ) {
            // 头像（PNG 角色卡原图）
            if (!card.filePath.isNullOrBlank() && File(card.filePath).exists()) {
                AsyncImage(
                    model = File(card.filePath),
                    contentDescription = card.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(avatarHeight)
                        .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                        .privacyBlur(privacyMode)
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(avatarHeight / 2)
                        .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = card.name.take(1),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = card.name.ifBlank { "未命名" },
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (card.isFavorite) {
                        Text(text = "★", color = MaterialTheme.colorScheme.primary)
                    }
                }

                Spacer(Modifier.height(4.dp))

                Text(
                    text = card.description.ifBlank { "（无描述）" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                // 最后互动预览
                item.lastMessage?.let { last ->
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text(
                                text = formatLastActive(item.lastActiveAt),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = last,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatLastActive(timestamp: Long?): String {
    if (timestamp == null || timestamp <= 0) return "尚未互动"
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    return when {
        diff < 60_000L -> "刚刚互动"
        diff < 3_600_000L -> "${diff / 60_000L} 分钟前"
        diff < 86_400_000L -> "${diff / 3_600_000L} 小时前"
        diff < 7 * 86_400_000L -> "${diff / 86_400_000L} 天前"
        else -> SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(timestamp))
    }
}

/** 微信式搜索栏 */
@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFFEDEDED),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = null,
                        tint = Color(0xFF999999),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    BasicTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = Color(0xFF111111)
                        ),
                        cursorBrush = SolidColor(WeChatGreen),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        decorationBox = { inner ->
                            if (query.isEmpty()) {
                                Text(
                                    text = placeholder,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color(0xFF999999)
                                )
                            }
                            inner()
                        }
                    )
                    if (query.isNotEmpty()) {
                        IconButton(
                            onClick = { onQueryChange("") },
                            modifier = Modifier.size(18.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "清除",
                                tint = Color(0xFF999999),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
