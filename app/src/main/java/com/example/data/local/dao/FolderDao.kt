package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update
import com.example.data.local.entity.FileEntity
import com.example.data.local.entity.FolderEntity
import kotlinx.coroutines.flow.Flow

data class FolderWithSubfolders(
    @Embedded val folder: FolderEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "parentFolderId"
    )
    val subfolders: List<FolderEntity>
)

data class FolderWithFiles(
    @Embedded val folder: FolderEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "folderId"
    )
    val files: List<FileEntity>
)

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

    @Query("SELECT * FROM folders WHERE id = :folderId")
    fun observeById(folderId: String): Flow<FolderEntity?>

    @Query("SELECT * FROM folders WHERE parentFolderId IS NULL ORDER BY name ASC")
    fun observeRootFolders(): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders WHERE parentFolderId IS NULL ORDER BY name ASC")
    suspend fun getRootFolders(): List<FolderEntity>

    @Query("SELECT * FROM folders WHERE parentFolderId IS :parentId ORDER BY name ASC")
    fun observeSubfolders(parentId: String?): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders WHERE parentFolderId IS :parentId ORDER BY name ASC")
    suspend fun getSubfolders(parentId: String?): List<FolderEntity>

    @Query("SELECT * FROM folders ORDER BY name ASC")
    fun observeAll(): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders ORDER BY name ASC")
    suspend fun getAll(): List<FolderEntity>

    @Query("SELECT * FROM folders WHERE name = :name AND parentFolderId IS :parentId LIMIT 1")
    suspend fun getByNameAndParent(name: String, parentId: String?): FolderEntity?

    @Query("SELECT COUNT(*) FROM folders")
    fun observeFolderCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM folders")
    suspend fun getFolderCount(): Int

    @Query("UPDATE folders SET name = :newName WHERE id = :folderId")
    suspend fun renameFolder(folderId: String, newName: String)

    @Query("UPDATE folders SET parentFolderId = :newParentId WHERE id = :folderId")
    suspend fun moveFolder(folderId: String, newParentId: String?)

    // File movement and inspection within folders
    @Query("UPDATE files SET folderId = :newFolderId WHERE id = :fileId")
    suspend fun moveFile(fileId: String, newFolderId: String?)

    @Query("UPDATE files SET folderId = :newFolderId WHERE id IN (:fileIds)")
    suspend fun moveFiles(fileIds: List<String>, newFolderId: String?)

    @Query("SELECT * FROM files WHERE folderId IS :folderId ORDER BY name ASC")
    suspend fun getFilesInFolder(folderId: String?): List<FileEntity>

    @Query("SELECT * FROM files WHERE folderId IS :folderId ORDER BY name ASC")
    fun observeFilesInFolder(folderId: String?): Flow<List<FileEntity>>

    @Query("SELECT COUNT(*) FROM files WHERE folderId IS :folderId")
    suspend fun countFilesInFolder(folderId: String?): Int

    @Transaction
    @Query("SELECT * FROM folders WHERE id = :folderId")
    suspend fun getFolderWithSubfolders(folderId: String): FolderWithSubfolders?

    @Transaction
    @Query("SELECT * FROM folders WHERE id = :folderId")
    fun observeFolderWithSubfolders(folderId: String): Flow<FolderWithSubfolders?>

    @Transaction
    @Query("SELECT * FROM folders WHERE id = :folderId")
    suspend fun getFolderWithFiles(folderId: String): FolderWithFiles?

    @Transaction
    @Query("SELECT * FROM folders WHERE id = :folderId")
    fun observeFolderWithFiles(folderId: String): Flow<FolderWithFiles?>

    @Query("DELETE FROM folders")
    suspend fun clearAll()
}
