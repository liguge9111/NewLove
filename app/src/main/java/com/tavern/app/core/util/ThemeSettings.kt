package com.tavern.app.core.util

import android.content.Context
import com.tavern.app.ui.theme.ThemePreset
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 主题设置
 *
 * 持久化主题预设选择（SharedPreferences + StateFlow 响应式）。
 */
@Singleton
class ThemeSettings @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val prefs = context.getSharedPreferences("tavern_settings", Context.MODE_PRIVATE)

    private val _preset = MutableStateFlow(loadPreset())
    val preset: StateFlow<ThemePreset> = _preset.asStateFlow()

    /** 设置主题预设 */
    fun setPreset(preset: ThemePreset) {
        _preset.value = preset
        prefs.edit().putString(KEY_PRESET, preset.name).apply()
    }

    private fun loadPreset(): ThemePreset {
        val name = prefs.getString(KEY_PRESET, null) ?: return ThemePreset.MATERIAL_YOU
        return runCatching { ThemePreset.valueOf(name) }
            .getOrDefault(ThemePreset.MATERIAL_YOU)
    }

    private companion object {
        const val KEY_PRESET = "theme_preset"
    }
}
