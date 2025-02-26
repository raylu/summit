package com.idunnololz.summit.lemmy.person

import android.os.Bundle
import android.text.Spannable
import android.transition.TransitionManager
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.MarginLayoutParams
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.text.buildSpannedString
import androidx.core.view.ViewCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import coil3.load
import coil3.request.allowHardware
import com.google.android.material.tabs.TabLayoutMediator
import com.idunnololz.summit.R
import com.idunnololz.summit.account.AccountManager
import com.idunnololz.summit.account.asAccount
import com.idunnololz.summit.account.info.isPersonBlocked
import com.idunnololz.summit.account.loadProfileImageOrDefault
import com.idunnololz.summit.account.toPersonRef
import com.idunnololz.summit.accountUi.AccountsAndSettingsDialogFragment
import com.idunnololz.summit.accountUi.SignInNavigator
import com.idunnololz.summit.alert.OldAlertDialogFragment
import com.idunnololz.summit.api.utils.fullName
import com.idunnololz.summit.avatar.AvatarHelper
import com.idunnololz.summit.databinding.FragmentPersonBinding
import com.idunnololz.summit.lemmy.PersonRef
import com.idunnololz.summit.lemmy.appendSeparator
import com.idunnololz.summit.lemmy.comment.AddOrEditCommentFragment
import com.idunnololz.summit.lemmy.community.SlidingPaneController
import com.idunnololz.summit.lemmy.post.PostFragment
import com.idunnololz.summit.lemmy.post.PostFragmentDirections
import com.idunnololz.summit.lemmy.toPersonRef
import com.idunnololz.summit.lemmy.userTags.AddOrEditUserTagDialogFragment
import com.idunnololz.summit.lemmy.userTags.UserTagsManager
import com.idunnololz.summit.lemmy.utils.SortTypeMenuHelper
import com.idunnololz.summit.lemmy.utils.actions.MoreActionsHelper
import com.idunnololz.summit.lemmy.utils.actions.installOnActionResultHandler
import com.idunnololz.summit.lemmy.utils.setup
import com.idunnololz.summit.offline.OfflineManager
import com.idunnololz.summit.preferences.Preferences
import com.idunnololz.summit.spans.RoundedBackgroundSpan
import com.idunnololz.summit.util.BaseFragment
import com.idunnololz.summit.util.BottomMenu
import com.idunnololz.summit.util.PrettyPrintUtils.defaultDecimalFormat
import com.idunnololz.summit.util.StatefulData
import com.idunnololz.summit.util.Utils
import com.idunnololz.summit.util.ViewPagerAdapter
import com.idunnololz.summit.util.dateStringToTs
import com.idunnololz.summit.util.ext.attachWithAutoDetachUsingLifecycle
import com.idunnololz.summit.util.ext.getDimenFromAttribute
import com.idunnololz.summit.util.ext.navigateSafe
import com.idunnololz.summit.util.ext.showAllowingStateLoss
import com.idunnololz.summit.util.setupForFragment
import dagger.hilt.android.AndroidEntryPoint
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.launch

@AndroidEntryPoint
class PersonTabbedFragment : BaseFragment<FragmentPersonBinding>(), SignInNavigator {

    companion object {
        private const val TAG = "PersonTabbedFragment"
    }

    private val args by navArgs<PersonTabbedFragmentArgs>()

    @Inject
    lateinit var offlineManager: OfflineManager

    @Inject
    lateinit var preferences: Preferences

    @Inject
    lateinit var avatarHelper: AvatarHelper

    @Inject
    lateinit var accountManager: AccountManager

    @Inject
    lateinit var moreActionsHelper: MoreActionsHelper

    @Inject
    lateinit var userTagsManager: UserTagsManager

    val viewModel: PersonTabbedViewModel by viewModels()
    var slidingPaneController: SlidingPaneController? = null

    private var isAnimatingTitleIn: Boolean = false
    private var isAnimatingTitleOut: Boolean = false

    private val personRef
        get() = args.personRef
            ?: accountManager.currentAccount.asAccount?.toPersonRef()

    private var consumedArgs = false

    enum class Screen {
        Posts,
        Comments,
        About,
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        super.onCreateView(inflater, container, savedInstanceState)

        setBinding(FragmentPersonBinding.inflate(inflater, container, false))

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val context = requireContext()
        val mainActivity = requireMainActivity()

        requireMainActivity().apply {
            setSupportActionBar(binding.toolbar)
            supportActionBar?.setDisplayShowHomeEnabled(true)
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
            supportActionBar?.title = ""

            insetViewAutomaticallyByPaddingAndNavUi(
                viewLifecycleOwner,
                binding.coordinatorLayout,
                applyTopInset = false,
            )
            insets.observe(viewLifecycleOwner) {
                val newToolbarHeight =
                    context.getDimenFromAttribute(androidx.appcompat.R.attr.actionBarSize).toInt() +
                        it.topInset

                binding.bannerDummy.updateLayoutParams<MarginLayoutParams> {
                    topMargin = -it.topInset
                }

                binding.coordinatorLayout.updatePadding(top = it.topInset)
                binding.collapsingToolbarLayout.scrimVisibleHeightTrigger =
                    (newToolbarHeight + Utils.convertDpToPixel(16f)).toInt()
                binding.bannerGradient.updateLayoutParams<ViewGroup.LayoutParams> {
                    height = newToolbarHeight
                }
            }
        }

        onPersonChanged()

        with(binding) {
            binding.fab.hide()
            binding.tabLayoutContainer.visibility = View.GONE

            appBar.addOnOffsetChangedListener { appBar, offset ->
                val topInset = mainActivity.insets.value?.topInset ?: 0

                val fixedTotalRange = appBar.totalScrollRange - topInset

                val progress = min(1f, abs(offset.toFloat() / fixedTotalRange))
                val scrimEndProgress = 0.7f

                bannerContainer.alpha = max(0f, 1f - progress / scrimEndProgress)
            }

            if (args.personRef == null) {
                viewModel.currentAccountView.observe(viewLifecycleOwner) {
                    it.loadProfileImageOrDefault(binding.accountImageView)

                    onPersonChanged()
                }
                accountImageView.setOnClickListener {
                    AccountsAndSettingsDialogFragment.newInstance()
                        .showAllowingStateLoss(childFragmentManager, "AccountsDialogFragment")
                }
            } else {
                binding.accountImageView.visibility = View.GONE
            }
            viewModel.personData.observe(viewLifecycleOwner) {
                when (it) {
                    is StatefulData.Error -> {
                        binding.fab.hide()
                        loadingView.showDefaultErrorMessageFor(it.error)
                    }
                    is StatefulData.Loading -> {
                        binding.fab.hide()
                        loadingView.showProgressBar()
                    }
                    is StatefulData.NotStarted -> {}
                    is StatefulData.Success -> {
                        binding.fab.show()
                        loadingView.hideAll()

                        setup(personRef)

                        if (!consumedArgs && savedInstanceState == null) {
                            consumedArgs = true

                            when (args.screen as Screen) {
                                Screen.Posts -> {
                                    viewPager.currentItem = 0
                                }
                                Screen.Comments -> {
                                    viewPager.currentItem = 1
                                }
                                Screen.About -> {
                                    viewPager.currentItem = 2
                                }
                            }
                        }
                    }
                }
            }
            binding.fab.setup(preferences)

            slidingPaneController = SlidingPaneController(
                fragment = this@PersonTabbedFragment,
                slidingPaneLayout = binding.slidingPaneLayout,
                childFragmentManager = childFragmentManager,
                viewModel = viewModel,
                globalLayoutMode = preferences.globalLayoutMode,
                lockPanes = true,
                retainClosedPosts = preferences.retainLastPost,
                emptyScreenText = getString(R.string.select_a_post_or_comment),
                fragmentContainerId = R.id.post_fragment_container,
            ).apply {
                onPageSelectedListener = { isOpen ->
                }
                init()
            }

            binding.title.alpha = 0f
            isAnimatingTitleIn = false
            isAnimatingTitleOut = false

            binding.cakeDate.visibility = View.GONE

            binding.banner.transitionName = "banner_image"

            val actionBarHeight = context.getDimenFromAttribute(
                androidx.appcompat.R.attr.actionBarSize,
            )
            binding.appBar.addOnOffsetChangedListener { _, verticalOffset ->
                if (!isBindingAvailable()) {
                    return@addOnOffsetChangedListener
                }

                val percentCollapsed =
                    -verticalOffset / binding.collapsingToolbarLayout.height.toDouble()
                val absPixelsShowing = binding.collapsingToolbarLayout.height + verticalOffset

                if (absPixelsShowing <= actionBarHeight) {
                    if (!isAnimatingTitleIn) {
                        isAnimatingTitleIn = true
                        isAnimatingTitleOut = false
                        binding.title.animate().alpha(1f)
                    }
                } else if (percentCollapsed < 0.66) {
                    if (!isAnimatingTitleOut) {
                        isAnimatingTitleOut = true
                        isAnimatingTitleIn = false
                        binding.title.animate().alpha(0f)
                    }
                }
            }

            installOnActionResultHandler(moreActionsHelper, coordinatorLayout)
        }

        binding.fab.setOnClickListener {
            showOverflowMenu()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            userTagsManager.onChangedFlow.collect {
                onUserTagChanged()
            }
        }
    }

    private fun onPersonChanged() {
        val personRef = personRef
        if (personRef == null) {
            // this can happen if the user tapped on the profile page and is not signed in.
            binding.loadingView.showErrorText(
                getString(R.string.error_not_signed_in),
            )
            binding.profileIcon.visibility = View.GONE
            binding.collapsingToolbarContent.visibility = View.GONE
            binding.viewPager.visibility = View.GONE
            binding.tabLayoutContainer.visibility = View.GONE
            binding.fab.hide()

            viewModel.clearPersonData()
        } else {
            binding.loadingView.hideAll()
            binding.profileIcon.visibility = View.VISIBLE
            binding.collapsingToolbarContent.visibility = View.VISIBLE
            binding.viewPager.visibility = View.VISIBLE
            binding.tabLayoutContainer.visibility = View.VISIBLE
            binding.fab.show()

            viewModel.fetchPersonIfNotDone(personRef)
        }

        setup(personRef)
    }

    private fun showOverflowMenu() {
        if (!isBindingAvailable()) return

        val context = requireContext()
        val personData = viewModel.personData.valueOrNull ?: return
        val person = personData.personView.person
        val personRef = person.toPersonRef()

        val bottomMenu = BottomMenu(requireContext()).apply {
            addItemWithIcon(
                id = R.id.sort,
                title = getString(R.string.sort_by),
                icon = R.drawable.baseline_sort_24,
            )
            addDivider()

            addItemWithIcon(
                id = R.id.message,
                title = R.string.send_message,
                icon = R.drawable.baseline_message_24,
            )

            addItemWithIcon(
                id = R.id.tag_user,
                title = getString(R.string.tag_user_format, personRef.name),
                icon = R.drawable.outline_sell_24,
            )

            if (moreActionsHelper.fullAccount?.isPersonBlocked(personRef) == true) {
                addItemWithIcon(
                    id = R.id.unblock_user,
                    title = context.getString(R.string.unblock_this_user_format, personRef.name),
                    icon = R.drawable.baseline_person_24,
                )
            } else {
                addItemWithIcon(
                    id = R.id.block_user,
                    title = context.getString(R.string.block_this_user_format, personRef.name),
                    icon = R.drawable.baseline_person_off_24,
                )
            }

            setOnMenuItemClickListener {
                when (it.id) {
                    R.id.sort -> {
                        SortTypeMenuHelper(
                            requireContext(),
                            this@PersonTabbedFragment,
                            { viewModel.sortType },
                            { viewModel.sortType = it },
                        ).show()
                    }
                    R.id.block_user -> {
                        moreActionsHelper.blockPerson(id = person.id, block = true)
                    }
                    R.id.unblock_user -> {
                        moreActionsHelper.blockPerson(id = person.id, block = false)
                    }
                    R.id.message -> {
                        AddOrEditCommentFragment.showMessageDialog(
                            childFragmentManager,
                            viewModel.instance,
                            person.id,
                        )
                    }
                    R.id.tag_user -> {
                        AddOrEditUserTagDialogFragment.show(
                            fragmentManager = childFragmentManager,
                            person = person,
                        )
                    }
                }
            }
        }

        requireMainActivity().showBottomMenu(bottomMenu)
    }

    override fun onResume() {
        super.onResume()

        setupForFragment<PersonTabbedFragment>()

        if (binding.viewPager.currentItem == 0) {
            getMainActivity()?.setNavUiOpenPercent(0f)
        }
    }

    private fun setup(personRef: PersonRef?) {
        if (!isBindingAvailable()) return

        val data = viewModel.personData.valueOrNull ?: return
        val context = requireContext()

        Log.d(TAG, "user id: ${data.personView.person.id}")
        Log.d(TAG, "actor id: ${data.personView.person.actor_id}")

        TransitionManager.beginDelayedTransition(binding.collapsingToolbarContent)

        with(binding) {
            val displayName = data.personView.person.display_name
                ?: data.personView.person.name

            binding.title.text = displayName

            val bannerUrl = data.personView.person.banner
            if (bannerUrl != null) {
                profileIcon.updateLayoutParams<ConstraintLayout.LayoutParams> {
                    this.topToBottom = bannerDummy.id
                    this.topToTop = ConstraintLayout.LayoutParams.UNSET
                    this.topMargin = -Utils.convertDpToPixel(32f).toInt()
                }

                bannerDummy.setOnClickListener {
                    getMainActivity()?.openImage(
                        banner,
                        null,
                        personRef?.fullName,
                        bannerUrl,
                        null,
                    )
                }
                offlineManager.fetchImage(root, bannerUrl) {
                    banner.load(it) {
                        allowHardware(false)
                    }
                }
            } else {
                bannerDummy.setOnClickListener(null)
            }
            avatarHelper.loadAvatar(profileIcon, data.personView.person)
            if (data.personView.person.avatar.isNullOrBlank()) {
                profileIcon.setOnClickListener {
                    OldAlertDialogFragment.Builder()
                        .setMessage(R.string.error_user_has_no_profile_image)
                        .createAndShow(childFragmentManager, "error_user_has_no_profile_image")
                }
            } else {
                ViewCompat.setTransitionName(profileIcon, "profileIcon")

                profileIcon.setOnClickListener {
                    getMainActivity()?.openImage(
                        profileIcon,
                        toolbar,
                        data.personView.person.fullName,
                        data.personView.person.avatar,
                        null,
                    )
                }
            }
            name.text = displayName
            subtitle2.text = buildSpannedString {
                append(
                    context.resources.getQuantityString(
                        R.plurals.posts_format,
                        data.personView.counts.post_count,
                        defaultDecimalFormat.format(data.personView.counts.post_count),
                    ),
                )

                appendSeparator()

                append(
                    context.resources.getQuantityString(
                        R.plurals.comments_format,
                        data.personView.counts.comment_count,
                        defaultDecimalFormat.format(data.personView.counts.comment_count),
                    ),
                )
            }

            val dateTime = LocalDateTime.ofEpochSecond(
                dateStringToTs(data.personView.person.published) / 1000,
                0,
                ZoneOffset.UTC,
            )
            val dateStr = dateTime.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG))
            cakeDate.text = getString(R.string.cake_day_on_format, dateStr)
            binding.cakeDate.visibility = View.VISIBLE

            if (viewPager.adapter == null) {
                viewPager.offscreenPageLimit = 5
                val adapter =
                    ViewPagerAdapter(context, childFragmentManager, viewLifecycleOwner.lifecycle)
                adapter.addFrag(PersonPostsFragment::class.java, getString(R.string.posts))
                adapter.addFrag(PersonCommentsFragment::class.java, getString(R.string.comments))
                adapter.addFrag(PersonAboutFragment::class.java, getString(R.string.about))
                viewPager.adapter = adapter
            }

            tabLayoutContainer.visibility = View.VISIBLE
            TabLayoutMediator(
                tabLayout,
                binding.viewPager,
                binding.viewPager.adapter as ViewPagerAdapter,
            ).attachWithAutoDetachUsingLifecycle(viewLifecycleOwner)
        }
        onUserTagChanged()
    }

    private fun onUserTagChanged() {
        val data = viewModel.personData.valueOrNull ?: return

        binding.subtitle.text = buildSpannedString {
            append(data.personView.person.fullName)

            val tag = userTagsManager.getUserTag(data.personView.person.fullName)
            if (tag != null) {
                append(" ")
                val s = length
                append(tag.tagName)
                val e = length

                setSpan(
                    RoundedBackgroundSpan(tag.fillColor, tag.borderColor),
                    s,
                    e,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            }
        }
    }

    fun closePost(postFragment: PostFragment) {
        slidingPaneController?.closePost(postFragment)
    }

    override fun navigateToSignInScreen() {
        val direction = PostFragmentDirections.actionGlobalLogin()
        findNavController().navigateSafe(direction)
    }

    override fun proceedAnyways(tag: Int) {
    }
}
