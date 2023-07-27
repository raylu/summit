package com.idunnololz.summit.main

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.commitNow
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.Navigation
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.discord.panels.OverlappingPanelsLayout
import com.discord.panels.PanelState
import com.google.android.material.navigation.NavigationBarView
import com.idunnololz.summit.R
import com.idunnololz.summit.databinding.FragmentMainBinding
import com.idunnololz.summit.lemmy.CommentRef
import com.idunnololz.summit.lemmy.CommunityRef
import com.idunnololz.summit.lemmy.PageRef
import com.idunnololz.summit.lemmy.PersonRef
import com.idunnololz.summit.lemmy.PostRef
import com.idunnololz.summit.lemmy.community.CommunityFragment
import com.idunnololz.summit.lemmy.community.CommunityFragmentArgs
import com.idunnololz.summit.lemmy.communityInfo.CommunityInfoViewModel
import com.idunnololz.summit.lemmy.person.PersonTabbedFragmentArgs
import com.idunnololz.summit.lemmy.post.PostFragmentArgs
import com.idunnololz.summit.main.communities_pane.CommunitiesPaneController
import com.idunnololz.summit.main.communities_pane.CommunitiesPaneViewModel
import com.idunnololz.summit.preferences.Preferences
import com.idunnololz.summit.tabs.TabsManager
import com.idunnololz.summit.tabs.communityRef
import com.idunnololz.summit.tabs.isHomeTab
import com.idunnololz.summit.user.TabCommunityState
import com.idunnololz.summit.user.UserCommunitiesManager
import com.idunnololz.summit.util.BaseFragment
import com.idunnololz.summit.util.ext.attachNavHostFragment
import com.idunnololz.summit.util.ext.detachNavHostFragment
import com.idunnololz.summit.util.ext.removeNavHostFragment
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class MainFragment : BaseFragment<FragmentMainBinding>() {

    companion object {
        private val TAG = MainFragment::class.java.canonicalName

        private const val SIS_FRAGMENT_TAGS = "SIS_FRAGMENT_TAGS"

        fun getTagForTab(tabId: Long): String = "innerFragment:$tabId"
        fun getTagForTab(communityRef: CommunityRef): String = "innerFragment:$communityRef"

        fun getTagForTab(tab: TabsManager.Tab): String =
            when (tab) {
                is TabsManager.Tab.SubscribedCommunityTab ->
                    getTagForTab(tab.communityRef)
                is TabsManager.Tab.UserCommunityTab ->
                    getTagForTab(tab.userCommunityItem.id)
            }

        fun getIdFromTag(tag: String): Long? =
            try {
                tag.split(":")[1].toLong()
            } catch (e: Exception) { null }
    }

    private val args: MainFragmentArgs by navArgs()

    private val sharedViewModel: SharedViewModel by activityViewModels()
    private val viewModel: MainFragmentViewModel by viewModels()
    private val communitiesPaneViewModel: CommunitiesPaneViewModel by viewModels()
    private val communityInfoViewModel: CommunityInfoViewModel by viewModels()

    private lateinit var firstFragmentTag: String

    @Inject
    lateinit var userCommunitiesManager: UserCommunitiesManager

    @Inject
    lateinit var tabsManager: TabsManager

    @Inject
    lateinit var preferences: Preferences

    lateinit var communitiesPaneController: CommunitiesPaneController

    private val fragmentTags = hashSetOf<String>()

    private var currentNavController: NavController? = null

    private val deferredNavigationRequests = mutableListOf<Runnable>()

    private var doubleBackToExitPressedOnce = false

    private val onDestinationChangedListener =
        NavController.OnDestinationChangedListener { _, destination, _ ->
            Log.d(TAG, "onDestinationChangedListener(): ${destination.label}")

            if (destination.id == R.id.postFragment || destination.id == R.id.communityFragment) {
                binding.rootView.setStartPanelLockState(OverlappingPanelsLayout.LockState.UNLOCKED)
            } else {
                binding.rootView.setStartPanelLockState(OverlappingPanelsLayout.LockState.CLOSE)
            }
        }

    private val paneOnBackPressHandler = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            if (!isBindingAvailable()) return

            binding.rootView.closePanels()
        }
    }

    private val onBackPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            if (!isBindingAvailable()) return

            val context = requireContext()

            if (currentNavController?.navigateUp() == false) {
                if (!findNavController().navigateUp()) {
                    if (doubleBackToExitPressedOnce) {
                        getMainActivity()?.finish()
                        return
                    }

                    doubleBackToExitPressedOnce = true
                    Toast.makeText(
                        context,
                        context.getString(R.string.press_back_again_to_exit),
                        Toast.LENGTH_SHORT,
                    ).show()

                    lifecycleScope.launch(Dispatchers.IO) {
                        delay(2000)
                        doubleBackToExitPressedOnce = false
                    }
                }
            }
        }
    }

    private val onNavigationItemReselectedListener =
        NavigationBarView.OnItemReselectedListener a@{
            if (it.itemId == R.id.mainFragment) {
                val tab = tabsManager.currentTab.value ?: return@a

                val currentFragment = getCurrentFragment()

                Log.d(TAG, "currentFragment: $currentFragment")

                if (!tab.isHomeTab &&
                    currentFragment is CommunityFragment && currentFragment.isPristineFirstPage()
                ) {

                    changeCommunity(tabsManager.getHomeTab())
                } else {
                    resetCurrentTab(tab)
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        savedInstanceState?.getStringArrayList(SIS_FRAGMENT_TAGS)?.forEach {
            fragmentTags.add(it)
        }
        requireActivity().onBackPressedDispatcher.addCallback(
            this,
            onBackPressedCallback,
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        postponeEnterTransition()

        setBinding(FragmentMainBinding.inflate(inflater, container, false))
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        communitiesPaneController = communitiesPaneViewModel.createController(
            binding.startPanel,
            viewLifecycleOwner,
        ) { tabRef, resetTab: Boolean ->
            binding.rootView.closePanels()

            binding.rootView.postDelayed(
                a@{
                    if (!isBindingAvailable()) return@a

                    val tab = tabsManager.getTab(tabRef)

                    val selectedTabFragment = childFragmentManager.findFragmentByTag(getTagForTab(tab))
                    if (selectedTabFragment?.findNavController() == currentNavController) {
                        if (resetTab) {
                            resetCurrentTab(tab)
                        }

                        // otherwise do nothing because we are already on the right tab
                    } else {
                        if (resetTab) {
                            removeNavHostFragment(childFragmentManager, getTagForTab(tab))
                        }

                        binding.rootView.post {
                            changeCommunity(tab)
                        }
                    }
                },
                300,
            )
        }

        val currentTab = requireNotNull(tabsManager.currentTab.value)
        firstFragmentTag = getTagForTab(
            (tabsManager.getHomeTab() as TabsManager.Tab.UserCommunityTab).userCommunityItem.id,
        )
        Log.d(TAG, "First fragment tag is $firstFragmentTag")

        changeCommunity(currentTab)

        userCommunitiesManager.getAllUserCommunities().forEach { tab ->
            if (tabsManager.currentTab.value?.isHomeTab != true) {
                childFragmentManager.findFragmentByTag(getTagForTab(tab.id))?.let {
                    detachNavHostFragment(
                        childFragmentManager,
                        it as NavHostFragment,
                    )
                }
            }
        }

        viewModel.userCommunitiesChangedEvents.observe(viewLifecycleOwner) {
            it.contentIfNotHandled ?: return@observe

            purgeUnusedFragments()
        }

        ArrayList(deferredNavigationRequests).forEach {
            it.run()
        }
        deferredNavigationRequests.clear()

        binding.rootView.post {
            startPostponedEnterTransition()
        }

        fun updatePaneBackPressHandler() {
            if (!isBindingAvailable()) return

            Log.d(TAG, "updatePaneBackPressHandler(): selected panel: ${binding.rootView.getSelectedPanel()}")
            if (binding.rootView.getSelectedPanel() != OverlappingPanelsLayout.Panel.CENTER) {
                paneOnBackPressHandler.remove()
                requireMainActivity().onBackPressedDispatcher.addCallback(paneOnBackPressHandler)
            } else {
                paneOnBackPressHandler.remove()
            }
        }

        binding.rootView
            .registerStartPanelStateListeners(
                object : OverlappingPanelsLayout.PanelStateListener {
                    override fun onPanelStateChange(panelState: PanelState) {
                        Log.d(TAG, "panelState: $panelState")
                        when (panelState) {
                            PanelState.Closed -> {
                                getMainActivity()?.setNavUiOpenness(0f)

                                updatePaneBackPressHandler()
                            }
                            is PanelState.Closing -> {
                                getMainActivity()?.setNavUiOpenness(panelState.progress)
                            }
                            PanelState.Opened -> {
                                getMainActivity()?.setNavUiOpenness(100f)
                                communitiesPaneController.onShown()

                                updatePaneBackPressHandler()
                            }
                            is PanelState.Opening -> {
                                getMainActivity()?.setNavUiOpenness(panelState.progress)
                            }
                        }
                    }
                },
            )
        binding.rootView.setEndPanelLockState(OverlappingPanelsLayout.LockState.CLOSE)

        addMenuProvider(
            object : MenuProvider {
                override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {}

                override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                    val currentNavController = currentNavController
                    if (menuItem.itemId == android.R.id.home && currentNavController != null) {
                        return currentNavController.navigateUp()
                    }
                    return false
                }
            },
        )

        requireMainActivity().apply {
            this.insetViewAutomaticallyByMargins(this, binding.startPanel.swipeRefreshLayout)
        }

        lifecycleScope.launch {
            withContext(Dispatchers.Default) {
                userCommunitiesManager.defaultCommunity.collect {
                }
            }
        }
    }

    fun navigateToPage(page: PageRef) {
        if (!isBindingAvailable()) return

        when (page) {
            is CommunityRef -> {
                currentNavController?.navigate(
                    R.id.communityFragment,
                    CommunityFragmentArgs(
                        tab = tabsManager.currentTab.value,
                        page,
                    ).toBundle(),
                )
            }
            is PostRef -> {
                currentNavController?.navigate(
                    R.id.postFragment,
                    PostFragmentArgs(
                        page.instance,
                        page.id,
                        null,
                        isSinglePage = true,
                    ).toBundle(),
                )
            }
            is CommentRef -> {
                currentNavController?.navigate(
                    R.id.postFragment,
                    PostFragmentArgs(
                        instance = page.instance,
                        id = 0,
                        commentId = page.id,
                        currentCommunity = null,
                        isSinglePage = true,
                    ).toBundle(),
                )
            }
            is PersonRef -> {
                currentNavController?.navigate(
                    R.id.personTabbedFragment,
                    PersonTabbedFragmentArgs(
                        page,
                    ).toBundle(),
                )
            }
        }
    }

    private fun getCurrentFragment(): Fragment? {
        val currentFragment = binding.innerNavHostContainer.getFragment<Fragment>()
        return if (currentFragment is NavHostFragment) {
            currentFragment.childFragmentManager.fragments.getOrNull(0)
        } else {
            null
        }
    }

    private var lastNavHostFragment: NavHostFragment? = null
    private fun changeCommunity(tab: TabsManager.Tab) {
        lastNavHostFragment?.navController?.removeOnDestinationChangedListener(
            onDestinationChangedListener,
        )

        val selectedNavHostFragment = updateAttachedFragment(tab)
        selectedNavHostFragment.navController.addOnDestinationChangedListener(
            onDestinationChangedListener,
        )
        attachNavHostFragment(childFragmentManager, selectedNavHostFragment, true)
        lastNavHostFragment = selectedNavHostFragment

        tabsManager.updateCurrentTab(tab)
    }

    private fun setCurrentNavController(navController: NavController) {
        Log.d(TAG, "currentNavController: ${navController.currentDestination?.label}")
        currentNavController = navController
        sharedViewModel.currentNavController.value = currentNavController
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        outState.putStringArrayList(
            SIS_FRAGMENT_TAGS,
            arrayListOf<String>().apply { addAll(fragmentTags) },
        )
    }

    override fun onResume() {
        super.onResume()

        requireMainActivity().registerOnNavigationItemReselectedListener(
            onNavigationItemReselectedListener,
        )
    }

    override fun onPause() {
        requireMainActivity().unregisterOnNavigationItemReselectedListener(
            onNavigationItemReselectedListener,
        )

        super.onPause()
    }

    override fun onDestroyView() {
        // Fixes some weird crash?
        view?.let { view ->
            Navigation.setViewNavController(view, findNavController())
        }

        super.onDestroyView()
        Log.d(TAG, "onDestroyView()")
    }

    private fun updateAttachedFragment(currentTab: TabsManager.Tab): NavHostFragment {
        val communityRef = currentTab.communityRef
        val fragmentManager = childFragmentManager
        fragmentManager.popBackStack(firstFragmentTag, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        val newlySelectedItemTag = getTagForTab(currentTab)
        val selectedFragment = obtainNavHostFragment(
            childFragmentManager,
            newlySelectedItemTag,
            R.navigation.community,
            R.id.innerNavHostContainer,
            CommunityFragmentArgs(
                tab = currentTab,
                communityRef = communityRef,
            ).toBundle(),
        )

        setCurrentNavController(selectedFragment.navController)

        // Exclude the first fragment tag because it's always in the back stack.
        if (firstFragmentTag != newlySelectedItemTag) {
            // Commit a transaction that cleans the back stack and adds the first fragment
            // to it, creating the fixed started destination.
            fragmentManager.beginTransaction()
                .setCustomAnimations(
                    androidx.navigation.ui.R.anim.nav_default_enter_anim,
                    androidx.navigation.ui.R.anim.nav_default_exit_anim,
                    androidx.navigation.ui.R.anim.nav_default_pop_enter_anim,
                    androidx.navigation.ui.R.anim.nav_default_pop_exit_anim,
                )
                .attach(selectedFragment)
                .setPrimaryNavigationFragment(selectedFragment)
                .apply {
                    // Detach all other Fragments
                    userCommunitiesManager.getAllUserCommunities().forEach { tab ->
                        if (getTagForTab(tab.id) != newlySelectedItemTag) {
                            fragmentManager.findFragmentByTag(firstFragmentTag)?.let {
                                detach(it)
                            }
                        }
                    }
                    tabsManager.previousTabs.forEach { tab ->
                        if (tab is TabsManager.Tab.SubscribedCommunityTab) {
                            if (getTagForTab(tab.communityRef) != newlySelectedItemTag) {
                                fragmentManager.findFragmentByTag(firstFragmentTag)?.let {
                                    detach(it)
                                }
                            }
                        }
                    }

//                    fragmentManager.fragments.forEach {
//                        if (tag != newlySelectedItemTag) {
//                            detach(it)
//                        }
//                    }
                }
                .addToBackStack(firstFragmentTag)
                .setReorderingAllowed(true)
                .commit()
        } else {
            fragmentManager.beginTransaction()
                .setPrimaryNavigationFragment(selectedFragment)
                .commit()
        }

        return selectedFragment
    }

    private fun purgeUnusedFragments() {
        val unusedFragmentTags = fragmentTags
            .mapNotNull { getIdFromTag(it) }
            .filter { userCommunitiesManager.getTab(it) == null }
            .map { getTagForTab(it) }

        purgeFragments(unusedFragmentTags)
    }

    private fun purgeFragments(fragmentsToRemoveTags: List<String>) {
        val fragmentManager = childFragmentManager
        val fragmentsToRemove = arrayListOf<Fragment>()

        fragmentsToRemoveTags.forEach {
            val existingFragment = fragmentManager.findFragmentByTag(it)
            if (lastNavHostFragment == existingFragment) {
                return@forEach // this is the current fragment, we can't purge it!
            }

            check(fragmentTags.remove(it)) { "Expected tag to be in fragmentTags but was not: $it" }
            if (existingFragment != null) {
                fragmentsToRemove.add(existingFragment)
                Log.d(TAG, "Removing unused fragment $it")
            } else {
                Log.d(TAG, "Removing unused fragment tag $it")
            }
        }

        if (fragmentsToRemove.isNotEmpty()) {
            val transaction = fragmentManager.beginTransaction()
            fragmentsToRemove.forEach {
                transaction.remove(it)
            }
            transaction.commitNow()
        }
    }

    private fun obtainNavHostFragment(
        fragmentManager: FragmentManager,
        fragmentTag: String,
        navGraphId: Int,
        containerId: Int,
        startDestinationArgs: Bundle? = null,
    ): NavHostFragment {
        fragmentTags.add(fragmentTag)

        return com.idunnololz.summit.util.ext.obtainNavHostFragment(
            fragmentManager,
            fragmentTag,
            navGraphId,
            containerId,
            startDestinationArgs,
        )
    }

    fun restoreTabState(state: TabCommunityState) {
//        // we need to check if tab is still open since user could have closed it
//        if (userCommunitiesManager.getTab(state.tabId) == null) {
//            // tab was closed... restore it
//            sharedViewModel.addTab(
//                UserCommunityItem(
//                    id = state.tabId,
//                    communityRef = state.viewState.communityState.communityRef,
//
//                    )
//            )
//        }
//
//        if (userCommunitiesManager.currentTabId.value != state.tabId) {
//            userCommunitiesManager.currentTabId.value = state.tabId
//        }
//
//        val runnable = object : Runnable {
//            override fun run() {
//                val fragmentTag = getTagForTab(state.tabId)
//                val fragment = childFragmentManager.findFragmentByTag(fragmentTag)
//
//                if (fragment?.isAdded != true) {
//                    binding.rootView.post(this)
//                } else {
//                    val curFrag = childFragmentManager.getCurrentNavigationFragment()
//                    if (curFrag is CommunityFragment) {
//                        curFrag.restoreState(state.viewState, reload = true)
//                    } else {
//                        // Pop the back stack to the start destination of the current navController graph
//                        fragment.findNavController().let {
//                            it.popBackStack(
//                                it.graph.startDestinationId, false
//                            )
//                        }
//                        binding.rootView.post(this)
//                    }
//                }
//            }
//        }
//
//        runnable.run()
    }

    fun updateCommunityInfoPane(communityRef: CommunityRef) {
        communityInfoViewModel.onCommunityChanged(communityRef)
    }

    fun setStartPanelLockState(lockState: OverlappingPanelsLayout.LockState) {
        binding.rootView.setStartPanelLockState(lockState)
    }

    fun expandStartPane() {
        binding.rootView.openStartPanel()
    }

    private fun resetCurrentTab(tab: TabsManager.Tab) {
        currentNavController?.popBackStack(R.id.community, false)

        currentNavController?.navigate(
            R.id.communityFragment,
            CommunityFragmentArgs(
                communityRef = tab.communityRef,
                tab = tab,
            ).toBundle(),
        )
    }
}
