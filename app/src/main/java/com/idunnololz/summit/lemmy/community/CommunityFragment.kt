package com.idunnololz.summit.lemmy.community

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.MarginLayoutParams
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.core.view.doOnPreDraw
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.viewModels
import androidx.navigation.findNavController
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.idunnololz.summit.R
import com.idunnololz.summit.account_ui.AccountsAndSettingsDialogFragment
import com.idunnololz.summit.api.dto.PostView
import com.idunnololz.summit.account_ui.PreAuthDialogFragment
import com.idunnololz.summit.account_ui.SignInNavigator
import com.idunnololz.summit.alert.AlertDialogFragment
import com.idunnololz.summit.api.utils.getUniqueKey
import com.idunnololz.summit.databinding.FragmentCommunityBinding
import com.idunnololz.summit.databinding.ListingItemCardBinding
import com.idunnololz.summit.databinding.ListingItemCompactBinding
import com.idunnololz.summit.databinding.ListingItemFullBinding
import com.idunnololz.summit.databinding.ListingItemListBinding
import com.idunnololz.summit.databinding.MainFooterItemBinding
import com.idunnololz.summit.history.HistoryManager
import com.idunnololz.summit.history.HistorySaveReason
import com.idunnololz.summit.lemmy.CommunityRef
import com.idunnololz.summit.lemmy.CommunitySortOrder
import com.idunnololz.summit.lemmy.CommunityViewState
import com.idunnololz.summit.lemmy.MoreActionsViewModel
import com.idunnololz.summit.lemmy.PageRef
import com.idunnololz.summit.lemmy.PostRef
import com.idunnololz.summit.lemmy.createOrEditPost.CreateOrEditPostFragment
import com.idunnololz.summit.lemmy.createOrEditPost.CreateOrEditPostFragmentArgs
import com.idunnololz.summit.lemmy.getShortDesc
import com.idunnololz.summit.lemmy.post.PostFragment
import com.idunnololz.summit.lemmy.postListView.ListingItemViewHolder
import com.idunnololz.summit.lemmy.postListView.PostListViewBuilder
import com.idunnololz.summit.main.MainActivity
import com.idunnololz.summit.main.MainFragment
import com.idunnololz.summit.offline.OfflineManager
import com.idunnololz.summit.preferences.Preferences
import com.idunnololz.summit.preview.VideoType
import com.idunnololz.summit.user.UserCommunitiesManager
import com.idunnololz.summit.util.BaseFragment
import com.idunnololz.summit.util.BottomMenu
import com.idunnololz.summit.util.CustomDividerItemDecoration
import com.idunnololz.summit.util.SharedElementTransition
import com.idunnololz.summit.util.StatefulData
import com.idunnololz.summit.util.Utils
import com.idunnololz.summit.util.VerticalSpaceItemDecoration
import com.idunnololz.summit.util.ext.forceShowIcons
import com.idunnololz.summit.util.ext.getDimen
import com.idunnololz.summit.util.ext.navigateSafe
import com.idunnololz.summit.util.ext.showAllowingStateLoss
import com.idunnololz.summit.util.getParcelableCompat
import com.idunnololz.summit.util.recyclerView.ViewBindingViewHolder
import com.idunnololz.summit.util.recyclerView.getBinding
import com.idunnololz.summit.video.VideoState
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class CommunityFragment : BaseFragment<FragmentCommunityBinding>(), SignInNavigator,
    AlertDialogFragment.AlertDialogFragmentListener {

    companion object {
        private const val TAG = "CommunityFragment"
    }

    private val args: CommunityFragmentArgs by navArgs()

    private val viewModel: CommunityViewModel by viewModels()
    val actionsViewModel: MoreActionsViewModel by viewModels()

    private var adapter: ListingItemAdapter? = null

    private var shouldScrollToTopAfterFresh = false

    @Inject
    lateinit var historyManager: HistoryManager

    @Inject
    lateinit var offlineManager: OfflineManager

    @Inject
    lateinit var userCommunitiesManager: UserCommunitiesManager

    @Inject
    lateinit var postListViewBuilder: PostListViewBuilder

    private var viewPagerController: ViewPagerController? = null

    private var isCustomAppBarExpanded = false

    private val onBackPressedHandler = object : OnBackPressedCallback(true /* enabled by default */) {
        override fun handleOnBackPressed() {
            if (viewModel.currentPageIndex.value != 0) {
                viewModel.fetchPrevPage()
            } else {
                isEnabled = false
                requireActivity().onBackPressed()
            }
        }
    }

    private val _sortByMenu: BottomMenu by lazy {
        BottomMenu(requireContext()).apply {
            addItem(R.id.sort_order_active, R.string.sort_order_active)
            addItem(R.id.sort_order_hot, R.string.sort_order_hot)
            addItem(R.id.sort_order_top, R.string.sort_order_top, R.drawable.baseline_chevron_right_24)
            addItem(R.id.sort_order_new, R.string.sort_order_new)
            addItem(R.id.sort_order_old, R.string.sort_order_old)
            addItem(R.id.sort_order_most_comments, R.string.sort_order_most_comments)
            addItem(R.id.sort_order_new_comments, R.string.sort_order_new_comments)
            setTitle(R.string.sort_by)

            setOnMenuItemClickListener { menuItem ->
                when(menuItem.id) {
                    R.id.sort_order_active ->
                        viewModel.setSortOrder(CommunitySortOrder.Active)
                    R.id.sort_order_hot ->
                        viewModel.setSortOrder(CommunitySortOrder.Hot)
                    R.id.sort_order_top ->
                        getMainActivity()?.showBottomMenu(getSortByTopMenu())
                    R.id.sort_order_new ->
                        viewModel.setSortOrder(CommunitySortOrder.New)
                    R.id.sort_order_old ->
                        viewModel.setSortOrder(CommunitySortOrder.Old)
                    R.id.sort_order_most_comments ->
                        viewModel.setSortOrder(CommunitySortOrder.MostComments)
                    R.id.sort_order_new_comments ->
                        viewModel.setSortOrder(CommunitySortOrder.NewComments)
                }
            }
        }
    }

    private val _sortByTopMenu: BottomMenu by lazy {
        BottomMenu(requireContext()).apply {
            addItem(R.id.sort_order_top_day, R.string.time_frame_today)
            addItem(R.id.sort_order_top_week, R.string.time_frame_this_week)
            addItem(R.id.sort_order_top_month, R.string.time_frame_this_month)
            addItem(R.id.sort_order_top_year, R.string.time_frame_this_year)
            addItem(R.id.sort_order_top_all_time, R.string.time_frame_all_time)
            setTitle(R.string.sort_by_top)

            setOnMenuItemClickListener { menuItem ->
                when(menuItem.id) {
                    R.id.sort_order_top_day ->
                        viewModel.setSortOrder(
                            CommunitySortOrder.TopOrder(CommunitySortOrder.TimeFrame.Today))
                    R.id.sort_order_top_week ->
                        viewModel.setSortOrder(
                            CommunitySortOrder.TopOrder(CommunitySortOrder.TimeFrame.ThisWeek))
                    R.id.sort_order_top_month ->
                        viewModel.setSortOrder(
                            CommunitySortOrder.TopOrder(CommunitySortOrder.TimeFrame.ThisMonth))
                    R.id.sort_order_top_year ->
                        viewModel.setSortOrder(
                            CommunitySortOrder.TopOrder(CommunitySortOrder.TimeFrame.ThisYear))
                    R.id.sort_order_top_all_time ->
                        viewModel.setSortOrder(
                            CommunitySortOrder.TopOrder(CommunitySortOrder.TimeFrame.AllTime))
                }
            }
        }
    }

    private val _layoutSelectorMenu: BottomMenu by lazy {
        BottomMenu(requireContext()).apply {
            addItemWithIcon(R.id.layout_list, R.string.list, R.drawable.baseline_list_24)
            addItemWithIcon(R.id.layout_compact, R.string.compact, R.drawable.baseline_list_24)
            addItemWithIcon(R.id.layout_card, R.string.card, R.drawable.baseline_article_24)
            addItemWithIcon(R.id.layout_full, R.string.full, R.drawable.baseline_view_day_24)
            setTitle(R.string.layout)

            setOnMenuItemClickListener { menuItem ->
                when(menuItem.id) {
                    R.id.layout_compact ->
                        preferences.setPostsLayout(CommunityLayout.Compact)
                    R.id.layout_list ->
                        preferences.setPostsLayout(CommunityLayout.List)
                    R.id.layout_card ->
                        preferences.setPostsLayout(CommunityLayout.Card)
                    R.id.layout_full ->
                        preferences.setPostsLayout(CommunityLayout.Full)
                }
                onSelectedLayoutChanged()
            }
        }
    }

    @Inject
    lateinit var preferences: Preferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState == null) {
            requireMainActivity().apply {
                setupForFragment<CommunityFragment>()
            }
        }

        val context = requireContext()
        if (adapter == null) {
            adapter = ListingItemAdapter(
                postListViewBuilder = postListViewBuilder,
                context = context,
                onNextClick = {
                    viewModel.fetchNextPage(clearPagePosition = true)
                },
                onPrevClick = {
                    viewModel.fetchPrevPage()
                },
                onSignInRequired = {
                    PreAuthDialogFragment.newInstance()
                        .show(childFragmentManager, "asdf")
                },
                onInstanceMismatch = { accountInstance, apiInstance ->
                    AlertDialogFragment.Builder()
                        .setTitle(R.string.error_account_instance_mismatch_title)
                        .setMessage(
                            getString(R.string.error_account_instance_mismatch,
                                accountInstance,
                                apiInstance)
                        )
                        .setNegativeButton(R.string.go_to_account_instance)
                        .createAndShow(childFragmentManager, "onInstanceMismatch")
                },
                onImageClick = { url ->
                    getMainActivity()?.openImage(null, url, null)
                },
                onVideoClick = { url, videoType, state ->
                    getMainActivity()?.openVideo(url, videoType, state)
                },
                onPageClick = {
                    getMainActivity()?.launchPage(it)
                },
                onItemClick = { instance, id, currentCommunity, post, jumpToComments, reveal, videoState ->
//                    val action = CommunityFragmentDirections.actionMainFragmentToPostFragment(

//                    )
//                    findNavController().navigateSafe(action)

                    viewPagerController?.openPost(
                        instance = instance,
                        id =  id,
                        reveal = reveal,
                        post = post,
                        jumpToComments = jumpToComments,
                        currentCommunity = currentCommunity,
                        videoState = videoState,
                    )
                },
                onShowMoreActions = {
                    showMoreOptionsFor(it)
                }
            )
            onSelectedLayoutChanged()
        }

        viewModel.changeCommunity(args.communityRef)

        if (savedInstanceState != null) {
            restoreState(CommunityViewState.restoreFromBundle(savedInstanceState), reload = false)
        }

        sharedElementEnterTransition = SharedElementTransition()
        sharedElementReturnTransition = SharedElementTransition()

        requireActivity().supportFragmentManager.setFragmentResultListener(
            CreateOrEditPostFragment.REQUEST_KEY,
            this
        ) { _, bundle ->
            val result = bundle.getParcelableCompat<PostView>(
                CreateOrEditPostFragment.REQUEST_KEY_RESULT)

            if (result != null) {
                viewModel.fetchCurrentPage(force = true)
                viewPagerController?.openPost(
                    instance = viewModel.instance,
                    id =  result.post.id,
                    reveal = false,
                    post = result,
                    jumpToComments = false,
                    currentCommunity = args.communityRef,
                    videoState = null,
                )
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        postponeEnterTransition()

        setBinding(FragmentCommunityBinding.inflate(inflater, container, false))

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.doOnPreDraw {
            adapter?.contentMaxWidth = binding.recyclerView.width
        }

        runOnReady {
            onReady()
        }

        binding.fab.setOnClickListener a@{
            val currentCommunity = viewModel.currentCommunityRef.value
            var communityName: String? = null
            var communityId: Int = -1
            when (currentCommunity) {
                is CommunityRef.All -> return@a
                is CommunityRef.CommunityRefByName -> {
                    communityName = currentCommunity.name
                }
                is CommunityRef.CommunityRefByObj -> {
                    communityId = currentCommunity.community.id
                }
                is CommunityRef.Local -> return@a
                is CommunityRef.Subscribed -> return@a
                null -> return@a
            }

            CreateOrEditPostFragment()
                .apply {
                    arguments = CreateOrEditPostFragmentArgs(
                        instance = viewModel.instance,
                        communityName = communityName,
                        communityId = communityId,
                    ).toBundle()
                }
                .showAllowingStateLoss(childFragmentManager, "CreateOrEditPostFragment")
        }

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, onBackPressedHandler)

        viewPagerController = ViewPagerController(
            this,
            binding.viewPager,
            childFragmentManager,
            viewModel,
        ) {
            if (it == 0) {
                val lastSelectedPost = viewModel.lastSelectedPost
                if (lastSelectedPost != null) {
                    // We came from a post...
                    adapter?.highlightPost(lastSelectedPost)
                    viewModel.lastSelectedPost = null
                }
            } else {
                val lastSelectedPost = viewModel.lastSelectedPost
                if (lastSelectedPost != null) {
                    adapter?.highlightPostForever(lastSelectedPost)
                }
            }
        }.apply {
            init()
        }
    }

    fun closePost(postFragment: PostFragment) {
        viewPagerController?.closePost(postFragment)
    }

    fun isPristineFirstPage(): Boolean {
        if (!isBindingAvailable()) {
            return false
        }

        val position = (binding.recyclerView.layoutManager as? LinearLayoutManager)
            ?.findFirstCompletelyVisibleItemPosition()

        return position == 0 && viewModel.currentPageIndex.value == 0
    }

    fun onPostUpdated() {
        viewModel.fetchCurrentPage(force = true)
    }

    private fun onReady() {
        val view = binding.root
        checkNotNull(view.findNavController())

        val mainView = binding.rootView
        val mainActivity = requireMainActivity()
        mainActivity.apply {
            headerOffset.observe(viewLifecycleOwner) {
                if (it != null)
                    mainView.translationY = it.toFloat()

                isCustomAppBarExpanded = it == getCustomAppBarHeight()

                updateFabState()
            }

            insetsChangedLiveData.observe(viewLifecycleOwner) {
                binding.fab.updateLayoutParams<MarginLayoutParams> {
                    this.bottomMargin = getBottomNavHeight() + getDimen(R.dimen.padding)
                }
            }
        }

        mainActivity.insetViewExceptTopAutomaticallyByPaddingAndNavUi(
            viewLifecycleOwner, binding.recyclerView)

        val context = requireContext()

        (parentFragment?.parentFragment as? MainFragment)?.updateCommunityInfoPane(
            requireNotNull(viewModel.currentCommunityRef.value)
        )

        binding.swipeRefreshLayout.setOnRefreshListener {
            shouldScrollToTopAfterFresh = true
            viewModel.fetchCurrentPage(true)
            binding.recyclerView.scrollToPosition(0)
        }
        binding.loadingView.setOnRefreshClickListener {
            viewModel.fetchCurrentPage(true)
        }

        binding.recyclerView.adapter = adapter
        binding.recyclerView.setHasFixedSize(true)
        binding.recyclerView.layoutManager = LinearLayoutManager(context)

        updateDecorator(binding.recyclerView)

        binding.fastScroller.setRecyclerView(binding.recyclerView)

        binding.recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)

                val adapter = adapter ?: return
                val pageIndex = adapter.pageIndex ?: return
                val layoutManager = (recyclerView.layoutManager as LinearLayoutManager)
                val firstPos = layoutManager.findLastVisibleItemPosition()
                val lastPos = layoutManager.findLastVisibleItemPosition()
                if (firstPos != 0 && lastPos == adapter.itemCount - 1) {
                    // firstPos != 0 - ensures that the page is scrollable even
                    viewModel.setPagePositionAtBottom(pageIndex)
                } else {
                    val firstView = layoutManager.findViewByPosition(firstPos)
                    viewModel.setPagePosition(pageIndex, firstPos, firstView?.top ?: 0)
                }
            }
        })

        setupMainActivityButtons()
        scheduleStartPostponedTransition(binding.rootView)

        viewModel.loadedPostsData.observe(viewLifecycleOwner) a@{
            when (it) {
                is StatefulData.Error -> {
                    binding.swipeRefreshLayout.isRefreshing = false
                    binding.recyclerView.visibility = View.GONE
                    binding.loadingView.showDefaultErrorMessageFor(it.error)
                }
                is StatefulData.Loading -> {
                    binding.loadingView.showProgressBar()
                }
                is StatefulData.NotStarted -> {}
                is StatefulData.Success -> {
                    val adapter = adapter ?: return@a

                    adapter.setItems(it.data.pageIndex, it.data)

                    adapter.curDataSource = it.data.instance

                    if (shouldScrollToTopAfterFresh) {
                        shouldScrollToTopAfterFresh = false
                        binding.recyclerView.scrollToPosition(0)
                    } else {
                        val pagePosition = viewModel.getPagePosition(it.data.pageIndex)
                        if (pagePosition.isAtBottom) {
                            (binding.recyclerView.layoutManager as LinearLayoutManager)
                                .scrollToPositionWithOffset(adapter.itemCount - 1, 0)
                        } else if (pagePosition.itemIndex != 0 || pagePosition.offset != 0) {
                            (binding.recyclerView.layoutManager as LinearLayoutManager)
                                .scrollToPositionWithOffset(
                                    pagePosition.itemIndex,
                                    pagePosition.offset
                                )
                        } else {
                            binding.recyclerView.scrollToPosition(0)
                            getMainActivity()?.showCustomAppBar()
                        }
                    }

                    binding.swipeRefreshLayout.isRefreshing = false
                    binding.recyclerView.visibility = View.VISIBLE
                    binding.loadingView.hideAll()
                    
                    if (it.data.posts.isEmpty()) {
                        binding.loadingView.showErrorText(R.string.no_posts)
                    }
                }
            }
        }

        actionsViewModel.blockCommunityResult.observe(viewLifecycleOwner) {
            if (it is StatefulData.Success) {
                actionsViewModel.blockCommunityResult.setIdle()
                viewModel.onBlockSettingsChanged()
            }
        }
        actionsViewModel.blockPersonResult.observe(viewLifecycleOwner) {
            if (it is StatefulData.Success) {
                actionsViewModel.blockPersonResult.setIdle()
                viewModel.onBlockSettingsChanged()
            }
        }

        if (adapter?.getItems().isNullOrEmpty()) {
            viewModel.fetchCurrentPage()
        }

        // try to restore state...
        viewPagerController?.onPageSelected()
    }

    override fun onResume() {
        super.onResume()

        runOnReady {
            val customAppBarController = requireMainActivity().getCustomAppBarController()

            viewModel.currentCommunityRef.observe(viewLifecycleOwner) {
                val currentDefaultPage = preferences.getDefaultPage()
                customAppBarController.setCommunity(it, it == currentDefaultPage)

                updateFabState()
            }
            viewModel.currentPageIndex.observe(viewLifecycleOwner) { currentPageIndex ->
                Log.d(TAG, "Current page: $currentPageIndex")
                customAppBarController.setPageIndex(currentPageIndex) { pageIndex ->
                    viewModel.fetchPage(pageIndex)
                }

                onBackPressedHandler.isEnabled = currentPageIndex != 0
            }

            onSelectedLayoutChanged()
        }
    }

    private fun updateFabState() {
        val communityRef = viewModel.currentCommunityRef.value

        if (communityRef is CommunityRef.CommunityRefByName || communityRef is CommunityRef.CommunityRefByObj) {
            if (isCustomAppBarExpanded) {
                binding.fab.show()
            } else {
                binding.fab.hide()
            }
        } else {
            binding.fab.hide()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        viewModel.createState()?.writeToBundle(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onPause() {
        val context = requireContext()
        val viewState = viewModel.createState()
        if (viewState != null) {
            historyManager.recordCommunityState(
                tabId = 0,
                saveReason = HistorySaveReason.LEAVE_SCREEN,
                state = viewState,
                shortDesc = viewState.getShortDesc(context)
            )
        }

        super.onPause()
    }

    override fun onDestroyView() {
        Log.d(TAG, "onDestroyView()")
        super.onDestroyView()
    }

    private fun restoreState(state: CommunityViewState?, reload: Boolean) {
        viewModel.restoreFromState(state ?: return)
        if (reload)
            viewModel.fetchCurrentPage()
    }

    private fun getSortByMenu(): BottomMenu {
        when (viewModel.getCurrentSortOrder()) {
            CommunitySortOrder.Active -> _sortByMenu.setChecked(R.id.sort_order_active)
            CommunitySortOrder.Hot -> _sortByMenu.setChecked(R.id.sort_order_hot)
            CommunitySortOrder.New -> _sortByMenu.setChecked(R.id.sort_order_new)
            is CommunitySortOrder.TopOrder -> _sortByMenu.setChecked(R.id.sort_order_top)
            CommunitySortOrder.MostComments -> _sortByMenu.setChecked(R.id.sort_order_most_comments)
            CommunitySortOrder.NewComments -> _sortByMenu.setChecked(R.id.sort_order_new_comments)
            CommunitySortOrder.Old -> _sortByMenu.setChecked(R.id.sort_order_old)
        }

        return _sortByMenu
    }
    private fun getSortByTopMenu(): BottomMenu {
        when (val order = viewModel.getCurrentSortOrder()) {
            is CommunitySortOrder.TopOrder -> {
                when (order.timeFrame) {
                    CommunitySortOrder.TimeFrame.Today ->
                        _sortByTopMenu.setChecked(R.id.sort_order_top_day)
                    CommunitySortOrder.TimeFrame.ThisWeek ->
                        _sortByTopMenu.setChecked(R.id.sort_order_top_week)
                    CommunitySortOrder.TimeFrame.ThisMonth ->
                        _sortByTopMenu.setChecked(R.id.sort_order_top_month)
                    CommunitySortOrder.TimeFrame.ThisYear ->
                        _sortByTopMenu.setChecked(R.id.sort_order_top_year)
                    CommunitySortOrder.TimeFrame.AllTime ->
                        _sortByTopMenu.setChecked(R.id.sort_order_top_all_time)
                }
            }
            else -> {}
        }

        return _sortByTopMenu
    }
    private fun getLayoutMenu(): BottomMenu {
        when (preferences.getPostsLayout()) {
            CommunityLayout.Compact ->
                _layoutSelectorMenu.setChecked(R.id.layout_compact)
            CommunityLayout.List ->
                _layoutSelectorMenu.setChecked(R.id.layout_list)
            CommunityLayout.Card ->
                _layoutSelectorMenu.setChecked(R.id.layout_card)
            CommunityLayout.Full ->
                _layoutSelectorMenu.setChecked(R.id.layout_full)
        }

        return _layoutSelectorMenu
    }

    private fun setupMainActivityButtons() {
        (activity as? MainActivity)?.apply {
            getCustomAppBarController().setup(
                communitySelectedListener = { controller, communityRef ->
                    val action = CommunityFragmentDirections.actionSubredditFragmentSwitchSubreddit(
                        communityRef = communityRef
                    )
                    findNavController().navigate(action)
                    Utils.hideKeyboard(activity)
                    controller.hide()
                },
                abOverflowClickListener = {
                    showOverflowMenu(it)
                },
                onAccountClick = {
                    AccountsAndSettingsDialogFragment.newInstance()
                        .showAllowingStateLoss(childFragmentManager, "AccountsDialogFragment")
                })
        }
    }

    private fun showOverflowMenu(view: View) {
        val context = context ?: return

        PopupMenu(context, view).apply {
            inflate(R.menu.menu_fragment_main)
            forceShowIcons()

            val currentCommunityRef = requireNotNull(viewModel.currentCommunityRef.value)
            val currentDefaultPage = preferences.getDefaultPage()
            val isBookmarked = userCommunitiesManager.isCommunityBookmarked(currentCommunityRef)
            val isCurrentPageDefault = currentCommunityRef == currentDefaultPage

            if (isCurrentPageDefault) {
                menu.findItem(R.id.set_as_default).isVisible = false
                menu.findItem(R.id.toggle_bookmark).isVisible = false
            } else {
                menu.findItem(R.id.set_as_default).isVisible = true
                menu.findItem(R.id.toggle_bookmark).isVisible = true

                menu.findItem(R.id.toggle_bookmark).apply {
                    if (isBookmarked) {
                        this.setTitle(R.string.remove_bookmark)
                        this.setIcon(R.drawable.baseline_bookmark_remove_24)
                    } else {
                        this.setTitle(R.string.bookmark_community)
                        this.setIcon(R.drawable.baseline_bookmark_add_24)
                    }
                }
            }

            setOnMenuItemClickListener {
                when (it.itemId) {
                    R.id.share -> {
                        val sendIntent: Intent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(
                                Intent.EXTRA_TEXT,
                                viewModel.getSharedLinkForCurrentPage()
                            )
                            type = "text/plain"
                        }

                        val shareIntent = Intent.createChooser(sendIntent, null)
                        startActivity(shareIntent)
                        true
                    }

                    R.id.sort -> {
                        getMainActivity()?.showBottomMenu(getSortByMenu())
                        true
                    }

                    R.id.set_as_default -> {
                        viewModel.setDefaultPage(currentCommunityRef)

                        Snackbar.make(
                            requireMainActivity().getSnackbarContainer(),
                            R.string.home_page_set,
                            Snackbar.LENGTH_LONG
                        ).show()
                        true
                    }
                    R.id.layout ->  {
                        getMainActivity()?.showBottomMenu(getLayoutMenu())
                        true
                    }

                    R.id.community_info -> {
                        (parentFragment?.parentFragment as? MainFragment)?.expandEndPane()
                        true
                    }

                    R.id.toggle_bookmark -> {
                        if (isBookmarked) {
                            userCommunitiesManager.removeCommunity(currentCommunityRef)
                        } else {
                            userCommunitiesManager.addUserCommunity(
                                currentCommunityRef,
                                viewModel.loadedPostsData.valueOrNull
                                    ?.posts?.firstOrNull()?.community?.icon)
                        }

                        true
                    }

                    else -> false
                }
            }
        }.show()
    }

    override fun navigateToSignInScreen() {
        val direction = CommunityFragmentDirections.actionCommunityFragmentToLogin()
        findNavController().navigateSafe(direction)
    }

    override fun onPositiveClick(dialog: AlertDialogFragment, tag: String?) {
    }

    override fun onNegativeClick(dialog: AlertDialogFragment, tag: String?) {
        if (tag == "onInstanceMismatch") {
            dialog.dismiss()
            viewModel.resetToAccountInstance()
        }
    }

    private fun onSelectedLayoutChanged() {
        val newPostUiConfig = preferences.getPostInListUiConfig()
        val didUiConfigChange = postListViewBuilder.postUiConfig != newPostUiConfig
        val didLayoutChange = adapter?.layout != preferences.getPostsLayout()

        if (didLayoutChange) {
            if (isBindingAvailable()) {
                updateDecorator(binding.recyclerView)
            }
        }

        if (didUiConfigChange) {
            postListViewBuilder.postUiConfig = newPostUiConfig
        }

        if (didLayoutChange || didUiConfigChange) {
            adapter?.layout = preferences.getPostsLayout()
        }
    }

    private fun updateDecorator(recyclerView: RecyclerView) {
        while (binding.recyclerView.itemDecorationCount != 0) {
            binding.recyclerView.removeItemDecorationAt(
                binding.recyclerView.itemDecorationCount - 1)
        }
        if (preferences.getPostsLayout().usesDividers()) {
            binding.recyclerView.addItemDecoration(
                CustomDividerItemDecoration(
                    recyclerView.context,
                    DividerItemDecoration.VERTICAL
                ).apply {
                    setDrawable(
                        checkNotNull(
                            ContextCompat.getDrawable(
                                context,
                                R.drawable.vertical_divider
                            )
                        )
                    )
                }
            )
        } else {
            binding.recyclerView.addItemDecoration(
                VerticalSpaceItemDecoration(
                    recyclerView.context.getDimen(R.dimen.padding_half),
                    false
                )
            )
        }
    }

    @SuppressLint("ResourceType")
    private fun showMoreOptionsFor(postView: PostView) {
        if (!isBindingAvailable()) {
            return
        }

        val context = requireContext()

        val bottomMenu = BottomMenu(context).apply {
            setTitle(R.string.more_actions)
            addItemWithIcon(
                R.id.block_community,
                getString(R.string.block_this_community_format, postView.community.name),
                R.drawable.baseline_block_24
            )
            addItemWithIcon(
                R.id.block_user,
                getString(R.string.block_this_user, postView.creator.name),
                R.drawable.baseline_person_off_24
            )

            setOnMenuItemClickListener {
                when (it.id) {
                    R.id.block_community -> {
                        actionsViewModel.blockCommunity(postView.community.id)
                    }
                    R.id.block_user -> {
                        actionsViewModel.blockPerson(postView.creator.id)
                    }
                }
            }
        }

        getMainActivity()?.showBottomMenu(bottomMenu)
    }

    private sealed class Item {
        data class PostItem(
            val postView: PostView,
            val instance: String,
        ) : Item()

        class FooterItem(val hasMore: Boolean) : Item()
    }

    private inner class ListingItemAdapter(
        private val postListViewBuilder: PostListViewBuilder,
        private val context: Context,
        private val onNextClick: () -> Unit,
        private val onPrevClick: () -> Unit,
        private val onSignInRequired: () -> Unit,
        private val onInstanceMismatch: (String, String) -> Unit,
        private val onImageClick: (String) -> Unit,
        private val onVideoClick: (String, VideoType, VideoState?) -> Unit,
        private val onPageClick: (PageRef) -> Unit,
        private val onItemClick: (
            instance: String,
            id: Int,
            currentCommunity: CommunityRef?,
            post: PostView,
            jumpToComments: Boolean,
            reveal: Boolean,
            videoState: VideoState?
        ) -> Unit,
        private val onShowMoreActions: (PostView) -> Unit,
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        var pageIndex: Int? = null
        private var rawData: CommunityViewModel.LoadedPostsData? = null
        private val inflater = LayoutInflater.from(context)

        private var items: List<Item> = listOf()

        private var expandedItems = mutableSetOf<String>()
        private var actionsExpandedItems = mutableSetOf<String>()

        /**
         * Set of items that is hidden by default but is reveals (ie. nsfw or spoiler tagged)
         */
        private var revealedItems = mutableSetOf<String>()

        var layout: CommunityLayout = CommunityLayout.List
            set(value) {
                field = value

                notifyDataSetChanged()
            }

        var curDataSource: String? = null

        var contentMaxWidth: Int = 0

        private var postToHighlightForever: PostRef? = null
        private var postToHighlight: PostRef? = null

        override fun getItemViewType(position: Int): Int = when (items[position]) {
            is Item.PostItem -> when (layout) {
                CommunityLayout.Compact -> R.layout.listing_item_compact
                CommunityLayout.List -> R.layout.listing_item_list
                CommunityLayout.Card -> R.layout.listing_item_card
                CommunityLayout.Full -> R.layout.listing_item_full
            }
            is Item.FooterItem -> R.layout.main_footer_item
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val v = inflater.inflate(viewType, parent, false)
            return when (viewType) {
                R.layout.listing_item_compact ->
                    ListingItemViewHolder.fromBinding(ListingItemCompactBinding.bind(v))
                R.layout.listing_item_list ->
                    ListingItemViewHolder.fromBinding(ListingItemListBinding.bind(v))
                R.layout.listing_item_card ->
                    ListingItemViewHolder.fromBinding(ListingItemCardBinding.bind(v))
                R.layout.listing_item_full ->
                    ListingItemViewHolder.fromBinding(ListingItemFullBinding.bind(v))
                R.layout.main_footer_item -> ViewBindingViewHolder(MainFooterItemBinding.bind(v))
                else -> throw RuntimeException("Unknown view type: $viewType")
            }
        }

        override fun onBindViewHolder(
            holder: RecyclerView.ViewHolder,
            position: Int,
            payloads: MutableList<Any>
        ) {
            when (val item = items[position]) {
                is Item.PostItem -> {
                    if (payloads.isEmpty()) {
                        super.onBindViewHolder(holder, position, payloads)
                    } else {
                        val h: ListingItemViewHolder = holder as ListingItemViewHolder
                        val isRevealed = revealedItems.contains(item.postView.getUniqueKey())
                        val isActionsExpanded = actionsExpandedItems
                            .contains(item.postView.getUniqueKey())
                        val isExpanded = expandedItems.contains(item.postView.getUniqueKey())

                        postListViewBuilder.bind(
                            holder = h,
                            container = binding.recyclerView,
                            postView = item.postView,
                            instance = item.instance,
                            isRevealed = isRevealed,
                            contentMaxWidth = contentMaxWidth,
                            viewLifecycleOwner = viewLifecycleOwner,
                            isExpanded = isExpanded,
                            isActionsExpanded = isActionsExpanded,
                            updateContent = false,
                            highlight = postToHighlight?.id == item.postView.post.id,
                            highlightForever = postToHighlightForever?.id == item.postView.post.id,
                            onRevealContentClickedFn = {
                                revealedItems.add(item.postView.getUniqueKey())
                                notifyItemChanged(h.absoluteAdapterPosition)
                            },
                            onImageClick = onImageClick,
                            onVideoClick = onVideoClick,
                            onPageClick = onPageClick,
                            onItemClick = onItemClick,
                            onShowMoreOptions = onShowMoreActions,
                            toggleItem = this::toggleItem,
                            toggleActions = this::toggleActions,
                            onSignInRequired = onSignInRequired,
                            onInstanceMismatch = onInstanceMismatch,
                            onHighlightComplete = {
                                postToHighlight = null
                            },
                        )
                    }
                }
                else -> {
                    super.onBindViewHolder(holder, position, payloads)
                }
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (val item = items[position]) {
                is Item.FooterItem -> {
                    val b = holder.getBinding<MainFooterItemBinding>()
                    if (item.hasMore) {
                        b.nextButton.visibility = View.VISIBLE
                        b.nextButton.setOnClickListener {
                            onNextClick()
                        }
                    } else {
                        b.nextButton.visibility = View.INVISIBLE
                    }
                    if (viewModel.currentPageIndex.value == 0) {
                        b.prevButton.visibility = View.INVISIBLE
                    } else {
                        b.prevButton.visibility = View.VISIBLE
                        b.prevButton.setOnClickListener {
                            onPrevClick()
                        }
                    }
                }
                is Item.PostItem -> {
                    val h: ListingItemViewHolder = holder as ListingItemViewHolder
                    val isRevealed = revealedItems.contains(item.postView.getUniqueKey())
                    val isActionsExpanded = actionsExpandedItems
                        .contains(item.postView.getUniqueKey())
                    val isExpanded = expandedItems.contains(item.postView.getUniqueKey())

                    postListViewBuilder.bind(
                        holder = h,
                        container = binding.recyclerView,
                        postView = item.postView,
                        instance = item.instance,
                        isRevealed = isRevealed,
                        contentMaxWidth = contentMaxWidth,
                        viewLifecycleOwner = viewLifecycleOwner,
                        isExpanded = isExpanded,
                        isActionsExpanded = isActionsExpanded,
                        updateContent = true,
                        highlight = postToHighlight?.id == item.postView.post.id,
                        highlightForever = postToHighlightForever?.id == item.postView.post.id,
                        onRevealContentClickedFn = {
                            revealedItems.add(item.postView.getUniqueKey())
                            notifyItemChanged(h.absoluteAdapterPosition)
                        },
                        onImageClick = onImageClick,
                        onShowMoreOptions = onShowMoreActions,
                        onVideoClick = onVideoClick,
                        onPageClick = onPageClick,
                        onItemClick = onItemClick,
                        toggleItem = this::toggleItem,
                        toggleActions = this::toggleActions,
                        onSignInRequired = onSignInRequired,
                        onInstanceMismatch = onInstanceMismatch,
                        onHighlightComplete = {
                            postToHighlight = null
                        }
                    )
                }
            }
        }

        override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
            super.onViewRecycled(holder)

            if (holder is ListingItemViewHolder) {
                postListViewBuilder.recycle(
                    holder
                )
            }
        }

        private fun toggleItem(position: Int, postView: PostView) {
            if (expandedItems.contains(postView.getUniqueKey())) {
                expandedItems.remove(postView.getUniqueKey())
            } else {
                expandedItems.add(postView.getUniqueKey())
            }

            notifyItemChanged(position)
        }

        private fun toggleActions(position: Int, postView: PostView) {
            if (actionsExpandedItems.contains(postView.getUniqueKey())) {
                actionsExpandedItems.remove(postView.getUniqueKey())
            } else {
                actionsExpandedItems.add(postView.getUniqueKey())
            }

            notifyItemChanged(position)
        }

        override fun getItemCount(): Int = items.size

        fun setItems(pageIndex: Int, data: CommunityViewModel.LoadedPostsData) {
            rawData = data
            this.pageIndex = pageIndex
            refreshItems()
        }

        fun highlightPost(postToHighlight: PostRef) {
            this.postToHighlight = postToHighlight
            this.postToHighlightForever = null
            val index = items.indexOfFirst {
                when (it) {
                    is Item.FooterItem -> false
                    is Item.PostItem ->
                        it.postView.post.id == postToHighlight.id
                }
            }

            if (index >= 0) {
                notifyItemChanged(index, Unit)
            }
        }

        fun highlightPostForever(postToHighlight: PostRef) {
            this.postToHighlightForever = postToHighlight
            this.postToHighlight = null
            val index = items.indexOfFirst {
                when (it) {
                    is Item.FooterItem -> false
                    is Item.PostItem ->
                        it.postView.post.id == postToHighlight.id
                }
            }

            if (index >= 0) {
                notifyItemChanged(index, Unit)
            }
        }

        fun refreshItems() {
            val newItems = rawData?.let { data ->
                data.posts
                    .map { Item.PostItem(it, data.instance) } + Item.FooterItem(data.hasMore)
            } ?: listOf()
            val oldItems = items

            val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
                override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                    val oldItem = oldItems[oldItemPosition]
                    val newItem = newItems[newItemPosition]

                    return if (oldItem::class.java == newItem::class.java) {
                        if (oldItem is Item.PostItem && newItem is Item.PostItem) {
                            oldItem.postView.getUniqueKey() == newItem.postView.getUniqueKey()
                        } else {
                            false
                        }
                    } else {
                        false
                    }
                }

                override fun getOldListSize(): Int = oldItems.size

                override fun getNewListSize(): Int = newItems.size

                override fun areContentsTheSame(
                    oldItemPosition: Int,
                    newItemPosition: Int
                ): Boolean {
                    val oldItem = oldItems[oldItemPosition]
                    val newItem = newItems[newItemPosition]
                    return oldItem == newItem
                }

            })
            this.items = newItems
            diff.dispatchUpdatesTo(this)
        }

        fun getItems() = items
    }
}
