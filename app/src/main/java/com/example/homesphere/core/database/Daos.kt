package com.example.homesphere.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ActivityEventDao {
    @Query("SELECT * FROM activity_events ORDER BY timestamp DESC")
    fun observeAllEvents(): Flow<List<ActivityEventEntity>>

    @Query("SELECT * FROM activity_events WHERE category = :category ORDER BY timestamp DESC LIMIT :limit")
    fun observeEventsByCategory(category: String, limit: Int = 50): Flow<List<ActivityEventEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: ActivityEventEntity): Long

    @Query("DELETE FROM activity_events")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM activity_events")
    suspend fun count(): Int
}

@Dao
interface AutomationRuleDao {
    @Query("SELECT * FROM automation_rules ORDER BY createdAt DESC")
    fun observeAllRules(): Flow<List<AutomationRuleEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRule(rule: AutomationRuleEntity): Long

    @Update
    suspend fun updateRule(rule: AutomationRuleEntity)

    @Query("DELETE FROM automation_rules WHERE id = :id")
    suspend fun deleteRuleById(id: Long)

    @Query("SELECT COUNT(*) FROM automation_rules")
    suspend fun count(): Int
}

@Dao
interface WaterHistoryDao {
    @Query("SELECT * FROM water_history ORDER BY timestamp ASC LIMIT 60")
    fun observeHistory(): Flow<List<WaterHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReading(reading: WaterHistoryEntity): Long

    @Query("SELECT COUNT(*) FROM water_history")
    suspend fun count(): Int
}

@Dao
interface ConnectedDeviceDao {
    @Query("SELECT * FROM connected_devices ORDER BY isOnline DESC, lastSeen DESC")
    fun observeDevices(): Flow<List<ConnectedDeviceEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDevice(device: ConnectedDeviceEntity): Long

    @Query("DELETE FROM connected_devices WHERE id = :id")
    suspend fun deleteDeviceById(id: Long)

    @Query("SELECT COUNT(*) FROM connected_devices")
    suspend fun count(): Int
}
