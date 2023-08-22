package com.idunnololz.summit.lemmy.inbox

import android.util.Log
import com.idunnololz.summit.account.info.AccountInfoManager
import com.idunnololz.summit.api.AccountAwareLemmyClient
import com.idunnololz.summit.api.dto.CommentSortType
import com.idunnololz.summit.lemmy.inbox.repository.InboxSource
import com.idunnololz.summit.lemmy.inbox.repository.LemmyListSource.PageResult
import dagger.hilt.android.scopes.ViewModelScoped
import javax.inject.Inject
import kotlin.math.min

@ViewModelScoped
class InboxRepository @Inject constructor(
    private val apiClient: AccountAwareLemmyClient,
    private val accountInfoManager: AccountInfoManager,
) {

    @ViewModelScoped
    class Factory @Inject constructor(
        private val apiClient: AccountAwareLemmyClient,
        private val accountInfoManager: AccountInfoManager,
    ) {
        fun create() =
            InboxRepository(apiClient, accountInfoManager)
    }

    companion object {
        private const val TAG = "InboxRepository"

        private const val PAGE_SIZE = 20
    }

    private val repliesStatelessSource: InboxSource<CommentSortType> =
        makeRepliesSource(unreadOnly = false)
    private val mentionsStatelessSource: InboxSource<CommentSortType> =
        makeMentionsSource(unreadOnly = false)
    private val messagesStatelessSource: InboxSource<Unit> =
        makeMessagesSource(unreadOnly = false)
    private val postReportsStatelessSource: InboxSource<Unit> =
        makePostReportsSource(unresolvedOnly = false)
    private val commentReportsStatelessSource: InboxSource<Unit> =
        makeCommentReportsSource(unresolvedOnly = false)

    private class InboxMultiDataSource(
        private val sources: List<InboxSource<*>>,
    ) {

        val allItems = mutableListOf<InboxItem>()

        suspend fun getPage(
            pageIndex: Int,
            pageType: InboxViewModel.PageType,
            force: Boolean,
        ): Result<PageResult<InboxItem>> {
            Log.d(
                TAG,
                "Page type: $pageType. Index: $pageIndex. Sources: ${sources.size}. Force: $force",
            )

            if (force) {
                allItems.clear()
                sources.forEach {
                    it.invalidate()
                }
            }

            val startIndex = pageIndex * PAGE_SIZE
            val endIndex = (pageIndex + 1) * PAGE_SIZE
            var hasMore = true

            while (allItems.size < endIndex) {
                val sourceToResult = sources.map { it to it.peekNextItem() }
                val sourceAndError = sourceToResult.firstOrNull { (_, result) -> result.isFailure }

                if (sourceAndError != null) {
                    return Result.failure(requireNotNull(sourceAndError.second.exceptionOrNull()))
                }

                val nextSourceAndResult = sourceToResult.maxBy {
                        (_, result) -> result.getOrNull()?.lastUpdateTs ?: 0L
                }
                val nextItem = nextSourceAndResult.second.getOrNull()

                if (nextItem == null) {
                    // no more items!
                    hasMore = false
                    break
                }

                Log.d(
                    TAG,
                    "Adding item ${nextItem.id} from source ${nextItem::class.java}",
                )

                allItems.add(nextItem)

                // increment the max item
                nextSourceAndResult.first.next()
            }

            return Result.success(
                PageResult(
                    pageIndex,
                    allItems
                        .slice(startIndex until min(endIndex, allItems.size)),
                    hasMore = hasMore,
                ),
            )
        }

        fun markAsRead(id: Int, read: Boolean): InboxItem? {
            sources.forEach {
                val item = it.markAsRead(id, read)
                if (item != null) {
                    return item
                }
            }
            return null
        }

        fun removeItemWithId(id: Int) {
            sources.forEach {
                it.removeItemWithId(id)
            }
        }

        fun invalidate() {
            allItems.clear()
            sources.forEach {
                it.invalidate()
            }
        }
    }

    private val repliesSource = InboxMultiDataSource(
        listOf(repliesStatelessSource),
    )
    private val mentionsSource =
        InboxMultiDataSource(
            listOf(mentionsStatelessSource),
        )
    private val messagesSource =
        InboxMultiDataSource(
            listOf(messagesStatelessSource),
        )
    private val reportsSource =
        InboxMultiDataSource(
            listOf(postReportsStatelessSource, commentReportsStatelessSource),
        )
    private val allSources = InboxMultiDataSource(
        listOf(
            repliesStatelessSource,
            mentionsStatelessSource,
            messagesStatelessSource,
            postReportsStatelessSource,
            commentReportsStatelessSource
        ),
    )
    private val unreadSources = InboxMultiDataSource(
        listOf(
            makeRepliesSource(unreadOnly = true),
            makeMentionsSource(unreadOnly = true),
            makeMessagesSource(unreadOnly = true),
            makePostReportsSource(unresolvedOnly = true),
            makeCommentReportsSource(unresolvedOnly = true),
        ),
    )

    private fun getSource(pageType: InboxViewModel.PageType) = when (pageType) {
        InboxViewModel.PageType.Unread -> unreadSources
        InboxViewModel.PageType.All -> allSources
        InboxViewModel.PageType.Replies -> repliesSource
        InboxViewModel.PageType.Mentions -> mentionsSource
        InboxViewModel.PageType.Messages -> messagesSource
        InboxViewModel.PageType.Reports -> reportsSource
    }

    suspend fun getPage(
        pageIndex: Int,
        pageType: InboxViewModel.PageType,
        force: Boolean,
    ): Result<PageResult<InboxItem>> {
        val source = getSource(pageType)

        val result = source.getPage(pageIndex, pageType, force)

        Log.d(TAG, "Got ${result.getOrNull()?.items?.size} items for page $pageType")

        return result
    }

    fun invalidate(pageType: InboxViewModel.PageType) {
        getSource(pageType).invalidate()
    }

    private fun makeRepliesSource(unreadOnly: Boolean) = InboxSource(
        apiClient,
        CommentSortType.New,
    ) { page: Int, sortOrder: CommentSortType, limit: Int, force: Boolean ->
        fetchReplies(
            sort = sortOrder,
            page = page,
            limit = limit,
            unreadOnly = unreadOnly,
            force = force,
        ).fold(
            onSuccess = {
                Result.success(it.map { InboxItem.ReplyInboxItem(it) })
            },
            onFailure = {
                Result.failure(it)
            },
        )
    }

    private fun makeMentionsSource(unreadOnly: Boolean) = InboxSource(
        apiClient,
        CommentSortType.New,
    ) { page: Int, sortOrder: CommentSortType, limit: Int, force: Boolean ->
        fetchMentions(
            sort = sortOrder,
            page = page,
            limit = limit,
            unreadOnly = unreadOnly,
            force = force,
        ).fold(
            onSuccess = {
                Result.success(it.map { InboxItem.MentionInboxItem(it) })
            },
            onFailure = {
                Result.failure(it)
            },
        )
    }

    private fun makeMessagesSource(unreadOnly: Boolean) = InboxSource(
        apiClient,
        Unit,
    ) { page: Int, _: Unit, limit: Int, force: Boolean ->
        fetchPrivateMessages(
            page = page,
            limit = limit,
            unreadOnly = unreadOnly,
            force = force,
        ).fold(
            onSuccess = {
                Result.success(it.map { InboxItem.MessageInboxItem(it) })
            },
            onFailure = {
                Result.failure(it)
            },
        )
    }

    private fun makePostReportsSource(unresolvedOnly: Boolean) = InboxSource(
        apiClient,
        Unit,
    ) { page: Int, _: Unit, limit: Int, force: Boolean ->
        fetchPostReports(
            page = page,
            limit = limit,
            unresolvedOnly = unresolvedOnly,
            force = force,
        ).fold(
            onSuccess = {
                Result.success(it.post_reports.map { InboxItem.ReportPostInboxItem(it) })
            },
            onFailure = {
                Result.failure(it)
            },
        )
    }

    private fun makeCommentReportsSource(unresolvedOnly: Boolean) = InboxSource(
        apiClient,
        Unit,
    ) { page: Int, _: Unit, limit: Int, force: Boolean ->
        fetchCommentReports(
            page = page,
            limit = limit,
            unresolvedOnly = unresolvedOnly,
            force = force,
        ).fold(
            onSuccess = {
                Result.success(it.comment_reports.map { InboxItem.ReportCommentInboxItem(it) })
            },
            onFailure = {
                Result.failure(it)
            },
        )
    }

    suspend fun markAsRead(inboxItem: InboxItem, read: Boolean): Result<Unit> {
        val itemMarked = allSources.markAsRead(inboxItem.id, read)

        if (read) {
            unreadSources.removeItemWithId(inboxItem.id)
        }

        allSources.invalidate()
        unreadSources.invalidate()

        invalidate(InboxViewModel.PageType.Unread)
        val result = when (inboxItem) {
            is InboxItem.MentionInboxItem ->
                apiClient.markMentionAsRead(inboxItem.id, read)
            is InboxItem.MessageInboxItem ->
                apiClient.markPrivateMessageAsRead(inboxItem.id, read)
            is InboxItem.ReplyInboxItem ->
                apiClient.markReplyAsRead(inboxItem.id, read)
            is InboxItem.ReportMessageInboxItem -> {
                Result.success(Unit)
            }
            is InboxItem.ReportCommentInboxItem -> {
                apiClient.resolveCommentReport(inboxItem.id, read)
            }
            is InboxItem.ReportPostInboxItem -> {
                apiClient.resolvePostReport(inboxItem.id, read)
            }
        }

        return result.fold(
            onSuccess = {
                getPage(0, InboxViewModel.PageType.Unread, force = true)
                invalidate(InboxViewModel.PageType.Unread)

                accountInfoManager.updateUnreadCount()

                Result.success(Unit)
            },
            onFailure = {
                allSources.markAsRead(inboxItem.id, !read)

                if (read) {
                    getPage(0, InboxViewModel.PageType.Unread, force = true)
                    invalidate(InboxViewModel.PageType.Unread)
                }
                Result.failure(it)
            },
        )
    }

    fun onServerChanged() {
        InboxViewModel.PageType.values().forEach {
            invalidate(it)
        }
    }
}
