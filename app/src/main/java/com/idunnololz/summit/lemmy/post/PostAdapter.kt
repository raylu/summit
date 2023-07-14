package com.idunnololz.summit.lemmy.post

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.updatePadding
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
import com.idunnololz.summit.api.utils.getDepth
import com.idunnololz.summit.api.utils.getUniqueKey
import com.idunnololz.summit.databinding.GenericFooterItemBinding
import com.idunnololz.summit.databinding.GenericLoadingItemBinding
import com.idunnololz.summit.databinding.PostCommentCollapsedItemBinding
import com.idunnololz.summit.databinding.PostCommentExpandedCompactItemBinding
import com.idunnololz.summit.databinding.PostCommentExpandedItemBinding
import com.idunnololz.summit.databinding.PostHeaderItemBinding
import com.idunnololz.summit.databinding.PostMoreCommentsItemBinding
import com.idunnololz.summit.databinding.PostPendingCommentCollapsedItemBinding
import com.idunnololz.summit.databinding.PostPendingCommentExpandedItemBinding
import com.idunnololz.summit.databinding.ViewAllCommentsBinding
import com.idunnololz.summit.lemmy.PageRef
import com.idunnololz.summit.lemmy.PostRef
import com.idunnololz.summit.lemmy.community.CommunityFragment
import com.idunnololz.summit.lemmy.flatten
import com.idunnololz.summit.lemmy.post.PostsAdapter.Item.CommentItem
import com.idunnololz.summit.lemmy.post.PostsAdapter.Item.FooterItem
import com.idunnololz.summit.lemmy.post.PostsAdapter.Item.HeaderItem
import com.idunnololz.summit.lemmy.post.PostsAdapter.Item.MoreCommentsItem
import com.idunnololz.summit.lemmy.post.PostsAdapter.Item.PendingCommentItem
import com.idunnololz.summit.lemmy.post.PostsAdapter.Item.ProgressOrErrorItem
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
    private val instance: String,
    private val revealAll: Boolean,
    private val currentAccountId: Int?,
    private val videoState: VideoState?,
    private val onRefreshClickCb: () -> Unit,
    private val onSignInRequired: () -> Unit,
    private val onInstanceMismatch: (String, String) -> Unit,
    private val onAddCommentClick: (Either<PostView, CommentView>) -> Unit,
    private val onEditCommentClick: (CommentView) -> Unit,
    private val onDeleteCommentClick: (CommentView) -> Unit,
    private val onImageClick: (View?, String) -> Unit,
    private val onVideoClick: (String, VideoType, VideoState?) -> Unit,
    private val onPageClick: (PageRef) -> Unit,
    private val onPostMoreClick: (PostView) -> Unit,
    private val onFetchComments: (CommentId) -> Unit,
    private val onLoadPost: (PostId) -> Unit,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private sealed class Item(
        open val id: String
    ) {

        data class HeaderItem(
            val postView: PostView,
            var videoState: VideoState?
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
            val isActionsExpanded: Boolean,
            val isHighlighted: Boolean,
        ) : Item(
            "comment_${comment.comment.id}"
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
        ) : Item(
            "pending_comment_${view.pendingCommentView.id}"
        )

        data class MoreCommentsItem(
            val parentId: CommentId?,
            val moreCount: Int,
            val depth: Int,
            val baseDepth: Int,
        ) : Item(
            "more_comments_${parentId}"
        )

        class ProgressOrErrorItem(
            val error: Throwable? = null
        ) : Item("wew_pls_no_progress")

        data class ViewAllComments(
            val postId: PostId
        ): Item("view_all_yo")

        object FooterItem : Item("footer")
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

            for (i in 0.. items.lastIndex) {
                if (items[i] is HeaderItem) {
                    notifyItemChanged(i)
                }
            }
        }

    override fun getItemViewType(position: Int): Int = when (val item = items[position]) {
        is HeaderItem -> R.layout.post_header_item
        is CommentItem ->
            if (item.isExpanded)
                if (postAndCommentViewBuilder.hideCommentActions) {
                    R.layout.post_comment_expanded_compact_item
                } else {
                    R.layout.post_comment_expanded_item
                }
            else
                R.layout.post_comment_collapsed_item
        is PendingCommentItem ->
            if (item.isExpanded)
                R.layout.post_pending_comment_expanded_item
            else
                R.layout.post_pending_comment_collapsed_item
        is ProgressOrErrorItem -> R.layout.generic_loading_item
        is MoreCommentsItem -> R.layout.post_more_comments_item
        is FooterItem -> R.layout.generic_footer_item
        is Item.ViewAllComments -> R.layout.view_all_comments
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
                            R.drawable.baseline_arrow_upward_16, 0, 0, 0)
                        headerView.textView2.compoundDrawablePadding =
                            Utils.convertDpToPixel(4f).toInt()
                        headerView.textView2.updatePaddingRelative(
                            start = Utils.convertDpToPixel(8f).toInt())
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
            R.layout.generic_footer_item ->
                ViewBindingViewHolder(GenericFooterItemBinding.bind(v))
            R.layout.view_all_comments ->
                ViewBindingViewHolder(ViewAllCommentsBinding.bind(v))
            else -> throw RuntimeException("ViewType: $viewType")
        }
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int,
        payloads: MutableList<Any>
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
                    )
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
                )
            }
            is CommentItem -> {
                if (item.isExpanded) {
                    val b = holder as CommentExpandedViewHolder
                    val highlight = highlightedComment == item.commentId
                    val highlightForever = highlightedCommentForever == item.commentId

                    postAndCommentViewBuilder.bindCommentViewExpanded(
                        holder,
                        b,
                        item.baseDepth,
                        item.depth,
                        item.comment,
                        item.isDeleting,
                        item.content,
                        instance,
                        item.isPostLocked,
                        item.isUpdating,
                        highlight,
                        highlightForever,
                        lifecycleOwner,
                        currentAccountId,
                        item.isActionsExpanded,
                        onImageClick,
                        onPageClick,
                        {
                            collapseSection(holder.bindingAdapterPosition)
                        },
                        {
                            toggleActions(item.comment.comment.id)
                        },
                        onAddCommentClick,
                        onEditCommentClick,
                        onDeleteCommentClick,
                        onSignInRequired,
                        onInstanceMismatch,
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
                        ::expandSection,
                    )
                }

                holder.itemView.setTag(R.id.swipeable, true)
                holder.itemView.setTag(R.id.comment_view, item.comment)
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
                        onPageClick,
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

                postAndCommentViewBuilder.threadLinesHelper.populateThreadLines(
                    b.threadLinesContainer, item.depth, item.baseDepth
                )
                b.moreButton.text = context.resources.getQuantityString(
                    R.plurals.replies_format, item.moreCount, item.moreCount
                )

                b.moreButton.setOnClickListener {
                    if (item.parentId != null) {
                        onFetchComments(item.parentId)
                    }
                }

                b.root.tag = ThreadLinesDecoration.ThreadLinesData(
                    depth = item.depth,
                    baseDepth = item.baseDepth
                )
            }

            FooterItem -> {}
            is Item.ViewAllComments -> {
                val b = holder.getBinding<ViewAllCommentsBinding>()
                b.button.setOnClickListener { 
                    onLoadPost(item.postId)
                }
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
    private fun refreshItems(refreshHeader: Boolean = true) {
        val rawData = rawData
        val oldItems = items

        val newItems =
            if (error == null) {
                rawData ?: return

                val finalItems = mutableListOf<Item>()

                val postView = rawData.postView
                if (postView != null) {
                    finalItems += HeaderItem(postView.post, videoState)

                    if (rawData.isSingleComment) {
                        finalItems += Item.ViewAllComments(postView.post.post.id)
                    }
                }

                rawData.commentTree.flatten().forEach {
                    when (val commentView = it.commentView) {
                        is PostViewModel.ListView.CommentListView -> {
                            val commentId = commentView.comment.comment.id
                            val isDeleting =
                                commentView.pendingCommentView?.isActionDelete == true
                            finalItems += CommentItem(
                                commentId = commentId,
                                content =
                                commentView.pendingCommentView?.content
                                    ?: commentView.comment.comment.content,
                                comment = commentView.comment,
                                depth = commentView.comment.getDepth(),
                                baseDepth = 0,
                                isExpanded = !commentView.isCollapsed,
                                isPending = false,
                                view = commentView,
                                childrenCount = it.children.size,
                                isPostLocked = commentView.comment.post.locked,
                                isUpdating = commentView.pendingCommentView != null,
                                isDeleting = isDeleting,
                                isActionsExpanded = actionExpandedComments.contains(
                                    commentId
                                ),
                                isHighlighted = rawData.selectedCommentId == commentId
                            )
                        }
                        is PostViewModel.ListView.PendingCommentListView -> {
                            finalItems += PendingCommentItem(
                                commentId = commentView.pendingCommentView.commentId,
                                content = commentView.pendingCommentView.content,
                                author = commentView.author,
                                depth = it.depth,
                                baseDepth = 0,
                                isExpanded = !commentView.isCollapsed,
                                isPending = false,
                                view = commentView,
                                childrenCount = it.children.size,
                            )
                        }
                        is PostViewModel.ListView.PostListView -> {
                            // should never happen
                        }

                        is PostViewModel.ListView.MoreCommentsItem -> {
                            if (commentView.parentCommentId != null) {
                                finalItems += MoreCommentsItem(
                                    parentId = commentView.parentCommentId,
                                    moreCount = commentView.moreCount,
                                    depth = it.depth,
                                    baseDepth = 0,
                                )
                            }
                        }
                    }
                }

                if (!isLoaded) {
                    finalItems += listOf(ProgressOrErrorItem())
                }

                finalItems += FooterItem

                finalItems
            } else {
                val finalItems = mutableListOf<Item>()
                rawData?.postView?.let {
                    finalItems += HeaderItem(it.post, videoState)
                }
                finalItems += ProgressOrErrorItem(error)

                finalItems
            }

        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return oldItems[oldItemPosition].id == newItems[newItemPosition].id
            }

            override fun getOldListSize(): Int = oldItems.size

            override fun getNewListSize(): Int = newItems.size

            override fun areContentsTheSame(
                oldItemPosition: Int,
                newItemPosition: Int
            ): Boolean {
                val oldItem = oldItems[oldItemPosition]
                val newItem = newItems[newItemPosition]
                return when (oldItem) {
                    is HeaderItem ->
                        oldItem.postView.post == (newItem as HeaderItem).postView.post
                    else -> oldItem == newItem
                }
            }

        })
        this.items = newItems
        diff.dispatchUpdatesTo(this)
        if (refreshHeader) {
            notifyItemChanged(0, Unit)
        }
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
            }
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

    private fun toggleActions(id: CommentId) {
        if (actionExpandedComments.contains(id)) {
            actionExpandedComments.remove(id)
        } else {
            actionExpandedComments.add(id)
        }

        refreshItems(refreshHeader = false)
    }
}