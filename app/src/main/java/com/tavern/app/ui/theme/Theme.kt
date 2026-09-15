package com.tavern.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

/**
 * 主题入口
 *
 * 支持深色/浅色模式 + 多套可切换主题
 * （Material You / 经典酒馆 / 现代聊天）。
 */
@Composable
fun TavernTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    preset: ThemePreset = ThemePreset.MATERIAL_YOU,
    content: @Composable () -> Unit
) {
    val colorScheme = preset.colorScheme(darkTheme)

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
