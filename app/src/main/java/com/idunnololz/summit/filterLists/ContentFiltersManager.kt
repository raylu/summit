package com.idunnololz.summit.filterLists

import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.idunnololz.summit.account.info.AccountInfoConverters
import com.idunnololz.summit.api.dto.PostView
import com.idunnololz.summit.api.utils.instance
import com.idunnololz.summit.coroutine.CoroutineScopeFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.regex.Pattern
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContentFiltersManager @Inject constructor(
    private val dao: ContentFiltersDao,
    private val coroutineScopeFactory: CoroutineScopeFactory,
) {

    companion object {
        private const val TAG = "ContentFiltersManager"
    }

    private val coroutineScope = coroutineScopeFactory.create()

    private var postListFilters: List<FilterEntry> = listOf()

    private var regexCache = mutableMapOf<Long, Pattern>()

    init {
        coroutineScope.launch {
            refreshFilters()

            withContext(Dispatchers.IO) {
                val filterCount = dao.count()

                if (filterCount > 1000) {
                    val e = TooManyFiltersException(filterCount)

                    Log.e(TAG, "", e)
                    FirebaseCrashlytics.getInstance().recordException(e)
                }
            }
        }
    }

    private suspend fun refreshFilters() {
        val filters = withContext(Dispatchers.IO) {
            dao.getAllFilters()
        }

        val postListFilters = mutableListOf<FilterEntry>()
        for (filter in filters) {
            when (filter.contentType) {
                ContentTypes.PostListType -> {
                    postListFilters.add(filter)
                }
            }
        }

        this.postListFilters = postListFilters
    }

    /**
     * Tests a [PostView] to see if it matches any of the filters.
     * @return true if the [PostView] matches at least one filter.
     */
    fun testPostView(postView: PostView): Boolean {
        return postListFilters.any { filter ->
            when (filter.filterType) {
                FilterTypes.KeywordFilter -> {
                    if (filter.isRegex) {
                        getPattern(filter).matcher(postView.post.name).find()
                    } else {
                        postView.post.name.contains(filter.filter, ignoreCase = true)
                    }
                }
                FilterTypes.InstanceFilter -> {
                    if (filter.isRegex) {
                        getPattern(filter).matcher(postView.community.instance).find()
                    } else {
                        postView.community.instance.contains(filter.filter, ignoreCase = true)
                    }
                }
                FilterTypes.CommunityFilter -> {
                    if (filter.isRegex) {
                        getPattern(filter).matcher(postView.community.name).find()
                    } else {
                        postView.community.name.contains(filter.filter, ignoreCase = true)
                    }
                }
                FilterTypes.UserFilter -> {
                    if (filter.isRegex) {
                        getPattern(filter).matcher(postView.creator.name).find()
                    } else {
                        postView.creator.name.contains(filter.filter, ignoreCase = true)
                    }
                }
                else -> false
            }
        }
    }

    fun getFilters(contentTypeId: ContentTypeId, filterTypeId: FilterTypeId): List<FilterEntry> {
        return when (contentTypeId) {
            ContentTypes.PostListType -> {
                postListFilters.filter { it.filterType == filterTypeId }
            }
            else -> listOf()
        }
    }

    suspend fun addFilter(filter: FilterEntry) {
        dao.insertFilter(filter)

        refreshFilters()

        regexCache.remove(filter.id)
    }

    suspend fun deleteFilter(filter: FilterEntry) {
        dao.delete(filter)

        refreshFilters()
    }

    private fun getPattern(filter: FilterEntry): Pattern {
        return regexCache[filter.id]
            ?: try {
                Pattern.compile(filter.filter)
            } catch (e: Exception) {
                Pattern.compile("""a^""")
            }.also {
                regexCache[filter.id] = it
            }
    }
}

class TooManyFiltersException(filtersCount: Long) : RuntimeException(
    "Has $filtersCount filters!"
)