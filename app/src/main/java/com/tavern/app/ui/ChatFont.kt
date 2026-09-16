package com.tavern.app.ui

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.sp

/** 聊天字体大小（气泡 / IM / 状态面板共用），由页面顶层注入 */
val LocalChatFontSize = staticCompositionLocalOf { 16.sp }
