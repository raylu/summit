package com.idunnololz.summit.lemmy.person

import android.os.Bundle
import android.transition.TransitionManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.text.buildSpannedString
import androidx.core.view.ViewCompat
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import coil.load
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayoutMediator
import com.idunnololz.summit.R
import com.idunnololz.summit.account.AccountImageGenerator
import com.idunnololz.summit.account.AccountManager
import com.idunnololz.summit.account.asAccount
import com.idunnololz.summit.account.loadProfileImageOrDefault
import com.idunnololz.summit.account.toPersonRef
import com.idunnololz.summit.accountUi.AccountsAndSettingsDialogFragment
import com.idunnololz.summit.accountUi.SignInNavigator
import com.idunnololz.summit.alert.AlertDialogFragment
import com.idunnololz.summit.api.utils.fullName
import com.idunnololz.summit.databinding.FragmentPersonBinding
import com.idunnololz.summit.lemmy.MoreActionsViewModel
import com.idunnololz.summit.lemmy.PersonRef
import com.idunnololz.summit.lemmy.appendSeparator
import com.idunnololz.summit.lemmy.comment.AddOrEditCommentFragment
import com.idunnololz.summit.lemmy.comment.AddOrEditCommentFragmentArgs
import com.idunnololz.summit.lemmy.community.SlidingPaneController
import com.idunnololz.summit.lemmy.post.PostFragment
import com.idunnololz.summit.lemmy.post.PostFragmentDirections
import com.idunnololz.summit.lemmy.utils.SortTypeMenuHelper
import com.idunnololz.summit.lemmy.utils.installOnActionResultHandler
import com.idunnololz.summit.lemmy.utils.setup
import com.idunnololz.summit.offline.OfflineManager
import com.idunnololz.summit.preferences.Preferences
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
import com.idunnololz.summit.util.toErrorMessage
import dagger.hilt.android.AndroidEntryPoint
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.inject.Inject

@AndroidEntryPoint
class PersonTabbedFragment : BaseFragment<FragmentPersonBinding>(), SignInNavigator {

    private val args by navArgs<PersonTabbedFragmentArgs>()

    @Inject
    lateinit var offlineManager: OfflineManager

    @Inject
    lateinit var preferences: Preferences

    @Inject
    lateinit var accountImageGenerator: AccountImageGenerator

    @Inject
    lateinit var accountManager: AccountManager

    val viewModel: PersonTabbedViewModel by viewModels()
    var slidingPaneController: SlidingPaneController? = null
    val actionsViewModel: MoreActionsViewModel by viewModels()

    private var isAnimatingTitleIn: Boolean = false
    private var isAnimatingTitleOut: Boolean = false

    private val personRef
        get() = args.personRef
            ?: accountManager.currentAccount.asAccount?.toPersonRef()

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

        requireMainActivity().apply {
            setupForFragment<PersonTabbedFragment>()

            setSupportActionBar(binding.toolbar)
            supportActionBar?.setDisplayShowHomeEnabled(true)
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
            supportActionBar?.title = ""

            insetViewAutomaticallyByPaddingAndNavUi(viewLifecycleOwner, binding.coordinatorLayout)
        }

        onPersonChanged()

        with(binding) {
            binding.fab.hide()
            binding.tabLayoutContainer.visibility = View.GONE

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
                    }
                }
            }
            binding.fab.setup(preferences)

            actionsViewModel.blockPersonResult.observe(viewLifecycleOwner) {
                when (it) {
                    is StatefulData.Error -> {
                        Snackbar.make(
                            binding.coordinatorLayout,
                            it.error.toErrorMessage(context),
                            Snackbar.LENGTH_LONG,
                        ).show()
                    }
                    is StatefulData.Loading -> {}
                    is StatefulData.NotStarted -> {}
                    is StatefulData.Success -> {
                        Snackbar.make(
                            binding.coordinatorLayout,
                            if (it.data.blockedPerson) {
                                getString(R.string.user_blocked)
                            } else {
                                getString(R.string.user_unblocked)
                            },
                            Snackbar.LENGTH_LONG,
                        ).show()
                    }
                }
            }

            slidingPaneController = SlidingPaneController(
                fragment = this@PersonTabbedFragment,
                slidingPaneLayout = binding.slidingPaneLayout,
                childFragmentManager = childFragmentManager,
                viewModel = viewModel,
                globalLayoutMode = preferences.globalLayoutMode,
                lockPanes = true,
                retainClosedPosts = preferences.retainLastPost,
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

            val actionBarHeight = context.getDimenFromAttribute(androidx.appcompat.R.attr.actionBarSize)
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

            installOnActionResultHandler(actionsViewModel, coordinatorLayout)
        }

        binding.fab.setOnClickListener {
            showOverflowMenu()
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

        val personData = viewModel.personData.valueOrNull ?: return
        val person = personData.personView.person

        val bottomMenu = BottomMenu(requireContext()).apply {
            addItemWithIcon(
                id = R.id.sort,
                title = getString(R.string.sort_by),
                icon = R.drawable.baseline_sort_24,
            )
            addDivider()
            addItemWithIcon(
                id = R.id.ca_block_user,
                title = getString(R.string.block_this_user_format, person.name),
                icon = R.drawable.baseline_person_off_24,
            )
            addItemWithIcon(
                id = R.id.unblock_user,
                title = getString(R.string.unblock_this_user_format, person.name),
                icon = R.drawable.baseline_person_24,
            )
            addItemWithIcon(
                id = R.id.message,
                title = getString(R.string.send_message),
                icon = R.drawable.baseline_message_24,
            )

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
                    R.id.ca_block_user -> {
                        actionsViewModel.blockPerson(person.id)
                    }
                    R.id.unblock_user -> {
                        actionsViewModel.blockPerson(person.id, false)
                    }
                    R.id.message -> {
                        AddOrEditCommentFragment()
                            .apply {
                                arguments = AddOrEditCommentFragmentArgs(
                                    instance = viewModel.instance,
                                    null,
                                    null,
                                    null,
                                    null,
                                    person.id,
                                ).toBundle()
                            }
                            .showAllowingStateLoss(childFragmentManager, "CreateOrEditPostFragment")
                    }
                }
            }
        }

        requireMainActivity().showBottomMenu(bottomMenu)
    }

    override fun onResume() {
        super.onResume()

        if (binding.viewPager.currentItem == 0) {
            getMainActivity()?.setNavUiOpenPercent(0f)
        }
    }

    private fun setup(personRef: PersonRef?) {
        if (!isBindingAvailable()) return

        val data = viewModel.personData.valueOrNull ?: return
        val context = requireContext()

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

                banner.setOnClickListener {
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
            }
            if (data.personView.person.avatar == null) {
                profileIcon.setImageDrawable(
                    accountImageGenerator.generateDrawableForPerson(data.personView.person),
                )

                profileIcon.setOnClickListener {
                    AlertDialogFragment.Builder()
                        .setMessage(R.string.error_user_has_no_profile_image)
                        .createAndShow(childFragmentManager, "error_user_has_no_profile_image")
                }
            } else {
                profileIcon.load(data.personView.person.avatar) {
                    fallback(R.drawable.thumbnail_placeholder_square)
                    allowHardware(false)
                }
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
            subtitle.text = data.personView.person.fullName
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
