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

    @Query("SELECT * FROM files WHERE folderId IS :folderId AND deletedAt IS NULL ORDER BY uploadDate DESC")
    fun observeByFolder(folderId: String?): Flow<List<FileEntity>>

    @Query("SELECT * FROM files WHERE folderId IS :folderId AND deletedAt IS NULL ORDER BY uploadDate DESC")
    suspend fun getByFolder(folderId: String?): List<FileEntity>

    @Query("SELECT * FROM files WHERE deletedAt IS NULL ORDER BY uploadDate DESC")
    fun observeAll(): Flow<List<FileEntity>>

    @Query("SELECT name, mimeType, size FROM files WHERE status = 'COMPLETED' AND deletedAt IS NULL")
    fun observeCompletedFilesCategoryData(): Flow<List<FileCategoryProjection>>

    @Query("SELECT * FROM files WHERE deletedAt IS NULL ORDER BY uploadDate DESC")
    suspend fun getAll(): List<FileEntity>

    @Query("SELECT * FROM files WHERE name LIKE '%' || :query || '%' AND deletedAt IS NULL ORDER BY uploadDate DESC")
    fun searchFiles(query: String): Flow<List<FileEntity>>

    @Query("SELECT * FROM files WHERE status IN (:statuses) AND deletedAt IS NULL ORDER BY uploadDate ASC")
    suspend fun getFilesByStatus(statuses: List<FileStatus>): List<FileEntity>

    @Query("SELECT * FROM files WHERE status IN (:statuses) AND deletedAt IS NULL ORDER BY uploadDate DESC")
    fun observeTransfers(statuses: List<FileStatus>): Flow<List<FileEntity>>

    @Query("SELECT COALESCE(SUM(size), 0) FROM files WHERE status = 'COMPLETED' AND deletedAt IS NULL")
    fun observeTotalStorageUsed(): Flow<Long>

    @Query("SELECT COALESCE(SUM(size), 0) FROM files WHERE status = 'COMPLETED' AND deletedAt IS NULL")
    suspend fun getTotalStorageUsed(): Long

    @Query("SELECT COUNT(*) FROM files WHERE status = 'COMPLETED' AND deletedAt IS NULL")
    fun observeCompletedFileCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM files WHERE status = 'COMPLETED' AND deletedAt IS NULL")
    suspend fun getCompletedFileCount(): Int

    // -----------------------------------------------------------------
    // Trash / Recycle Bin Queries & Mutations
    // -----------------------------------------------------------------

    @Query("SELECT * FROM files WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    fun observeTrashFiles(): Flow<List<FileEntity>>

    @Query("SELECT * FROM files WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    suspend fun getTrashFiles(): List<FileEntity>

    @Query("SELECT COUNT(*) FROM files WHERE deletedAt IS NOT NULL")
    fun observeTrashCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM files WHERE deletedAt IS NOT NULL")
    suspend fun getTrashCount(): Int

    @Query("UPDATE files SET deletedAt = :timestamp WHERE id = :fileId")
    suspend fun moveToTrash(fileId: String, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE files SET deletedAt = :timestamp WHERE id IN (:fileIds)")
    suspend fun bulkMoveToTrash(fileIds: List<String>, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE files SET deletedAt = NULL WHERE id = :fileId")
    suspend fun restoreFromTrash(fileId: String)

    @Query("UPDATE files SET deletedAt = NULL WHERE id IN (:fileIds)")
    suspend fun bulkRestoreFromTrash(fileIds: List<String>)

    @Query("SELECT * FROM files WHERE deletedAt IS NOT NULL AND deletedAt <= :thresholdTimestamp")
    suspend fun getExpiredTrashFiles(thresholdTimestamp: Long): List<FileEntity>

    @Query("UPDATE files SET name = :newName WHERE id = :fileId")
    suspend fun renameFile(fileId: String, newName: String)

    @Query("UPDATE files SET folderId = :newFolderId WHERE id = :fileId")
    suspend fun moveFile(fileId: String, newFolderId: String?)

    @Query("UPDATE files SET status = :status, errorMessage = :error WHERE id = :fileId")
    suspend fun updateStatus(fileId: String, status: FileStatus, error: String? = null)

    @Query("UPDATE files SET completedChunks = :completed, status = :status WHERE id = :fileId")
    suspend fun updateProgress(fileId: String, completed: Int, status: FileStatus)

    @Query("UPDATE files SET manifestMessageId = :manifestMessageId, channelId = COALESCE(:channelId, channelId) WHERE id = :fileId")
    suspend fun updateManifestId(fileId: String, manifestMessageId: Long, channelId: String? = null)

    @Query("UPDATE files SET localPath = :localPath, localUri = :localUri, status = 'COMPLETED' WHERE id = :fileId")
    suspend fun markDownloaded(fileId: String, localPath: String, localUri: String? = localPath)

    @Query("UPDATE files SET channelId = :channelId WHERE status = 'COMPLETED'")
    suspend fun updateChannelForCompletedFiles(channelId: String)

    @Query("UPDATE files SET thumbnailFileId = :fileIdRemote, thumbnailMessageId = :messageId, thumbnailLocalPath = :localPath WHERE id = :fileId")
    suspend fun updateThumbnailInfo(fileId: String, fileIdRemote: String?, messageId: Long?, localPath: String?)

    @Query("UPDATE files SET thumbnailLocalPath = :localPath WHERE id = :fileId")
    suspend fun updateThumbnailLocalPath(fileId: String, localPath: String?)

    @Query("DELETE FROM files")
    suspend fun clearAll()
}

data class FileCategoryProjection(
    val name: String,
    val mimeType: String,
    val size: Long
)
