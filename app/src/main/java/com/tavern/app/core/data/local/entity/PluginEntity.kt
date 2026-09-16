package com.tavern.app.core.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 插件实体
 */
@Entity(tableName = "plugins")
data class PluginEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val name: String = "",

    val version: String = "1.0",

    val description: String = "",

    val author: String = "",

    val enabled: Boolean = true,

    val isBuiltin: Boolean = false,

    /** 绑定的角色卡 ID（null = 全局生效；非空 = 仅该角色聊天时生效） */
    val characterCardId: Long? = null,

    /** 规则列表（JSON 序列化） */
    val rulesJson: String = "[]"
)
