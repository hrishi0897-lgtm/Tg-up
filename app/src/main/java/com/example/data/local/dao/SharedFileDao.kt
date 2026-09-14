package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.SharedFileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SharedFileDao {

    @Query("SELECT * FROM shared_files ORDER BY createdDate DESC")
    fun getAllSharedFiles(): Flow<List<SharedFileEntity>>

    @Query("SELECT * FROM shared_files WHERE revoked = 0 ORDER BY createdDate DESC")
    fun getActiveSharedFiles(): Flow<List<SharedFileEntity>>

    @Query("SELECT * FROM shared_files WHERE fileId = :fileId ORDER BY createdDate DESC")
    fun getSharesForFile(fileId: String): Flow<List<SharedFileEntity>>

    @Query("SELECT * FROM shared_files WHERE fileId = :fileId AND revoked = 0 ORDER BY createdDate DESC LIMIT 1")
    suspend fun getActiveShareForFile(fileId: String): SharedFileEntity?

    @Query("SELECT * FROM shared_files WHERE id = :id")
    suspend fun getById(id: String): SharedFileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(share: SharedFileEntity)

    @Update
    suspend fun update(share: SharedFileEntity)

    @Query("UPDATE shared_files SET revoked = 1 WHERE id = :id")
    suspend fun markRevoked(id: String)

    @Delete
    suspend fun delete(share: SharedFileEntity)

    @Query("DELETE FROM shared_files WHERE id = :id")
    suspend fun deleteById(id: String)
}
