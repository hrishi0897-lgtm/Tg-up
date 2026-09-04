package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.FileEntity
import com.example.data.local.entity.FileStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface FileDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(file: FileEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(files: List<FileEntity>)

    @Update
    suspend fun update(file: FileEntity)

    @Delete
    suspend fun delete(file: FileEntity)

    @Query("DELETE FROM files WHERE id = :fileId")
    suspend fun deleteById(fileId: String)

    @Query("SELECT * FROM files WHERE id = :fileId")
    suspend fun getById(fileId: String): FileEntity?

    @Query("SELECT * FROM files WHERE folderId IS :folderId ORDER BY uploadDate DESC")
    fun observeByFolder(folderId: String?): Flow<List<FileEntity>>

    @Query("SELECT * FROM files WHERE folderId IS :folderId ORDER BY uploadDate DESC")
    suspend fun getByFolder(folderId: String?): List<FileEntity>

    @Query("SELECT * FROM files ORDER BY uploadDate DESC")
    fun observeAll(): Flow<List<FileEntity>>

    @Query("SELECT * FROM files ORDER BY uploadDate DESC")
    suspend fun getAll(): List<FileEntity>

    @Query("SELECT * FROM files WHERE name LIKE '%' || :query || '%' ORDER BY uploadDate DESC")
    fun searchFiles(query: String): Flow<List<FileEntity>>

    @Query("SELECT * FROM files WHERE status IN (:statuses) ORDER BY uploadDate ASC")
    suspend fun getFilesByStatus(statuses: List<FileStatus>): List<FileEntity>

    @Query("SELECT * FROM files WHERE status IN (:statuses) ORDER BY uploadDate DESC")
    fun observeTransfers(statuses: List<FileStatus>): Flow<List<FileEntity>>

    @Query("SELECT COALESCE(SUM(size), 0) FROM files WHERE status = 'COMPLETED'")
    fun observeTotalStorageUsed(): Flow<Long>

    @Query("SELECT COALESCE(SUM(size), 0) FROM files WHERE status = 'COMPLETED'")
    suspend fun getTotalStorageUsed(): Long

    @Query("SELECT COUNT(*) FROM files WHERE status = 'COMPLETED'")
    fun observeCompletedFileCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM files WHERE status = 'COMPLETED'")
    suspend fun getCompletedFileCount(): Int

    @Query("UPDATE files SET folderId = :newFolderId WHERE id = :fileId")
    suspend fun moveFile(fileId: String, newFolderId: String?)

    @Query("UPDATE files SET status = :status, errorMessage = :error WHERE id = :fileId")
    suspend fun updateStatus(fileId: String, status: FileStatus, error: String? = null)

    @Query("UPDATE files SET completedChunks = :completed, status = :status WHERE id = :fileId")
    suspend fun updateProgress(fileId: String, completed: Int, status: FileStatus)

    @Query("UPDATE files SET manifestMessageId = :manifestMessageId WHERE id = :fileId")
    suspend fun updateManifestId(fileId: String, manifestMessageId: Long)

    @Query("UPDATE files SET localPath = :localPath, status = 'COMPLETED' WHERE id = :fileId")
    suspend fun markDownloaded(fileId: String, localPath: String)

    @Query("DELETE FROM files")
    suspend fun clearAll()
}
