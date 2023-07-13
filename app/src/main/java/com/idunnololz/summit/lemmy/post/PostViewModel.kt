package com.idunnololz.summit.lemmy.post

import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import arrow.core.Either
import arrow.core.right
import com.idunnololz.summit.account.AccountActionsManager
import com.idunnololz.summit.account.AccountManager
import com.idunnololz.summit.actions.PendingCommentView
import com.idunnololz.summit.actions.PostReadManager
import com.idunnololz.summit.api.AccountAwareLemmyClient
import com.idunnololz.summit.api.dto.Comment
import com.idunnololz.summit.api.dto.CommentId
import com.idunnololz.summit.api.dto.CommentSortType
import com.idunnololz.summit.api.dto.CommentView
import com.idunnololz.summit.api.dto.Post
import com.idunnololz.summit.api.dto.PostView
import com.idunnololz.summit.api.utils.getDepth
import com.idunnololz.summit.lemmy.CommentNodeData
import com.idunnololz.summit.lemmy.CommentRef
import com.idunnololz.summit.lemmy.CommentTreeBuilder
import com.idunnololz.summit.lemmy.PostRef
import com.idunnololz.summit.lemmy.utils.VoteUiHandler
import com.idunnololz.summit.scrape.WebsiteAdapterLoader
import com.idunnololz.summit.reddit.CommentsSortOrder
import com.idunnololz.summit.reddit.toApiSortOrder
import com.idunnololz.summit.util.StatefulLiveData
import com.idunnololz.summit.util.dateStringToTs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.LinkedHashMap
import java.util.LinkedList
import javax.inject.Inject

@HiltViewModel
class PostViewModel @Inject constructor(
    private val lemmyApiClient: AccountAwareLemmyClient,
    private val accountActionsManager: AccountActionsManager,
    private val accountManager: AccountManager,
    private val postReadManager: PostReadManager,
) : ViewModel() {

    companion object {
        private const val TAG = "PostViewModel"

        const val HIGHLIGHT_COMMENT_MS = 3_500L
    }

    private var postOrCommentRef: Either<PostRef, CommentRef>? = null

    private var loader: WebsiteAdapterLoader? = null

    private var commentLoaders = ArrayList<WebsiteAdapterLoader>()

    private var postView: PostView? = null
    private var comments: List<CommentView>? = null
    private var pendingComments: List<PendingCommentView>? = null
    private var newlyPostedCommentId: CommentId? = null

    private val additionalLoadedCommentIds = mutableSetOf<CommentId>()
    /**
     * Comments that didn't load by default but were loaded by the user requesting additional comments
     */
    private var supplementaryComments = mutableMapOf<Int, CommentView>()

    val commentsSortOrderLiveData = MutableLiveData(CommentsSortOrder.Top)

    val postData = StatefulLiveData<PostData>()

    init {
        commentsSortOrderLiveData.observeForever {
            val postOrCommentRef = postOrCommentRef
            if (postOrCommentRef != null) {
                fetchPostData(postOrCommentRef, fetchPostData = false)
            }
        }
        viewModelScope.launch {
            accountActionsManager.onCommentActionChanged.collect {
                val postOrCommentRef = postOrCommentRef
                if (postOrCommentRef != null) {
                    updatePendingComments(postOrCommentRef, resolveCompletedPendingComments = true)
                }
            }
        }

    }

    val instance: String
        get() = lemmyApiClient.instance

    fun fetchPostData(
        postOrCommentRef: Either<PostRef, CommentRef>,
        fetchPostData: Boolean = true,
        fetchCommentData: Boolean = true,
        force: Boolean = false
    ) {
        this.postOrCommentRef = postOrCommentRef
        postData.setIsLoading()

        val sortOrder = requireNotNull(commentsSortOrderLiveData.value).toApiSortOrder()

        viewModelScope.launch {
            lemmyApiClient.changeInstance(instance)

            val postResult = if (fetchPostData) {
                postOrCommentRef
                    .fold(
                        {
                            lemmyApiClient.fetchPostWithRetry(Either.Left(it.id), force)
                        },
                        {
                            lemmyApiClient.fetchPostWithRetry(Either.Right(it.id), force)
                        }
                    )
            } else {
                Result.success(this@PostViewModel.postView)
            }

            this@PostViewModel.postView = postResult.getOrNull()

            val commentsResult = if (fetchCommentData) {
                postOrCommentRef
                    .fold(
                        {
                            lemmyApiClient.fetchCommentsWithRetry(Either.Left(it.id), sortOrder, force)
                        },
                        {
                            lemmyApiClient.fetchCommentsWithRetry(Either.Right(it.id), sortOrder, force)
                        }
                    )
            } else {
                Result.success(this@PostViewModel.comments)
            }
            this@PostViewModel.comments = commentsResult.getOrNull()

            if (force) {
                additionalLoadedCommentIds.forEach {
                    fetchMoreCommentsInternal(it, sortOrder, force)
                }
            }

            updatePendingCommentsInternal(postOrCommentRef, sortOrder, true)

            val post = postResult.getOrNull()
            val comments = commentsResult.getOrNull()

            if (post != null) {
                postReadManager.markPostAsReadLocal(instance, post.post.id, read = true)
            }

            if (post == null || comments == null) {
                postResult
                    .onFailure {
                        postData.postError(it)
                    }
                    .onSuccess {}

                commentsResult
                    .onFailure {
                        postData.postError(it)
                    }
                    .onSuccess {}
            } else {
                updateData()
            }
        }
    }

    private fun updatePendingComments(
        postOrCommentRef: Either<PostRef, CommentRef>,
        resolveCompletedPendingComments: Boolean,
    ) {
        val sortOrder = requireNotNull(commentsSortOrderLiveData.value).toApiSortOrder()

        viewModelScope.launch {
            updatePendingCommentsInternal(
                postOrCommentRef = postOrCommentRef,
                sortOrder = sortOrder,
                resolveCompletedPendingComments = resolveCompletedPendingComments
            )

            updateData()
        }
    }

    private suspend fun updatePendingCommentsInternal(
        postOrCommentRef: Either<PostRef, CommentRef>,
        sortOrder: CommentSortType,
        resolveCompletedPendingComments: Boolean,
    ) {
        val postRef = postOrCommentRef.leftOrNull() ?: return

        pendingComments = accountActionsManager.getPendingComments(postRef)

        var modified = false
        if (resolveCompletedPendingComments) {
            pendingComments?.forEach { pendingComment ->
                if (pendingComment.complete) {
                    val result = lemmyApiClient.fetchCommentsWithRetry(
                        Either.Left(pendingComment.postRef.id), sortOrder, force = true
                    )

                    this@PostViewModel.comments = result.getOrNull()

                    val commentsResult = if (pendingComment.parentId == null) {
                        result
                    } else {
                        fetchMoreCommentsInternal(pendingComment.parentId, sortOrder, force = true)
                    }

                    commentsResult
                        .onSuccess {
                            modified = true

                            // find the comment that was recently posts by guessing!

                            if (pendingComment.isActionDelete) {
                                newlyPostedCommentId = pendingComment.commentId
                            } else if (pendingComment.commentId != null) {
                                newlyPostedCommentId = pendingComment.commentId
                            } else {
                                newlyPostedCommentId = it
                                    .sortedByDescending {
                                        if (it.comment.updated != null) {
                                            dateStringToTs(it.comment.updated)
                                        } else {
                                            dateStringToTs(it.comment.published)
                                        }
                                    }
                                    .firstOrNull {
                                        it.comment.creator_id == accountManager.currentAccount.value?.id
                                    }
                                    ?.comment?.id
                            }

                            accountActionsManager.removePendingComment(pendingComment)
                        }
                }
            }
        }

        if (modified) {
            pendingComments = accountActionsManager.getPendingComments(postRef)
        }
    }

    private suspend fun updateData() {
        val post = postView ?: return
        val comments = comments
        val pendingComments = pendingComments
        val supplementaryComments = supplementaryComments

        postData.postValue(
            PostData(
                ListView.PostListView(post),
                CommentTreeBuilder(accountManager).buildCommentsTreeListView(
                    post,
                    comments,
                    parentComment = true,
                    pendingComments,
                    supplementaryComments,
                ),
                newlyPostedCommentId = newlyPostedCommentId,
                selectedCommentId = postOrCommentRef?.getOrNull()?.id,
                isSingleComment = postOrCommentRef?.getOrNull() != null,
            )
        )
    }

    fun fetchMoreComments(parentId: CommentId?, force: Boolean = false) {
        val sortOrder = requireNotNull(commentsSortOrderLiveData.value).toApiSortOrder()

        viewModelScope.launch {
            if (parentId != null) {
                fetchMoreCommentsInternal(parentId, sortOrder, force)
            } else {
                // TODO maybe?
            }

            updateData()
        }
    }

    fun resetNewlyPostedComment() {
        newlyPostedCommentId = null

        viewModelScope.launch {
            delay(HIGHLIGHT_COMMENT_MS)

            updateData()
        }
    }

    private suspend fun fetchMoreCommentsInternal(
        parentId: CommentId,
        sortOrder: CommentSortType,
        force: Boolean = false
    ): Result<List<CommentView>> {
        val result = lemmyApiClient.fetchCommentsWithRetry(
            Either.Right(parentId),
            sortOrder,
            force,
        )

        additionalLoadedCommentIds.add(parentId)

        result.onSuccess { comments ->
            comments.forEach {
                supplementaryComments[it.comment.id] = it
            }
        }

        return result
    }

    override fun onCleared() {
        super.onCleared()

        loader?.destroy()

        for (l in commentLoaders) {
            l.destroy()
        }
    }

    data class PostData(
        val postView: ListView.PostListView,
        val commentTree: List<CommentNodeData>,
        val newlyPostedCommentId: CommentId?,
        val selectedCommentId: CommentId?,
        val isSingleComment: Boolean,
    )

    sealed interface ListView {
        data class PostListView(
            val post: PostView
        ) : ListView

        data class CommentListView(
            val comment: CommentView,
            val pendingCommentView: PendingCommentView? = null,
            var isCollapsed: Boolean = false,
        ) : ListView

        data class PendingCommentListView(
            val pendingCommentView: PendingCommentView,
            var isCollapsed: Boolean = false,
            var author: String?,
        ) : ListView

        data class MoreCommentsItem(
            val parentCommentId: CommentId?,
            val depth: Int,
            val moreCount: Int,
        ) : ListView
    }


    fun setCommentsSortOrder(sortOrder: CommentsSortOrder) {
        commentsSortOrderLiveData.value = sortOrder
    }

    fun deleteComment(postRef: PostRef, commentId: Int) {
        viewModelScope.launch {
            accountActionsManager.deleteComment(postRef, commentId)
        }
    }
}