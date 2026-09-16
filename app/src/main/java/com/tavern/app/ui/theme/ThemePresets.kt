package com.tavern.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * 主题预设
 *
 * 三套可切换的界面风格。
 */
enum class ThemePreset {
    /** Material You（默认紫色系） */
    MATERIAL_YOU,

    /** 经典酒馆（暖棕/暗金色系） */
    CLASSIC_TAVERN,

    /** 现代聊天（蓝绿色系） */
    MODERN_CHAT
}

// ===== Material You（微信绿主色） =====

private val MaterialYouLight = lightColorScheme(
    primary = Color(0xFF07C160),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD9F5E5),
    onPrimaryContainer = Color(0xFF053B1E),
    secondary = Color(0xFF526358),
    tertiary = Color(0xFF3A6B4F),
    error = Color(0xFFE53935)
)

private val MaterialYouDark = darkColorScheme(
    primary = Color(0xFF3DDC84),
    onPrimary = Color(0xFF00391B),
    primaryContainer = Color(0xFF0B5230),
    onPrimaryContainer = Color(0xFFB9F2CE),
    secondary = Color(0xFFB6CCBE),
    tertiary = Color(0xFF9FD5B4),
    error = Color(0xFFFFB4AB)
)

// ===== 经典酒馆 =====

private val ClassicTavernLight = lightColorScheme(
    primary = Color(0xFF7A4E2D),
    secondary = Color(0xFF8D6E63),
    tertiary = Color(0xFF6D4C41),
    background = Color(0xFFFAF3EA),
    surface = Color(0xFFFAF3EA),
    surfaceVariant = Color(0xFFEFE0CC)
)

private val ClassicTavernDark = darkColorScheme(
    primary = Color(0xFFD7A86E),
    secondary = Color(0xFFBCAAA4),
    tertiary = Color(0xFFB98C7A),
    background = Color(0xFF1F1712),
    surface = Color(0xFF1F1712),
    surfaceVariant = Color(0xFF3A2E24)
)

// ===== 现代聊天 =====

private val ModernChatLight = lightColorScheme(
    primary = Color(0xFF00696D),
    secondary = Color(0xFF4A6367),
    tertiary = Color(0xFF4F5E5E)
)

private val ModernChatDark = darkColorScheme(
    primary = Color(0xFF4DD9E0),
    secondary = Color(0xFFB1CBCF),
    tertiary = Color(0xFFC0C8CA)
)

/**
 * 根据主题预设与深浅色返回配色方案
 */
fun ThemePreset.colorScheme(darkTheme: Boolean): ColorScheme = when (this) {
    ThemePreset.MATERIAL_YOU ->
        if (darkTheme) MaterialYouDark else MaterialYouLight
    ThemePreset.CLASSIC_TAVERN ->
        if (darkTheme) ClassicTavernDark else ClassicTavernLight
    ThemePreset.MODERN_CHAT ->
        if (darkTheme) ModernChatDark else ModernChatLight
}

/**
 * 主题显示名
 */
fun ThemePreset.displayName(): String = when (this) {
    ThemePreset.MATERIAL_YOU -> "Material You"
    ThemePreset.CLASSIC_TAVERN -> "经典酒馆"
    ThemePreset.MODERN_CHAT -> "现代聊天"
}
