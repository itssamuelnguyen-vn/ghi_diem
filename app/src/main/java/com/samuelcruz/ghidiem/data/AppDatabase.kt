package com.samuelcruz.ghidiem.data

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MatchDao {
    @Query("SELECT * FROM matches ORDER BY createdAt DESC")
    fun all(): Flow<List<MatchRow>>

    @Query("SELECT * FROM matches WHERE id = :id")
    fun watch(id: Long): Flow<MatchRow?>

    @Query("SELECT * FROM matches WHERE id = :id")
    suspend fun get(id: Long): MatchRow?

    @Insert
    suspend fun insert(m: MatchRow): Long

    @Update
    suspend fun update(m: MatchRow)

    @Delete
    suspend fun delete(m: MatchRow)

    @Query("SELECT * FROM events WHERE matchId = :id ORDER BY id")
    fun events(id: Long): Flow<List<EventRow>>

    @Query("SELECT * FROM events WHERE matchId = :id ORDER BY id")
    suspend fun eventsOnce(id: Long): List<EventRow>

    @Insert
    suspend fun insert(e: EventRow): Long

    @Update
    suspend fun update(e: EventRow)

    @Delete
    suspend fun delete(e: EventRow)

    /** Names typed before, most used first, for the new-match form. */
    @Query(
        "SELECT name FROM (SELECT nameA AS name, createdAt FROM matches UNION ALL SELECT nameB, createdAt FROM matches) " +
            "GROUP BY name ORDER BY COUNT(*) DESC, MAX(createdAt) DESC LIMIT 12",
    )
    fun recentNames(): Flow<List<String>>

    @Query("SELECT * FROM matches WHERE finishedAt > 0")
    fun finished(): Flow<List<MatchRow>>

    @Query("SELECT * FROM matches WHERE tournamentId = :id")
    fun ofTournament(id: Long): Flow<List<MatchRow>>

    @Query("SELECT * FROM matches WHERE tournamentId = :id AND fixture = :fixture LIMIT 1")
    suspend fun byFixture(id: Long, fixture: String): MatchRow?

    @Query("SELECT * FROM matches WHERE finishedAt = 0 ORDER BY createdAt DESC LIMIT 1")
    suspend fun latestLive(): MatchRow?

    @Query("SELECT * FROM matches ORDER BY createdAt DESC LIMIT 1")
    suspend fun latest(): MatchRow?

    @Query("SELECT * FROM tournaments ORDER BY createdAt DESC")
    fun tournaments(): Flow<List<TournamentRow>>

    @Query("SELECT * FROM tournaments WHERE id = :id")
    fun watchTournament(id: Long): Flow<TournamentRow?>

    @Insert
    suspend fun insert(t: TournamentRow): Long

    @Update
    suspend fun update(t: TournamentRow)

    @Query("DELETE FROM matches WHERE tournamentId = :id")
    suspend fun deleteTournamentMatches(id: Long)

    @Query("DELETE FROM tournaments WHERE id = :id")
    suspend fun deleteTournament(id: Long)

    // Backup: whole tables.
    @Query("SELECT * FROM matches") suspend fun allMatches(): List<MatchRow>
    @Query("SELECT * FROM events") suspend fun allEvents(): List<EventRow>
    @Query("SELECT * FROM tournaments") suspend fun allTournaments(): List<TournamentRow>
    @Query("DELETE FROM matches") suspend fun clearMatches()
    @Query("DELETE FROM tournaments") suspend fun clearTournaments()
    @Insert suspend fun insertMatches(m: List<MatchRow>)
    @Insert suspend fun insertEvents(e: List<EventRow>)
    @Insert suspend fun insertTournaments(t: List<TournamentRow>)

    /** Replaces everything (restore / sync download) in one transaction: all or nothing. */
    @Transaction
    suspend fun replaceAll(m: List<MatchRow>, e: List<EventRow>, t: List<TournamentRow>) {
        clearMatches() // events go with their matches (cascade)
        clearTournaments()
        insertTournaments(t)
        insertMatches(m)
        insertEvents(e)
    }
}

// v2: doubles, tournaments. Never destructive: these are real records.
@Database(
    entities = [MatchRow::class, EventRow::class, TournamentRow::class],
    version = 2,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun matches(): MatchDao

    companion object {
        fun build(context: Context) =
            Room.databaseBuilder(context, AppDatabase::class.java, "ghidiem.db").build()
    }
}
