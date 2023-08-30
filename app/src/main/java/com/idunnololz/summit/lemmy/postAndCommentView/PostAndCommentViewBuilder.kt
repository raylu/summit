package com.idunnololz.summit.lemmy.postAndCommentView

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Spannable
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Space
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.text.buildSpannedString
import androidx.core.view.setPadding
import androidx.core.view.updateLayoutParams
import androidx.core.widget.TextViewCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.LifecycleOwner
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import arrow.core.Either
import com.google.android.material.button.MaterialButton
import com.idunnololz.summit.R
import com.idunnololz.summit.account.AccountActionsManager
import com.idunnololz.summit.api.dto.CommentView
import com.idunnololz.summit.api.dto.PersonId
import com.idunnololz.summit.api.dto.PostView
import com.idunnololz.summit.databinding.CommentActionsViewBinding
import com.idunnololz.summit.databinding.InboxListItemBinding
import com.idunnololz.summit.databinding.PostCommentCollapsedItemBinding
import com.idunnololz.summit.databinding.PostCommentExpandedCompactItemBinding
import com.idunnololz.summit.databinding.PostHeaderItemBinding
import com.idunnololz.summit.databinding.PostMissingCommentItemBinding
import com.idunnololz.summit.databinding.PostMoreCommentsItemBinding
import com.idunnololz.summit.databinding.PostPendingCommentCollapsedItemBinding
import com.idunnololz.summit.databinding.PostPendingCommentExpandedItemBinding
import com.idunnololz.summit.lemmy.LemmyContentHelper
import com.idunnololz.summit.lemmy.LemmyHeaderHelper
import com.idunnololz.summit.lemmy.LemmyTextHelper
import com.idunnololz.summit.lemmy.LemmyUtils
import com.idunnololz.summit.lemmy.LinkResolver
import com.idunnololz.summit.lemmy.PageRef
import com.idunnololz.summit.lemmy.inbox.CommentBackedItem
import com.idunnololz.summit.lemmy.inbox.InboxItem
import com.idunnololz.summit.lemmy.inbox.ReportItem
import com.idunnololz.summit.lemmy.post.QueryMatchHelper
import com.idunnololz.summit.lemmy.post.QueryMatchHelper.HighlightTextData
import com.idunnololz.summit.lemmy.post.ThreadLinesData
import com.idunnololz.summit.lemmy.postListView.CommentUiConfig
import com.idunnololz.summit.lemmy.postListView.PostAndCommentsUiConfig
import com.idunnololz.summit.lemmy.postListView.PostUiConfig
import com.idunnololz.summit.lemmy.utils.VotableRef
import com.idunnololz.summit.lemmy.utils.bind
import com.idunnololz.summit.offline.OfflineManager
import com.idunnololz.summit.preferences.GlobalFontSizeId
import com.idunnololz.summit.preferences.Preferences
import com.idunnololz.summit.preview.VideoType
import com.idunnololz.summit.util.CustomLinkMovementMethod
import com.idunnololz.summit.util.DefaultLinkLongClickListener
import com.idunnololz.summit.util.LinkUtils
import com.idunnololz.summit.util.RecycledState
import com.idunnololz.summit.util.Size
import com.idunnololz.summit.util.Utils
import com.idunnololz.summit.util.ViewRecycler
import com.idunnololz.summit.util.abbrevNumber
import com.idunnololz.summit.util.dateStringToPretty
import com.idunnololz.summit.util.ext.appendLink
import com.idunnololz.summit.util.ext.getColorCompat
import com.idunnololz.summit.util.ext.getColorFromAttribute
import com.idunnololz.summit.util.ext.getDimen
import com.idunnololz.summit.util.ext.getDrawableCompat
import com.idunnololz.summit.util.ext.getResIdFromAttribute
import com.idunnololz.summit.video.ExoPlayerManager
import com.idunnololz.summit.video.VideoState
import com.idunnololz.summit.view.LemmyHeaderView
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

    var tapCommentToCollapse = preferences.tapCommentToCollapse
        private set

    private val inflater = LayoutInflater.from(activity)

    private var postUiConfig: PostUiConfig = uiConfig.postUiConfig
    private var commentUiConfig: CommentUiConfig = uiConfig.commentUiConfig
    private var globalFontSizeMultiplier: Float =
        GlobalFontSizeId.getFontSizeMultiplier(preferences.globalFontSize)

    val lemmyHeaderHelper = LemmyHeaderHelper(context)
    private val lemmyContentHelper = LemmyContentHelper(
        context,
        offlineManager,
        ExoPlayerManager.get(activity),
    ).also {
        it.globalFontSizeMultiplier = globalFontSizeMultiplier
    }
    val voteUiHandler = accountActionsManager.voteUiHandler

    private var upvoteColor = preferences.upvoteColor
    private var downvoteColor = preferences.downvoteColor
    private val normalTextColor = ContextCompat.getColor(context, R.color.colorText)
    private val unimportantTextColor = ContextCompat.getColor(context, R.color.colorTextFaint)
    private var showUpAndDownVotes: Boolean = preferences.showUpAndDownVotes
    private val selectableItemBackgroundBorderless =
        context.getResIdFromAttribute(androidx.appcompat.R.attr.selectableItemBackgroundBorderless)
    private var displayInstanceStyle = preferences.displayInstanceStyle

    private val viewRecycler: ViewRecycler<View> = ViewRecycler<View>()

    private val tempSize = Size()

    init {
        lemmyContentHelper.config = uiConfig.postUiConfig.fullContentConfig
    }

    fun onPreferencesChanged() {
        uiConfig = preferences.getPostAndCommentsUiConfig()
        hideCommentActions = preferences.hideCommentActions
        tapCommentToCollapse = preferences.tapCommentToCollapse
        globalFontSizeMultiplier = GlobalFontSizeId.getFontSizeMultiplier(preferences.globalFontSize)
        lemmyContentHelper.globalFontSizeMultiplier = globalFontSizeMultiplier
        lemmyContentHelper.alwaysShowLinkBelowPost = preferences.alwaysShowLinkButtonBelowPost
        displayInstanceStyle = preferences.displayInstanceStyle

        upvoteColor = preferences.upvoteColor
        downvoteColor = preferences.downvoteColor
        showUpAndDownVotes = preferences.showUpAndDownVotes
    }

    class CustomViewHolder(
        val root: ViewGroup,
        val commentButton: View,
        val controlsDivider: View,
        var upvoteCount: TextView? = null,
        var upvoteButton: View? = null,
        var downvoteCount: TextView? = null,
        var downvoteButton: View? = null,
    )

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
        highlightTextData: HighlightTextData?,
        onRevealContentClickedFn: () -> Unit,
        onImageClick: (Either<PostView, CommentView>, View?, String) -> Unit,
        onVideoClick: (url: String, videoType: VideoType, videoState: VideoState?) -> Unit,
        onPageClick: (PageRef) -> Unit,
        onAddCommentClick: (Either<PostView, CommentView>) -> Unit,
        onPostMoreClick: (PostView) -> Unit,
        onLinkLongClick: (url: String, text: String?) -> Unit,
        onSignInRequired: () -> Unit,
        onInstanceMismatch: (String, String) -> Unit,
    ) = with(binding) {
        val viewHolder =
            root.getTag(R.id.view_holder) as? CustomViewHolder
                ?: run {
                    val vh = CustomViewHolder(root, commentButton, controlsDivider)
                    this.root.setTag(R.id.view_holder, vh)
                    vh
                }

        ensureContent(viewHolder)

        scaleTextSizes()
        viewHolder.scaleTextSizes()

        lemmyHeaderHelper.populateHeaderSpan(
            headerContainer = headerContainer,
            postView = postView,
            instance = instance,
            onPageClick = onPageClick,
            onLinkLongClick = onLinkLongClick,
            displayInstanceStyle = displayInstanceStyle,
            listAuthor = true,
        )

        LemmyTextHelper.bindText(
            title,
            postView.post.name,
            instance,
            highlight = if (highlightTextData?.targetSubtype == 0) {
                highlightTextData
            } else {
                highlightTextData?.copy(matchIndex = null)
            },
            onImageClick = {
                onImageClick(Either.Left(postView), null, it)
            },
            onVideoClick = { url ->
                onVideoClick(url, VideoType.UNKNOWN, null)
            },
            onPageClick = onPageClick,
            onLinkLongClick = onLinkLongClick,
        )

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
            videoViewMaxHeight = (
                container.height -
                    Utils.convertDpToPixel(56f) -
                    Utils.convertDpToPixel(16f)
                ).toInt(),
            contentMaxWidth = contentMaxWidth,
            fullImageViewTransitionName = "post_image",
            postView = postView,
            instance = instance,
            rootView = root,
            fullContentContainerView = fullContent,
            lazyUpdate = !updateContent,
            videoState = videoState,
            highlight = if (highlightTextData?.targetSubtype == 1) {
                highlightTextData
            } else {
                highlightTextData?.copy(matchIndex = null)
            },
            onFullImageViewClickListener = { view, url ->
                onImageClick(Either.Left(postView), view, url)
            },
            onImageClickListener = { url ->
                onImageClick(Either.Left(postView), null, url)
            },
            onVideoClickListener = onVideoClick,
            onRevealContentClickedFn = onRevealContentClickedFn,
            onItemClickListener = {},
            onLemmyUrlClick = onPageClick,
            onLinkLongClick = onLinkLongClick,
        )

        voteUiHandler.bind(
            lifecycleOwner = viewLifecycleOwner,
            instance = instance,
            postView = postView,
            upVoteView = viewHolder.upvoteButton,
            downVoteView = viewHolder.downvoteButton,
            scoreView = viewHolder.upvoteCount!!,
            upvoteCount = viewHolder.upvoteCount,
            downvoteCount = viewHolder.downvoteCount,
            onUpdate = null,
            onSignInRequired = onSignInRequired,
            onInstanceMismatch = onInstanceMismatch,
        )

        root.tag = postView
    }

    fun ensureContent(vh: CustomViewHolder) = with(vh) {
        val currentState = vh.root.getTag(R.id.show_up_and_down_votes) as? Boolean

        if (showUpAndDownVotes == currentState) {
            return@with
        }

        vh.root.setTag(R.id.show_up_and_down_votes, showUpAndDownVotes)

        root.removeView(upvoteButton)
        root.removeView(downvoteButton)
        root.removeView(upvoteCount)

        if (showUpAndDownVotes) {
            val downvoteButton = MaterialButton(
                context,
                null,
                context.getResIdFromAttribute(
                    com.google.android.material.R.attr.materialIconButtonStyle)
            ).apply {
                id = View.generateViewId()
                layoutParams = ConstraintLayout.LayoutParams(
                    ConstraintLayout.LayoutParams.WRAP_CONTENT,
                    ConstraintLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topToTop = commentButton.id
                    bottomToBottom = commentButton.id
                    endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
                    marginEnd = context.getDimen(R.dimen.padding)
                }
                iconPadding = context.getDimen(R.dimen.padding_half)
                setPadding(
                    context.getDimen(R.dimen.padding_half),
                    context.getDimen(R.dimen.padding_quarter),
                    context.getDimen(R.dimen.padding),
                    context.getDimen(R.dimen.padding_quarter),
                )
                setIconResource(R.drawable.baseline_expand_more_18)
                setBackgroundResource(R.drawable.downvote_chip_bg)
                backgroundTintList = null
                minHeight = Utils.convertDpToPixel(32f).toInt()
                gravity = Gravity.CENTER
            }.also {
                downvoteButton = it
                downvoteCount = it
            }
            root.addView(downvoteButton)

            val upvoteButton = MaterialButton(
                context,
                null,
                context.getResIdFromAttribute(com.google.android.material.R.attr.materialIconButtonStyle)
            ).apply {
                id = View.generateViewId()
                layoutParams = ConstraintLayout.LayoutParams(
                    ConstraintLayout.LayoutParams.WRAP_CONTENT,
                    ConstraintLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topToTop = commentButton.id
                    bottomToBottom = commentButton.id
                    endToStart = downvoteButton.id
                    marginEnd = Utils.convertDpToPixel(2f).toInt()
                }
                iconPadding = context.getDimen(R.dimen.padding_half)
                setPadding(
                    context.getDimen(R.dimen.padding_half),
                    context.getDimen(R.dimen.padding_quarter),
                    context.getDimen(R.dimen.padding),
                    context.getDimen(R.dimen.padding_quarter),
                )
                setIconResource(R.drawable.baseline_expand_less_18)
                setBackgroundResource(R.drawable.upvote_chip_bg)
                backgroundTintList = null
                minHeight = Utils.convertDpToPixel(32f).toInt()
                gravity = Gravity.CENTER
            }.also {
                upvoteButton = it
                upvoteCount = it
            }
            root.addView(upvoteButton)

            controlsDivider.updateLayoutParams<ConstraintLayout.LayoutParams> {
                endToStart = upvoteButton.id
                marginEnd = context.getDimen(R.dimen.padding_half)
            }
        } else {
            val downvoteButton = ImageView(
                context
            ).apply {
                id = View.generateViewId()
                layoutParams = ConstraintLayout.LayoutParams(
                    ConstraintLayout.LayoutParams.WRAP_CONTENT,
                    ConstraintLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topToTop = commentButton.id
                    bottomToBottom = commentButton.id
                    endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
                    marginEnd = context.getDimen(R.dimen.padding_quarter)
                }
                setPadding(context.getDimen(R.dimen.padding_half))
                setImageResource(R.drawable.baseline_arrow_downward_24)
                setBackgroundResource(selectableItemBackgroundBorderless)
            }.also {
                downvoteButton = it
            }
            root.addView(downvoteButton)

            val upvoteCount = TextView(
                context
            ).apply {
                id = View.generateViewId()
                layoutParams = ConstraintLayout.LayoutParams(
                    ConstraintLayout.LayoutParams.WRAP_CONTENT,
                    ConstraintLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topToTop = commentButton.id
                    bottomToBottom = commentButton.id
                    endToStart = downvoteButton.id
                }
            }.also {
                upvoteCount = it
            }
            root.addView(upvoteCount)

            val upvoteButton = ImageView(
                context
            ).apply {
                id = View.generateViewId()
                layoutParams = ConstraintLayout.LayoutParams(
                    ConstraintLayout.LayoutParams.WRAP_CONTENT,
                    ConstraintLayout.LayoutParams.WRAP_CONTENT
                ).apply {

                    topToTop = commentButton.id
                    bottomToBottom = commentButton.id
                    endToStart = upvoteCount.id
                }
                setPadding(context.getDimen(R.dimen.padding_half))
                setImageResource(R.drawable.baseline_arrow_upward_24)
                setBackgroundResource(selectableItemBackgroundBorderless)
            }.also {
                upvoteButton = it
            }
            root.addView(upvoteButton)

            controlsDivider.updateLayoutParams<ConstraintLayout.LayoutParams> {
                endToStart = upvoteButton.id
            }
        }
    }

    fun bindCommentViewExpanded(
        h: ViewHolder,
        binding: CommentExpandedViewHolder,
        baseDepth: Int,
        depth: Int,
        commentView: CommentView,
        isDeleting: Boolean,
        isRemoved: Boolean,
        content: String,
        instance: String,
        isPostLocked: Boolean,
        isUpdating: Boolean,
        highlight: Boolean,
        highlightForever: Boolean,
        viewLifecycleOwner: LifecycleOwner,
        isActionsExpanded: Boolean,
        highlightTextData: HighlightTextData?,
        onImageClick: (Either<PostView, CommentView>, View?, String) -> Unit,
        onVideoClick: (url: String, videoType: VideoType, videoState: VideoState?) -> Unit,
        onPageClick: (PageRef) -> Unit,
        collapseSection: (position: Int) -> Unit,
        toggleActionsExpanded: () -> Unit,
        onAddCommentClick: (Either<PostView, CommentView>) -> Unit,
        onCommentMoreClick: (CommentView) -> Unit,
        onLinkLongClick: (url: String, text: String) -> Unit,
        onSignInRequired: () -> Unit,
        onInstanceMismatch: (String, String) -> Unit,
    ) = with(binding) {
        if (showUpAndDownVotes) {
            binding.headerView.textView3.visibility = View.VISIBLE

            binding.upvoteCount = binding.headerView.textView2
            binding.downvoteCount = binding.headerView.textView3
        } else {
            binding.headerView.textView3.visibility = View.GONE

            binding.upvoteCount = null
            binding.downvoteCount = null
        }

        scaleTextSizes()

        val isCompactView = this.rawBinding is PostCommentExpandedCompactItemBinding

        fun getActionsView(): CommentActionsViewBinding =
            (
                viewRecycler.getRecycledView(R.layout.comment_actions_view)
                    ?.let { CommentActionsViewBinding.bind(it) }
                    ?: CommentActionsViewBinding.inflate(
                        inflater,
                        binding.actionsContainer,
                        false,
                    )
                )
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

        threadLinesSpacer.updateThreadSpacer(depth, baseDepth)
        lemmyHeaderHelper.populateHeaderSpan(
            headerContainer = headerView,
            commentView = commentView,
            instance = instance,
            score = null,
            onPageClick = onPageClick,
            onLinkLongClick = onLinkLongClick,
            displayInstanceStyle = displayInstanceStyle,
        )

        if (commentView.comment.deleted || isDeleting) {
            LemmyTextHelper.bindText(
                textView = text,
                text = context.getString(R.string.deleted_special2),
                instance = instance,
                highlight = highlightTextData,
                onImageClick = {
                    onImageClick(Either.Right(commentView), null, it)
                },
                onVideoClick = { url ->
                    onVideoClick(url, VideoType.UNKNOWN, null)
                },
                onPageClick = onPageClick,
                onLinkLongClick = onLinkLongClick,
            )
        } else if (commentView.comment.removed || isRemoved) {
            LemmyTextHelper.bindText(
                textView = text,
                text = context.getString(R.string.removed_special2),
                instance = instance,
                highlight = highlightTextData,
                onImageClick = {
                    onImageClick(Either.Right(commentView), null, it)
                },
                onVideoClick = { url ->
                    onVideoClick(url, VideoType.UNKNOWN, null)
                },
                onPageClick = onPageClick,
                onLinkLongClick = onLinkLongClick,
            )
        } else {
            LemmyTextHelper.bindText(
                textView = text,
                text = content,
                instance = instance,
                highlight = highlightTextData,
                onImageClick = {
                    onImageClick(Either.Right(commentView), null, it)
                },
                onVideoClick = { url ->
                    onVideoClick(url, VideoType.UNKNOWN, null)
                },
                onPageClick = onPageClick,
                onLinkLongClick = onLinkLongClick,
            )
        }

        mediaContainer.removeAllViews()
        mediaContainer.visibility = View.GONE

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
            onCommentMoreClick(commentView)
        }
        if (commentView.comment.distinguished) {
            overlay.visibility = View.VISIBLE
            overlay.setBackgroundResource(R.drawable.locked_overlay)
        } else {
            overlay.visibility = View.GONE
        }

        voteUiHandler.bind(
            lifecycleOwner = viewLifecycleOwner,
            instance = instance,
            commentView = commentView,
            upVoteView = upvoteButton,
            downVoteView = downvoteButton,
            scoreView = scoreCount,
            upvoteCount = upvoteCount,
            downvoteCount = downvoteCount,
            onUpdate = {
                if (showUpAndDownVotes) {
                    if (it > 0) {
                        upvoteCount!!.setTextColor(upvoteColor)
                        TextViewCompat.setCompoundDrawableTintList(
                            upvoteCount!!,
                            ColorStateList.valueOf(upvoteColor),
                        )
                        downvoteCount!!.setTextColor(unimportantTextColor)
                        TextViewCompat.setCompoundDrawableTintList(
                            downvoteCount!!,
                            ColorStateList.valueOf(unimportantTextColor),
                        )
                    } else if (it == 0) {
                        upvoteCount!!.setTextColor(unimportantTextColor)
                        TextViewCompat.setCompoundDrawableTintList(
                            upvoteCount!!,
                            ColorStateList.valueOf(unimportantTextColor),
                        )
                        downvoteCount!!.setTextColor(unimportantTextColor)
                        TextViewCompat.setCompoundDrawableTintList(
                            downvoteCount!!,
                            ColorStateList.valueOf(unimportantTextColor),
                        )
                    } else {
                        upvoteCount!!.setTextColor(unimportantTextColor)
                        TextViewCompat.setCompoundDrawableTintList(
                            upvoteCount!!,
                            ColorStateList.valueOf(unimportantTextColor),
                        )
                        downvoteCount!!.setTextColor(downvoteColor)
                        TextViewCompat.setCompoundDrawableTintList(
                            downvoteCount!!,
                            ColorStateList.valueOf(downvoteColor),
                        )
                    }
                } else {
                    if (it > 0) {
                        scoreCount.setTextColor(upvoteColor)
                        TextViewCompat.setCompoundDrawableTintList(
                            scoreCount,
                            ColorStateList.valueOf(upvoteColor),
                        )
                    } else if (it == 0) {
                        scoreCount.setTextColor(unimportantTextColor)
                        TextViewCompat.setCompoundDrawableTintList(
                            scoreCount,
                            ColorStateList.valueOf(unimportantTextColor),
                        )
                    } else {
                        scoreCount.setTextColor(downvoteColor)
                        TextViewCompat.setCompoundDrawableTintList(
                            scoreCount,
                            ColorStateList.valueOf(downvoteColor),
                        )
                    }
                }
            },
            onSignInRequired = onSignInRequired,
            onInstanceMismatch = onInstanceMismatch,
        )

        highlightComment(highlight, highlightForever, highlightBg)

        if (tapCommentToCollapse) {
            binding.root.setOnClickListener {
                collapseSection(h.bindingAdapterPosition)
            }
        }

        if (isCompactView) {
            if (tapCommentToCollapse) {
                binding.root.setOnLongClickListener {
                    toggleActionsExpanded()
                    true
                }
            } else {
                binding.root.setOnClickListener {
                    toggleActionsExpanded()
                }
                binding.root.setOnLongClickListener {
                    toggleActionsExpanded()
                    true
                }
            }
        }

        if (isUpdating) {
            progressBar.visibility = View.VISIBLE
        } else {
            progressBar.visibility = View.GONE
        }

        root.tag = ThreadLinesData(
            depth,
            baseDepth,
            commentUiConfig.indentationPerLevelDp,
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
        instance: String,
        expandSection: (position: Int) -> Unit,
        onPageClick: (PageRef) -> Unit,
        onLinkLongClick: (url: String, text: String) -> Unit,
    ) = with(binding) {
        scaleTextSizes()

        threadLinesSpacer.updateThreadSpacer(depth, baseDepth)
        lemmyHeaderHelper.populateHeaderSpan(
            headerContainer = headerView,
            commentView = commentView,
            instance = instance,
            score = accountActionsManager.getScore(VotableRef.CommentRef(commentView.comment.id)),
            onPageClick = onPageClick,
            detailed = true,
            childrenCount = childrenCount,
            onLinkLongClick = onLinkLongClick,
            displayInstanceStyle = displayInstanceStyle,
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

        root.tag = ThreadLinesData(
            depth,
            baseDepth,
            commentUiConfig.indentationPerLevelDp,
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
        onImageClick: (Either<PostView, CommentView>?, View?, String) -> Unit,
        onVideoClick: (url: String, videoType: VideoType, videoState: VideoState?) -> Unit,
        onPageClick: (PageRef) -> Unit,
        onLinkLongClick: (url: String, text: String) -> Unit,
        collapseSection: (position: Int) -> Unit,
    ) = with(binding) {
        scaleTextSizes()

        val context = binding.root.context

        threadLinesSpacer.updateThreadSpacer(depth, baseDepth)
        headerContainer.setTextFirstPart(author ?: context.getString(R.string.unknown))
        headerContainer.setTextSecondPart("")

        LemmyTextHelper.bindText(
            textView = text,
            text = content,
            instance = instance,
            onImageClick = {
                onImageClick(null, null, it)
            },
            onVideoClick = { url ->
                onVideoClick(url, VideoType.UNKNOWN, null)
            },
            onPageClick = onPageClick,
            onLinkLongClick = onLinkLongClick,
        )

        collapseSectionButton.setOnClickListener {
            collapseSection(h.bindingAdapterPosition)
        }
        topHotspot.setOnClickListener {
            collapseSection(h.bindingAdapterPosition)
        }

        highlightComment(highlight, highlightForever, highlightBg)

        root.tag = ThreadLinesData(
            depth,
            baseDepth,
            commentUiConfig.indentationPerLevelDp,
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
        threadLinesSpacer.updateThreadSpacer(depth, baseDepth)
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

        root.tag = ThreadLinesData(
            depth,
            baseDepth,
            commentUiConfig.indentationPerLevelDp,
        )
    }

    fun bindMessage(
        viewLifecycleOwner: LifecycleOwner,
        b: InboxListItemBinding,
        instance: String,
        accountId: PersonId?,
        item: InboxItem,
        onImageClick: (String) -> Unit,
        onVideoClick: (url: String, videoType: VideoType, videoState: VideoState?) -> Unit,
        onPageClick: (PageRef) -> Unit,
        onMarkAsRead: (InboxItem, Boolean) -> Unit,
        onMessageClick: (InboxItem) -> Unit,
        onAddCommentClick: (InboxItem) -> Unit,
        onOverflowMenuClick: (InboxItem) -> Unit,
        onLinkLongClick: (url: String, text: String) -> Unit,
        onSignInRequired: () -> Unit,
        onInstanceMismatch: (String, String) -> Unit,
    ) = with(b) {
        b.author.text = buildSpannedString {
            run {
                val s = length
                appendLink(
                    item.authorName,
                    LinkUtils.getLinkForPerson(item.authorInstance, item.authorName),
                    underline = false,
                )
                val e = length
                setSpan(
                    ForegroundColorSpan(normalTextColor),
                    s,
                    e,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
                setSpan(
                    StyleSpan(Typeface.BOLD),
                    s,
                    e,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            }
        }
        b.author.movementMethod = CustomLinkMovementMethod().apply {
            onLinkLongClickListener = DefaultLinkLongClickListener(context, onLinkLongClick)
            onLinkClickListener = object : CustomLinkMovementMethod.OnLinkClickListener {
                override fun onClick(
                    textView: TextView,
                    url: String,
                    text: String,
                    rect: RectF,
                ): Boolean {
                    val pageRef = LinkResolver.parseUrl(url, instance)

                    return if (pageRef != null) {
                        onPageClick(pageRef)
                        true
                    } else {
                        false
                    }
                }
            }
        }

        when (item) {
            is CommentBackedItem -> {
                val scoreDrawable = context.getDrawableCompat(R.drawable.baseline_arrow_upward_24)
                scoreDrawable?.setBounds(
                    0,
                    0,
                    Utils.convertDpToPixel(16f).toInt(),
                    Utils.convertDpToPixel(16f).toInt(),
                )
                b.score.setCompoundDrawablesRelative(
                    scoreDrawable,
                    null,
                    null,
                    null,
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
                    null,
                    null,
                    onSignInRequired,
                    onInstanceMismatch,
                )
                upvoteButton.isEnabled = true
                downvoteButton.isEnabled = true
                b.reply.isEnabled = true
            }

            is ReportItem -> {
                voteUiHandler.unbindVoteUi(b.score)
                b.score.visibility = View.GONE

                upvoteButton.isEnabled = false
                downvoteButton.isEnabled = false
                b.reply.isEnabled = false
            }

            else -> {
                voteUiHandler.unbindVoteUi(b.score)
                b.score.visibility = View.GONE

                upvoteButton.isEnabled = false
                downvoteButton.isEnabled = false
                b.reply.isEnabled = true
            }
        }

        val drawable = when (item) {
            is InboxItem.MentionInboxItem -> {
                TextViewCompat.setCompoundDrawableTintList(
                    b.author,
                    ColorStateList.valueOf(context.getColorCompat(R.color.colorTextTitle))
                )
                context.getDrawableCompat(R.drawable.baseline_at_24)
            }
            is InboxItem.MessageInboxItem -> {
                TextViewCompat.setCompoundDrawableTintList(
                    b.author,
                    ColorStateList.valueOf(context.getColorCompat(R.color.colorTextTitle))
                )
                context.getDrawableCompat(R.drawable.baseline_email_24)
            }
            is InboxItem.ReplyInboxItem -> {
                TextViewCompat.setCompoundDrawableTintList(
                    b.author,
                    ColorStateList.valueOf(context.getColorCompat(R.color.colorTextTitle))
                )
                context.getDrawableCompat(R.drawable.baseline_reply_24)
            }
            is InboxItem.ReportMessageInboxItem,
            is InboxItem.ReportPostInboxItem,
            is InboxItem.ReportCommentInboxItem, -> {
                TextViewCompat.setCompoundDrawableTintList(
                    b.author,
                    ColorStateList.valueOf(context.getColorCompat(R.color.style_red))
                )
                context.getDrawableCompat(R.drawable.baseline_outlined_flag_24)
            }
        }
        drawable?.setBounds(
            0,
            0,
            Utils.convertDpToPixel(16f).toInt(),
            Utils.convertDpToPixel(16f).toInt(),
        )
        val faintTextColor = context.getColorCompat(R.color.colorTextFaint)

        b.author.setCompoundDrawablesRelative(
            drawable,
            null,
            null,
            null,
        )
        b.date.text = dateStringToPretty(context, item.lastUpdate)

        LemmyTextHelper.bindText(
            textView = b.title,
            text = item.title,
            instance = instance,
            onImageClick = {
                onImageClick(it)
            },
            onVideoClick = { url ->
                onVideoClick(url, VideoType.UNKNOWN, null)
            },
            onPageClick = onPageClick,
            onLinkLongClick = onLinkLongClick,
        )

        if (item.isDeleted) {
            b.content.text = buildSpannedString {
                append(context.getString(R.string.deleted_special))
                setSpan(StyleSpan(Typeface.ITALIC), 0, length, 0)
            }
        } else if (item.isRemoved) {
            b.content.text = buildSpannedString {
                append(context.getString(R.string.removed_special))
                setSpan(StyleSpan(Typeface.ITALIC), 0, length, 0)
            }
        } else {
            LemmyTextHelper.bindText(
                textView = b.content,
                text = item.content,
                instance = instance,
                onImageClick = {
                    onImageClick(it)
                },
                onVideoClick = { url ->
                    onVideoClick(url, VideoType.UNKNOWN, null)
                },
                onPageClick = onPageClick,
                onLinkLongClick = onLinkLongClick,
            )
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
                    context.getColorFromAttribute(androidx.appcompat.R.attr.colorControlNormal),
                )
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
            item.authorId == accountId
        ) {
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

    fun bindMoreCommentsItem(
        b: PostMoreCommentsItemBinding,
        depth: Int,
        baseDepth: Int,
    ) = with(b) {
        threadLinesSpacer.updateThreadSpacer(depth, baseDepth)

        b.root.tag = ThreadLinesData(
            depth = depth,
            baseDepth = baseDepth,
            commentUiConfig.indentationPerLevelDp,
        )
    }

    fun bindMissingCommentItem(b: PostMissingCommentItemBinding, depth: Int, baseDepth: Int) {
        b.text.text = buildSpannedString {
            append(context.getString(R.string.missing_comment_special))
            setSpan(StyleSpan(Typeface.ITALIC), 0, length, 0)
        }
        b.threadLinesSpacer.updateThreadSpacer(depth, baseDepth)
        b.root.tag = ThreadLinesData(
            depth = depth,
            baseDepth = baseDepth,
            commentUiConfig.indentationPerLevelDp,
        )
    }

    fun recycle(b: PostHeaderItemBinding): RecycledState {
        val recycledState = lemmyContentHelper.recycleFullContent(b.fullContent)
        offlineManager.cancelFetch(b.root)
        (b.root.getTag(R.id.view_holder) as? CustomViewHolder)?.upvoteCount?.let {
            voteUiHandler.unbindVoteUi(it)
        }
        return recycledState
    }

    fun recycle(b: CommentExpandedViewHolder) {
        voteUiHandler.unbindVoteUi(b.scoreCount)
        b.actionsContainer?.let {
            if (it.childCount > 0) {
                val actionsView = it.getChildAt(0)
                it.removeAllViews()
                viewRecycler.addRecycledView(actionsView, R.layout.comment_actions_view)
            }
        }
    }

    fun populateHeaderSpan(
        headerContainer: LemmyHeaderView,
        commentView: CommentView,
        instance: String,
        onPageClick: (PageRef) -> Unit,
        onLinkLongClick: (url: String, text: String) -> Unit,
    ) {
        lemmyHeaderHelper.populateHeaderSpan(
            headerContainer,
            commentView,
            instance,
            score = null,
            onPageClick,
            onLinkLongClick,
            displayInstanceStyle,
        )
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
    }

    private fun CustomViewHolder.scaleTextSizes() {
        upvoteCount?.textSize = postUiConfig.footerTextSizeSp.toPostTextSize()
        downvoteCount?.textSize = postUiConfig.footerTextSizeSp.toPostTextSize()
    }


    private fun CommentExpandedViewHolder.scaleTextSizes() {
        headerView.textSize = postUiConfig.headerTextSizeSp.toCommentTextSize()
        text.textSize = commentUiConfig.contentTextSizeSp.toCommentTextSize()
        scoreCount.textSize = postUiConfig.footerTextSizeSp.toCommentTextSize()
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

    private fun Space.updateThreadSpacer(depth: Int, baseDepth: Int) {
        val absoluteDepth = depth - baseDepth

        updateLayoutParams {
            width = if (absoluteDepth == 0) {
                0
            } else {
                Utils.convertDpToPixel(
                    (commentUiConfig.indentationPerLevelDp * (absoluteDepth - 1)).toFloat() + 16f,
                ).toInt()
            }
        }
    }

    private fun Float.toPostTextSize(): Float =
        this * postUiConfig.textSizeMultiplier * globalFontSizeMultiplier

    private fun Float.toCommentTextSize(): Float =
        this * commentUiConfig.textSizeMultiplier * globalFontSizeMultiplier
}