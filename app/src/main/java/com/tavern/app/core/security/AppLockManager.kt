package com.tavern.app.core.security

import android.content.Context
import android.os.SystemClock
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 应用锁管理器
 *
 * PIN 解锁（4-6位数字）；退到后台超过 30 秒后重新锁定。
 * PIN 以加盐 SHA-256 存储。
 */
@Singleton
class AppLockManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val prefs = context.getSharedPreferences("tavern_settings", Context.MODE_PRIVATE)

    /** 是否处于锁定状态 */
    private val _locked = MutableStateFlow(false)
    val locked: StateFlow<Boolean> = _locked.asStateFlow()

    /** 应用锁开关 */
    private val _pinEnabled = MutableStateFlow(prefs.getBoolean(KEY_LOCK_ENABLED, false))
    val pinEnabled: StateFlow<Boolean> = _pinEnabled.asStateFlow()

    private var pausedAt: Long = 0L

    /** 退后台时记录时间 */
    fun onAppPaused() {
        pausedAt = SystemClock.elapsedRealtime()
    }

    /** 回前台时判断是否需要上锁（后台超过 30 秒） */
    fun onAppResumed() {
        if (!_pinEnabled.value) return
        if (pausedAt == 0L) return
        val away = SystemClock.elapsedRealtime() - pausedAt
        if (away >= LOCK_DELAY_MS) {
            _locked.value = true
        }
        pausedAt = 0L
    }

    /** 立即锁定 */
    fun lockNow() {
        if (_pinEnabled.value) _locked.value = true
    }

    fun unlock() {
        _locked.value = false
    }

    fun setEnabled(enabled: Boolean) {
        _pinEnabled.value = enabled
        prefs.edit().putBoolean(KEY_LOCK_ENABLED, enabled).apply()
        if (!enabled) _locked.value = false
    }

    /** 是否已设置 PIN */
    fun hasPin(): Boolean = prefs.contains(KEY_PIN_HASH)

    /** 设置/修改 PIN（4-6位数字） */
    fun setPin(pin: String): Boolean {
        val p = pin.trim()
        if (p.length !in 4..6 || !p.all { it.isDigit() }) return false
        prefs.edit()
            .putString(KEY_PIN_SALT, SALT)
            .putString(KEY_PIN_HASH, hashPin(p))
            .apply()
        return true
    }

    /** 验证 PIN */
    fun verifyPin(pin: String): Boolean {
        val stored = prefs.getString(KEY_PIN_HASH, null) ?: return false
        return stored == hashPin(pin.trim())
    }

    companion object {
        private const val LOCK_DELAY_MS = 30_000L
        private const val KEY_LOCK_ENABLED = "app_lock_enabled"
        private const val KEY_PIN_HASH = "app_lock_pin_hash"
        private const val KEY_PIN_SALT = "app_lock_pin_salt"
        private const val SALT = "NewLove-lock-salt-v1"

        /** 加盐 SHA-256（便于纯逻辑测试） */
        fun hashPin(pin: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
            val bytes = digest.digest((SALT + pin).toByteArray(Charsets.UTF_8))
            return bytes.joinToString("") { "%02x".format(it) }
        }
    }
}
