package dev.russell.audiolimit.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [UsageSegment::class, RuleEntity::class], version = AppDatabase.VERSION, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun usageDao(): UsageDao
    abstract fun ruleDao(): RuleDao

    companion object {
        const val VERSION = 1

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "audio_limit.db").build()
    }
}
