package com.lockin.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "token_state")
data class TokenState(
    @PrimaryKey val id: Int = 0, // singleton row
    val tokensRemaining: Int = 10,
    val weekStartEpochDay: Long,
    /** Epoch millis when an active token skip expires, or null. */
    val activeSkipExpiresAtEpochMillis: Long? = null
)
