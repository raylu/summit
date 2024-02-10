package com.idunnololz.summit.lemmy.postListView

import androidx.fragment.app.FragmentManager
import arrow.core.Either
import com.idunnololz.summit.R
import com.idunnololz.summit.account.asAccount
import com.idunnololz.summit.api.dto.PostView
import com.idunnololz.summit.api.utils.instance
import com.idunnololz.summit.lemmy.MoreActionsViewModel
import com.idunnololz.summit.lemmy.PostRef
import com.idunnololz.summit.lemmy.comment.AddOrEditCommentFragment
import com.idunnololz.summit.lemmy.comment.PreviewCommentDialogFragment
import com.idunnololz.summit.lemmy.comment.PreviewCommentDialogFragmentArgs
import com.idunnololz.summit.lemmy.contentDetails.ContentDetailsDialogFragment
import com.idunnololz.summit.lemmy.createOrEditPost.CreateOrEditPostFragment
import com.idunnololz.summit.lemmy.createOrEditPost.CreateOrEditPostFragmentArgs
import com.idunnololz.summit.lemmy.fastAccountSwitcher.FastAccountSwitcherDialogFragment
import com.idunnololz.summit.lemmy.mod.ModActionsDialogFragment
import com.idunnololz.summit.lemmy.report.ReportContentDialogFragment
import com.idunnololz.summit.lemmy.toCommunityRef
import com.idunnololz.summit.util.BaseFragment
import com.idunnololz.summit.util.BottomMenu
import com.idunnololz.summit.util.LinkUtils
import com.idunnololz.summit.util.Utils
import com.idunnololz.summit.util.ext.showAllowingStateLoss

fun BaseFragment<*>.showMorePostOptions(
    instance: String,
    postView: PostView,
    actionsViewModel: MoreActionsViewModel,
    fragmentManager: FragmentManager,
    isPostMenu: Boolean = false,
    onSortOrderClick: () -> Unit = {},
    onRefreshClick: () -> Unit = {},
    onFindInPageClick: () -> Unit = {},
    onScreenshotClick: (() -> Unit)? = null,
) {
    if (!isBindingAvailable()) {
        return
    }

    val context = requireContext()

    val bottomMenu = BottomMenu(context).apply {
        setTitle(R.string.more_actions)

        addItemWithIcon(
            R.id.action_add_comment,
            getString(R.string.add_comment),
            R.drawable.baseline_add_comment_24,
        )

        if (isPostMenu) {
            addDivider()
            addItemWithIcon(
                R.id.sort_comments_by,
                R.string.sort_comments_by,
                R.drawable.baseline_sort_24,
            )
            addItemWithIcon(
                R.id.refresh,
                R.string.refresh,
                R.drawable.baseline_refresh_24,
            )
            addItemWithIcon(
                R.id.find_in_page,
                R.string.find_in_page,
                R.drawable.outline_find_in_page_24,
            )
            addDivider()
        }

        if (postView.post.creator_id == actionsViewModel.accountManager.currentAccount.asAccount?.id) {
            addItemWithIcon(R.id.edit_post, R.string.edit_post, R.drawable.baseline_edit_24)
            addItemWithIcon(R.id.delete, R.string.delete_post, R.drawable.baseline_delete_24)
        }

        val fullAccount = actionsViewModel.accountInfoManager.currentFullAccount.value
        val miscAccountInfo = fullAccount
            ?.accountInfo
            ?.miscAccountInfo

        if (instance == fullAccount?.account?.instance &&
            miscAccountInfo?.isAdmin == true
        ) {
            addDivider()

            addItemWithIcon(
                id = R.id.ca_admin_tools,
                title = R.string.admin_tools,
                icon = R.drawable.outline_shield_24,
            )

            addDivider()
        } else if (miscAccountInfo
            ?.modCommunityIds
            ?.contains(postView.community.id) == true
        ) {
            addDivider()

            addItemWithIcon(
                id = R.id.ca_mod_tools,
                title = R.string.mod_tools,
                icon = R.drawable.outline_shield_24,
            )

            addDivider()
        }

        addItemWithIcon(
            R.id.hide_post,
            getString(R.string.hide_post),
            R.drawable.baseline_hide_24,
        )

        if (postView.saved) {
            addItemWithIcon(
                R.id.ca_remove_from_saved,
                getString(R.string.remove_from_saved),
                R.drawable.baseline_bookmark_remove_24,
            )
        } else {
            addItemWithIcon(
                R.id.ca_save,
                getString(R.string.save),
                R.drawable.baseline_bookmark_add_24,
            )
        }

        addItemWithIcon(
            R.id.ca_share,
            getString(R.string.share),
            R.drawable.baseline_share_24,
        )

        if (onScreenshotClick != null) {
            addItemWithIcon(
                R.id.ca_screenshot,
                getString(R.string.take_screenshot),
                R.drawable.baseline_screenshot_24,
            )
        }

        addItemWithIcon(
            R.id.cross_post,
            getString(R.string.cross_post),
            R.drawable.baseline_content_copy_24,
        )

        addItemWithIcon(
            R.id.ca_share_fediverse_link,
            getString(R.string.share_source_link),
            R.drawable.ic_fediverse_24,
        )

        addItemWithIcon(
            R.id.community_info,
            getString(R.string.community_info),
            R.drawable.ic_community_24,
        )

        addDivider()
        addItemWithIcon(
            R.id.report_post,
            getString(R.string.report_post),
            R.drawable.baseline_outlined_flag_24,
        )
        addItemWithIcon(
            R.id.ca_block_user,
            getString(R.string.block_this_user_format, postView.creator.name),
            R.drawable.baseline_person_off_24,
        )
        addItemWithIcon(
            R.id.block_community,
            getString(R.string.block_this_community_format, postView.community.name),
            R.drawable.baseline_block_24,
        )
        addItemWithIcon(
            R.id.block_instance,
            getString(R.string.block_this_instance_format, postView.instance),
            R.drawable.baseline_public_off_24,
        )
        addDivider()
//        addItemWithIcon(
//            R.id.switch_account_temp,
//            R.string.switch_account_for_post,
//            R.drawable.baseline_account_circle_24)
        addItemWithIcon(R.id.ca_view_source, R.string.view_source, R.drawable.baseline_code_24)
        addItemWithIcon(R.id.ca_detailed_view, R.string.detailed_view, R.drawable.baseline_open_in_full_24)

        setOnMenuItemClickListener {
            when (it.id) {
                R.id.action_add_comment -> {
                    AddOrEditCommentFragment.showReplyDialog(
                        instance = instance,
                        postOrCommentView = Either.Left(postView),
                        fragmentManager = childFragmentManager,
                    )
                }
                R.id.edit_post -> {
                    CreateOrEditPostFragment()
                        .apply {
                            arguments = CreateOrEditPostFragmentArgs(
                                instance = actionsViewModel.apiInstance,
                                post = postView.post,
                                communityName = null,
                            ).toBundle()
                        }
                        .showAllowingStateLoss(childFragmentManager, "CreateOrEditPostFragment")
                }
                R.id.delete -> {
                    actionsViewModel.deletePost(postView.post.id)
                }
                R.id.hide_post -> {
                    actionsViewModel.hidePost(postView.post.id)
                }
                R.id.ca_save -> {
                    actionsViewModel.savePost(postView.post.id, save = true)
                }
                R.id.ca_remove_from_saved -> {
                    actionsViewModel.savePost(postView.post.id, save = false)
                }
                R.id.community_info -> {
                    getMainActivity()?.showCommunityInfo(postView.community.toCommunityRef())
                }
                R.id.block_community -> {
                    actionsViewModel.blockCommunity(postView.community.id)
                }
                R.id.ca_block_user -> {
                    actionsViewModel.blockPerson(postView.creator.id)
                }
                R.id.block_instance -> {
                    actionsViewModel.blockInstance(postView.community.instance_id)
                }
                R.id.ca_share -> {
                    Utils.shareLink(
                        context = context,
                        link = LinkUtils.getLinkForPost(instance, postView.post.id),
                    )
                }
                R.id.cross_post -> {
                    CreateOrEditPostFragment()
                        .apply {
                            arguments = CreateOrEditPostFragmentArgs(
                                instance = actionsViewModel.apiInstance,
                                post = null,
                                communityName = null,
                                crosspost = postView.post,
                            ).toBundle()
                        }
                        .showAllowingStateLoss(childFragmentManager, "CreateOrEditPostFragment")
                }
                R.id.ca_share_fediverse_link -> {
                    Utils.shareLink(
                        context = context,
                        link = postView.post.ap_id,
                    )
                }
                R.id.ca_view_source -> {
                    PreviewCommentDialogFragment()
                        .apply {
                            arguments = PreviewCommentDialogFragmentArgs(
                                "",
                                buildString {
                                    appendLine("Title:")
                                    appendLine(postView.post.name)
                                    appendLine()
                                    appendLine("Body:")
                                    appendLine(postView.post.body ?: "")
                                    appendLine()
                                    appendLine("Url:")
                                    appendLine(postView.post.url ?: "")
                                },
                                true,
                            ).toBundle()
                        }
                        .showAllowingStateLoss(fragmentManager, "PreviewCommentDialogFragment")
                }
                R.id.ca_detailed_view -> {
                    ContentDetailsDialogFragment
                        .show(childFragmentManager, instance, postView)
                }
                R.id.ca_admin_tools -> {
                    ModActionsDialogFragment.show(postView, childFragmentManager)
                }
                R.id.ca_mod_tools -> {
                    ModActionsDialogFragment.show(postView, childFragmentManager)
                }
                R.id.report_post -> {
                    ReportContentDialogFragment.show(
                        childFragmentManager,
                        PostRef(
                            instance,
                            postView.post.id,
                        ),
                        null,
                    )
                }
                R.id.switch_account_temp -> {
                    FastAccountSwitcherDialogFragment.show(childFragmentManager)
                }
                R.id.sort_comments_by -> {
                    onSortOrderClick()
                }
                R.id.refresh -> {
                    onRefreshClick()
                }
                R.id.find_in_page -> {
                    onFindInPageClick()
                }
                R.id.ca_screenshot -> {
                    onScreenshotClick?.invoke()
                }
            }
        }
    }

    getMainActivity()?.showBottomMenu(bottomMenu, expandFully = false)
}
