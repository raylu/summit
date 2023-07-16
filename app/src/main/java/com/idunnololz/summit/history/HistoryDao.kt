package com.idunnololz.summit.history

import android.util.Log
import androidx.room.*
import com.idunnololz.summit.user.TabCommunityState
import com.idunnololz.summit.util.moshi
import io.reactivex.Completable
import io.reactivex.Single


private const val TAG = "HistoryDao"

@Dao
interface HistoryDao {

    @Query("SELECT * FROM history ORDER BY ts")
    suspend fun getAllHistoryEntries(): List<HistoryEntry>

    @Query("SELECT id, url, shortDesc, ts, type, reason FROM history ORDER BY ts DESC")
    suspend fun getAllLiteHistoryEntries(): List<LiteHistoryEntry>

    @Query("SELECT id, url, shortDesc, ts, type, reason FROM history WHERE ts < :ts ORDER BY ts DESC LIMIT 1000")
    suspend fun getLiteHistoryEntriesFrom(ts: Long): List<LiteHistoryEntry>

    @Query("SELECT * FROM history WHERE id = :entryId")
    suspend fun getHistoryEntry(entryId: Long): HistoryEntry?

    @Query("SELECT * FROM history ORDER BY ts DESC LIMIT 1")
    suspend fun getLastHistoryEntry(): HistoryEntry?

    @Query("SELECT * FROM history WHERE type = :type ORDER BY ts DESC LIMIT 1")
    suspend fun getLastHistoryEntryWithType(type: Int): HistoryEntry?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistoryEntry(historyEntry: HistoryEntry): Long

    @Delete
    suspend fun delete(historyEntry: HistoryEntry)

    @Query("DELETE FROM history WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM history")
    suspend fun deleteAllHistoryEntries()

    @Query("SELECT id, url, shortDesc, ts, type, reason FROM history WHERE shortDesc LIKE '%' || :query || '%' OR url LIKE '%' || :query || '%' ORDER BY ts DESC LIMIT 1000")
    suspend fun query(query: String): List<LiteHistoryEntry>


    @Transaction
    open suspend fun insertEntryMergeWithPreviousIfSame(newEntry: HistoryEntry) {
        val lastEntry = getLastHistoryEntryWithType(newEntry.type)

        Log.d(TAG, "Last entry: $lastEntry")

        val entryToInsert = try {
            if (lastEntry?.type != newEntry.type) {
                newEntry
            } else {
                when (newEntry.type) {
                    HistoryEntry.TYPE_PAGE_VISIT ->
                        if (lastEntry.url == newEntry.url) {
                            // just update the last entry
                            Log.d(TAG, "Using copy of last entry...")
                            lastEntry.copy(
                                ts = newEntry.ts,
                                shortDesc = newEntry.shortDesc
                            )
                        } else {
                            newEntry
                        }

                    HistoryEntry.TYPE_COMMUNITY_STATE -> {
                        // Url for subreddit state is actually the tab id...
                        val adapter = moshi.adapter(TabCommunityState::class.java)
                        val oldState = adapter.fromJson(lastEntry.extras)
                        val newState = adapter.fromJson(newEntry.extras)
                        if (oldState?.viewState?.communityState?.communityRef == newState?.viewState?.communityState?.communityRef &&
                            oldState?.viewState?.communityState?.currentPageIndex ==
                            newState?.viewState?.communityState?.currentPageIndex
                        ) {
                            // just update the last entry
                            lastEntry.copy(
                                ts = newEntry.ts,
                                shortDesc = newEntry.shortDesc,
                                extras = newEntry.extras
                            )
                        } else {
                            newEntry
                        }
                    }

                    else -> newEntry
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "", e)
            newEntry
        }

        Log.d(TAG, "Inserting entry: $entryToInsert")

        insertHistoryEntry(entryToInsert)
    }
}