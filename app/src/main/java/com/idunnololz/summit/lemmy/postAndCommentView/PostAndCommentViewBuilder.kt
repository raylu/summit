package com.idunnololz.summit.lemmy.postAndCommentView

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.text.Spannable
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.widget.FrameLayout
import androidx.annotation.LayoutRes
import androidx.appcompat.widget.PopupMenu
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.text.buildSpannedString
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.LifecycleOwner
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import arrow.core.Either
import com.facebook.drawee.backends.pipeline.Fresco
import com.facebook.drawee.drawable.ScalingUtils
import com.facebook.drawee.interfaces.DraweeController
import com.facebook.drawee.view.SimpleDraweeView
import com.idunnololz.summit.R
import com.idunnololz.summit.account.AccountActionsManager
import com.idunnololz.summit.api.dto.CommentView
import com.idunnololz.summit.api.dto.PersonId
import com.idunnololz.summit.api.dto.PostView
import com.idunnololz.summit.databinding.CommentActionsViewBinding
import com.idunnololz.summit.databinding.InboxListItemBinding
import com.idunnololz.summit.databinding.PostCommentCollapsedItemBinding
import com.idunnololz.summit.databinding.PostCommentExpandedCompactItemBinding
import com.idunnololz.summit.databinding.PostCommentExpandedItemBinding
import com.idunnololz.summit.databinding.PostHeaderItemBinding
import com.idunnololz.summit.databinding.PostPendingCommentCollapsedItemBinding
import com.idunnololz.summit.databinding.PostPendingCommentExpandedItemBinding
import com.idunnololz.summit.lemmy.LemmyContentHelper
import com.idunnololz.summit.lemmy.LemmyHeaderHelper
import com.idunnololz.summit.lemmy.LemmyTextHelper
import com.idunnololz.summit.lemmy.PageRef
import com.idunnololz.summit.lemmy.inbox.CommentBackedItem
import com.idunnololz.summit.lemmy.inbox.InboxItem
import com.idunnololz.summit.lemmy.post.ThreadLinesDecoration
import com.idunnololz.summit.lemmy.postListView.CommentUiConfig
import com.idunnololz.summit.lemmy.postListView.PostAndCommentsUiConfig
import com.idunnololz.summit.lemmy.postListView.PostUiConfig
import com.idunnololz.summit.lemmy.utils.bind
import com.idunnololz.summit.lemmy.utils.getFormattedAuthor
import com.idunnololz.summit.lemmy.utils.getFormattedTitle
import com.idunnololz.summit.offline.OfflineManager
import com.idunnololz.summit.preferences.Preferences
import com.idunnololz.summit.preview.VideoType
import com.idunnololz.summit.reddit.LemmyUtils
import com.idunnololz.summit.util.LinkUtils
import com.idunnololz.summit.util.RecycledState
import com.idunnololz.summit.util.Size
import com.idunnololz.summit.util.ThreadLinesHelper
import com.idunnololz.summit.util.Utils
import com.idunnololz.summit.util.ViewRecycler
import com.idunnololz.summit.util.abbrevNumber
import com.idunnololz.summit.util.dateStringToPretty
import com.idunnololz.summit.util.ext.getColorCompat
import com.idunnololz.summit.util.ext.getColorFromAttribute
import com.idunnololz.summit.util.ext.getDrawableCompat
import com.idunnololz.summit.video.ExoPlayerManager
import com.idunnololz.summit.video.VideoState
import dagger.hilt.android.qualifiers.ActivityContext
import dagger.hilt.android.scopes.ActivityScoped
import javax.inject.Inject

@ActivityScoped
class PostAndCommentViewBuilder @Inject constructor(
    private val activity: FragmentActivity,
    @ActivityContext private val context: Context,
    private val offlineManager: OfflineManager,
    private val accountActionsManager: AccountActionsManager,
    private val preferences: Preferences,
) {
    private val emphasisColor: Int = context.getColorCompat(R.color.colorTextTitle)

    var uiConfig: PostAndCommentsUiConfig = preferences.getPostAndCommentsUiConfig()
        set(value) {
            field = value

            lemmyContentHelper.config = uiConfig.postUiConfig.fullContentConfig

            postUiConfig = uiConfig.postUiConfig
            commentUiConfig = uiConfig.commentUiConfig
        }

    var hideCommentActions = preferences.hideCommentActions
        private set

    private val inflater = LayoutInflater.from(activity)

    private var postUiConfig: PostUiConfig = uiConfig.postUiConfig
    private var commentUiConfig: CommentUiConfig = uiConfig.commentUiConfig

    val lemmyHeaderHelper = LemmyHeaderHelper(context)
    private val lemmyContentHelper = LemmyContentHelper(
        context,
        offlineManager,
        ExoPlayerManager.get(activity),
    )
    val voteUiHandler = accountActionsManager.voteUiHandler
    val threadLinesHelper = ThreadLinesHelper(context)

    private val upvoteColor = ContextCompat.getColor(context, R.color.upvoteColor)
    private val downvoteColor = ContextCompat.getColor(context, R.color.downvoteColor)
    private val normalTextColor = ContextCompat.getColor(context, R.color.colorText)

    private val viewRecycler: ViewRecycler<View> = ViewRecycler<View>()

    private val tempSize = Size()

    init {
        lemmyContentHelper.config = uiConfig.postUiConfig.fullContentConfig
    }

    fun onPreferencesChanged() {
        uiConfig = preferences.getPostAndCommentsUiConfig()
        hideCommentActions = preferences.hideCommentActions
    }

    fun bindPostView(
        binding: PostHeaderItemBinding,
        container: View,
        postView: PostView,
        instance: String,
        isRevealed: Boolean,
        contentMaxWidth: Int,
        viewLifecycleOwner: LifecycleOwner,
        videoState: VideoState?,
        updateContent: Boolean,
        onRevealContentClickedFn: () -> Unit,
        onImageClick: (View?, String) -> Unit,
        onVideoClick: (url: String, videoType: VideoType, videoState: VideoState?) -> Unit,
        onPageClick: (PageRef) -> Unit,
        onAddCommentClick: (Either<PostView, CommentView>) -> Unit,
        onPostMoreClick: (PostView) -> Unit,
        onSignInRequired: () -> Unit,
        onInstanceMismatch: (String, String) -> Unit,
    ) = with(binding) {

        scaleTextSizes()

        lemmyHeaderHelper.populateHeaderSpan(
            headerContainer = headerContainer,
            postView = postView,
            instance = instance,
            onPageClick = onPageClick,
            listAuthor = false
        )

        title.text = postView.getFormattedTitle()
        author.text = postView.getFormattedAuthor()

        commentButton.text = abbrevNumber(postView.counts.comments.toLong())
        commentButton.isEnabled = !postView.post.locked
        commentButton.setOnClickListener {
            onAddCommentClick(Either.Left(postView))
        }
        addCommentButton.isEnabled = !postView.post.locked
        addCommentButton.setOnClickListener {
            onAddCommentClick(Either.Left(postView))
        }

        moreButton.setOnClickListener {
            onPostMoreClick(postView)
        }

        lemmyContentHelper.setupFullContent(
            reveal = isRevealed,
            tempSize = tempSize,
            videoViewMaxHeight = (container.height
                    - Utils.convertDpToPixel(56f)
                    - Utils.convertDpToPixel(16f)
                    ).toInt(),
            contentMaxWidth = contentMaxWidth,
            fullImageViewTransitionName = "post_image",
            postView = postView,
            instance = instance,
            rootView = root,
            fullContentContainerView = fullContent,
            lazyUpdate = !updateContent,
            videoState = videoState,
            onFullImageViewClickListener = { view, url ->
                onImageClick(view, url)
            },
            onImageClickListener = { url ->
                onImageClick(null, url)
            },
            onVideoClickListener = onVideoClick,
            onRevealContentClickedFn = onRevealContentClickedFn,
            onItemClickListener = {},
            onLemmyUrlClick = onPageClick
        )

        voteUiHandler.bind(
            viewLifecycleOwner,
            instance,
            postView,
            upvoteButton,
            downvoteButton,
            upvoteCount,
            null,
            onSignInRequired,
            onInstanceMismatch,
        )
    }

    fun bindCommentViewExpanded(
        h: ViewHolder,
        binding: CommentExpandedViewHolder,
        baseDepth: Int,
        depth: Int,
        commentView: CommentView,
        isDeleting: Boolean,
        content: String,
        instance: String,
        isPostLocked: Boolean,
        isUpdating: Boolean,
        highlight: Boolean,
        highlightForever: Boolean,
        viewLifecycleOwner: LifecycleOwner,
        currentAccountId: PersonId?,
        isActionsExpanded: Boolean,
        onImageClick: (View?, String) -> Unit,
        onPageClick: (PageRef) -> Unit,
        collapseSection: (position: Int) -> Unit,
        toggleActionsExpanded: () -> Unit,
        onAddCommentClick: (Either<PostView, CommentView>) -> Unit,
        onEditCommentClick: (CommentView) -> Unit,
        onDeleteCommentClick: (CommentView) -> Unit,
        onSignInRequired: () -> Unit,
        onInstanceMismatch: (String, String) -> Unit,
    ) = with(binding) {

        scaleTextSizes()

        val isCompactView = this.rawBinding is PostCommentExpandedCompactItemBinding

        fun getActionsView(): CommentActionsViewBinding =
            (viewRecycler.getRecycledView(R.layout.comment_actions_view)
                ?.let { CommentActionsViewBinding.bind(it) }
                ?: CommentActionsViewBinding.inflate(
                    inflater,
                    binding.actionsContainer,
                    false
                ))
                .also {
                    it.root.updateLayoutParams<FrameLayout.LayoutParams> {
                        gravity = Gravity.END
                    }
                    binding.actionsContainer?.addView(it.root)
                }

        var upvoteButton = upvoteButton
        var downvoteButton = downvoteButton
        var commentButton = commentButton
        var moreButton = moreButton

        if (isActionsExpanded) {
            getActionsView().apply {
                upvoteButton = this.upvoteButton
                downvoteButton = this.downvoteButton
                commentButton = this.commentButton
                moreButton = this.moreButton
            }
        }

        threadLinesHelper.populateThreadLines(
            threadLinesContainer, depth, baseDepth
        )
        lemmyHeaderHelper.populateHeaderSpan(headerView, commentView)

        if (commentView.comment.deleted || isDeleting) {
            text.text = buildSpannedString {
                append(context.getString(R.string.deleted))
                setSpan(StyleSpan(Typeface.ITALIC), 0, length, 0);
            }
        } else if (commentView.comment.removed) {
            text.text = buildSpannedString {
                append(context.getString(R.string.removed))
                setSpan(StyleSpan(Typeface.ITALIC), 0, length, 0);
            }
        } else {
            LemmyTextHelper.bindText(
                textView = text,
                text = content,
                instance = instance,
                onImageClick = {
                    onImageClick(null, it)
                },
                onPageClick = onPageClick,
            )
        }

        val giphyLinks = LemmyUtils.findGiphyLinks(content)
        if (giphyLinks.isNotEmpty()) {
            var lastViewId = 0
            giphyLinks.withIndex().forEach { (index, giphyKey) ->
                mediaContainer.visibility = View.VISIBLE
                val viewId = View.generateViewId()
                val imageView = SimpleDraweeView(context).apply {
                    layoutParams = ConstraintLayout.LayoutParams(
                        ConstraintLayout.LayoutParams.MATCH_CONSTRAINT,
                        ConstraintLayout.LayoutParams.MATCH_CONSTRAINT,
                    ).apply {
                        if (index == 0) {
                            this.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
                        } else {
                            this.topToBottom = lastViewId
                        }
                        this.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
                        this.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
                        this.dimensionRatio = "H,16:9"
                    }
                    id = viewId
                    hierarchy.actualImageScaleType = ScalingUtils.ScaleType.FIT_CENTER
                }
                mediaContainer.addView(imageView)

                val processedGiphyKey: String = if (giphyKey.contains('|')) {
                    giphyKey.split('|')[0]
                } else {
                    giphyKey
                }

                val fullUrl = "https://i.giphy.com/media/${processedGiphyKey}/giphy.webp"
                val controller: DraweeController = Fresco.newDraweeControllerBuilder()
                    .setUri(fullUrl)
                    .setAutoPlayAnimations(true)
                    .build()
                imageView.controller = controller

                imageView.setOnClickListener {
                    onImageClick(null, fullUrl)
                }

                lastViewId = viewId
            }
        } else {
            mediaContainer.removeAllViews()
            mediaContainer.visibility = View.GONE
        }

        collapseSectionButton.setOnClickListener {
            collapseSection(h.bindingAdapterPosition)
        }
        topHotspot.setOnClickListener {
            collapseSection(h.bindingAdapterPosition)
        }
        headerView.setOnClickListener {
            collapseSection(h.absoluteAdapterPosition)
        }

        commentButton?.isEnabled = !isPostLocked
        commentButton?.setOnClickListener {
            onAddCommentClick(Either.Right(commentView))
        }
        moreButton?.setOnClickListener {
            PopupMenu(context, it).apply {
                inflate(R.menu.menu_comment_item)
                if (commentView.creator.id !=
                    currentAccountId) {
                    menu.setGroupVisible(R.id.mod_post_actions, false)
                    //menu.findItem(R.id.edit_comment).isVisible = false
                }

                setOnMenuItemClickListener {
                    when (it.itemId) {
                        // fix this later or something
//                        R.id.raw_comment -> {
//                            val action =
//                                PostFragmentDirections.actionPostFragmentToCommentRawDialogFragment(
//                                    commentItemStr = Utils.gson.toJson(commentView)
//                                )
//                            findNavController().navigate(action)
//                        }
                        R.id.edit_comment -> {
                            onEditCommentClick(commentView)
                        }
                        R.id.delete_comment -> {
                            onDeleteCommentClick(commentView)
                        }
                    }
                    true
                }
            }.show()
        }
        if (commentView.comment.distinguished) {
            overlay.visibility = View.VISIBLE
            overlay.setBackgroundResource(R.drawable.locked_overlay)
        } else {
            overlay.visibility = View.GONE
        }

        voteUiHandler.bind(
            viewLifecycleOwner,
            instance,
            commentView,
            upvoteButton,
            downvoteButton,
            upvoteCount,
            {
              if (isCompactView) {
                  if (it > 0) {
                      upvoteCount.setTextColor(upvoteColor)
                  } else if (it == 0) {
                      upvoteCount.setTextColor(normalTextColor)
                  } else {
                      upvoteCount.setTextColor(downvoteColor)
                  }
              }
            },
            onSignInRequired,
            onInstanceMismatch,
        )

        highlightComment(highlight, highlightForever, highlightBg)

        if (isCompactView) {
            binding.root.setOnClickListener {
                toggleActionsExpanded()
            }
        }

        if (isUpdating) {
            progressBar.visibility = View.VISIBLE
        } else {
            progressBar.visibility = View.GONE
        }

        root.tag = ThreadLinesDecoration.ThreadLinesData(
            depth, baseDepth
        )
    }

    fun bindCommentViewCollapsed(
        h: ViewHolder,
        binding: PostCommentCollapsedItemBinding,
        baseDepth: Int,
        depth: Int,
        childrenCount: Int,
        highlight: Boolean,
        highlightForever: Boolean,
        isUpdating: Boolean,
        commentView: CommentView,
        expandSection: (position: Int) -> Unit,
    ) = with(binding) {

        scaleTextSizes()

        threadLinesHelper.populateThreadLines(
            threadLinesContainer, depth, baseDepth
        )
        lemmyHeaderHelper.populateHeaderSpan(
            headerContainer = headerView,
            item = commentView,
            detailed = true,
            childrenCount = childrenCount
        )

        expandSectionButton.setOnClickListener {
            expandSection(h.absoluteAdapterPosition)
        }
        topHotspot.setOnClickListener {
            expandSection(h.absoluteAdapterPosition)
        }
        headerView.setOnClickListener {
            expandSection(h.absoluteAdapterPosition)
        }
        if (commentView.comment.distinguished) {
            overlay.visibility = View.VISIBLE
            overlay.setBackgroundResource(R.drawable.locked_overlay)
        } else {
            overlay.visibility = View.GONE
        }

        highlightComment(highlight, highlightForever, highlightBg)

        if (isUpdating) {
            progressBar.visibility = View.VISIBLE
        } else {
            progressBar.visibility = View.GONE
        }

        root.tag = ThreadLinesDecoration.ThreadLinesData(
            depth, baseDepth
        )
    }

    fun bindPendingCommentViewExpanded(
        h: ViewHolder,
        binding: PostPendingCommentExpandedItemBinding,
        baseDepth: Int,
        depth: Int,
        content: String,
        instance: String,
        author: String?,
        highlight: Boolean,
        highlightForever: Boolean,
        onImageClick: (View?, String) -> Unit,
        onPageClick: (PageRef) -> Unit,
        collapseSection: (position: Int) -> Unit,
    ) = with(binding) {

        scaleTextSizes()

        val context = binding.root.context

        threadLinesHelper.populateThreadLines(
            threadLinesContainer, depth, baseDepth
        )
        headerContainer.setTextFirstPart(author ?: context.getString(R.string.unknown))
        headerContainer.setTextSecondPart("")

        LemmyTextHelper.bindText(
            textView = text,
            text = content,
            instance = instance,
            onImageClick = {
                onImageClick(null, it)
            },
            onPageClick = onPageClick,
        )

        val giphyLinks = LemmyUtils.findGiphyLinks(content)
        if (giphyLinks.isNotEmpty()) {
            var lastViewId = 0
            giphyLinks.withIndex().forEach { (index, giphyKey) ->
                mediaContainer.visibility = View.VISIBLE
                val viewId = View.generateViewId()
                val imageView = SimpleDraweeView(context).apply {
                    layoutParams = ConstraintLayout.LayoutParams(
                        ConstraintLayout.LayoutParams.MATCH_CONSTRAINT,
                        ConstraintLayout.LayoutParams.MATCH_CONSTRAINT,
                    ).apply {
                        if (index == 0) {
                            this.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
                        } else {
                            this.topToBottom = lastViewId
                        }
                        this.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
                        this.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
                        this.dimensionRatio = "H,16:9"
                    }
                    id = viewId
                    hierarchy.actualImageScaleType = ScalingUtils.ScaleType.FIT_CENTER
                }
                mediaContainer.addView(imageView)

                val processedGiphyKey: String = if (giphyKey.contains('|')) {
                    giphyKey.split('|')[0]
                } else {
                    giphyKey
                }

                val fullUrl = "https://i.giphy.com/media/${processedGiphyKey}/giphy.webp"
                val controller: DraweeController = Fresco.newDraweeControllerBuilder()
                    .setUri(fullUrl)
                    .setAutoPlayAnimations(true)
                    .build()
                imageView.controller = controller

                imageView.setOnClickListener {
                    onImageClick(null, fullUrl)
                }

                lastViewId = viewId
            }
        } else {
            mediaContainer.removeAllViews()
            mediaContainer.visibility = View.GONE
        }

        collapseSectionButton.setOnClickListener {
            collapseSection(h.bindingAdapterPosition)
        }
        topHotspot.setOnClickListener {
            collapseSection(h.bindingAdapterPosition)
        }

        highlightComment(highlight, highlightForever, highlightBg)

        root.tag = ThreadLinesDecoration.ThreadLinesData(
            depth, baseDepth
        )
    }

    fun bindPendingCommentViewCollapsed(
        holder: ViewHolder,
        binding: PostPendingCommentCollapsedItemBinding,
        baseDepth: Int,
        depth: Int,
        author: String?,
        highlight: Boolean,
        highlightForever: Boolean,
        expandSection: (position: Int) -> Unit,
    ) = with(binding) {

        scaleTextSizes()

        val context = holder.itemView.context
        threadLinesHelper.populateThreadLines(
            threadLinesContainer, depth, baseDepth
        )
        headerContainer.setTextFirstPart(author ?: context.getString(R.string.unknown))
        headerContainer.setTextSecondPart("")

        expandSectionButton.setOnClickListener {
            expandSection(holder.absoluteAdapterPosition)
        }
        topHotspot.setOnClickListener {
            expandSection(holder.absoluteAdapterPosition)
        }
        overlay.visibility = View.GONE

        highlightComment(highlight, highlightForever, highlightBg)

        root.tag = ThreadLinesDecoration.ThreadLinesData(
            depth, baseDepth
        )
    }

    fun bindMessage(
        viewLifecycleOwner: LifecycleOwner,
        b: InboxListItemBinding,
        instance: String,
        accountId: PersonId?,
        item: InboxItem,
        onImageClick: (String) -> Unit,
        onPageClick: (PageRef) -> Unit,
        onMarkAsRead: (InboxItem, Boolean) -> Unit,
        onMessageClick: (InboxItem) -> Unit,
        onAddCommentClick: (InboxItem) -> Unit,
        onOverflowMenuClick: (InboxItem) -> Unit,
        onSignInRequired: () -> Unit,
        onInstanceMismatch: (String, String) -> Unit,
    ) = with(b) {
        b.author.text = buildSpannedString {
            val s = length
            append(LemmyUtils.formatAuthor(item.authorName))
            LinkUtils.getLinkForPerson(item.authorInstance, item.authorName)
            val e = length
            setSpan(
                ForegroundColorSpan(emphasisColor),
                s,
                e,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        if (item is CommentBackedItem) {
            val scoreDrawable = context.getDrawableCompat(R.drawable.baseline_arrow_upward_24)
            scoreDrawable?.setBounds(
                0,
                0,
                Utils.convertDpToPixel(16f).toInt(),
                Utils.convertDpToPixel(16f).toInt()
            )
            b.score.setCompoundDrawablesRelative(
                scoreDrawable,
                null,
                null,
                null
            )
            b.score.text = LemmyUtils.abbrevNumber(item.score.toLong())
            b.score.visibility = View.VISIBLE

            voteUiHandler.bind(
                viewLifecycleOwner,
                instance,
                item,
                upvoteButton,
                downvoteButton,
                b.score,
                null,
                onSignInRequired,
                onInstanceMismatch,
            )
            upvoteButton.isEnabled = true
            downvoteButton.isEnabled = true
        } else {
            voteUiHandler.unbindVoteUi(b.score)
            b.score.visibility = View.GONE

            upvoteButton.isEnabled = false
            downvoteButton.isEnabled = false
        }

        val drawable = when (item) {
            is InboxItem.MentionInboxItem ->
                context.getDrawableCompat(R.drawable.baseline_at_24)
            is InboxItem.MessageInboxItem ->
                context.getDrawableCompat(R.drawable.baseline_email_24)
            is InboxItem.ReplyInboxItem ->
                context.getDrawableCompat(R.drawable.baseline_reply_24)
        }
        drawable?.setBounds(
            0,
            0,
            Utils.convertDpToPixel(16f).toInt(),
            Utils.convertDpToPixel(16f).toInt()
        )
        val faintTextColor = context.getColorCompat(R.color.colorTextFaint)

        b.author.setCompoundDrawablesRelative(
            drawable,
            null,
            null,
            null
        )
        b.date.text = dateStringToPretty(item.lastUpdate)
        b.title.text = item.title

        b.content.text = if (item.isDeleted) {
            buildSpannedString {
                append(context.getString(R.string.deleted))
                setSpan(StyleSpan(Typeface.ITALIC), 0, length, 0);
            }
        } else if (item.isRemoved) {
            buildSpannedString {
                append(context.getString(R.string.removed))
                setSpan(StyleSpan(Typeface.ITALIC), 0, length, 0);
            }
        } else {
            item.content
        }

        if (item.isRead) {
            b.author.setTextColor(faintTextColor)
            b.title.setTextColor(faintTextColor)
            b.content.setTextColor(faintTextColor)

            b.markAsRead.imageTintList =
                ColorStateList.valueOf(context.getColorCompat(R.color.style_green))
            b.markAsRead.setOnClickListener {
                onMarkAsRead(item, false)
            }
            b.author.alpha = .5f
        } else {
            b.author.setTextColor(context.getColorCompat(R.color.colorText))
            b.author.alpha = 1f
            b.title.setTextColor(context.getColorCompat(R.color.colorTextTitle))
            b.content.setTextColor(context.getColorCompat(R.color.colorText))
            b.markAsRead.imageTintList =
                ColorStateList.valueOf(
                    context.getColorFromAttribute(androidx.appcompat.R.attr.colorControlNormal))
            b.markAsRead.setOnClickListener {
                onMarkAsRead(item, true)
            }
        }

        b.reply.setOnClickListener {
            onAddCommentClick(item)
        }
        b.moreButton.setOnClickListener {
            onOverflowMenuClick(item)
        }

        root.setOnClickListener {
            onMessageClick(item)
        }


        if (item is InboxItem.MessageInboxItem &&
            item.authorId == accountId) {

            b.root.setTag(R.id.swipe_enabled, false)
//            b.markAsRead.isEnabled = false
        } else {
            b.root.setTag(R.id.swipe_enabled, true)
            b.markAsRead.isEnabled = true
        }

        if (item.isRead) {
            b.root.setTag(R.id.swipe_enabled, false)
        }

//        LemmyTextHelper.bindText(
//            textView = content,
//            text = message.content,
//            instance = instance,
//            onImageClickListener = onImageClick,
//            onPageClick = onPageClick,
//        )
    }

    fun recycle(b: PostHeaderItemBinding): RecycledState {
        val recycledState = lemmyContentHelper.recycleFullContent(b.fullContent)
        offlineManager.cancelFetch(b.root)
        voteUiHandler.unbindVoteUi(b.upvoteCount)
        return recycledState
    }

    fun recycle(b: CommentExpandedViewHolder) {
        voteUiHandler.unbindVoteUi(b.upvoteCount)
        b.actionsContainer?.let {
            if (it.childCount > 0) {
                val actionsView = it.getChildAt(0)
                it.removeAllViews()
                viewRecycler.addRecycledView(actionsView,R.layout.comment_actions_view)
            }
        }
    }


    private fun highlightComment(isCommentHighlighted: Boolean, highlightForever: Boolean, bg: View) {
        if (highlightForever) {
            bg.visibility = View.VISIBLE
            bg.clearAnimation()
        } else if (isCommentHighlighted) {
            bg.visibility = View.VISIBLE

            val animation = AlphaAnimation(0f, 0.9f)
            animation.repeatCount = 5
            animation.repeatMode = Animation.REVERSE
            animation.duration = 300
            animation.fillAfter = true

            bg.startAnimation(animation)
        } else {
            bg.visibility = View.GONE
            bg.clearAnimation()
        }
    }

    private fun PostHeaderItemBinding.scaleTextSizes() {
        headerContainer.textSize = postUiConfig.headerTextSizeSp.toPostTextSize()
        title.textSize = postUiConfig.titleTextSizeSp.toPostTextSize()
        commentButton.textSize = postUiConfig.footerTextSizeSp.toPostTextSize()
        upvoteCount.textSize = postUiConfig.footerTextSizeSp.toPostTextSize()
        author.textSize = postUiConfig.footerTextSizeSp.toPostTextSize()
    }

    private fun CommentExpandedViewHolder.scaleTextSizes() {
        headerView.textSize = postUiConfig.headerTextSizeSp.toCommentTextSize()
        text.textSize = commentUiConfig.contentTextSizeSp.toCommentTextSize()
        upvoteCount.textSize = postUiConfig.footerTextSizeSp.toCommentTextSize()
    }

    private fun PostCommentCollapsedItemBinding.scaleTextSizes() {
        headerView.textSize = postUiConfig.headerTextSizeSp.toCommentTextSize()
    }

    private fun PostPendingCommentExpandedItemBinding.scaleTextSizes() {
        headerContainer.textSize = postUiConfig.headerTextSizeSp.toCommentTextSize()
        text.textSize = commentUiConfig.contentTextSizeSp.toCommentTextSize()
    }

    private fun PostPendingCommentCollapsedItemBinding.scaleTextSizes() {
        headerContainer.textSize = postUiConfig.headerTextSizeSp.toCommentTextSize()
    }

    private fun Float.toPostTextSize(): Float =
        this * postUiConfig.textSizeMultiplier

    private fun Float.toCommentTextSize(): Float =
        this * commentUiConfig.textSizeMultiplier
}