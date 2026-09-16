package com.tavern.app.core.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 角色卡实体
 *
 * 采用"原始 JSON + 冗余查询字段"策略：
 * - rawJson 保存角色卡完整原始 JSON（保真，未来字段不丢失）
 * - 冗余字段用于列表展示与搜索筛选
 */
@Entity(tableName = "character_cards")
data class CharacterCardEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** 角色名 */
    val name: String = "",

    /** 描述摘要（列表展示用） */
    val description: String = "",

    /** 标签（逗号分隔，筛选用） */
    val tags: String = "",

    /** 创建者 */
    val creator: String = "",

    /** 卡片规范（CHARA_CARD_V2 / CHARA_CARD_V3 / TAVERN_AI_V1） */
    val spec: String = "",

    /** 完整原始 JSON */
    val rawJson: String = "",

    /** 导入源文件路径（PNG 头像等） */
    val filePath: String? = null,

    /** 导入时间戳 */
    val importedAt: Long = System.currentTimeMillis(),

    /** 是否收藏 */
    val isFavorite: Boolean = false
)
