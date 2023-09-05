package com.idunnololz.summit.lemmy.post

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.updatePaddingRelative
import androidx.lifecycle.LifecycleOwner
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import arrow.core.Either
import com.idunnololz.summit.R
import com.idunnololz.summit.api.dto.CommentId
import com.idunnololz.summit.api.dto.CommentView
import com.idunnololz.summit.api.dto.PostId
import com.idunnololz.summit.api.dto.PostView
import com.idunnololz.summit.api.utils.getUniqueKey
import com.idunnololz.summit.databinding.GenericLoadingItemBinding
import com.idunnololz.summit.databinding.GenericSpaceFooterItemBinding
import com.idunnololz.summit.databinding.PostCommentCollapsedItemBinding
import com.idunnololz.summit.databinding.PostCommentExpandedCompactItemBinding
import com.idunnololz.summit.databinding.PostCommentExpandedItemBinding
import com.idunnololz.summit.databinding.PostHeaderItemBinding
import com.idunnololz.summit.databinding.PostMissingCommentItemBinding
import com.idunnololz.summit.databinding.PostMoreCommentsItemBinding
import com.idunnololz.summit.databinding.PostPendingCommentCollapsedItemBinding
import com.idunnololz.summit.databinding.PostPendingCommentExpandedItemBinding
import com.idunnololz.summit.databinding.ViewAllCommentsBinding
import com.idunnololz.summit.lemmy.PageRef
import com.idunnololz.summit.lemmy.flatten
import com.idunnololz.summit.lemmy.post.PostsAdapter.Item.CommentItem
import com.idunnololz.summit.lemmy.post.PostsAdapter.Item.FooterItem
import com.idunnololz.summit.lemmy.post.PostsAdapter.Item.HeaderItem
import com.idunnololz.summit.lemmy.post.PostsAdapter.Item.MoreCommentsItem
import com.idunnololz.summit.lemmy.post.PostsAdapter.Item.PendingCommentItem
import com.idunnololz.summit.lemmy.post.PostsAdapter.Item.ProgressOrErrorItem
import com.idunnololz.summit.lemmy.post.QueryMatchHelper.HighlightTextData
import com.idunnololz.summit.lemmy.post.QueryMatchHelper.QueryResult
import com.idunnololz.summit.lemmy.postAndCommentView.CommentExpandedViewHolder
import com.idunnololz.summit.lemmy.postAndCommentView.PostAndCommentViewBuilder
import com.idunnololz.summit.preview.VideoType
import com.idunnololz.summit.util.Utils
import com.idunnololz.summit.util.recyclerView.ViewBindingViewHolder
import com.idunnololz.summit.util.recyclerView.getBinding
import com.idunnololz.summit.util.recyclerView.isBinding
import com.idunnololz.summit.video.VideoState

class PostsAdapter(
    private val postAndCommentViewBuilder: PostAndCommentViewBuilder,
    private val context: Context,
    private val containerView: View,
    private val lifecycleOwner: LifecycleOwner,
    var instance: String,
    private val revealAll: Boolean,
    private val useFooter: Boolean,
    /**
     * Set this to true if this adapter is used for an embedded view.
     */
    private val isEmbedded: Boolean,
    private val videoState: VideoState?,
    private val onRefreshClickCb: () -> Unit,
    private val onSignInRequired: () -> Unit,
    private val onInstanceMismatch: (String, String) -> Unit,
    private val onAddCommentClick: (Either<PostView, CommentView>) -> Unit,
    private val onImageClick: (Either<PostView, CommentView>?, View?, String) -> Unit,
    private val onVideoClick: (String, VideoType, VideoState?) -> Unit,
    private val onPageClick: (PageRef) -> Unit,
    private val onPostMoreClick: (PostView) -> Unit,
    private val onCommentMoreClick: (CommentView) -> Unit,
    private val onFetchComments: (CommentId) -> Unit,
    private val onLoadPost: (PostId) -> Unit,
    private val onLinkLongClick: (url: String, text: String?) -> Unit,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private sealed class Item(
        open val id: String,
    ) {

        data class HeaderItem(
            val postView: PostView,
            var videoState: VideoState?,
            val showBottomDivider: Boolean,
            val query: String?,
            val currentMatch: QueryResult?,
        ) : Item(postView.getUniqueKey())

        data class CommentItem(
            val commentId: CommentId,
            val content: String,
            val comment: CommentView,
            val depth: Int,
            val baseDepth: Int,
            val isExpanded: Boolean,
            val isPending: Boolean,
            val view: PostViewModel.ListView.CommentListView,
            val childrenCount: Int,
            val isPostLocked: Boolean,
            val isUpdating: Boolean,
            val isDeleting: Boolean,
            val isRemoved: Boolean,
            val isActionsExpanded: Boolean,
            val isHighlighted: Boolean,
            val query: String?,
            val currentMatch: QueryResult?,
        ) : Item(
            "comment_${comment.comment.id}",
        )

        data class PendingCommentItem(
            val commentId: CommentId?,
            val content: String,
            val author: String?,
            val depth: Int,
            val baseDepth: Int,
            val isExpanded: Boolean,
            val isPending: Boolean,
            val view: PostViewModel.ListView.PendingCommentListView,
            val childrenCount: Int,
            val query: String?,
        ) : Item(
            "pending_comment_${view.pendingCommentView.id}",
        )

        data class MoreCommentsItem(
            val parentId: CommentId?,
            val moreCount: Int,
            val depth: Int,
            val baseDepth: Int,
        ) : Item(
            "more_comments_$parentId",
        )

        data class MissingCommentItem(
            val commentId: CommentId?,
            val depth: Int,
            val baseDepth: Int,
        ) : Item(
            "deleted_$commentId",
        )

        class ProgressOrErrorItem(
            val error: Throwable? = null,
        ) : Item("wew_pls_no_progress")

        data class ViewAllComments(
            val postId: PostId,
        ) : Item("view_all_yo")

        data object FooterItem : Item("footer")
    }

    private val inflater = LayoutInflater.from(context)

    private var items: List<Item> = listOf()

    private var parentHeight: Int = 0

    /**
     * Set of items that is hidden by default but is reveals (ie. nsfw or spoiler tagged)
     */
    private var revealedItems = mutableSetOf<String>()

    private var actionExpandedComments = mutableSetOf<CommentId>()

    private var rawData: PostViewModel.PostData? = null

    private var highlightedComment: CommentId = -1
    private var highlightedCommentForever: CommentId = -1

    private var topLevelCommentIndices = listOf<Int>()
    private var absolutionPositionToTopLevelCommentPosition = listOf<Int>()

    private var query: String? = null
    var currentMatch: QueryResult? = null
        set(value) {
            field = value

            refreshItems(animate = false)
        }

    var isLoaded: Boolean = false
    var error: Throwable? = null
        set(value) {
            field = value

            refreshItems()
        }

    var contentMaxWidth = 0
        set(value) {
            if (value == field) {
                return
            }
            field = value

            for (i in 0..items.lastIndex) {
                if (items[i] is HeaderItem) {
                    notifyItemChanged(i)
                }
            }
        }

    override fun getItemViewType(position: Int): Int = when (val item = items[position]) {
        is HeaderItem -> R.layout.post_header_item
        is CommentItem ->
            if (item.isExpanded) {
                if (postAndCommentViewBuilder.hideCommentActions) {
                    R.layout.post_comment_expanded_compact_item
                } else {
                    R.layout.post_comment_expanded_item
                }
            } else {
                R.layout.post_comment_collapsed_item
            }
        is PendingCommentItem ->
            if (item.isExpanded) {
                R.layout.post_pending_comment_expanded_item
            } else {
                R.layout.post_pending_comment_collapsed_item
            }
        is ProgressOrErrorItem -> R.layout.generic_loading_item
        is MoreCommentsItem -> R.layout.post_more_comments_item
        is FooterItem -> R.layout.generic_space_footer_item
        is Item.ViewAllComments -> R.layout.view_all_comments
        is Item.MissingCommentItem -> R.layout.post_missing_comment_item
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val v = inflater.inflate(viewType, parent, false)

        parentHeight = parent.height

        return when (viewType) {
            R.layout.post_header_item -> ViewBindingViewHolder(PostHeaderItemBinding.bind(v))
            R.layout.post_comment_expanded_item ->
                CommentExpandedViewHolder.fromBinding(PostCommentExpandedItemBinding.bind(v))
            R.layout.post_comment_expanded_compact_item ->
                CommentExpandedViewHolder.fromBinding(PostCommentExpandedCompactItemBinding.bind(v))
                    .apply {
                        headerView.textView2.setCompoundDrawablesRelativeWithIntrinsicBounds(
                            R.drawable.baseline_arrow_upward_16,
                            0,
                            0,
                            0,
                        )
                        headerView.textView2.compoundDrawablePadding =
                            Utils.convertDpToPixel(4f).toInt()
                        headerView.textView2.updatePaddingRelative(
                            start = Utils.convertDpToPixel(8f).toInt(),
                        )

                        headerView.textView3.setCompoundDrawablesRelativeWithIntrinsicBounds(
                            R.drawable.baseline_arrow_downward_16,
                            0,
                            0,
                            0,
                        )
                        headerView.textView3.compoundDrawablePadding =
                            Utils.convertDpToPixel(4f).toInt()
                        headerView.textView3.updatePaddingRelative(
                            start = Utils.convertDpToPixel(8f).toInt(),
                        )
                    }
            R.layout.post_comment_collapsed_item ->
                ViewBindingViewHolder(PostCommentCollapsedItemBinding.bind(v))
            R.layout.post_pending_comment_expanded_item ->
                ViewBindingViewHolder(PostPendingCommentExpandedItemBinding.bind(v))
            R.layout.post_pending_comment_collapsed_item ->
                ViewBindingViewHolder(PostPendingCommentCollapsedItemBinding.bind(v))
            R.layout.post_more_comments_item ->
                ViewBindingViewHolder(PostMoreCommentsItemBinding.bind(v))
            R.layout.generic_loading_item ->
                ViewBindingViewHolder(GenericLoadingItemBinding.bind(v))
            R.layout.generic_space_footer_item ->
                ViewBindingViewHolder(GenericSpaceFooterItemBinding.bind(v))
            R.layout.view_all_comments ->
                ViewBindingViewHolder(ViewAllCommentsBinding.bind(v))
            R.layout.post_missing_comment_item ->
                ViewBindingViewHolder(PostMissingCommentItemBinding.bind(v))
            else -> throw RuntimeException("ViewType: $viewType")
        }
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int,
        payloads: MutableList<Any>,
    ) {
        when (val item = items[position]) {
            is HeaderItem -> {
                if (payloads.isEmpty()) {
                    super.onBindViewHolder(holder, position, payloads)
                } else {
                    // this is an incremental update... Only update the stats, do not update content...
                    val b = holder.getBinding<PostHeaderItemBinding>()
                    val post = item.postView
                    val postKey = post.getUniqueKey()

                    postAndCommentViewBuilder.bindPostView(
                        binding = b,
                        container = containerView,
                        postView = item.postView,
                        instance = instance,
                        isRevealed = revealAll || revealedItems.contains(postKey),
                        contentMaxWidth = contentMaxWidth,
                        viewLifecycleOwner = lifecycleOwner,
                        videoState = item.videoState,
                        updateContent = false,
                        highlightTextData = if (item.query == null) {
                            null
                        } else {
                            HighlightTextData(
                                item.query,
                                item.currentMatch?.relativeMatchIndex,
                                item.currentMatch?.targetSubtype,
                            )
                        },
                        onRevealContentClickedFn = {
                            revealedItems.add(postKey)
                            notifyItemChanged(holder.absoluteAdapterPosition)
                        },
                        onImageClick = onImageClick,
                        onVideoClick = onVideoClick,
                        onPageClick = onPageClick,
                        onAddCommentClick = onAddCommentClick,
                        onPostMoreClick = onPostMoreClick,
                        onSignInRequired = onSignInRequired,
                        onInstanceMismatch = onInstanceMismatch,
                        onLinkLongClick = onLinkLongClick,
                    )

                    if (item.showBottomDivider) {
                        b.bottomDivider.visibility = View.VISIBLE
                    } else {
                        b.bottomDivider.visibility = View.GONE
                    }
                }
            }
            else -> super.onBindViewHolder(holder, position, payloads)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is HeaderItem -> {
                val b = holder.getBinding<PostHeaderItemBinding>()
                val post = item.postView
                val postKey = post.getUniqueKey()

                holder.itemView.setTag(R.id.swipeable, true)

                postAndCommentViewBuilder.bindPostView(
                    binding = b,
                    container = containerView,
                    postView = item.postView,
                    instance = instance,
                    isRevealed = revealAll || revealedItems.contains(postKey),
                    contentMaxWidth = contentMaxWidth,
                    viewLifecycleOwner = lifecycleOwner,
                    videoState = item.videoState,
                    updateContent = true,
                    highlightTextData = if (item.query == null) {
                        null
                    } else {
                        HighlightTextData(
                            item.query,
                            item.currentMatch?.relativeMatchIndex,
                            item.currentMatch?.targetSubtype,
                        )
                    },
                    onRevealContentClickedFn = {
                        revealedItems.add(postKey)
                        notifyItemChanged(holder.absoluteAdapterPosition)
                    },
                    onImageClick = onImageClick,
                    onVideoClick = onVideoClick,
                    onPageClick = onPageClick,
                    onAddCommentClick = onAddCommentClick,
                    onPostMoreClick = onPostMoreClick,
                    onSignInRequired = onSignInRequired,
                    onInstanceMismatch = onInstanceMismatch,
                    onLinkLongClick = onLinkLongClick,
                )

                if (item.showBottomDivider) {
                    b.bottomDivider.visibility = View.VISIBLE
                } else {
                    b.bottomDivider.visibility = View.GONE
                }
            }
            is CommentItem -> {
                if (item.isExpanded) {
                    val b = holder as CommentExpandedViewHolder
                    val highlight = highlightedComment == item.commentId
                    val highlightForever = highlightedCommentForever == item.commentId

                    postAndCommentViewBuilder.bindCommentViewExpanded(
                        h = holder,
                        holder = b,
                        baseDepth = item.baseDepth,
                        depth = item.depth,
                        commentView = item.comment,
                        isDeleting = item.isDeleting,
                        isRemoved = item.isRemoved,
                        content = item.content,
                        instance = instance,
                        isPostLocked = item.isPostLocked,
                        isUpdating = item.isUpdating,
                        highlight = highlight,
                        highlightForever = highlightForever,
                        viewLifecycleOwner = lifecycleOwner,
                        isActionsExpanded = item.isActionsExpanded,
                        onImageClick = onImageClick,
                        onVideoClick = onVideoClick,
                        onPageClick = onPageClick,
                        highlightTextData = if (item.query == null) {
                            null
                        } else {
                            HighlightTextData(
                                item.query,
                                item.currentMatch?.relativeMatchIndex,
                                item.currentMatch?.targetSubtype,
                            )
                        },
                        collapseSection = {
                            collapseSection(holder.bindingAdapterPosition)
                        },
                        toggleActionsExpanded = {
                            toggleActions(item.comment.comment.id)
                        },
                        onAddCommentClick = onAddCommentClick,
                        onCommentMoreClick = onCommentMoreClick,
                        onLinkLongClick = onLinkLongClick,
                        onSignInRequired = onSignInRequired,
                        onInstanceMismatch = onInstanceMismatch,
                    )
                } else {
                    // collapsed
                    val b = holder.getBinding<PostCommentCollapsedItemBinding>()
                    val highlight = highlightedComment == item.commentId
                    val highlightForever = highlightedCommentForever == item.commentId

                    postAndCommentViewBuilder.bindCommentViewCollapsed(
                        holder,
                        b,
                        item.baseDepth,
                        item.depth,
                        item.childrenCount,
                        highlight,
                        highlightForever,
                        item.isUpdating,
                        item.comment,
                        instance,
                        ::expandSection,
                        onPageClick,
                        onLinkLongClick = onLinkLongClick,
                    )
                }

                holder.itemView.setTag(R.id.swipeable, true)
                holder.itemView.setTag(R.id.comment_view, item.comment)
                holder.itemView.setTag(R.id.expanded, item.isExpanded)
            }
            is PendingCommentItem -> {
                if (item.isExpanded) {
                    val b = holder.getBinding<PostPendingCommentExpandedItemBinding>()
                    val highlight = highlightedComment == item.commentId
                    val highlightForever = highlightedCommentForever == item.commentId

                    postAndCommentViewBuilder.bindPendingCommentViewExpanded(
                        holder,
                        b,
                        item.baseDepth,
                        item.depth,
                        item.content,
                        instance,
                        item.author,
                        highlight,
                        highlightForever,
                        onImageClick,
                        onVideoClick,
                        onPageClick,
                        onLinkLongClick = onLinkLongClick,
                        ::collapseSection,
                    )
                } else {
                    // collapsed
                    val b = holder.getBinding<PostPendingCommentCollapsedItemBinding>()
                    val highlight = highlightedComment == item.commentId
                    val highlightForever = highlightedCommentForever == item.commentId

                    postAndCommentViewBuilder.bindPendingCommentViewCollapsed(
                        holder,
                        b,
                        item.baseDepth,
                        item.depth,
                        item.author,
                        highlight,
                        highlightForever,
                        ::collapseSection,
                    )
                }
                holder.itemView.setTag(R.id.expanded, item.isExpanded)
            }
            is ProgressOrErrorItem -> {
                val b = holder.getBinding<GenericLoadingItemBinding>()
                if (item.error != null) {
                    b.loadingView.showDefaultErrorMessageFor(item.error)
                } else if (!isLoaded) {
                    b.loadingView.showProgressBar()
                }
                b.loadingView.setOnRefreshClickListener {
                    onRefreshClickCb()
                }
            }
            is MoreCommentsItem -> {
                val b = holder.getBinding<PostMoreCommentsItemBinding>()

                postAndCommentViewBuilder.bindMoreCommentsItem(b, item.depth, item.baseDepth)
                b.moreButton.text = context.resources.getQuantityString(
                    R.plurals.replies_format,
                    item.moreCount,
                    item.moreCount,
                )

                b.moreButton.setOnClickListener {
                    if (item.parentId != null) {
                        onFetchComments(item.parentId)
                    }
                }
            }

            FooterItem -> {}
            is Item.ViewAllComments -> {
                val b = holder.getBinding<ViewAllCommentsBinding>()
                b.button.setOnClickListener {
                    onLoadPost(item.postId)
                }
            }

            is Item.MissingCommentItem -> {
                val b = holder.getBinding<PostMissingCommentItemBinding>()

                postAndCommentViewBuilder.bindMissingCommentItem(b, item.depth, item.baseDepth)
            }
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        super.onViewRecycled(holder)

        val item = if (holder.absoluteAdapterPosition >= 0) {
            items[holder.absoluteAdapterPosition]
        } else {
            null
        }

        if (holder.isBinding<PostHeaderItemBinding>()) {
            val b = holder.getBinding<PostHeaderItemBinding>()
            val state = postAndCommentViewBuilder.recycle(b)

            (item as? HeaderItem)?.videoState = state.videoState
        } else if (holder is CommentExpandedViewHolder) {
            postAndCommentViewBuilder.recycle(holder)
        }
    }

    override fun getItemCount(): Int = items.size

    /**
     * @param refreshHeader Pass false to not always refresh header. Useful for web view headers
     * as refreshing them might cause a relayout causing janky animations
     */
    private fun refreshItems(
        refreshHeader: Boolean = true,
        animate: Boolean = true,
        cb: () -> Unit = {},
    ) {
        val rawData = rawData
        val oldItems = items
        val query = query
        val currentMatch = currentMatch

        val topLevelCommentIndices = mutableListOf<Int>()
        val absolutionPositionToTopLevelCommentPosition = mutableListOf<Int>()

        val newItems =
            if (error == null) {
                rawData ?: return

                val finalItems = mutableListOf<Item>()

                val postView = rawData.postView
                val commentItems = rawData.commentTree.flatten()
                finalItems += HeaderItem(
                    postView = postView.post,
                    videoState = videoState,
                    showBottomDivider = !isEmbedded || commentItems.isNotEmpty(),
                    query = query,
                    currentMatch = if (currentMatch?.targetId == postView.post.post.id) {
                        currentMatch
                    } else {
                        null
                    }
                )

                absolutionPositionToTopLevelCommentPosition += -1

                if (rawData.isSingleComment) {
                    finalItems += Item.ViewAllComments(postView.post.post.id)
                    absolutionPositionToTopLevelCommentPosition += -1
                }

                var lastTopLevelCommentPosition = -1

                for (commentItem in commentItems) {
                    var depth = -1

                    when (val commentView = commentItem.commentView) {
                        is PostViewModel.ListView.CommentListView -> {
                            val commentId = commentView.comment.comment.id
                            val isCurrentMatchThisComment = currentMatch?.targetId == commentId
                            val isDeleting =
                                commentView.pendingCommentView?.isActionDelete == true
                            depth = commentItem.depth

                            if (depth == 0) {
                                lastTopLevelCommentPosition++
                            }

                            finalItems += CommentItem(
                                commentId = commentId,
                                content =
                                commentView.pendingCommentView?.content
                                    ?: commentView.comment.comment.content,
                                comment = commentView.comment,
                                depth = commentItem.depth,
                                baseDepth = 0,
                                isExpanded = !commentView.isCollapsed || isCurrentMatchThisComment,
                                isPending = false,
                                view = commentView,
                                childrenCount = commentItem.children.size,
                                isPostLocked = commentView.comment.post.locked,
                                isUpdating = commentView.pendingCommentView != null,
                                isDeleting = isDeleting,
                                isRemoved = commentView.isRemoved,
                                isActionsExpanded = actionExpandedComments.contains(
                                    commentId,
                                ),
                                isHighlighted = rawData.selectedCommentId == commentId,
                                query = query,
                                currentMatch = if (isCurrentMatchThisComment) {
                                    currentMatch
                                } else {
                                    null
                                }
                            )
                            absolutionPositionToTopLevelCommentPosition += lastTopLevelCommentPosition
                        }
                        is PostViewModel.ListView.PendingCommentListView -> {
                            depth = commentItem.depth
                            if (depth == 0) {
                                lastTopLevelCommentPosition++
                            }
                            finalItems += PendingCommentItem(
                                commentId = commentView.pendingCommentView.commentId,
                                content = commentView.pendingCommentView.content,
                                author = commentView.author,
                                depth = commentItem.depth,
                                baseDepth = 0,
                                isExpanded = !commentView.isCollapsed,
                                isPending = false,
                                view = commentView,
                                childrenCount = commentItem.children.size,
                                query = query,
                            )
                            absolutionPositionToTopLevelCommentPosition += lastTopLevelCommentPosition
                        }
                        is PostViewModel.ListView.PostListView -> {
                            // should never happen
                        }
                        is PostViewModel.ListView.MoreCommentsItem -> {
                            if (commentView.parentCommentId != null) {
                                depth = commentItem.depth
                                if (depth == 0) {
                                    lastTopLevelCommentPosition++
                                }
                                finalItems += MoreCommentsItem(
                                    parentId = commentView.parentCommentId,
                                    moreCount = commentView.moreCount,
                                    depth = commentItem.depth,
                                    baseDepth = 0,
                                )
                                absolutionPositionToTopLevelCommentPosition += lastTopLevelCommentPosition
                            }
                        }

                        is PostViewModel.ListView.MissingCommentItem -> {
                            finalItems += Item.MissingCommentItem(
                                commentId = commentView.commentId,
                                depth = commentItem.depth,
                                baseDepth = 0,
                            )
                        }
                    }

                    if (depth == 0) {
                        topLevelCommentIndices += finalItems.lastIndex
                    }
                }

                if (!isLoaded) {
                    finalItems += listOf(ProgressOrErrorItem())
                }

                if (useFooter) {
                    finalItems += FooterItem
                }

                finalItems
            } else {
                val finalItems = mutableListOf<Item>()
                rawData?.postView?.let {
                    finalItems += HeaderItem(
                        postView = it.post,
                        videoState = videoState,
                        showBottomDivider = true,
                        query = query,
                        currentMatch = if (currentMatch?.targetId == it.post.post.id) {
                            currentMatch
                        } else {
                            null
                        },
                    )
                }
                finalItems += ProgressOrErrorItem(error)

                finalItems
            }

        val diff = DiffUtil.calculateDiff(
            object : DiffUtil.Callback() {
                override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                    return oldItems[oldItemPosition].id == newItems[newItemPosition].id
                }

                override fun getOldListSize(): Int = oldItems.size

                override fun getNewListSize(): Int = newItems.size

                override fun areContentsTheSame(
                    oldItemPosition: Int,
                    newItemPosition: Int,
                ): Boolean {
                    val oldItem = oldItems[oldItemPosition]
                    val newItem = newItems[newItemPosition]
                    return when (oldItem) {
                        is HeaderItem ->
                            oldItem.postView.post == (newItem as HeaderItem).postView.post
                        else -> oldItem == newItem
                    }
                }

                override fun getChangePayload(oldItemPosition: Int, newItemPosition: Int): Any? {
                    return if (animate) {
                        null
                    } else {
                        Unit
                    }
                }
            },
        )
        this.items = newItems
        diff.dispatchUpdatesTo(this)
        if (refreshHeader) {
            notifyItemChanged(0, Unit)
        }

        this.topLevelCommentIndices = topLevelCommentIndices
        this.absolutionPositionToTopLevelCommentPosition = absolutionPositionToTopLevelCommentPosition

        cb()
    }

    fun getPositionOfComment(commentId: CommentId): Int =
        items.indexOfFirst {
            when (it) {
                is CommentItem -> it.commentId == commentId
                is HeaderItem -> false
                is MoreCommentsItem -> false
                is PendingCommentItem -> it.commentId == commentId
                is ProgressOrErrorItem -> false
                FooterItem -> false
                is Item.ViewAllComments -> false
                is Item.MissingCommentItem -> false
            }
        }

    fun getPrevTopLevelCommentPosition(position: Int): Int? {
        val topLevelPosition = absolutionPositionToTopLevelCommentPosition.getOrNull(position)
            ?: if (position in 0 until itemCount) {
                return 0
            } else {
                return null
            }

        val curP = topLevelCommentIndices.getOrNull(topLevelPosition)
        if (position == curP) {
            return topLevelCommentIndices.getOrNull(topLevelPosition - 1) ?: 0
        } else {
            return curP
        }
    }

    fun getNextTopLevelCommentPosition(position: Int): Int? {
        val topLevelPosition = absolutionPositionToTopLevelCommentPosition.getOrNull(position)
            ?: return null
        return topLevelCommentIndices.getOrNull(topLevelPosition + 1)
    }

    fun setStartingData(data: PostViewModel.PostData) {
        rawData = data

        refreshItems()
    }

    fun hasStartingData(): Boolean = rawData?.postView != null

    fun setData(data: PostViewModel.PostData) {
        if (!isLoaded) {
            isLoaded = true
        }

        error = null
        rawData = data

        refreshItems()
    }

    fun highlightComment(commentId: CommentId) {
        val pos = getPositionOfComment(commentId)
        if (pos == -1) {
            return
        }

        highlightedComment = commentId
        highlightedCommentForever = -1

        notifyItemChanged(pos, Unit)
    }

    fun highlightCommentForever(commentId: CommentId) {
        val pos = getPositionOfComment(commentId)
        if (pos == -1) {
            return
        }

        this.highlightedComment = -1
        this.highlightedCommentForever = commentId

        notifyItemChanged(pos, Unit)
    }

    fun clearHighlightComment() {
        val pos = getPositionOfComment(highlightedComment)
        if (pos == -1) {
            return
        }

        highlightedComment = -1
        notifyItemChanged(pos, Unit)
    }

    private fun collapseSection(position: Int) {
        if (position < 0) return

        (items[position] as? CommentItem)?.view?.isCollapsed = true
        (items[position] as? PendingCommentItem)?.view?.isCollapsed = true

        refreshItems(refreshHeader = false)
    }

    private fun expandSection(position: Int) {
        if (position < 0) return

        (items[position] as? CommentItem)?.view?.isCollapsed = false
        (items[position] as? PendingCommentItem)?.view?.isCollapsed = false

        refreshItems(refreshHeader = false)
    }

    fun toggleSection(position: Int) {
        val isCollapsed = (items[position] as? CommentItem)?.view?.isCollapsed
            ?: (items[position] as? PendingCommentItem)?.view?.isCollapsed

        if (isCollapsed == true) {
            expandSection(position)
        } else if (isCollapsed == false) {
            collapseSection(position)
        }
    }

    private fun toggleActions(id: CommentId) {
        if (actionExpandedComments.contains(id)) {
            actionExpandedComments.remove(id)
        } else {
            actionExpandedComments.add(id)
        }

        refreshItems(refreshHeader = false)
    }

    fun setQuery(query: String?, cb: (List<QueryResult>) -> Unit) {
        val realQuery = if (query.isNullOrBlank()) {
            null
        } else {
            query
        }

        if (realQuery == this.query) {
            return
        }

        this.query = realQuery

        val occurrences = mutableListOf<QueryResult>()

        fun count(s: String?, query: String, cb: (count: Int) -> Unit) {
            s ?: return

            var count = 0
            val queryLength = query.length
            var lastIndex = 0

            while (true) {
                val nextIndex = s.indexOf(query, lastIndex, ignoreCase = true)

                if (nextIndex < 0) break

                cb(count)

                count += 1

                lastIndex = nextIndex + queryLength
            }
        }

        refreshItems(refreshHeader = true) {
            val finalQuery = this.query ?: return@refreshItems

            items.withIndex().forEach { (index, item) ->
                when (item) {
                    is CommentItem -> {
                        count(item.comment.comment.content, finalQuery) {
                            occurrences.add(QueryResult(
                                targetId = item.commentId,
                                targetSubtype = 0,
                                relativeMatchIndex = it,
                                itemIndex = index,
                                matchIndex = occurrences.size
                            ))
                        }
                    }
                    FooterItem -> {}
                    is HeaderItem -> {
                        count(item.postView.post.name, finalQuery) {
                            occurrences.add(QueryResult(
                                targetId = item.postView.post.id,
                                targetSubtype = 0,
                                relativeMatchIndex = it,
                                itemIndex = index,
                                matchIndex = occurrences.size
                            ))
                        }
                        count(item.postView.post.body, finalQuery) {
                            occurrences.add(QueryResult(
                                targetId = item.postView.post.id,
                                targetSubtype = 1,
                                relativeMatchIndex = it,
                                itemIndex = index,
                                matchIndex = occurrences.size
                            ))
                        }
                    }
                    is Item.MissingCommentItem -> {}
                    is MoreCommentsItem -> {}
                    is PendingCommentItem -> {}
                    is ProgressOrErrorItem -> {}
                    is Item.ViewAllComments -> {}
                }
            }

            cb(occurrences)
        }
    }
}
