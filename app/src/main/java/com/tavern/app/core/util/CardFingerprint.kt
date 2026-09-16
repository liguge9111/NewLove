package com.tavern.app.core.util

import java.security.MessageDigest

/**
 * 角色卡内容指纹（用于重复导入检测）
 *
 * 基于 名称 + 首条消息，不依赖文件名。
 */
object CardFingerprint {

    fun of(name: String, firstMessage: String): String {
        val raw = name.trim() + "|" + firstMessage.trim().take(200)
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(raw.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}
