package com.tavern.app.core.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.tavern.app.core.data.local.dao.*
import com.tavern.app.core.data.local.entity.*

/**
 * 酒馆APP 数据库
 *
 * 纯本地存储，包含角色卡、聊天、模型/生图/语音配置、角色状态等表。
 */
@Database(
    entities = [
        CharacterCardEntity::class,
        ChatSessionEntity::class,
        ChatMessageEntity::class,
        ModelConfigEntity::class,
        ImageConfigEntity::class,
        VoiceConfigEntity::class,
        CharacterStateEntity::class,
        PluginEntity::class,
        AsrConfigEntity::class
    ],
    version = 7,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class TavernDatabase : RoomDatabase() {

    abstract fun characterCardDao(): CharacterCardDao
    abstract fun chatDao(): ChatDao
    abstract fun providerDao(): ProviderDao
    abstract fun characterStateDao(): CharacterStateDao
    abstract fun pluginDao(): PluginDao

    companion object {
        const val DATABASE_NAME = "tavern.db"
    }
}
