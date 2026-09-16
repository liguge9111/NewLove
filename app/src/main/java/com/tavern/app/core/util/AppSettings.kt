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

    // ===== 记忆系统（实验室，默认关闭） =====

    /** 记忆系统总开关 */
    private val _memoryEnabled = MutableStateFlow(prefs.getBoolean(KEY_MEM_ENABLED, false))
    val memoryEnabled: StateFlow<Boolean> = _memoryEnabled.asStateFlow()

    /** 滚动摘要 */
    private val _memorySummary = MutableStateFlow(prefs.getBoolean(KEY_MEM_SUMMARY, true))
    val memorySummary: StateFlow<Boolean> = _memorySummary.asStateFlow()

    /** 长期事实记忆 */
    private val _memoryFacts = MutableStateFlow(prefs.getBoolean(KEY_MEM_FACTS, true))
    val memoryFacts: StateFlow<Boolean> = _memoryFacts.asStateFlow()

    /** 上下文预算裁剪 */
    private val _memoryBudget = MutableStateFlow(prefs.getBoolean(KEY_MEM_BUDGET, true))
    val memoryBudget: StateFlow<Boolean> = _memoryBudget.asStateFlow()

    /** 摘要窗口条数 N */
    private val _memorySummaryN = MutableStateFlow(prefs.getInt(KEY_MEM_N, 20))
    val memorySummaryN: StateFlow<Int> = _memorySummaryN.asStateFlow()

    /** 保留原始消息条数 K */
    private val _memoryKeepK = MutableStateFlow(prefs.getInt(KEY_MEM_K, 10))
    val memoryKeepK: StateFlow<Int> = _memoryKeepK.asStateFlow()

    /** 事实抽取间隔（每 N 轮） */
    private val _memoryFactEveryN = MutableStateFlow(prefs.getInt(KEY_MEM_EVERY, 3))
    val memoryFactEveryN: StateFlow<Int> = _memoryFactEveryN.asStateFlow()

    /** 检索注入条数 */
    private val _memoryTopK = MutableStateFlow(prefs.getInt(KEY_MEM_TOPK, 5))
    val memoryTopK: StateFlow<Int> = _memoryTopK.asStateFlow()

    /** 角色主动消息开关（默认关） */
    private val _proactiveEnabled = MutableStateFlow(prefs.getBoolean(KEY_PROACTIVE, false))
    val proactiveEnabled: StateFlow<Boolean> = _proactiveEnabled.asStateFlow()

    /** 主动消息检查间隔（小时） */
    private val _proactiveIntervalH = MutableStateFlow(prefs.getInt(KEY_PROACTIVE_H, 2))
    val proactiveIntervalH: StateFlow<Int> = _proactiveIntervalH.asStateFlow()

    /** 主动消息触发概率（百分比5-40） */
    private val _proactiveProb = MutableStateFlow(prefs.getInt(KEY_PROACTIVE_PROB, 15))
    val proactiveProb: StateFlow<Int> = _proactiveProb.asStateFlow()

    /** 向量记忆检索开关（默认关） */
    private val _memoryVector = MutableStateFlow(prefs.getBoolean(KEY_MEM_VECTOR, false))
    val memoryVector: StateFlow<Boolean> = _memoryVector.asStateFlow()

    /** Embedding 服务配置 */
    val embBaseUrl: String get() = prefs.getString(KEY_EMB_URL, "") ?: ""
    val embApiKey: String get() = prefs.getString(KEY_EMB_KEY, "") ?: ""
    val embModel: String get() = prefs.getString(KEY_EMB_MODEL, "text-embedding-3-small") ?: "text-embedding-3-small"

    fun setMemoryVector(v: Boolean) {
        _memoryVector.value = v
        prefs.edit().putBoolean(KEY_MEM_VECTOR, v).apply()
    }

    fun setEmbeddingConfig(baseUrl: String, apiKey: String, model: String) {
        prefs.edit()
            .putString(KEY_EMB_URL, baseUrl.trim())
            .putString(KEY_EMB_KEY, apiKey.trim())
            .putString(KEY_EMB_MODEL, model.trim().ifBlank { "text-embedding-3-small" })
            .apply()
    }

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

    fun setMemoryEnabled(v: Boolean) {
        _memoryEnabled.value = v
        prefs.edit().putBoolean(KEY_MEM_ENABLED, v).apply()
    }

    fun setMemorySummary(v: Boolean) {
        _memorySummary.value = v
        prefs.edit().putBoolean(KEY_MEM_SUMMARY, v).apply()
    }

    fun setMemoryFacts(v: Boolean) {
        _memoryFacts.value = v
        prefs.edit().putBoolean(KEY_MEM_FACTS, v).apply()
    }

    fun setMemoryBudget(v: Boolean) {
        _memoryBudget.value = v
        prefs.edit().putBoolean(KEY_MEM_BUDGET, v).apply()
    }

    fun setMemorySummaryN(v: Int) {
        val c = v.coerceIn(8, 60)
        _memorySummaryN.value = c
        prefs.edit().putInt(KEY_MEM_N, c).apply()
    }

    fun setMemoryKeepK(v: Int) {
        val c = v.coerceIn(4, 30)
        _memoryKeepK.value = c
        prefs.edit().putInt(KEY_MEM_K, c).apply()
    }

    fun setMemoryFactEveryN(v: Int) {
        val c = v.coerceIn(1, 10)
        _memoryFactEveryN.value = c
        prefs.edit().putInt(KEY_MEM_EVERY, c).apply()
    }

    fun setMemoryTopK(v: Int) {
        val c = v.coerceIn(1, 12)
        _memoryTopK.value = c
        prefs.edit().putInt(KEY_MEM_TOPK, c).apply()
    }

    fun setProactiveEnabled(v: Boolean) {
        _proactiveEnabled.value = v
        prefs.edit().putBoolean(KEY_PROACTIVE, v).apply()
    }

    fun setProactiveIntervalH(v: Int) {
        val c = v.coerceIn(1, 12)
        _proactiveIntervalH.value = c
        prefs.edit().putInt(KEY_PROACTIVE_H, c).apply()
    }

    fun setProactiveProb(v: Int) {
        val c = v.coerceIn(5, 40)
        _proactiveProb.value = c
        prefs.edit().putInt(KEY_PROACTIVE_PROB, c).apply()
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
        const val KEY_MEM_ENABLED = "memory_enabled"
        const val KEY_MEM_SUMMARY = "memory_summary"
        const val KEY_MEM_FACTS = "memory_facts"
        const val KEY_MEM_BUDGET = "memory_budget"
        const val KEY_MEM_N = "memory_summary_n"
        const val KEY_MEM_K = "memory_keep_k"
        const val KEY_MEM_EVERY = "memory_fact_every_n"
        const val KEY_MEM_TOPK = "memory_topk"
        const val KEY_PROACTIVE = "proactive_enabled"
        const val KEY_PROACTIVE_H = "proactive_interval_h"
        const val KEY_PROACTIVE_PROB = "proactive_prob"
        const val KEY_MEM_VECTOR = "memory_vector"
        const val KEY_EMB_URL = "emb_base_url"
        const val KEY_EMB_KEY = "emb_api_key"
        const val KEY_EMB_MODEL = "emb_model"
    }
}
