package com.tavern.app.feature.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import com.tavern.app.core.security.AppLockManager
import com.tavern.app.core.util.AppSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * 设置主页 ViewModel
 */
@HiltViewModel
class SettingsHomeViewModel @Inject constructor(
    private val appSettings: AppSettings,
    private val appLock: AppLockManager
) : ViewModel() {

    /** 应用锁开关 */
    val pinEnabled: StateFlow<Boolean> = appLock.pinEnabled

    fun setAppLockEnabled(v: Boolean) = appLock.setEnabled(v)
    fun hasPin(): Boolean = appLock.hasPin()
    fun setPin(pin: String): Boolean = appLock.setPin(pin)

    /** 文字状态栏格式校验开关 */
    val strictStatusFormat: StateFlow<Boolean> = appSettings.strictStatusFormat

    /** 隐私模式开关 */
    val privacyMode: StateFlow<Boolean> = appSettings.privacyMode

    /** 聊天增强开关 */
    val chatEnhance: StateFlow<Boolean> = appSettings.chatEnhance

    /** 自动朗读 */
    val autoSpeak: StateFlow<Boolean> = appSettings.autoSpeak

    /** 角色主动消息 */
    val proactiveEnabled: StateFlow<Boolean> = appSettings.proactiveEnabled
    val proactiveIntervalH: StateFlow<Int> = appSettings.proactiveIntervalH
    val proactiveProb: StateFlow<Int> = appSettings.proactiveProb

    fun setProactiveEnabled(v: Boolean) = appSettings.setProactiveEnabled(v)
    fun setProactiveIntervalH(v: Int) = appSettings.setProactiveIntervalH(v)
    fun setProactiveProb(v: Int) = appSettings.setProactiveProb(v)

    /** 玩家昵称 */
    val playerName: StateFlow<String> = appSettings.playerName

    /** 玩家头像路径 */
    val playerAvatarPath: StateFlow<String> = appSettings.playerAvatarPath

    /** 聊天字体大小（sp） */
    val chatFontSize: StateFlow<Int> = appSettings.chatFontSize

    /** 深色模式：light / system */
    val darkMode: StateFlow<String> = appSettings.darkMode

    // ===== 记忆系统 =====
    val memoryEnabled: StateFlow<Boolean> = appSettings.memoryEnabled
    val memorySummary: StateFlow<Boolean> = appSettings.memorySummary
    val memoryFacts: StateFlow<Boolean> = appSettings.memoryFacts
    val memoryBudget: StateFlow<Boolean> = appSettings.memoryBudget
    val memorySummaryN: StateFlow<Int> = appSettings.memorySummaryN
    val memoryKeepK: StateFlow<Int> = appSettings.memoryKeepK
    val memoryFactEveryN: StateFlow<Int> = appSettings.memoryFactEveryN
    val memoryTopK: StateFlow<Int> = appSettings.memoryTopK

    fun setMemoryEnabled(v: Boolean) = appSettings.setMemoryEnabled(v)
    fun setMemorySummary(v: Boolean) = appSettings.setMemorySummary(v)
    fun setMemoryFacts(v: Boolean) = appSettings.setMemoryFacts(v)
    fun setMemoryBudget(v: Boolean) = appSettings.setMemoryBudget(v)
    fun setMemorySummaryN(v: Int) = appSettings.setMemorySummaryN(v)
    fun setMemoryKeepK(v: Int) = appSettings.setMemoryKeepK(v)
    fun setMemoryFactEveryN(v: Int) = appSettings.setMemoryFactEveryN(v)
    fun setMemoryTopK(v: Int) = appSettings.setMemoryTopK(v)

    private val _avatarError = MutableStateFlow<String?>(null)
    val avatarError: StateFlow<String?> = _avatarError.asStateFlow()

    fun setStrictStatusFormat(enabled: Boolean) {
        appSettings.setStrictStatusFormat(enabled)
    }

    fun setPrivacyMode(enabled: Boolean) {
        appSettings.setPrivacyMode(enabled)
    }

    fun setChatEnhance(enabled: Boolean) {
        appSettings.setChatEnhance(enabled)
    }

    fun setAutoSpeak(enabled: Boolean) {
        appSettings.setAutoSpeak(enabled)
    }

    fun setChatFontSize(sp: Int) {
        appSettings.setChatFontSize(sp)
    }

    fun setDarkMode(mode: String) {
        appSettings.setDarkMode(mode)
    }

    fun setPlayerName(name: String) {
        appSettings.setPlayerName(name)
    }

    fun setPlayerAvatar(uri: Uri) {
        appSettings.setPlayerAvatarFromUri(uri)
            .onFailure { _avatarError.value = "头像设置失败：${it.message}" }
    }

    fun clearAvatarError() {
        _avatarError.value = null
    }

    // ===== 向量检索 =====
    val memoryVector: StateFlow<Boolean> = appSettings.memoryVector
    val embBaseUrl: String get() = appSettings.embBaseUrl
    val embApiKey: String get() = appSettings.embApiKey
    val embModel: String get() = appSettings.embModel

    fun setMemoryVector(v: Boolean) = appSettings.setMemoryVector(v)
    fun setEmbeddingConfig(url: String, key: String, model: String) =
        appSettings.setEmbeddingConfig(url, key, model)
}
