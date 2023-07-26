package com.idunnololz.summit.history

import android.content.Context
import android.util.Log
import com.idunnololz.summit.api.AccountAwareLemmyClient
import com.idunnololz.summit.api.dto.PostView
import com.idunnololz.summit.coroutine.CoroutineScopeFactory
import com.idunnololz.summit.db.MainDatabase
import com.idunnololz.summit.lemmy.CommunityViewState
import com.idunnololz.summit.lemmy.toUrl
import com.idunnololz.summit.user.TabCommunityState
import com.idunnololz.summit.util.Utils
import com.idunnololz.summit.util.moshi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HistoryManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val coroutineScopeFactory: CoroutineScopeFactory,
    private val historyDao: HistoryDao,
    private val apiClient: AccountAwareLemmyClient,
) {

    companion object {
        private const val TAG = "HistoryManager"
    }

    interface OnHistoryChangedListener {
        fun onHistoryChanged()
    }

    private val coroutineScope = coroutineScopeFactory.create()
    @OptIn(ExperimentalCoroutinesApi::class)
    private val dbContext = Dispatchers.IO.limitedParallelism(1)

    private val historyChangedListeners = arrayListOf<OnHistoryChangedListener>()

    suspend fun getEntireHistory(): List<LiteHistoryEntry> = withContext(Dispatchers.IO) {
        historyDao
            .getAllLiteHistoryEntries()
    }

    suspend fun query(query: String): List<LiteHistoryEntry> = withContext(Dispatchers.IO) {
        historyDao.query(query)
    }

    suspend fun getHistoryFrom(ts: Long): List<LiteHistoryEntry> = withContext(Dispatchers.IO) {
        historyDao.getLiteHistoryEntriesFrom(ts)
    }

    fun recordVisit(jsonUrl: String, saveReason: HistorySaveReason, post: PostView?) {
        coroutineScope.launch {
            withContext(dbContext) {
                val ts = System.currentTimeMillis()
                val newEntry = HistoryEntry(
                    id = 0,
                    type = HistoryEntry.TYPE_PAGE_VISIT,
                    reason = saveReason,
                    url = jsonUrl,
                    shortDesc = post?.post?.name ?: "",
                    ts = ts,
                    extras = ""
                )

                recordHistoryEntry(newEntry)
            }
        }
    }

    fun recordCommunityState(
        tabId: Long,
        saveReason: HistorySaveReason,
        state: CommunityViewState,
        shortDesc: String
    ) {
        coroutineScope.launch {
            withContext(dbContext) {
                val ts = System.currentTimeMillis()
                val historyEntry = HistoryEntry(
                    id = 0,
                    type = HistoryEntry.TYPE_COMMUNITY_STATE,
                    reason = saveReason,
                    url = state.toUrl(apiClient.instance),
                    shortDesc = shortDesc,
                    ts = ts,
                    extras = moshi.adapter(TabCommunityState::class.java)
                        .toJson(TabCommunityState(tabId = tabId, viewState = state))
                )
                recordHistoryEntry(historyEntry)
            }
        }
    }

    fun removeEntry(id: Long) {
        coroutineScope.launch {
            withContext(dbContext) {
                historyDao
                    .deleteById(id)
            }
            historyChangedListeners.forEach { it.onHistoryChanged() }
        }
    }

    private suspend fun recordHistoryEntry(newEntry: HistoryEntry) {
        withContext(dbContext) {
            historyDao.insertEntryMergeWithPreviousIfSame(newEntry)
        }

        withContext(Dispatchers.Default) {
            historyChangedListeners.forEach { it.onHistoryChanged() }
        }
    }

    suspend fun getHistoryEntry(id: Long): HistoryEntry? = withContext(Dispatchers.IO) {
        historyDao
            .getHistoryEntry(id)
    }

    suspend fun clearHistory() {
        withContext(Dispatchers.IO) {
            historyDao
                .deleteAllHistoryEntries()
        }

        historyChangedListeners.forEach { it.onHistoryChanged() }
    }

    fun registerOnHistoryChangedListener(l: OnHistoryChangedListener) {
        historyChangedListeners.add(l)
    }

    fun unregisterOnHistoryChangedListener(l: OnHistoryChangedListener) {
        historyChangedListeners.remove(l)
    }
}