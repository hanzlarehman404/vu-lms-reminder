package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.data.model.ActivityEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ActivityDao {

    @Query("SELECT * FROM activities ORDER BY endDate ASC")
    fun getAllActivities(): Flow<List<ActivityEntity>>

    @Query("SELECT * FROM activities WHERE isCompleted = 0 AND endDate > :currentTime ORDER BY endDate ASC")
    fun getPendingUpcomingActivities(currentTime: Long): Flow<List<ActivityEntity>>

    @Query("SELECT * FROM activities WHERE isCompleted = 0 AND endDate > :currentTime ORDER BY endDate ASC")
    suspend fun getPendingUpcomingActivitiesSync(currentTime: Long): List<ActivityEntity>

    @Query("SELECT * FROM activities WHERE isCompleted = 0 AND startDate <= :currentTime AND endDate > :currentTime ORDER BY endDate ASC")
    suspend fun getAvailablePendingActivitiesSync(currentTime: Long): List<ActivityEntity>

    @Query("SELECT * FROM activities WHERE id = :id LIMIT 1")
    suspend fun getActivityById(id: String): ActivityEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(activity: ActivityEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateAll(activities: List<ActivityEntity>)

    @Query("UPDATE activities SET isCompleted = :completed WHERE id = :id")
    suspend fun setCompleted(id: String, completed: Boolean)

    @Query("UPDATE activities SET isCompleted = 1 WHERE startDate <= :currentTime AND endDate >= :currentTime AND isCompleted = 0")
    suspend fun markAllAvailableCompleted(currentTime: Long)

    @Query("UPDATE activities SET isCompleted = 0")
    suspend fun resetAllCompletions()

    @Query("DELETE FROM activities WHERE endDate < :cutoffMillis")
    suspend fun deleteOldExpiredActivities(cutoffMillis: Long)

    @Query("DELETE FROM activities WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM activities")
    suspend fun clearAll()

    @Transaction
    suspend fun upsertPreservingCompletion(
        newActivities: List<ActivityEntity>,
        autoCompletedIds: Set<String> = emptySet()
    ) {
        for (item in newActivities) {
            val existing = getActivityById(item.id)
            val shouldBeCompleted = (existing?.isCompleted == true) || autoCompletedIds.contains(item.id)
            insertOrUpdate(item.copy(isCompleted = shouldBeCompleted))
        }
    }
}
