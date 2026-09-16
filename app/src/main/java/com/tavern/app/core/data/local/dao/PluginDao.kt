package com.tavern.app.core.data.local.dao

import androidx.room.*
import com.tavern.app.core.data.local.entity.PluginEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PluginDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(plugin: PluginEntity): Long

    @Update
    suspend fun update(plugin: PluginEntity)

    @Delete
    suspend fun delete(plugin: PluginEntity)

    @Query("DELETE FROM plugins WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM plugins WHERE id = :id")
    suspend fun getById(id: Long): PluginEntity?

    @Query("SELECT * FROM plugins ORDER BY id ASC")
    fun getAllFlow(): Flow<List<PluginEntity>>

    @Query("SELECT * FROM plugins WHERE enabled = 1 ORDER BY id ASC")
    suspend fun getEnabled(): List<PluginEntity>
}
