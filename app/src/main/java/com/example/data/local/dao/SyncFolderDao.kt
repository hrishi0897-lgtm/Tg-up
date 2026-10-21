package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.SyncFolderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncFolderDao {

    @Query("SELECT * FROM sync_folders ORDER BY localDisplayName ASC")
    fun observeAll(): Flow<List<SyncFolderEntity>>

    @Query("SELECT * FROM sync_folders ORDER BY localDisplayName ASC")
    suspend fun getAll(): List<SyncFolderEntity>

    @Query("SELECT * FROM sync_folders WHERE id = :id")
    suspend fun getById(id: String): SyncFolderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(syncFolder: SyncFolderEntity)

    @Update
    suspend fun update(syncFolder: SyncFolderEntity)

    @Delete
    suspend fun delete(syncFolder: SyncFolderEntity)

    @Query("DELETE FROM sync_folders WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE sync_folders SET autoSyncEnabled = :enabled WHERE id = :id")
    suspend fun setAutoSyncEnabled(id: String, enabled: Boolean)

    @Query("UPDATE sync_folders SET encryptOnSync = :encrypt WHERE id = :id")
    suspend fun setEncryptOnSync(id: String, encrypt: Boolean)
}
