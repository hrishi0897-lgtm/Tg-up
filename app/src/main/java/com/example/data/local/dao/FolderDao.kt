package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.FolderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FolderDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(folder: FolderEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(folders: List<FolderEntity>)

    @Update
    suspend fun update(folder: FolderEntity)

    @Delete
    suspend fun delete(folder: FolderEntity)

    @Query("DELETE FROM folders WHERE id = :folderId")
    suspend fun deleteById(folderId: String)

    @Query("SELECT * FROM folders WHERE id = :folderId")
    suspend fun getById(folderId: String): FolderEntity?

    @Query("SELECT * FROM folders WHERE parentFolderId IS :parentId ORDER BY name ASC")
    fun observeSubfolders(parentId: String?): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders WHERE parentFolderId IS :parentId ORDER BY name ASC")
    suspend fun getSubfolders(parentId: String?): List<FolderEntity>

    @Query("SELECT * FROM folders ORDER BY name ASC")
    fun observeAll(): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders ORDER BY name ASC")
    suspend fun getAll(): List<FolderEntity>

    @Query("SELECT COUNT(*) FROM folders")
    fun observeFolderCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM folders")
    suspend fun getFolderCount(): Int

    @Query("UPDATE folders SET name = :newName WHERE id = :folderId")
    suspend fun renameFolder(folderId: String, newName: String)

    @Query("UPDATE folders SET parentFolderId = :newParentId WHERE id = :folderId")
    suspend fun moveFolder(folderId: String, newParentId: String?)

    @Query("DELETE FROM folders")
    suspend fun clearAll()
}
