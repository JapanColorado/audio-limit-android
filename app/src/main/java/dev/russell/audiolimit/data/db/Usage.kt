package dev.russell.audiolimit.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * One stretch of continuous playback. The tracker inserts the row when playback starts and keeps
 * pushing [endMillis] forward while it continues, so a crash loses at most one flush interval.
 */
@Entity(tableName = "usage_segment", indices = [Index("endMillis")])
data class UsageSegment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val startMillis: Long,
    val endMillis: Long,
)

@Dao
interface UsageDao {
    @Insert
    suspend fun insert(segment: UsageSegment): Long

    @Query("UPDATE usage_segment SET endMillis = :end WHERE id = :id")
    suspend fun extend(id: Long, end: Long)

    @Query("SELECT * FROM usage_segment WHERE endMillis > :from AND startMillis < :to")
    suspend fun overlapping(from: Long, to: Long): List<UsageSegment>

    @Query("SELECT DISTINCT packageName FROM usage_segment")
    suspend fun mediaPackages(): List<String>

    /** Emits on every insert/extend; used as a change signal. */
    @Query("SELECT MAX(endMillis) FROM usage_segment")
    fun observeLatestEnd(): Flow<Long?>
}
