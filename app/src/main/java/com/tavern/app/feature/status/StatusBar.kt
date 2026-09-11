package com.tavern.app.feature.status

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.tavern.app.core.model.CharacterState
import com.tavern.app.core.model.StatusItemDef

/**
 * 角色状态栏（按卡模板渲染，独立可折叠区域）
 *
 * 状态项由角色卡的状态模板决定，数值按角色持久化，
 * 只随 [STATUS] 增量更新，不随对话重新生成。
 */
@Composable
fun StatusBar(
    state: CharacterState?,
    schema: List<StatusItemDef>,
    modifier: Modifier = Modifier
) {
    if (state == null) return

    val items = schema.ifEmpty {
        listOf(
            StatusItemDef("mood", "心情", 50),
            StatusItemDef("energy", "体力", 100),
            StatusItemDef("affection", "好感", 0)
        )
    }

    // 记住折叠状态（跨会话/模式保持）
    var expanded by rememberSaveable { mutableStateOf(true) }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            // 标题行（点击折叠/展开）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "角色状态",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
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
                    modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items.forEachIndexed { index, item ->
                        val value = currentValue(item, state)
                        StatusBarRow(
                            label = item.label,
                            value = value,
                            color = paletteColor(index)
                        )
                    }
                }
            }
        }
    }
}

private fun currentValue(item: StatusItemDef, state: CharacterState): Int = when (item.key) {
    "mood" -> state.mood
    "energy" -> state.energy
    "affection" -> state.affection
    else -> state.customStates[item.key] ?: item.defaultValue
}

private fun paletteColor(index: Int): Color = when (index % 6) {
    0 -> Color(0xFFE91E63)
    1 -> Color(0xFF4CAF50)
    2 -> Color(0xFFFF9800)
    3 -> Color(0xFF2196F3)
    4 -> Color(0xFF9C27B0)
    else -> Color(0xFF009688)
}

@Composable
private fun StatusBarRow(
    label: String,
    value: Int,
    color: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.width(56.dp),
            maxLines = 1
        )
        LinearProgressIndicator(
            progress = { value / 100f },
            modifier = Modifier
                .weight(1f)
                .height(8.dp),
            color = color,
            trackColor = MaterialTheme.colorScheme.surface,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "$value",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.width(28.dp)
        )
    }
}
