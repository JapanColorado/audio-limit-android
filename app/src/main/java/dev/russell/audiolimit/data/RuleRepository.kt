package dev.russell.audiolimit.data

import dev.russell.audiolimit.data.db.RuleDao
import dev.russell.audiolimit.data.db.RuleEntity
import dev.russell.audiolimit.domain.BlockWindow
import dev.russell.audiolimit.domain.DayMask
import dev.russell.audiolimit.domain.Rule
import dev.russell.audiolimit.domain.RuleKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Raw rule storage. Edits from the UI must go through LockGuard, which enforces locks. */
class RuleRepository(private val dao: RuleDao) {
    fun observeAll(): Flow<List<Rule>> = dao.observeAll().map { list -> list.map { it.toDomain() } }
    suspend fun getAll(): List<Rule> = dao.getAll().map { it.toDomain() }
    suspend fun get(id: Long): Rule? = dao.get(id)?.toDomain()
    suspend fun save(rule: Rule): Long = dao.upsert(rule.toEntity()).let { if (it == -1L) rule.id else it }
    suspend fun delete(id: Long) = dao.delete(id)
}

@Serializable
private data class WindowJson(val start: Int, val end: Int, val days: Int)

private fun RuleEntity.toDomain() = Rule(
    id = id,
    name = name,
    kind = RuleKind.valueOf(kind),
    packages = Json.decodeFromString<List<String>>(packagesJson).toSet(),
    limitMinutes = limitMinutes,
    limitDays = DayMask.days(limitDaysMask),
    windows = Json.decodeFromString<List<WindowJson>>(windowsJson)
        .map { BlockWindow(it.start, it.end, DayMask.days(it.days)) },
)

private fun Rule.toEntity() = RuleEntity(
    id = id,
    name = name,
    kind = kind.name,
    packagesJson = Json.encodeToString(packages.sorted()),
    limitMinutes = limitMinutes,
    limitDaysMask = DayMask.of(limitDays),
    windowsJson = Json.encodeToString(windows.map { WindowJson(it.startMinute, it.endMinute, DayMask.of(it.days)) }),
)
