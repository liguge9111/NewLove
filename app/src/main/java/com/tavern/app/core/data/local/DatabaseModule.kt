package com.tavern.app.core.data.local

import android.content.Context
import androidx.room.Room
import com.tavern.app.core.data.local.dao.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Room 数据库依赖注入模块
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): TavernDatabase =
        Room.databaseBuilder(
            context,
            TavernDatabase::class.java,
            TavernDatabase.DATABASE_NAME
        )
            .addMigrations(
                Migrations.MIGRATION_1_2,
                Migrations.MIGRATION_2_3,
                Migrations.MIGRATION_3_4,
                Migrations.MIGRATION_4_5,
                Migrations.MIGRATION_5_6,
                Migrations.MIGRATION_6_7
            )
            .build()

    @Provides
    fun provideCharacterCardDao(db: TavernDatabase): CharacterCardDao = db.characterCardDao()

    @Provides
    fun provideChatDao(db: TavernDatabase): ChatDao = db.chatDao()

    @Provides
    fun provideProviderDao(db: TavernDatabase): ProviderDao = db.providerDao()

    @Provides
    fun provideCharacterStateDao(db: TavernDatabase): CharacterStateDao = db.characterStateDao()

    @Provides
    fun providePluginDao(db: TavernDatabase): PluginDao = db.pluginDao()
}
