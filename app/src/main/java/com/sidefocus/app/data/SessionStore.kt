package com.sidefocus.app.data

import android.content.Context
import androidx.room.*
import com.sidefocus.core.StudySession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

val AppJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

@Entity(tableName = "sessions")
data class SessionRow(
    @PrimaryKey val id: String,
    val startedAt: Long,
    val payload: String,
    val active: Boolean = false,
    val syncStatus: String = "PENDING",
    val syncError: String? = null
) {
    fun session(): StudySession = AppJson.decodeFromString(payload)
}

@Dao
interface SessionDao {
    @Query("SELECT * FROM sessions WHERE active = 0 ORDER BY startedAt DESC")
    fun observeFinished(): Flow<List<SessionRow>>
    @Query("SELECT * FROM sessions WHERE active = 1")
    suspend fun interrupted(): List<SessionRow>
    @Query("SELECT * FROM sessions WHERE active = 0 AND syncStatus = 'PENDING'")
    suspend fun pending(): List<SessionRow>
    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun find(id: String): SessionRow?
    @Upsert suspend fun save(row: SessionRow)
    @Query("UPDATE sessions SET syncStatus = :status, syncError = :error WHERE id = :id")
    suspend fun markSync(id: String, status: String, error: String? = null)
    @Query("UPDATE sessions SET syncStatus = 'PENDING', syncError = NULL WHERE syncStatus = 'ERROR'")
    suspend fun retryErrors()
    @Query("DELETE FROM sessions WHERE id = :id") suspend fun delete(id: String)
    @Query("DELETE FROM sessions") suspend fun deleteAll()
}

@Database(entities = [SessionRow::class], version = 1, exportSchema = true)
abstract class FocusDatabase : RoomDatabase() {
    abstract fun sessions(): SessionDao
    companion object {
        fun open(context: Context): FocusDatabase = Room.databaseBuilder(context, FocusDatabase::class.java, "sidefocus.db").build()
    }
}

class SessionStore(val dao: SessionDao) {
    val rows = dao.observeFinished()
    val sessions: Flow<List<StudySession>> = rows.map { rows -> rows.map { it.session() } }

    suspend fun checkpoint(session: StudySession, active: Boolean) {
        dao.save(SessionRow(session.id, session.startedAt, AppJson.encodeToString(session), active,
            if (session.demo) "DEMO" else "PENDING"))
    }

    /** Recover only through the last durable checkpoint; never invent time after process death. */
    suspend fun recoverInterrupted() {
        dao.interrupted().forEach { row ->
            val session = row.session().copy(recovered = true)
            dao.save(row.copy(payload = AppJson.encodeToString(session), active = false))
        }
    }
}
