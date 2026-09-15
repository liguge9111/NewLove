package com.tavern.app.ui

import android.os.Build
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import android.graphics.RenderEffect as AndroidRenderEffect
import android.graphics.Shader

/** 全局隐私模式开关（由页面顶层 CompositionLocalProvider 提供） */
val LocalPrivacyMode = staticCompositionLocalOf { false }

/**
 * 隐私模式图片模糊修饰符
 *
 * Android 12+ 使用真实高斯模糊；低版本以半透明遮罩兜底。
 *
 * @param enabled 隐私模式是否开启
 */
fun Modifier.privacyBlur(enabled: Boolean): Modifier = composed {
    if (!enabled) return@composed this
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        this.graphicsLayer {
            renderEffect = AndroidRenderEffect
                .createBlurEffect(28f, 28f, Shader.TileMode.CLAMP)
                .asComposeRenderEffect()
        }
    } else {
        // 低版本无 RenderEffect：覆盖遮罩达到"看不清"效果
        this.drawBehind {
            drawRect(Color.Black.copy(alpha = 0.72f))
        }
    }
}
