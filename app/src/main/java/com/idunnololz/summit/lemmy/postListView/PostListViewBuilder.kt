package com.idunnololz.summit.lemmy.postListView

import android.content.Context
import android.content.res.ColorStateList
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams
import android.view.ViewGroup.MarginLayoutParams
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.Barrier
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.view.marginBottom
import androidx.core.view.marginTop
import androidx.core.view.setPadding
import androidx.core.view.updateLayoutParams
import androidx.core.widget.TextViewCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.LifecycleOwner
import coil.load
import com.commit451.coiltransformations.BlurTransformation
import com.google.android.material.button.MaterialButton
import com.google.android.material.imageview.ShapeableImageView
import com.idunnololz.summit.R
import com.idunnololz.summit.account.AccountActionsManager
import com.idunnololz.summit.api.dto.PostView
import com.idunnololz.summit.api.utils.PostType
import com.idunnololz.summit.api.utils.getType
import com.idunnololz.summit.databinding.ListingItemCard2Binding
import com.idunnololz.summit.databinding.ListingItemCard3Binding
import com.idunnololz.summit.databinding.ListingItemCardBinding
import com.idunnololz.summit.databinding.ListingItemCompactBinding
import com.idunnololz.summit.databinding.ListingItemFullBinding
import com.idunnololz.summit.databinding.ListingItemLargeListBinding
import com.idunnololz.summit.databinding.ListingItemListBinding
import com.idunnololz.summit.databinding.SearchResultPostItemBinding
import com.idunnololz.summit.lemmy.CommunityRef
import com.idunnololz.summit.lemmy.LemmyContentHelper
import com.idunnololz.summit.lemmy.LemmyHeaderHelper
import com.idunnololz.summit.lemmy.LemmyTextHelper
import com.idunnololz.summit.lemmy.LemmyUtils
import com.idunnololz.summit.lemmy.PageRef
import com.idunnololz.summit.lemmy.toCommunityRef
import com.idunnololz.summit.lemmy.utils.bind
import com.idunnololz.summit.offline.OfflineManager
import com.idunnololz.summit.preferences.GlobalFontSizeId
import com.idunnololz.summit.preferences.Preferences
import com.idunnololz.summit.preferences.ThemeManager
import com.idunnololz.summit.preview.VideoType
import com.idunnololz.summit.util.ContentUtils
import com.idunnololz.summit.util.Size
import com.idunnololz.summit.util.Utils
import com.idunnololz.summit.util.ext.getColorCompat
import com.idunnololz.summit.util.ext.getDimen
import com.idunnololz.summit.util.ext.getResIdFromAttribute
import com.idunnololz.summit.video.ExoPlayerManager
import com.idunnololz.summit.video.VideoState
import dagger.hilt.android.qualifiers.ActivityContext
import dagger.hilt.android.scopes.ActivityScoped
import javax.inject.Inject

@ActivityScoped
class PostListViewBuilder @Inject constructor(
    private val activity: FragmentActivity,
    @ActivityContext private val context: Context,
    private val offlineManager: OfflineManager,
    private val accountActionsManager: AccountActionsManager,
    private val preferences: Preferences,
    private val themeManager: ThemeManager,
) {

    var postUiConfig: PostInListUiConfig = preferences.getPostInListUiConfig()
        set(value) {
            field = value

            onPostUiConfigUpdated()
        }

    private var globalFontSizeMultiplier: Float =
        GlobalFontSizeId.getFontSizeMultiplier(preferences.globalFontSize)

    private val lemmyHeaderHelper = LemmyHeaderHelper(context)
    private val lemmyContentHelper = LemmyContentHelper(
        context,
        offlineManager,
        ExoPlayerManager.get(activity),
    ).also {
        it.globalFontSizeMultiplier = globalFontSizeMultiplier
    }

    private val padding = context.getDimen(R.dimen.padding)
    private val paddingHalf = context.getDimen(R.dimen.padding_half)

    private val voteUiHandler = accountActionsManager.voteUiHandler
    private var textSizeMultiplier: Float = postUiConfig.textSizeMultiplier
    private var singleTapToViewImage: Boolean = preferences.postListViewImageOnSingleTap
    private var contentMaxLines: Int = postUiConfig.contentMaxLines
    private var showUpAndDownVotes: Boolean = preferences.showUpAndDownVotes
    private var displayInstanceStyle = preferences.displayInstanceStyle

    private val upvoteColor = preferences.upvoteColor
    private val downvoteColor = preferences.downvoteColor
    private val normalTextColor = ContextCompat.getColor(context, R.color.colorText)
    private val unimportantTextColor = ContextCompat.getColor(context, R.color.colorTextFaint)

    private val selectableItemBackground =
        context.getResIdFromAttribute(androidx.appcompat.R.attr.selectableItemBackground)
    private val selectableItemBackgroundBorderless =
        context.getResIdFromAttribute(androidx.appcompat.R.attr.selectableItemBackgroundBorderless)

    private val tempSize = Size()

    init {
        onPostUiConfigUpdated()
    }

    fun onPostUiConfigUpdated() {
        lemmyContentHelper.config = postUiConfig.fullContentConfig
        textSizeMultiplier = postUiConfig.textSizeMultiplier
        contentMaxLines = postUiConfig.contentMaxLines

        globalFontSizeMultiplier =
            GlobalFontSizeId.getFontSizeMultiplier(preferences.globalFontSize)
        lemmyContentHelper.globalFontSizeMultiplier = globalFontSizeMultiplier
        lemmyContentHelper.alwaysShowLinkBelowPost = preferences.alwaysShowLinkButtonBelowPost
        singleTapToViewImage = preferences.postListViewImageOnSingleTap
        showUpAndDownVotes = preferences.showUpAndDownVotes
        displayInstanceStyle = preferences.displayInstanceStyle
    }

    /**
     * @param updateContent False for a fast update (also removes flickering)
     */
    fun bind(
        holder: ListingItemViewHolder,
        postView: PostView,
        instance: String,
        isRevealed: Boolean,
        contentMaxWidth: Int,
        contentPreferredHeight: Int,
        viewLifecycleOwner: LifecycleOwner,
        isExpanded: Boolean,
        isActionsExpanded: Boolean,
        alwaysRenderAsUnread: Boolean,
        updateContent: Boolean,
        highlight: Boolean,
        highlightForever: Boolean,
        onRevealContentClickedFn: () -> Unit,
        onImageClick: (PostView, sharedElementView: View?, String) -> Unit,
        onVideoClick: (url: String, videoType: VideoType, videoState: VideoState?) -> Unit,
        onPageClick: (PageRef) -> Unit,
        onItemClick: (
            instance: String,
            id: Int,
            currentCommunity: CommunityRef?,
            post: PostView,
            jumpToComments: Boolean,
            reveal: Boolean,
            videoState: VideoState?,
        ) -> Unit,
        onShowMoreOptions: (PostView) -> Unit,
        toggleItem: (postView: PostView) -> Unit,
        toggleActions: (postView: PostView) -> Unit,
        onSignInRequired: () -> Unit,
        onInstanceMismatch: (String, String) -> Unit,
        onHighlightComplete: () -> Unit,
        onLinkLongClick: (url: String, text: String?) -> Unit,
    ) {
        val url = postView.post.url
        val thumbnailUrl = postView.post.thumbnail_url
        with(holder) {
            if (holder.state.preferUpAndDownVotes != showUpAndDownVotes) {
                when (val rb = rawBinding) {
                    is ListingItemCompactBinding -> {
                        val downvoteCount = TextView(
                            context,
                        ).apply {
                            id = View.generateViewId()
                            layoutParams = ConstraintLayout.LayoutParams(
                                ConstraintLayout.LayoutParams.WRAP_CONTENT,
                                ConstraintLayout.LayoutParams.WRAP_CONTENT
                            ).apply {
                                startToEnd = R.id.score_text
                                baselineToBaseline = R.id.comment_text
                                marginStart = context.getDimen(R.dimen.padding_half)
                            }
                            setCompoundDrawablesRelativeWithIntrinsicBounds(
                                R.drawable.baseline_arrow_downward_16, 0, 0, 0)
                            TextViewCompat.setCompoundDrawableTintList(
                                this, ColorStateList.valueOf(normalTextColor))
                            includeFontPadding = false
                            setBackgroundResource(selectableItemBackground)
                            gravity = Gravity.CENTER
                        }.also {
                            downvoteCount = it
                        }
                        rb.root.addView(downvoteCount)
                    }
                    is ListingItemListBinding -> {
                        ensureActionButtons(rb.root)
                    }
                    is ListingItemCardBinding -> {
                        ensureActionButtons(rb.constraintLayout)
                        rb.bottomBarrier.referencedIds = intArrayOf(commentButton!!.id)
                        commentButton!!.updateLayoutParams<ConstraintLayout.LayoutParams> {
                            topToBottom = R.id.title
                            bottomToBottom = ConstraintLayout.LayoutParams.UNSET
                            bottomToTop = ConstraintLayout.LayoutParams.UNSET
                        }
                    }
                    is ListingItemCard2Binding -> {
                        ensureActionButtons(rb.constraintLayout)
                        commentButton!!.updateLayoutParams<ConstraintLayout.LayoutParams> {
                            topToBottom = R.id.image
                            bottomToBottom = ConstraintLayout.LayoutParams.UNSET
                            bottomToTop = ConstraintLayout.LayoutParams.UNSET
                        }
                    }
                    is ListingItemCard3Binding -> {
                        ensureActionButtons(rb.constraintLayout)
                        commentButton!!.updateLayoutParams<ConstraintLayout.LayoutParams> {
                            topToBottom = R.id.headerView
                            bottomToBottom = ConstraintLayout.LayoutParams.UNSET
                            bottomToTop = ConstraintLayout.LayoutParams.UNSET
                        }
                    }
                    is ListingItemLargeListBinding -> {
                        ensureActionButtons(rb.root)
                        commentButton!!.updateLayoutParams<ConstraintLayout.LayoutParams> {
                            topToBottom = R.id.image
                            bottomToBottom = ConstraintLayout.LayoutParams.UNSET
                            bottomToTop = ConstraintLayout.LayoutParams.UNSET
                        }
                    }
                    is ListingItemFullBinding -> {
                        ensureActionButtons(rb.root)
                        commentButton!!.updateLayoutParams<ConstraintLayout.LayoutParams> {
                            topToBottom = R.id.bottomBarrier
                            bottomToBottom = ConstraintLayout.LayoutParams.UNSET
                            bottomToTop = ConstraintLayout.LayoutParams.UNSET
                        }
                    }
                    is SearchResultPostItemBinding -> {
                        ensureActionButtons(rb.root)
                        commentButton!!.updateLayoutParams<ConstraintLayout.LayoutParams> {
                            topToBottom = R.id.bottomBarrier
                            bottomToBottom = ConstraintLayout.LayoutParams.UNSET
                            bottomToTop = ConstraintLayout.LayoutParams.UNSET
                        }
                    }
                }

                holder.state.preferUpAndDownVotes = showUpAndDownVotes
            }

            if (holder.state.preferTitleText != postUiConfig.preferTitleText) {
                if (postUiConfig.preferTitleText) {
                    when (val rb = rawBinding) {
                        is ListingItemListBinding -> {
                            TextViewCompat.setTextAppearance(
                                rb.title,
                                context.getResIdFromAttribute(
                                    com.google.android.material.R.attr.textAppearanceTitleMedium,
                                ),
                            )
                        }
                        is ListingItemCompactBinding -> {
                            TextViewCompat.setTextAppearance(
                                rb.title,
                                context.getResIdFromAttribute(
                                    com.google.android.material.R.attr.textAppearanceTitleMedium,
                                ),
                            )
                        }
                    }
                } else {
                    when (val rb = rawBinding) {
                        is ListingItemListBinding -> {
                            TextViewCompat.setTextAppearance(
                                rb.title,
                                context.getResIdFromAttribute(
                                    com.google.android.material.R.attr.textAppearanceBodyMedium,
                                ),
                            )
                        }
                        is ListingItemCompactBinding -> {
                            TextViewCompat.setTextAppearance(
                                rb.title,
                                context.getResIdFromAttribute(
                                    com.google.android.material.R.attr.textAppearanceBodyMedium,
                                ),
                            )
                        }
                    }
                }

                holder.state.preferTitleText = postUiConfig.preferTitleText
            }

            scaleTextSizes()

            if (holder.state.preferImagesAtEnd != postUiConfig.preferImagesAtEnd) {
                if (postUiConfig.preferImagesAtEnd) {
                    when (val rb = rawBinding) {
                        is ListingItemListBinding -> {
                            rb.image.updateLayoutParams<ConstraintLayout.LayoutParams> {
                                this.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
                                this.startToStart = ConstraintLayout.LayoutParams.UNSET
                                this.marginEnd = padding
                            }
                            rb.iconImage.updateLayoutParams<ConstraintLayout.LayoutParams> {
                                this.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
                                this.startToStart = ConstraintLayout.LayoutParams.UNSET
                                this.marginEnd = padding
                            }
                            rb.iconGoneSpacer.updateLayoutParams<ConstraintLayout.LayoutParams> {
                                this.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
                                this.startToStart = ConstraintLayout.LayoutParams.UNSET
                            }
                            rb.leftBarrier.type = Barrier.START
                            rb.title.updateLayoutParams<ConstraintLayout.LayoutParams> {
                                this.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
                                this.endToStart = rb.leftBarrier.id
                                this.marginStart = padding
                                this.marginEnd = paddingHalf
                            }
                        }

                        is ListingItemCompactBinding -> {
                            rb.image.updateLayoutParams<ConstraintLayout.LayoutParams> {
                                this.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
                                this.startToStart = ConstraintLayout.LayoutParams.UNSET
                                this.marginEnd = padding
                            }
                            rb.iconImage.updateLayoutParams<ConstraintLayout.LayoutParams> {
                                this.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
                                this.startToStart = ConstraintLayout.LayoutParams.UNSET
                                this.marginEnd = padding
                            }
                            rb.iconGoneSpacer.updateLayoutParams<ConstraintLayout.LayoutParams> {
                                this.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
                                this.startToStart = ConstraintLayout.LayoutParams.UNSET
                            }
                            rb.leftBarrier.type = Barrier.START
                            rb.headerContainer.updateLayoutParams<ConstraintLayout.LayoutParams> {
                                this.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
                                this.endToStart = rb.leftBarrier.id
                                this.marginStart = padding
                                this.marginEnd = paddingHalf
                            }
                            rb.title.updateLayoutParams<ConstraintLayout.LayoutParams> {
                                this.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
                                this.endToStart = rb.leftBarrier.id
                                this.marginStart = padding
                                this.marginEnd = paddingHalf
                            }
                            rb.commentText.updateLayoutParams<ConstraintLayout.LayoutParams> {
                                this.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
                                this.endToStart = rb.leftBarrier.id
                                this.marginStart = padding
                                this.marginEnd = paddingHalf
                            }
                        }

                        is ListingItemCardBinding -> {
                            rb.image.updateLayoutParams<ConstraintLayout.LayoutParams> {
                                this.topToTop = ConstraintLayout.LayoutParams.UNSET
                                this.topToBottom = rb.bottomBarrier.id
                            }
                            rb.headerView.updateLayoutParams<ConstraintLayout.LayoutParams> {
                                this.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
                            }
                        }
                    }
                }

                holder.state.preferImagesAtEnd = postUiConfig.preferImagesAtEnd
            }

            if (holder.state.preferFullSizeImages != postUiConfig.preferFullSizeImages) {
                if (postUiConfig.preferFullSizeImages) {
                    when (val rb = rawBinding) {
                        is ListingItemCardBinding -> {
                            rb.image.updateLayoutParams<ConstraintLayout.LayoutParams> {
                                this.dimensionRatio = null
                            }
                        }
                        is ListingItemCard2Binding -> {
                            rb.image.updateLayoutParams<ConstraintLayout.LayoutParams> {
                                this.dimensionRatio = null
                            }
                        }
                        is ListingItemCard3Binding -> {
                            rb.image.updateLayoutParams<ConstraintLayout.LayoutParams> {
                                this.dimensionRatio = null
                            }
                        }

                        is ListingItemLargeListBinding -> {
                            rb.image.updateLayoutParams<ConstraintLayout.LayoutParams> {
                                this.dimensionRatio = null
                            }
                        }
                    }
                } else {
                    when (val rb = rawBinding) {
                        is ListingItemCardBinding -> {
                            rb.image.updateLayoutParams<ConstraintLayout.LayoutParams> {
                                this.dimensionRatio = "H,16:9"
                            }
                        }
                        is ListingItemCard2Binding -> {
                            rb.image.updateLayoutParams<ConstraintLayout.LayoutParams> {
                                this.dimensionRatio = "H,16:9"
                            }
                        }
                        is ListingItemCard3Binding -> {
                            rb.image.updateLayoutParams<ConstraintLayout.LayoutParams> {
                                this.dimensionRatio = "H,16:9"
                            }
                        }
                        is ListingItemLargeListBinding -> {
                            rb.image.updateLayoutParams<ConstraintLayout.LayoutParams> {
                                this.dimensionRatio = "H,16:9"
                            }
                        }
                    }
                }

                holder.state.preferFullSizeImages = postUiConfig.preferFullSizeImages
            }

            val finalContentMaxWidth =
                when (val rb = rawBinding) {
                    is ListingItemCardBinding -> {
                        val lp = rb.root.layoutParams as MarginLayoutParams
                        contentMaxWidth - lp.marginStart - lp.marginEnd
                    }
                    is ListingItemCard2Binding -> {
                        val lp = rb.root.layoutParams as MarginLayoutParams
                        contentMaxWidth - lp.marginStart - lp.marginEnd
                    }
                    is ListingItemCard3Binding -> {
                        val lp = rb.root.layoutParams as MarginLayoutParams
                        contentMaxWidth - lp.marginStart - lp.marginEnd
                    }
                    is ListingItemLargeListBinding -> {
                        val lp = rb.image.layoutParams as MarginLayoutParams
                        contentMaxWidth - lp.marginStart - lp.marginEnd
                    }
                    else -> contentMaxWidth
                }
            val postImageWidth = (postUiConfig.imageWidthPercent * finalContentMaxWidth).toInt()

            fun onItemClick() {
                val videoState = if (fullContentContainerView == null) {
                    null
                } else {
                    lemmyContentHelper.getState(fullContentContainerView).videoState?.let {
                        it.copy(currentTime = it.currentTime - ExoPlayerManager.CONVENIENCE_REWIND_TIME_MS)
                    }
                }
                onItemClick(
                    instance,
                    postView.post.id,
                    postView.community.toCommunityRef(),
                    postView,
                    false,
                    isRevealed,
                    videoState,
                )
            }

            val postType = postView.getType()
            if (updateContent) {
                lemmyHeaderHelper.populateHeaderSpan(
                    headerContainer = headerContainer,
                    postView = postView,
                    instance = instance,
                    onPageClick = onPageClick,
                    onLinkLongClick = onLinkLongClick,
                    displayInstanceStyle = displayInstanceStyle,
                )


                fun showDefaultImage() {
                    image?.visibility = View.GONE
                    iconImage?.visibility = View.VISIBLE
                    iconImage?.setImageResource(R.drawable.baseline_article_24)
                }

                fun loadAndShowImage() {
                    val image = image ?: return

                    val thumbnailImageUrl = if (thumbnailUrl != null &&
                        ContentUtils.isUrlImage(thumbnailUrl)
                    ) {
                        thumbnailUrl
                    } else {
                        null
                    }

                    val imageUrl = thumbnailImageUrl ?: url

                    if (imageUrl == "default") {
                        showDefaultImage()
                        return
                    }

                    if (imageUrl.isNullOrBlank()) {
                        image.visibility = View.GONE
                        return
                    }

                    if (!ContentUtils.isUrlImage(imageUrl)) {
                        image.visibility = View.GONE
                        return
                    }

                    image.visibility = View.VISIBLE
                    iconImage?.visibility = View.GONE

                    image.load(R.drawable.thumbnail_placeholder_16_9) {
                        if (image is ShapeableImageView) {
                            allowHardware(false)
                        }
                    }

                    offlineManager.fetchImageWithError(itemView, imageUrl, {
                        offlineManager.calculateImageMaxSizeIfNeeded(it)
                        offlineManager.getMaxImageSizeHint(it, tempSize)

                        var w: Int? = null
                        var h: Int? = null
                        if (tempSize.height > 0 && tempSize.width > 0) {
                            val heightToWidthRatio = tempSize.height / tempSize.width

                            if (heightToWidthRatio > 10) {
                                // shrink the image if needed
                                w = tempSize.width
                                h = tempSize.height
                            }
                        }

                        image.load(it) {
                            if (image is ShapeableImageView) {
                                allowHardware(false)
                            }

                            if (w != null && h != null) {
                                this.size(w, h)
                            }
                            fallback(R.drawable.thumbnail_placeholder_16_9)
                            placeholder(R.drawable.thumbnail_placeholder_16_9)

                            if (!isRevealed && postView.post.nsfw) {
                                val sampling = (contentMaxWidth * 0.04f).coerceAtLeast(10f)

                                transformations(BlurTransformation(context, sampling = sampling))
                            }
                        }
                    }, {
                        image.visibility = View.GONE
                    },)
                    image.transitionName = "image_$absoluteAdapterPosition"
                    image.setOnClickListener {
                        if (fullContentContainerView != null) {
                            if (singleTapToViewImage) {
                                onImageClick(postView, image, imageUrl)
                            } else {
                                toggleItem(postView)
                            }
                        } else {
                            if (url != null && (postType == PostType.Text || postType == PostType.Link)) {
                                Utils.openExternalLink(context, url)
                            } else {
                                onImageClick(postView, image, imageUrl)
                            }
                        }
                    }

                    if (fullContentContainerView != null) {
                        image.setOnLongClickListener {
                            if (singleTapToViewImage) {
                                toggleItem(postView)
                            } else {
                                onImageClick(postView, image, imageUrl)
                            }
                            true
                        }
                    } else {
                        image.setOnLongClickListener {
                            onImageClick(postView, image, imageUrl)
                            true
                        }
                    }
                }

                linkTypeImage?.visibility = View.GONE
                iconImage?.visibility = View.GONE
                iconImage?.setOnClickListener(null)
                iconImage?.isClickable = false
                image?.setOnClickListener(null)
                image?.isClickable = false

                fun showFullContent() {
                    fullContentContainerView ?: return

                    lemmyContentHelper.setupFullContent(
                        reveal = isRevealed,
                        tempSize = tempSize,
                        videoViewMaxHeight = contentPreferredHeight,
                        contentMaxWidth = contentMaxWidth,
                        fullImageViewTransitionName = "full_image_$absoluteAdapterPosition",
                        postView = postView,
                        instance = instance,
                        rootView = itemView,
                        fullContentContainerView = fullContentContainerView,
                        contentMaxLines = contentMaxLines,
                        onFullImageViewClickListener = { v, url ->
                            onImageClick(postView, v, url)
                        },
                        onImageClickListener = {
                            onImageClick(postView, null, it)
                        },
                        onVideoClickListener = onVideoClick,
                        onItemClickListener = {
                            onItemClick()
                        },
                        onRevealContentClickedFn = {
                            onRevealContentClickedFn()
                        },
                        onLemmyUrlClick = onPageClick,
                        onLinkLongClick = onLinkLongClick,
                    )
                }

                when (postType) {
                    PostType.Image -> {
                        loadAndShowImage()

                        if (isExpanded) {
                            showFullContent()
                        }
                    }

                    PostType.Video -> {
                        loadAndShowImage()

                        iconImage?.visibility = View.VISIBLE
                        iconImage?.setImageResource(R.drawable.baseline_play_circle_filled_24)
                        iconImage?.setOnClickListener {
                            if (fullContentContainerView != null) {
                                toggleItem(postView)
                            } else {
                                onItemClick()
                            }
                        }

                        if (isExpanded) {
                            showFullContent()
                        }
                    }

                    PostType.Link,
                    PostType.Text,
                    -> {
                        if (thumbnailUrl == null) {
                            image?.visibility = View.GONE

                            // see if this text post has additional content
                            val hasAdditionalContent =
                                !postView.post.body.isNullOrBlank() ||
                                    !url.isNullOrBlank()

                            if (hasAdditionalContent) {
                                showDefaultImage()
                                iconImage?.setOnClickListener {
                                    if (fullContentContainerView != null) {
                                        toggleItem(postView)
                                    } else {
                                        onItemClick()
                                    }
                                }
                            }
                        } else {
                            loadAndShowImage()
                        }

                        if (isExpanded) {
                            showFullContent()
                        }

                        linkTypeImage?.visibility = View.GONE
                        linkTypeImage?.setImageResource(R.drawable.baseline_open_in_new_24)
                    }
                }

                image?.updateLayoutParams {
                    width = postImageWidth
                }
                iconImage?.updateLayoutParams {
                    width = postImageWidth
                }

                if (layoutShowsFullContent) {
                    showFullContent()
                }
            }

            LemmyTextHelper.bindText(
                title,
                postView.post.name,
                instance,
                onImageClick = {
                    onImageClick(postView, null, it)
                },
                onVideoClick = {
                    onVideoClick(it, VideoType.UNKNOWN, null)
                },
                onPageClick = onPageClick,
                onLinkLongClick = onLinkLongClick,
            )

            if (postView.read && !alwaysRenderAsUnread) {
                if (themeManager.isLightTheme) {
                    title.alpha = 0.41f
                } else {
                    title.alpha = 0.66f
                }
            } else {
                title.alpha = 1f
            }

            commentText?.text =
                LemmyUtils.abbrevNumber(postView.counts.comments.toLong())

            itemView.setOnClickListener {
                onItemClick()
            }
            commentButton?.setOnClickListener {
                onItemClick(
                    instance,
                    postView.post.id,
                    postView.community.toCommunityRef(),
                    postView,
                    true,
                    isRevealed,
                    null,
                )
            }
            commentButton?.isEnabled = !postView.post.locked

            val upvoteCount = upvoteCount
            if (upvoteCount != null) {
                voteUiHandler.bind(
                    viewLifecycleOwner,
                    instance,
                    postView,
                    upvoteButton,
                    downvoteButton,
                    upvoteCount,
                    upvoteCount,
                    downvoteCount,
                    {
                        if (rawBinding is ListingItemCompactBinding) {
                            if (showUpAndDownVotes) {
                                if (it > 0) {
                                    upvoteCount.setTextColor(upvoteColor)
                                    TextViewCompat.setCompoundDrawableTintList(
                                        upvoteCount,
                                        ColorStateList.valueOf(upvoteColor),
                                    )
                                    downvoteCount!!.setTextColor(unimportantTextColor)
                                    TextViewCompat.setCompoundDrawableTintList(
                                        downvoteCount!!,
                                        ColorStateList.valueOf(unimportantTextColor),
                                    )
                                } else if (it == 0) {
                                    upvoteCount.setTextColor(unimportantTextColor)
                                    TextViewCompat.setCompoundDrawableTintList(
                                        upvoteCount,
                                        ColorStateList.valueOf(unimportantTextColor),
                                    )
                                    downvoteCount!!.setTextColor(unimportantTextColor)
                                    TextViewCompat.setCompoundDrawableTintList(
                                        downvoteCount!!,
                                        ColorStateList.valueOf(unimportantTextColor),
                                    )
                                } else {
                                    upvoteCount.setTextColor(unimportantTextColor)
                                    TextViewCompat.setCompoundDrawableTintList(
                                        upvoteCount,
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
                                    upvoteCount.setTextColor(upvoteColor)
                                    TextViewCompat.setCompoundDrawableTintList(
                                        upvoteCount,
                                        ColorStateList.valueOf(upvoteColor),
                                    )
                                } else if (it == 0) {
                                    upvoteCount.setTextColor(unimportantTextColor)
                                    TextViewCompat.setCompoundDrawableTintList(
                                        upvoteCount,
                                        ColorStateList.valueOf(unimportantTextColor),
                                    )
                                } else {
                                    upvoteCount.setTextColor(downvoteColor)
                                    TextViewCompat.setCompoundDrawableTintList(
                                        upvoteCount,
                                        ColorStateList.valueOf(downvoteColor),
                                    )
                                }
                            }
                        }
                    },
                    onSignInRequired = onSignInRequired,
                    onInstanceMismatch,
                )
            }

            if (highlightForever) {
                highlightBg.visibility = View.VISIBLE
                highlightBg.alpha = 1f
            } else if (highlight) {
                highlightBg.visibility = View.VISIBLE
                highlightBg.animate()
                    .alpha(0f)
                    .apply {
                        duration = 350
                    }
                    .withEndAction {
                        highlightBg.visibility = View.GONE

                        onHighlightComplete()
                    }
            } else {
                highlightBg.visibility = View.GONE
            }

            moreButton?.setOnClickListener {
                onShowMoreOptions(postView)
            }

            if (rawBinding is ListingItemCardBinding ||
                rawBinding is ListingItemCard2Binding ||
                rawBinding is ListingItemCard3Binding ||
                rawBinding is ListingItemLargeListBinding
            ) {
                if (url == null) {
                    openLinkButton?.visibility = View.GONE
                    linkText?.visibility = View.GONE
                    linkIcon?.visibility = View.GONE
                    linkOverlay?.visibility = View.GONE
                } else {
                    if ((postType == PostType.Link || postType == PostType.Text) && thumbnailUrl != null) {
                        openLinkButton?.visibility = View.GONE
                        linkText?.visibility = View.VISIBLE
                        linkIcon?.visibility = View.VISIBLE
                        linkOverlay?.visibility = View.VISIBLE

                        val t = Uri.parse(url).host ?: url
                        linkText?.text = t
                        linkOverlay?.setOnClickListener {
                            Utils.openExternalLink(context, url)
                        }
                        linkOverlay?.setOnLongClickListener {
                            onLinkLongClick(url, t)
                            true
                        }
                    } else {
                        openLinkButton?.visibility = View.VISIBLE
                        linkText?.visibility = View.GONE
                        linkIcon?.visibility = View.GONE
                        linkOverlay?.visibility = View.GONE
                        openLinkButton?.setOnClickListener {
                            Utils.openExternalLink(context, url)
                        }
                    }
                }
            } else {
                openLinkButton?.visibility = View.GONE
            }
            when (val rb = rawBinding) {
                is ListingItemCompactBinding -> {
                    if (isActionsExpanded) {
                        upvoteButton?.visibility = View.VISIBLE
                        downvoteButton?.visibility = View.VISIBLE
                        createCommentButton?.visibility = View.VISIBLE
                        moreButton?.visibility = View.VISIBLE
                    } else {
                        upvoteButton?.visibility = View.GONE
                        downvoteButton?.visibility = View.GONE
                        createCommentButton?.visibility = View.GONE
                        moreButton?.visibility = View.GONE
                    }

                    rb.root.setOnLongClickListener {
                        toggleActions(postView)
                        true
                    }
                }
                else -> {
                    rb.root.setOnLongClickListener {
                        onShowMoreOptions(postView)
                        true
                    }
                }
            }
        }
    }

    private fun ListingItemViewHolder.scaleTextSizes() {
        headerContainer.textSize = postUiConfig.headerTextSizeSp.toTextSize()
        title.textSize = postUiConfig.titleTextSizeSp.toTextSize()
        commentText?.textSize = postUiConfig.footerTextSizeSp.toTextSize()
        upvoteCount?.textSize = postUiConfig.footerTextSizeSp.toTextSize()
        downvoteCount?.textSize = postUiConfig.footerTextSizeSp.toTextSize()
    }

    private fun Float.toTextSize(): Float =
        this * textSizeMultiplier * globalFontSizeMultiplier

    fun recycle(holder: ListingItemViewHolder) {
        if (holder.fullContentContainerView != null) {
            lemmyContentHelper.recycleFullContent(holder.fullContentContainerView)
        }
        offlineManager.cancelFetch(holder.itemView)
        holder.upvoteCount?.let {
            voteUiHandler.unbindVoteUi(it)
        }
    }

    private fun ListingItemViewHolder.ensureActionButtons(root: ViewGroup) {
        root.removeView(commentButton)
        root.removeView(upvoteButton)
        root.removeView(downvoteButton)
        root.removeView(upvoteCount)

        if (showUpAndDownVotes) {
            val commentButton = TextView(
                context,
            ).apply {
                id = View.generateViewId()
                layoutParams = ConstraintLayout.LayoutParams(
                    ConstraintLayout.LayoutParams.WRAP_CONTENT,
                    ConstraintLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    startToStart = ConstraintLayout.LayoutParams.PARENT_ID
                    topToBottom = R.id.bottomBarrier
                }
                setCompoundDrawablesRelativeWithIntrinsicBounds(
                    R.drawable.baseline_comment_18, 0, 0, 0)
                TextViewCompat.setCompoundDrawableTintList(
                    this, ColorStateList.valueOf(normalTextColor))
                compoundDrawablePadding = context.getDimen(R.dimen.padding_half)
                includeFontPadding = false
                setPadding(context.getDimen(R.dimen.padding))
                setBackgroundResource(selectableItemBackground)
            }.also {
                commentButton = it
                commentText = it
            }
            root.addView(commentButton)

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
        } else {
            val commentButton = TextView(
                context,
            ).apply {
                id = View.generateViewId()
                layoutParams = ConstraintLayout.LayoutParams(
                    ConstraintLayout.LayoutParams.WRAP_CONTENT,
                    ConstraintLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    startToStart = ConstraintLayout.LayoutParams.PARENT_ID
                    topToBottom = R.id.bottomBarrier
                }
                setCompoundDrawablesRelativeWithIntrinsicBounds(
                    R.drawable.baseline_comment_18, 0, 0, 0)
                TextViewCompat.setCompoundDrawableTintList(
                    this, ColorStateList.valueOf(normalTextColor))
                compoundDrawablePadding = context.getDimen(R.dimen.padding_half)
                includeFontPadding = false
                setPadding(context.getDimen(R.dimen.padding))
                setBackgroundResource(selectableItemBackground)
            }.also {
                commentButton = it
                commentText = it
            }
            root.addView(commentButton)

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

            upvoteButton = ImageView(
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
            }
            root.addView(upvoteButton)
        }
    }

}