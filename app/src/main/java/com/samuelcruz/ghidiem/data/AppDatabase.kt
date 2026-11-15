package com.samuelcruz.ghidiem.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
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
}

@Database(entities = [MatchRow::class, EventRow::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun matches(): MatchDao

    companion object {
        fun build(context: Context) =
            Room.databaseBuilder(context, AppDatabase::class.java, "ghidiem.db").build()
    }
}
