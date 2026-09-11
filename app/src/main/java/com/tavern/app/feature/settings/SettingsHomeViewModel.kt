package com.tavern.app.feature.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
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
    private val appSettings: AppSettings
) : ViewModel() {

    /** 文字状态栏格式校验开关 */
    val strictStatusFormat: StateFlow<Boolean> = appSettings.strictStatusFormat

    /** 隐私模式开关 */
    val privacyMode: StateFlow<Boolean> = appSettings.privacyMode

    /** 聊天增强开关 */
    val chatEnhance: StateFlow<Boolean> = appSettings.chatEnhance

    /** 自动朗读 */
    val autoSpeak: StateFlow<Boolean> = appSettings.autoSpeak

    /** 玩家昵称 */
    val playerName: StateFlow<String> = appSettings.playerName

    /** 玩家头像路径 */
    val playerAvatarPath: StateFlow<String> = appSettings.playerAvatarPath

    /** 聊天字体大小（sp） */
    val chatFontSize: StateFlow<Int> = appSettings.chatFontSize

    /** 深色模式：light / system */
    val darkMode: StateFlow<String> = appSettings.darkMode

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
}
