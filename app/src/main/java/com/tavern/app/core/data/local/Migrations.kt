package com.tavern.app.core.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room 数据库迁移
 */
object Migrations {

    /**
     * v1 → v2：chat_sessions 表新增 modelConfigId（会话级模型选择）
     */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE chat_sessions ADD COLUMN modelConfigId INTEGER NOT NULL DEFAULT -1"
            )
        }
    }

    /**
     * v2 → v3：新增 plugins 表（插件系统）
     */
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS plugins (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    name TEXT NOT NULL,
                    version TEXT NOT NULL,
                    description TEXT NOT NULL,
                    author TEXT NOT NULL,
                    enabled INTEGER NOT NULL,
                    isBuiltin INTEGER NOT NULL,
                    rulesJson TEXT NOT NULL
                )
                """.trimIndent()
            )
        }
    }

    /**
     * v3 → v4：
     * - chat_messages 新增 narration（联动旁白）
     * - character_states 新增 statusSchema（按卡状态栏模板）
     * - 新增 asr_configs 表（网络语音识别）
     */
    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE chat_messages ADD COLUMN narration TEXT")
            db.execSQL(
                "ALTER TABLE character_states ADD COLUMN statusSchema TEXT NOT NULL DEFAULT ''"
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS asr_configs (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    name TEXT NOT NULL,
                    baseUrl TEXT NOT NULL,
                    apiKey TEXT NOT NULL,
                    modelName TEXT NOT NULL,
                    language TEXT NOT NULL,
                    isDefault INTEGER NOT NULL
                )
                """.trimIndent()
            )
        }
    }

    /**
     * v4 → v5：chat_messages 新增 statusPanel（文字状态栏）
     */
    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE chat_messages ADD COLUMN statusPanel TEXT")
        }
    }

    /**
     * v5 → v6：chat_messages 新增 isIm / imContent（手机聊天双视图）
     */
    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE chat_messages ADD COLUMN isIm INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE chat_messages ADD COLUMN imContent TEXT")
        }
    }

    /**
     * v6 → v7：plugins 新增 characterCardId（角色脚本绑定角色卡）
     */
    val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE plugins ADD COLUMN characterCardId INTEGER")
        }
    }
}
