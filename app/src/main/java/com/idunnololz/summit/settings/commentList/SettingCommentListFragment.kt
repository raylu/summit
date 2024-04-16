package com.idunnololz.summit.settings.commentList

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.IdRes
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.idunnololz.summit.R
import com.idunnololz.summit.databinding.FragmentSettingsCommentListBinding
import com.idunnololz.summit.filterLists.ContentTypes
import com.idunnololz.summit.filterLists.FilterTypes
import com.idunnololz.summit.lemmy.idToCommentsSortOrder
import com.idunnololz.summit.lemmy.toApiSortOrder
import com.idunnololz.summit.lemmy.toId
import com.idunnololz.summit.preferences.Preferences
import com.idunnololz.summit.settings.CommentListSettings
import com.idunnololz.summit.settings.SettingPath.getPageName
import com.idunnololz.summit.settings.SettingsFragment
import com.idunnololz.summit.settings.dialogs.MultipleChoiceDialogFragment
import com.idunnololz.summit.settings.dialogs.SettingValueUpdateCallback
import com.idunnololz.summit.settings.util.bindTo
import com.idunnololz.summit.settings.util.isEnabled
import com.idunnololz.summit.util.BaseFragment
import com.idunnololz.summit.util.ext.navigateSafe
import com.idunnololz.summit.util.ext.showAllowingStateLoss
import com.idunnololz.summit.util.insetViewExceptBottomAutomaticallyByMargins
import com.idunnololz.summit.util.insetViewExceptTopAutomaticallyByPadding
import com.idunnololz.summit.util.setupForFragment
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class SettingCommentListFragment :
    BaseFragment<FragmentSettingsCommentListBinding>(),
    SettingValueUpdateCallback {

    private val args: SettingCommentListFragmentArgs by navArgs()

    @Inject
    lateinit var preferences: Preferences

    @Inject
    lateinit var settings: CommentListSettings

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        super.onCreateView(inflater, container, savedInstanceState)

        setBinding(FragmentSettingsCommentListBinding.inflate(inflater, container, false))

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val context = requireContext()

        requireMainActivity().apply {
            setupForFragment<SettingsFragment>()
            insetViewExceptTopAutomaticallyByPadding(viewLifecycleOwner, binding.scrollView)
            insetViewExceptBottomAutomaticallyByMargins(viewLifecycleOwner, binding.toolbar)

            setSupportActionBar(binding.toolbar)

            supportActionBar?.setDisplayShowHomeEnabled(true)
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
            supportActionBar?.title = settings.getPageName(context)
        }

        updateRendering()
    }

    private fun updateRendering() {
        settings.defaultCommentsSortOrder.bindTo(
            binding.defaultCommentsSortOrder,
            {
                preferences.defaultCommentsSortOrder?.toApiSortOrder()?.toId()
                    ?: R.id.comments_sort_order_default
            },
            { setting, currentValue ->
                MultipleChoiceDialogFragment.newInstance(setting, currentValue)
                    .showAllowingStateLoss(childFragmentManager, "aaaaaaa")
            },
        )

        settings.relayStyleNavigation.bindTo(
            binding.relayStyleNavigation,
            { preferences.commentsNavigationFab },
            { preferences.commentsNavigationFab = it },
        )
        settings.useVolumeButtonNavigation.bindTo(
            binding.useVolumeButtonNavigation,
            { preferences.useVolumeButtonNavigation },
            { preferences.useVolumeButtonNavigation = it },
        )
        settings.collapseChildCommentsByDefault.bindTo(
            binding.collapseChildCommentsByDefault,
            { preferences.collapseChildCommentsByDefault },
            { preferences.collapseChildCommentsByDefault = it },
        )
        settings.hideCommentScores.bindTo(
            binding.hideCommentScores,
            { preferences.hideCommentScores },
            {
                preferences.hideCommentScores = it
            },
        )
        settings.autoCollapseCommentThreshold.bindTo(
            binding.autoCollapseComments,
            { convertAutoCollapseCommentToOptionId(preferences.autoCollapseCommentThreshold) },
            { setting, currentValue ->
                MultipleChoiceDialogFragment.newInstance(setting, currentValue)
                    .showAllowingStateLoss(childFragmentManager, "aaaaaaa")
            },
        )
        settings.showCommentUpvotePercentage.bindTo(
            binding.showCommentUpvotePercentage,
            { preferences.showCommentUpvotePercentage },
            {
                preferences.showCommentUpvotePercentage = it
            },
        )
        settings.showInlineMediaAsLinks.bindTo(
            binding.showInlineMediaAsLinks,
            { preferences.commentsShowInlineMediaAsLinks },
            {
                preferences.commentsShowInlineMediaAsLinks = it
            },
        )

        binding.commentHeader.title.text = getString(R.string.comment_header)
        settings.showProfileIcons.bindTo(
            binding.showProfileIcons,
            { preferences.showProfileIcons },
            {
                preferences.showProfileIcons = it
                updateRendering()
            },
        )
        settings.commentHeaderLayout.bindTo(
            binding.commentHeaderLayout,
            { preferences.commentHeaderLayout },
            { setting, currentValue ->
                MultipleChoiceDialogFragment.newInstance(setting, currentValue)
                    .showAllowingStateLoss(childFragmentManager, "commentHeaderLayout")
            },
        )
        settings.customizeCommentQuickActions.bindTo(
            binding.customizeCommentQuickActions,
        ) {
            val directions = SettingCommentListFragmentDirections
                .actionSettingCommentListFragmentToCustomQuickActionsFragment()
            findNavController().navigateSafe(directions)
        }

        // Comment header layout only takes effect is not showing profile icons.
        binding.commentHeaderLayout.isEnabled = !preferences.showProfileIcons

        if (args.account != null) {
            binding.keywordFilters.root.visibility = View.GONE
            binding.instanceFilters.root.visibility = View.GONE
            binding.userFilters.root.visibility = View.GONE
        } else {
            binding.keywordFilters.root.visibility = View.VISIBLE
            binding.instanceFilters.root.visibility = View.VISIBLE
            binding.userFilters.root.visibility = View.VISIBLE
        }

        settings.keywordFilters.bindTo(binding.keywordFilters) {
            val direction = SettingCommentListFragmentDirections
                .actionSettingCommentListFragmentToSettingsFilterListFragment(
                    ContentTypes.CommentListType,
                    FilterTypes.KeywordFilter,
                    getString(R.string.keyword_filters),
                )
            findNavController().navigateSafe(direction)
        }
        settings.instanceFilters.bindTo(binding.instanceFilters) {
            val direction = SettingCommentListFragmentDirections
                .actionSettingCommentListFragmentToSettingsFilterListFragment(
                    ContentTypes.CommentListType,
                    FilterTypes.InstanceFilter,
                    getString(R.string.instance_filters),
                )
            findNavController().navigateSafe(direction)
        }
        settings.userFilters.bindTo(binding.userFilters) {
            val direction = SettingCommentListFragmentDirections
                .actionSettingCommentListFragmentToSettingsFilterListFragment(
                    ContentTypes.CommentListType,
                    FilterTypes.UserFilter,
                    getString(R.string.user_filters),
                )
            findNavController().navigateSafe(direction)
        }
    }

    private fun convertAutoCollapseCommentToOptionId(value: Float) = when {
        value >= 0.499f -> {
            R.id.auto_collapse_comment_threshold_50
        }
        value >= 0.399f -> {
            R.id.auto_collapse_comment_threshold_40
        }
        value >= 0.299f -> {
            R.id.auto_collapse_comment_threshold_30
        }
        value >= 0.199f -> {
            R.id.auto_collapse_comment_threshold_20
        }
        value >= 0f -> {
            R.id.auto_collapse_comment_threshold_10
        }
        else -> {
            R.id.auto_collapse_comment_threshold_never_collapse
        }
    }

    private fun convertOptionIdToAutoCollapseCommentThreshold(@IdRes id: Int) = when (id) {
        R.id.auto_collapse_comment_threshold_50 -> 0.5f
        R.id.auto_collapse_comment_threshold_40 -> 0.4f
        R.id.auto_collapse_comment_threshold_30 -> 0.3f
        R.id.auto_collapse_comment_threshold_20 -> 0.2f
        R.id.auto_collapse_comment_threshold_10 -> 0.1f
        R.id.auto_collapse_comment_threshold_never_collapse -> -1f
        else -> null
    }

    override fun updateValue(key: Int, value: Any?) {
        when (key) {
            settings.defaultCommentsSortOrder.id -> {
                preferences.defaultCommentsSortOrder = idToCommentsSortOrder(value as Int)
            }
            settings.autoCollapseCommentThreshold.id -> {
                val threshold = convertOptionIdToAutoCollapseCommentThreshold(value as Int)

                if (threshold != null) {
                    preferences.autoCollapseCommentThreshold = threshold
                }
            }
            settings.commentHeaderLayout.id -> {
                preferences.commentHeaderLayout = value as Int
            }
        }

        updateRendering()
    }
}
