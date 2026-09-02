package com.lockin.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

class Converters {
    @TypeConverter fun fromDuration(v: TaskDuration): String = v.name
    @TypeConverter fun toDuration(v: String): TaskDuration = TaskDuration.valueOf(v)

    @TypeConverter fun fromRecurrence(v: TaskRecurrence): String = v.name
    @TypeConverter fun toRecurrence(v: String): TaskRecurrence = TaskRecurrence.valueOf(v)

    @TypeConverter fun fromVerification(v: VerificationMethod): String = v.name
    @TypeConverter fun toVerification(v: String): VerificationMethod = VerificationMethod.valueOf(v)
}

@Database(
    entities = [Task::class, LockedApp::class, TokenState::class, TaskAppLink::class],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun lockedAppDao(): LockedAppDao
    abstract fun tokenDao(): TokenDao
    abstract fun taskAppLinkDao(): TaskAppLinkDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "lockin.db"
                )
                    // v1 never shipped in a working state, so there is no real
                    // data to migrate. Export your tasks before upgrading past
                    // this point, or write a proper Migration.
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
