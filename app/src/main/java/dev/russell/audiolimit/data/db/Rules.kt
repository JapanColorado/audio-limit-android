package dev.russell.audiolimit.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** [kind] is a RuleKind name; [packagesJson] and [windowsJson] are kotlinx-serialization JSON. */
@Entity(tableName = "rule")
data class RuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val kind: String,
    val packagesJson: String,
    val limitMinutes: Int?,
    val limitDaysMask: Int,
    val windowsJson: String,
)

@Dao
interface RuleDao {
    @Query("SELECT * FROM rule ORDER BY id")
    fun observeAll(): Flow<List<RuleEntity>>

    @Query("SELECT * FROM rule ORDER BY id")
    suspend fun getAll(): List<RuleEntity>

    @Query("SELECT * FROM rule WHERE id = :id")
    suspend fun get(id: Long): RuleEntity?

    @Upsert
    suspend fun upsert(rule: RuleEntity): Long

    @Query("DELETE FROM rule WHERE id = :id")
    suspend fun delete(id: Long)
}
