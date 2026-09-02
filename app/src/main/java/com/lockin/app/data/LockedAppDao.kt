package com.lockin.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface LockedAppDao {
    @Query("SELECT * FROM locked_apps ORDER BY appLabel COLLATE NOCASE ASC")
    fun getAll(): Flow<List<LockedApp>>

    @Query("SELECT * FROM locked_apps")
    suspend fun getAllOnce(): List<LockedApp>

    @Query("SELECT * FROM locked_apps WHERE packageName = :packageName")
    suspend fun getByPackage(packageName: String): LockedApp?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(app: LockedApp)

    @Delete
    suspend fun delete(app: LockedApp)

    @Update
    suspend fun update(app: LockedApp)

    /**
     * Adds elapsed seconds to today's counter, resetting first if the stored
     * counter belongs to a previous day. Done in SQL so the read-modify-write
     * cannot interleave with another call.
     */
    @Query(
        """
        UPDATE locked_apps
        SET secondsUsedToday = CASE WHEN usageEpochDay = :epochDay
                                    THEN secondsUsedToday + :seconds
                                    ELSE :seconds END,
            usageEpochDay = :epochDay
        WHERE packageName = :packageName
        """
    )
    suspend fun addUsage(packageName: String, seconds: Int, epochDay: Long)
}
