package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.StandbyBotEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StandbyBotDao {

    @Query("SELECT * FROM standby_bots ORDER BY addedDate DESC")
    fun getAllStandbyBots(): Flow<List<StandbyBotEntity>>

    @Query("SELECT * FROM standby_bots ORDER BY addedDate DESC")
    suspend fun getStandbyBotsList(): List<StandbyBotEntity>

    @Query("SELECT * FROM standby_bots WHERE id = :id")
    suspend fun getById(id: String): StandbyBotEntity?

    @Query("SELECT * FROM standby_bots WHERE isVerifiedMember = 1 ORDER BY addedDate DESC")
    suspend fun getVerifiedBots(): List<StandbyBotEntity>

    @Query("SELECT COUNT(*) FROM standby_bots WHERE isVerifiedMember = 1")
    fun getVerifiedBotsCountFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM standby_bots")
    fun getTotalBotsCountFlow(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(bot: StandbyBotEntity)

    @Update
    suspend fun update(bot: StandbyBotEntity)

    @Query("UPDATE standby_bots SET isVerifiedMember = :isVerified, lastVerifiedDate = :timestamp, channelId = :channelId, username = COALESCE(:username, username) WHERE id = :id")
    suspend fun updateVerification(id: String, isVerified: Boolean, timestamp: Long, channelId: String, username: String? = null)

    @Query("UPDATE standby_bots SET label = :label WHERE id = :id")
    suspend fun updateLabel(id: String, label: String)

    @Delete
    suspend fun delete(bot: StandbyBotEntity)

    @Query("DELETE FROM standby_bots WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM standby_bots")
    suspend fun clearAll()
}
