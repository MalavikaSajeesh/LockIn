package com.lockin.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskAppLinkDao {
    @Query("SELECT * FROM task_app_links")
    fun observeAll(): Flow<List<TaskAppLink>>

    @Query("SELECT * FROM task_app_links")
    suspend fun getAllOnce(): List<TaskAppLink>

    @Query("SELECT * FROM task_app_links WHERE taskId = :taskId")
    suspend fun forTask(taskId: Long): List<TaskAppLink>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(links: List<TaskAppLink>)

    @Query("DELETE FROM task_app_links WHERE taskId = :taskId")
    suspend fun clearForTask(taskId: Long)

    @Query("DELETE FROM task_app_links WHERE packageName = :packageName")
    suspend fun clearForPackage(packageName: String)
}
