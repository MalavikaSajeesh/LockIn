package com.lockin.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TokenDao {
    @Query("SELECT * FROM token_state WHERE id = 0")
    fun observe(): Flow<TokenState?>

    @Query("SELECT * FROM token_state WHERE id = 0")
    suspend fun get(): TokenState?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: TokenState)
}
