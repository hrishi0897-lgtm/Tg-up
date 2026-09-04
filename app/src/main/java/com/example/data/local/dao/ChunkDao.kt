package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.ChunkEntity

@Dao
interface ChunkDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(chunk: ChunkEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(chunks: List<ChunkEntity>)

    @Update
    suspend fun update(chunk: ChunkEntity)

    @Query("SELECT * FROM chunks WHERE fileId = :fileId ORDER BY chunkIndex ASC")
    suspend fun getChunksForFile(fileId: String): List<ChunkEntity>

    @Query("SELECT * FROM chunks WHERE fileId = :fileId AND chunkIndex = :chunkIndex")
    suspend fun getChunk(fileId: String, chunkIndex: Int): ChunkEntity?

    @Query("SELECT COUNT(*) FROM chunks WHERE fileId = :fileId AND isUploaded = 1")
    suspend fun getUploadedChunkCount(fileId: String): Int

    @Query("SELECT COUNT(*) FROM chunks WHERE fileId = :fileId AND isDownloaded = 1")
    suspend fun getDownloadedChunkCount(fileId: String): Int

    @Query("UPDATE chunks SET telegramMessageId = :messageId, telegramFileId = :fileIdRemote, isUploaded = 1 WHERE fileId = :fileId AND chunkIndex = :chunkIndex")
    suspend fun markChunkUploaded(
        fileId: String,
        chunkIndex: Int,
        messageId: Long,
        fileIdRemote: String
    )

    @Query("UPDATE chunks SET isDownloaded = 1 WHERE fileId = :fileId AND chunkIndex = :chunkIndex")
    suspend fun markChunkDownloaded(fileId: String, chunkIndex: Int)

    @Query("DELETE FROM chunks WHERE fileId = :fileId")
    suspend fun deleteForFile(fileId: String)

    @Query("DELETE FROM chunks")
    suspend fun clearAll()
}
