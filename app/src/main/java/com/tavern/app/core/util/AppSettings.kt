package com.tavern.app.core.util

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 应用级偏好设置（SharedPreferences + StateFlow）
 */
@Singleton
class AppSettings @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val prefs = context.getSharedPreferences("tavern_settings", Context.MODE_PRIVATE)

    private val playerAvatarFile: File
        get() = File(context.filesDir, "player_avatar.png")

    /** AI 回复后自动朗读 */
    private val _autoSpeak = MutableStateFlow(prefs.getBoolean(KEY_AUTO_SPEAK, false))
    val autoSpeak: StateFlow<Boolean> = _autoSpeak.asStateFlow()

    /**
     * 文字状态栏格式校验：
     * 开启 = 卡声明了状态栏协议但模型未按格式输出时自动重试；
     * 关闭 = 未按格式输出则忽略状态栏，正文照常显示。
     */
    private val _strictStatusFormat = MutableStateFlow(prefs.getBoolean(KEY_STRICT_STATUS, true))
    val strictStatusFormat: StateFlow<Boolean> = _strictStatusFormat.asStateFlow()

    /** 隐私模式：打开后全 App 图片模糊化 */
    private val _privacyMode = MutableStateFlow(prefs.getBoolean(KEY_PRIVACY_MODE, false))
    val privacyMode: StateFlow<Boolean> = _privacyMode.asStateFlow()

    /**
     * 聊天增强：
     * 开 = IM 纯聊天生成 + 每轮回复后自动同步叙事（双倍 token，沉浸感更好）；
     * 关 = 实时模式（IM 消息直接混入叙事，[IM] 块回泡）。
     */
    private val _chatEnhance = MutableStateFlow(prefs.getBoolean(KEY_CHAT_ENHANCE, false))
    val chatEnhance: StateFlow<Boolean> = _chatEnhance.asStateFlow()

    /** 玩家昵称（用于 {{user}} 与聊天同步措辞） */
    private val _playerName = MutableStateFlow(prefs.getString(KEY_PLAYER_NAME, null) ?: "玩家")
    val playerName: StateFlow<String> = _playerName.asStateFlow()

    /** 玩家头像路径（空 = 无头像） */
    private val _playerAvatarPath = MutableStateFlow(
        prefs.getString(KEY_PLAYER_AVATAR, null)
            ?.takeIf { File(it).exists() }
            ?: playerAvatarFile.absolutePath.takeIf { playerAvatarFile.exists() }
            ?: ""
    )
    val playerAvatarPath: StateFlow<String> = _playerAvatarPath.asStateFlow()

    /** 聊天字体大小（sp，作用于气泡/IM/状态面板） */
    private val _chatFontSize = MutableStateFlow(prefs.getInt(KEY_CHAT_FONT_SIZE, 16))
    val chatFontSize: StateFlow<Int> = _chatFontSize.asStateFlow()

    /** 深色模式：light = 默认浅色；system = 跟随系统 */
    private val _darkMode = MutableStateFlow(prefs.getString(KEY_DARK_MODE, "light") ?: "light")
    val darkMode: StateFlow<String> = _darkMode.asStateFlow()

    /**
     * 插件实验室（默认关闭）：
     * 开启后启用插件导入向导与角色卡 regex_scripts 自动导入。
     */
    private val _pluginLabEnabled = MutableStateFlow(prefs.getBoolean(KEY_PLUGIN_LAB, false))
    val pluginLabEnabled: StateFlow<Boolean> = _pluginLabEnabled.asStateFlow()

    /** 视觉小说：选项点击方式（true = 直接发送；false = 先预览再确认） */
    private val _vnDirectSend = MutableStateFlow(prefs.getBoolean(KEY_VN_DIRECT_SEND, true))
    val vnDirectSend: StateFlow<Boolean> = _vnDirectSend.asStateFlow()

    /** 视觉小说：模型漏出选项时自动重试一次 */
    private val _vnRetryMissing = MutableStateFlow(prefs.getBoolean(KEY_VN_RETRY, true))
    val vnRetryMissing: StateFlow<Boolean> = _vnRetryMissing.asStateFlow()

    fun setAutoSpeak(enabled: Boolean) {
        _autoSpeak.value = enabled
        prefs.edit().putBoolean(KEY_AUTO_SPEAK, enabled).apply()
    }

    fun setStrictStatusFormat(enabled: Boolean) {
        _strictStatusFormat.value = enabled
        prefs.edit().putBoolean(KEY_STRICT_STATUS, enabled).apply()
    }

    fun setPrivacyMode(enabled: Boolean) {
        _privacyMode.value = enabled
        prefs.edit().putBoolean(KEY_PRIVACY_MODE, enabled).apply()
    }

    fun setChatEnhance(enabled: Boolean) {
        _chatEnhance.value = enabled
        prefs.edit().putBoolean(KEY_CHAT_ENHANCE, enabled).apply()
    }

    fun setPlayerName(name: String) {
        val trimmed = name.trim().ifBlank { "玩家" }
        _playerName.value = trimmed
        prefs.edit().putString(KEY_PLAYER_NAME, trimmed).apply()
    }

    /** 从相册 URI 复制头像到应用私有目录 */
    fun setPlayerAvatarFromUri(uri: Uri): Result<String> = runCatching {
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("无法读取图片")
        val bytes = input.use { it.readBytes() }
        playerAvatarFile.writeBytes(bytes)
        val path = playerAvatarFile.absolutePath
        _playerAvatarPath.value = path
        prefs.edit().putString(KEY_PLAYER_AVATAR, path).apply()
        path
    }

    fun setChatFontSize(sp: Int) {
        val clamped = sp.coerceIn(14, 24)
        _chatFontSize.value = clamped
        prefs.edit().putInt(KEY_CHAT_FONT_SIZE, clamped).apply()
    }

    /** darkMode: "light" | "system" */
    fun setDarkMode(mode: String) {
        val value = if (mode == "system") "system" else "light"
        _darkMode.value = value
        prefs.edit().putString(KEY_DARK_MODE, value).apply()
    }

    fun setPluginLabEnabled(enabled: Boolean) {
        _pluginLabEnabled.value = enabled
        prefs.edit().putBoolean(KEY_PLUGIN_LAB, enabled).apply()
    }

    fun setVnDirectSend(enabled: Boolean) {
        _vnDirectSend.value = enabled
        prefs.edit().putBoolean(KEY_VN_DIRECT_SEND, enabled).apply()
    }

    fun setVnRetryMissing(enabled: Boolean) {
        _vnRetryMissing.value = enabled
        prefs.edit().putBoolean(KEY_VN_RETRY, enabled).apply()
    }

    private companion object {
        const val KEY_AUTO_SPEAK = "auto_speak_replies"
        const val KEY_STRICT_STATUS = "strict_status_format"
        const val KEY_PRIVACY_MODE = "privacy_mode"
        const val KEY_CHAT_ENHANCE = "chat_enhance"
        const val KEY_PLAYER_NAME = "player_name"
        const val KEY_PLAYER_AVATAR = "player_avatar_path"
        const val KEY_CHAT_FONT_SIZE = "chat_font_size"
        const val KEY_DARK_MODE = "dark_mode"
        const val KEY_PLUGIN_LAB = "plugin_lab_enabled"
        const val KEY_VN_DIRECT_SEND = "vn_direct_send"
        const val KEY_VN_RETRY = "vn_retry_missing"
    }
}
