package com.idunnololz.summit.lemmy.postListView

import androidx.fragment.app.FragmentManager
import com.idunnololz.summit.R
import com.idunnololz.summit.api.dto.PostView
import com.idunnololz.summit.api.utils.instance
import com.idunnololz.summit.lemmy.MoreActionsViewModel
import com.idunnololz.summit.lemmy.comment.PreviewCommentDialogFragment
import com.idunnololz.summit.lemmy.comment.PreviewCommentDialogFragmentArgs
import com.idunnololz.summit.lemmy.createOrEditPost.CreateOrEditPostFragment
import com.idunnololz.summit.lemmy.createOrEditPost.CreateOrEditPostFragmentArgs
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
) {
    if (!isBindingAvailable()) {
        return
    }

    val context = requireContext()

    val bottomMenu = BottomMenu(context).apply {
        setTitle(R.string.more_actions)

        if (postView.post.creator_id == actionsViewModel.accountManager.currentAccount.value?.id) {
            addItemWithIcon(R.id.edit_post, R.string.edit_post, R.drawable.baseline_edit_24)
            addItemWithIcon(R.id.delete, R.string.delete_post, R.drawable.baseline_delete_24)
        }

        addItemWithIcon(
            R.id.hide_post,
            getString(R.string.hide_post),
            R.drawable.baseline_hide_24,
        )

        if (postView.saved) {
            addItemWithIcon(
                R.id.remove_from_saved,
                getString(R.string.remove_from_saved),
                R.drawable.baseline_bookmark_remove_24,
            )
        } else {
            addItemWithIcon(
                R.id.save,
                getString(R.string.save),
                R.drawable.baseline_bookmark_add_24,
            )
        }

        addItemWithIcon(
            R.id.share,
            getString(R.string.share),
            R.drawable.baseline_share_24,
        )

        addItemWithIcon(
            R.id.share_fediverse_link,
            getString(R.string.share_source_link),
            R.drawable.ic_fediverse_24,
        )

        addItemWithIcon(
            R.id.block_community,
            getString(R.string.block_this_community_format, postView.community.name),
            R.drawable.baseline_block_24,
        )
        addItemWithIcon(
            R.id.block_user,
            getString(R.string.block_this_user_format, postView.creator.name),
            R.drawable.baseline_person_off_24,
        )
        addItemWithIcon(R.id.view_source, R.string.view_source, R.drawable.baseline_code_24)

        setOnMenuItemClickListener {
            when (it.id) {
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
                    actionsViewModel.deletePost(postView.post)
                }
                R.id.hide_post -> {
                    actionsViewModel.hidePost(postView.post.id)
                }
                R.id.save -> {
                    actionsViewModel.savePost(postView.post.id, save = true)
                }
                R.id.remove_from_saved -> {
                    actionsViewModel.savePost(postView.post.id, save = false)
                }
                R.id.block_community -> {
                    actionsViewModel.blockCommunity(postView.community.id)
                }
                R.id.block_user -> {
                    actionsViewModel.blockPerson(postView.creator.id)
                }
                R.id.share -> {
                    Utils.shareLink(
                        context = context,
                        link = LinkUtils.getLinkForPost(instance, postView.post.id),
                    )
                }
                R.id.share_fediverse_link -> {
                    Utils.shareLink(
                        context = context,
                        link = postView.post.ap_id,
                    )
                }
                R.id.view_source -> {
                    PreviewCommentDialogFragment()
                        .apply {
                            arguments = PreviewCommentDialogFragmentArgs(
                                "",
                                buildString {
                                    appendLine("Title:")
                                    appendLine(postView.post.name)
                                    appendLine("")
                                    appendLine("Body:")
                                    appendLine(postView.post.body ?: "")
                                },
                                true,
                            ).toBundle()
                        }
                        .showAllowingStateLoss(fragmentManager, "PreviewCommentDialogFragment")
                }
            }
        }
    }

    getMainActivity()?.showBottomMenu(bottomMenu)
}
