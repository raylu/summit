package com.idunnololz.summit.main

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
import android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.TextView
import androidx.activity.viewModels
import androidx.annotation.StringRes
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.behavior.HideBottomViewOnScrollBehavior
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.navigation.NavigationBarView
import com.idunnololz.summit.R
import com.idunnololz.summit.databinding.ActivityMainBinding
import com.idunnololz.summit.history.HistoryFragment
import com.idunnololz.summit.offline.OfflineFragment
import com.idunnololz.summit.lemmy.post.PostFragment
import com.idunnololz.summit.preview.ImageViewerFragment
import com.idunnololz.summit.preview.VideoViewerFragment
import com.idunnololz.summit.settings.AccountSettingsFragment
import com.idunnololz.summit.settings.SettingsFragment
import com.idunnololz.summit.lemmy.community.CommunityFragment
import com.idunnololz.summit.login.LoginFragment
import com.idunnololz.summit.saved.SavedFragment
import com.idunnololz.summit.user.TabCommunityState
import com.idunnololz.summit.util.BaseActivity
import com.idunnololz.summit.util.BottomMenu
import com.idunnololz.summit.util.Utils
import com.idunnololz.summit.util.ext.getCurrentNavigationFragment
import com.idunnololz.summit.video.ExoPlayerManager
import dagger.hilt.android.AndroidEntryPoint
import kotlin.math.abs
import kotlin.math.max

@AndroidEntryPoint
class MainActivity : BaseActivity() {

    companion object {
        private val TAG = MainActivity::class.java.canonicalName
    }

    private val sharedViewModel: SharedViewModel by viewModels()

    var toolbarTextView: TextView? = null
        private set

    // holds the original Toolbar height.
    // this can also be obtained via (an)other method(s)
    private var toolbarHeight: Int = 0

    private var lastCustomAppBarOffset = -1f

    private var lastToolbarAppBarOffset = -1f

    private lateinit var binding: ActivityMainBinding

    val headerOffset = MutableLiveData<Int>()
    val bottomNavViewOffset = MutableLiveData<Int>()
    val windowInsets = MutableLiveData<Rect>(Rect())

    private var animatingBottomNavView = false
    private var enableBottomNavViewScrolling = false

    private val viewModel: MainActivityViewModel by viewModels()

    private var communitySelectorController: CommunitySelectorController? = null

    private var currentNavController: NavController? = null

    private var showNotificationBarBg: Boolean = true

    private lateinit var lemmyAppBarController: LemmyAppBarController

    private val insetsChangedLiveData = MutableLiveData<Int>()

    private val onNavigationItemReselectedListeners =
        mutableListOf<NavigationBarView.OnItemReselectedListener>()

    private var currentBottomMenu: BottomMenu? = null
    private var lastInsets: MainActivityInsets = MainActivityInsets()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()

        WindowCompat.setDecorFitsSystemWindows(window, false)

        super.onCreate(savedInstanceState)

        viewModel.communities.observe(this) {
            communitySelectorController?.setCommunities(it)
        }

        binding = ActivityMainBinding.inflate(LayoutInflater.from(this))
        setContentView(binding.root)

        setupActionBar()
        setupCustomActionBar()
        registerInsetsHandler()
        registerCurrentAccountListener()
        registerDefaultCommunityListener()

        lemmyAppBarController = LemmyAppBarController(this, binding.customAppBar)

        bottomNavViewOffset.observe(this) {
            binding.bottomNavigationView.translationY = it.toFloat()
        }

        handleIntent(intent)

        runOnReady(this) {
            viewModel.loadCommunities()

            if (savedInstanceState == null) {
                setupBottomNavigationBar()
            } // Else, need to wait for onRestoreInstanceState
        }

        // Set up an OnPreDrawListener to the root view.
        val content: View = findViewById(android.R.id.content)
        content.viewTreeObserver.addOnPreDrawListener(
            object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    // Check whether the initial data is ready.
                    return if (viewModel.isReady.value == true) {
                        // The content is ready. Start drawing.
                        content.viewTreeObserver.removeOnPreDrawListener(this)
                        true
                    } else {
                        // The content isn't ready. Suspend.
                        false
                    }
                }
            }
        )
    }

    private fun registerDefaultCommunityListener() {
        viewModel.defaultCommunity.observe(this) {
            lemmyAppBarController.setDefaultCommunity(it)
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        ExoPlayerManager.destroyAll()
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)

        runOnReady(this) {
            // Now that BottomNavigationBar has restored its instance state
            // and its selectedItemId, we can proceed with setting up the
            // BottomNavigationBar with Navigation
            setupBottomNavigationBar()
        }
    }

    override fun onSupportNavigateUp(): Boolean =
        currentNavController?.navigateUp() ?: false

    private fun setupBottomNavigationBar() {
        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment

        val navController = navHostFragment.navController
        val inflater = navController.navInflater
        val graph = inflater.inflate(R.navigation.main)
        navController.graph = graph

        binding.bottomNavigationView.setupWithNavController(navController)

//        val navGraphIds = listOf(
//            R.navigation.main,
//            R.navigation.saved,
//            R.navigation.history,
//        )
//
//        // Setup the bottom navigation view with a list of navigation graphs
//        val controller = binding.bottomNavigationView.setupWithNavController(
//            navGraphIds = navGraphIds,
//            fragmentManager = supportFragmentManager,
//            containerId = R.id.navHostContainer,
//            intent = intent
//        )

        binding.bottomNavigationView.setOnItemReselectedListener { menuItem ->
            Log.d(TAG, "Reselected nav item: ${menuItem.itemId}")

            onNavigationItemReselectedListeners.forEach {
                it.onNavigationItemReselected(menuItem)
            }
        }

        currentNavController = navHostFragment.navController
    }

    fun registerOnNavigationItemReselectedListener(
        onNavigationItemReselectedListener: NavigationBarView.OnItemReselectedListener
    ) {
        onNavigationItemReselectedListeners.add(onNavigationItemReselectedListener)
    }

    fun unregisterOnNavigationItemReselectedListener(
        onNavigationItemReselectedListener: NavigationBarView.OnItemReselectedListener
    ) {
        onNavigationItemReselectedListeners.remove(onNavigationItemReselectedListener)
    }

    private fun registerInsetsHandler() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val systemBarsInsets = insets.getInsetsIgnoringVisibility(
                WindowInsetsCompat.Type.systemBars(),
            )
            val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime()) // keyboard
            val imeHeight = imeInsets.bottom
            val topInset = systemBarsInsets.top
            val bottomInset = max(systemBarsInsets.bottom, imeHeight)
            val leftInset = systemBarsInsets.left
            val rightInset = systemBarsInsets.right

            lastInsets = MainActivityInsets(
                imeHeight = imeHeight,
                topInset = topInset,
                bottomInset = bottomInset,
                leftInset = leftInset,
                rightInset = rightInset,
            )

            Log.d(TAG, "Updated insets: top: $topInset bottom: $bottomInset")

            // Move toolbar below status bar
            binding.appBar.layoutParams = (binding.appBar.layoutParams as ViewGroup.MarginLayoutParams).apply {
                //topMargin = insets.systemWindowInsetTop
                leftMargin = leftInset
                rightMargin = rightInset
            }
            binding.toolbarContainer.updatePadding(top = topInset)

            binding.customAppBar.layoutParams =
                (binding.customAppBar.layoutParams as ViewGroup.MarginLayoutParams).apply {
                    leftMargin = leftInset
                    rightMargin = rightInset
                }
            binding.bottomNavigationView.layoutParams =
                (binding.bottomNavigationView.layoutParams as ViewGroup.MarginLayoutParams).apply {
                    leftMargin = leftInset
                    rightMargin = rightInset
                }
            binding.customActionBar.updatePadding(top = topInset)

            // Move our RecyclerView below toolbar + 10dp
            windowInsets.value = checkNotNull(windowInsets.value).apply {
                left = leftInset
                top = topInset
                right = rightInset
                bottom = bottomInset
            }
            binding.bottomNavigationView.layoutParams =
                (binding.bottomNavigationView.layoutParams as ViewGroup.MarginLayoutParams).apply {
                    leftMargin = leftInset
                    rightMargin = rightInset
                }
            binding.snackbarContainer.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = binding.bottomNavigationView.height
            }
            binding.bottomNavigationView.updatePadding(bottom = bottomInset)

            onStatusBarHeightChanged(topInset)

            insetsChangedLiveData.postValue(0)

            WindowInsetsCompat.CONSUMED
        }
    }

    @SuppressLint("MissingSuperCall")
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)

        handleIntent(intent)
    }

    fun showNotificationBarBg() {
        showNotificationBarBg = true
        showNotificationBarBgIfNeeded()
    }

    fun hideNotificationBarBg() {
        showNotificationBarBg = false
        hideNotificationBarBgIfNeeded()
    }

    private fun hideNotificationBarBgIfNeeded() {
        if (showNotificationBarBg) return
        binding.notificationBarBg.animate()
            .setDuration(250)
            .translationY((-binding.notificationBarBg.height).toFloat())
    }

    private fun showNotificationBarBgIfNeeded() {
        if (!showNotificationBarBg) return
        if (binding.notificationBarBg.translationY == 0f && binding.notificationBarBg.visibility == View.VISIBLE) return
        binding.notificationBarBg.visibility = View.VISIBLE
        binding.notificationBarBg.animate()
            .setDuration(250)
            .translationY(0f)
    }

    private fun showBottomNavWithScrollBehavior() {
        showBottomNav()
        binding.bottomNavigationView.layoutParams =
            (binding.bottomNavigationView.layoutParams as CoordinatorLayout.LayoutParams).apply {
                behavior = HideBottomViewOnScrollBehavior<BottomNavigationView>()
            }
    }

    fun enableBottomNavViewScrolling() {
        enableBottomNavViewScrolling = true
        binding.bottomNavigationView.visibility = View.VISIBLE
    }

    fun disableBottomNavViewScrolling() {
        enableBottomNavViewScrolling = false
    }

    fun setNavUiOpenness(progress: Float) {
        binding.customAppBar.translationY = -binding.customAppBar.height * progress
        binding.bottomNavigationView.translationY =
            binding.bottomNavigationView.height * progress
    }

    fun disableCustomAppBar() {
        binding.customAppBar.removeOnOffsetChangedListener(customAppBarOnOffsetChangedListener)
        binding.customAppBar.animate()
            .translationY(-binding.customAppBar.height.toFloat())
            .withEndAction {
                binding.customAppBar.visibility = View.INVISIBLE
            }

        communitySelectorController?.hide()

        ValueAnimator.ofInt(headerOffset.value ?: 0, 0).apply {
            duration = 300
            addUpdateListener {
                headerOffset.value = it.animatedValue as Int
            }
        }.start()
    }

    private val customAppBarOnOffsetChangedListener =
        AppBarLayout.OnOffsetChangedListener { appBarLayout, verticalOffset ->
            lastCustomAppBarOffset = verticalOffset.toFloat()
            headerOffset.value = verticalOffset + appBarLayout.height

            val percentShown = abs(lastCustomAppBarOffset) / binding.customAppBar.height
            if (!animatingBottomNavView)
                bottomNavViewOffset.value =
                    (percentShown * binding.bottomNavigationView.height).toInt()
        }

    fun enableCustomAppBar() {
        binding.customAppBar.addOnOffsetChangedListener(customAppBarOnOffsetChangedListener)
        val customAppBar = binding.customAppBar
        headerOffset.value = (customAppBar.height + lastCustomAppBarOffset).toInt()
        customAppBar.visibility = View.VISIBLE
        customAppBar.animate()
            .translationY(0f)
        val percentShown = abs(lastCustomAppBarOffset) / customAppBar.height
        val endOff = percentShown * binding.bottomNavigationView.height

        animatingBottomNavView = true
        ValueAnimator.ofInt(binding.bottomNavigationView.translationY.toInt(), endOff.toInt()).apply {
            duration = 300
            addUpdateListener {
                bottomNavViewOffset.value = it.animatedValue as Int

                if (it.animatedFraction == 1.0f) {
                    animatingBottomNavView = false
                }
            }
        }.start()
    }

    var lastScrollFlagsToolbar: Int = 0
    fun fixToolbar() {
        binding.toolbarContainer.layoutParams =
            (binding.toolbarContainer.layoutParams as AppBarLayout.LayoutParams).apply {
                lastScrollFlagsToolbar = scrollFlags
                scrollFlags = 0
            }
    }

    fun unfixToolbar() {
        binding.toolbarContainer.layoutParams =
            (binding.toolbarContainer.layoutParams as AppBarLayout.LayoutParams).apply {
                scrollFlags = lastScrollFlagsToolbar
            }
    }

    fun showCustomAppBar() {
        binding.customAppBar.setExpanded(true)
    }

    fun showBottomNav() {
        if (enableBottomNavViewScrolling && binding.bottomNavigationView.visibility == View.VISIBLE) {
            return
        }
        if (binding.bottomNavigationView.visibility != View.VISIBLE) {
            binding.bottomNavigationView.visibility = View.VISIBLE
            binding.bottomNavigationView.translationY =
                binding.bottomNavigationView.height.toFloat()
            Log.d(TAG, "bottomNavigationView.height: ${binding.bottomNavigationView.height}")
            binding.bottomNavigationView.post {
                binding.bottomNavigationView.animate()
                    .translationY(0f)
                    .setDuration(250)
            }
        } else {
            if (binding.bottomNavigationView.translationY == 0f) return

            binding.bottomNavigationView.animate()
                .translationY(0f)
                .setDuration(250)
        }
    }

    fun hideBottomNav() {
        Log.d(TAG, "bottomNavigationView.height: ${binding.bottomNavigationView.height}")
        binding.bottomNavigationView.animate()
            .translationY(binding.bottomNavigationView.height.toFloat())
            .setDuration(250)
            .withEndAction {
                binding.bottomNavigationView.visibility = View.INVISIBLE
            }
    }

    private fun handleIntent(intent: Intent?) {
//        intent ?: return
//
//        val data = intent.data ?: return
//        if (data.authority == "auth") {
//            RedditAuthManager.instance.handleAuthAttempt(data, supportFragmentManager)
//        } else if (RedditUtils.isUriReddit(data)) {
//            if (RedditUtils.isUriGallery(data) && data.pathSegments.size >= 2) {
//                RedirectHandlerDialogFragment.newInstance(LinkUtils.getRedirectLink(data.pathSegments[1]))
//                    .show(supportFragmentManager, "asd")
//            } else if (RedditUtils.isUriRedirect(data)) {
//                RedirectHandlerDialogFragment.newInstance(data.toString())
//                    .show(supportFragmentManager, "asd")
//            } else {
//                if (binding.bottomNavigationView.selectedItemId != R.id.main) {
//                    binding.bottomNavigationView.selectedItemId = R.id.main
//                }
//                executeWhenMainFragmentAvailable { mainFragment ->
//                    mainFragment.navigateToUri(data)
//                }
//            }
//        }
    }

    private fun executeWhenMainFragmentAvailable(fn: (MainFragment) -> Unit) {
        val navigateToPageRunnable = object : Runnable {
            override fun run() {
                val currentFragment = supportFragmentManager.getCurrentNavigationFragment()
                if (currentFragment is MainFragment) {
                    fn(currentFragment)
                } else {
                    // super hacky :x
                    binding.bottomNavigationView.post(this)
                }
            }
        }

        navigateToPageRunnable.run()
    }

    private fun onStatusBarHeightChanged(statusBarHeight: Int) {
        val lp = binding.notificationBarBg.layoutParams
        if (lp.height != statusBarHeight) {
            Log.d(TAG, "New status bar height: $statusBarHeight")
            lp.height = statusBarHeight
            binding.notificationBarBg.layoutParams = lp

            if (!showNotificationBarBg) {
                binding.notificationBarBg.visibility = View.INVISIBLE
            }
        }
    }

    private fun setupActionBar() {
        setSupportActionBar(binding.toolbar)

        val actionBar = supportActionBar
        actionBar?.setDisplayShowHomeEnabled(true)
        actionBar?.setDisplayHomeAsUpEnabled(true)

        toolbarTextView = Utils.getToolbarTextView(binding.toolbar)
    }

    private fun setupCustomActionBar() {
        binding.customActionBar.layoutParams =
            (binding.customActionBar.layoutParams as AppBarLayout.LayoutParams).apply {
                scrollFlags =
                    AppBarLayout.LayoutParams.SCROLL_FLAG_SCROLL or
                            AppBarLayout.LayoutParams.SCROLL_FLAG_ENTER_ALWAYS or
                            AppBarLayout.LayoutParams.SCROLL_FLAG_SNAP
            }
    }

    fun setupActionBar(@StringRes title: Int, showUp: Boolean) {
        setupActionBar(getString(title), showUp, showSpinner = false, animateActionBarIn = true)
    }

    @JvmOverloads
    fun setupActionBar(
        title: CharSequence,
        showUp: Boolean,
        showSpinner: Boolean = false,
        animateActionBarIn: Boolean = true
    ) {
        val ab = supportActionBar
        if (ab != null) {
            if (showSpinner) {
                ab.setDisplayShowTitleEnabled(false)
            } else {
                ab.setDisplayShowTitleEnabled(true)
                ab.title = title
            }
        }
        // undo any alpha changes. See EsportsTeamFragment.
        toolbarTextView?.alpha = 1f
        if (animateActionBarIn) {
            showActionBar()
        }

        if (showUp) {
            ab?.setDisplayHomeAsUpEnabled(true)
            ab?.setDisplayShowHomeEnabled(true)
        } else {
            ab?.setDisplayHomeAsUpEnabled(false)
            ab?.setDisplayShowHomeEnabled(false)
        }
    }

    private val onOffsetChangedListener = AppBarLayout.OnOffsetChangedListener {
            appBarLayout, verticalOffset ->

        lastToolbarAppBarOffset = verticalOffset.toFloat()
        headerOffset.value = verticalOffset + appBarLayout.height

        val percentShown = abs(lastToolbarAppBarOffset) / binding.appBar.height
        if (!animatingBottomNavView)
            bottomNavViewOffset.value =
                (percentShown * binding.bottomNavigationView.height).toInt()
    }
    fun showActionBar() {
        setSupportActionBar(binding.toolbar)

        hideActionBar(false)
        unfixToolbar()

        binding.toolbarContainer.updateLayoutParams<AppBarLayout.LayoutParams> {
            scrollFlags =
                AppBarLayout.LayoutParams.SCROLL_FLAG_SCROLL or
                        AppBarLayout.LayoutParams.SCROLL_FLAG_ENTER_ALWAYS or
                        AppBarLayout.LayoutParams.SCROLL_FLAG_SNAP
        }
//        val supportActionBar = supportActionBar ?: return
//        if (supportActionBar.isShowing) return
        binding.appBar.visibility = View.VISIBLE
        if (ViewCompat.isLaidOut(binding.appBar)) {
            binding.appBar.animate().translationY(0f)
        } else {
            binding.appBar.translationY = 0f
        }
//        supportActionBar.show()
    }

    fun enableScrollingBottomNavByActionBar() {
        binding.appBar.addOnOffsetChangedListener(onOffsetChangedListener)
    }

    fun hideActionBar(hideToolbar: Boolean = true) {
        binding.appBar.removeOnOffsetChangedListener(onOffsetChangedListener)
        if (hideToolbar) {
            fixToolbar()
//            val supportActionBar = supportActionBar ?: return
//            if (!supportActionBar.isShowing) {
//                return
//            }
            if (ViewCompat.isLaidOut(binding.appBar)) {
                binding.appBar.animate().translationY((-binding.appBar.height).toFloat())
            } else {
                binding.appBar.visibility = View.INVISIBLE
            }
//            supportActionBar.hide()
        }

        updateToolbarHeight()
    }

    private fun createOrGetSubredditSelectorController(): CommunitySelectorController =
        communitySelectorController
            ?: viewModel.communitySelectorControllerFactory.create(this).also {
                it.inflate(binding.overlayContainer)
                it.setCommunities(viewModel.communities.value)
                communitySelectorController = it
            }

    fun showCommunitySelector(): CommunitySelectorController {
        val communitySelectorController = createOrGetSubredditSelectorController()

        viewModel.communities.value.let {
            communitySelectorController.setCommunities(it)
        }

        communitySelectorController.show(lastInsets)
        return communitySelectorController
    }

    fun getCustomAppBarController() = lemmyAppBarController

    private fun updateToolbarHeight() {
        if (toolbarHeight == 0) {
            toolbarHeight = binding.toolbar.height
            if (toolbarHeight == 0) {
                binding.toolbar.measure(0, 0)
                toolbarHeight = binding.toolbar.measuredHeight
            }
        }
    }

    override fun onBackPressed() {
        onBackPressed(force = false)
    }

    fun onBackPressed(force: Boolean) {
        if (!force && communitySelectorController?.isVisible == true) {
            communitySelectorController?.onBackPressed()
        } else {
            super.onBackPressed()
        }
    }

    fun hideSystemUI() {
        // Enables regular immersive mode.
        // For "lean back" mode, remove SYSTEM_UI_FLAG_IMMERSIVE.
        // Or for "sticky immersive," replace it with SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE
                // Set the content to appear under the system bars so that the
                // content doesn't resize when the system bars hide and show.
                or SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                // Hide the nav bar and status bar
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN)
    }

    // Shows the system bars by removing all the flags
    // except for the ones that make the content appear under the system bars.
    fun showSystemUI(animate: Boolean) {
        window.decorView.systemUiVisibility = (SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or if (resources.getBoolean(R.bool.isLightTheme)) {
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        } else 0)
    }

    fun insetViewAutomaticallyByMargins(lifecycleOwner: LifecycleOwner, rootView: View) {
        insetsChangedLiveData.observe(lifecycleOwner) {
            val lp = rootView.layoutParams as ViewGroup.MarginLayoutParams
            val insets = checkNotNull(windowInsets.value)

            lp.topMargin = insets.top
            lp.bottomMargin = insets.bottom
            lp.leftMargin = insets.left
            lp.rightMargin = insets.right
            rootView.requestLayout()
        }
    }

    fun insetViewExceptBottomAutomaticallyByMargins(lifecycleOwner: LifecycleOwner, rootView: View) {
        insetsChangedLiveData.observe(lifecycleOwner) {
            val lp = rootView.layoutParams as ViewGroup.MarginLayoutParams
            val insets = checkNotNull(windowInsets.value)

            lp.topMargin = insets.top
            lp.leftMargin = insets.left
            lp.rightMargin = insets.right
            rootView.requestLayout()
        }
    }


    fun insetViewExceptTopAutomaticallyByPadding(lifecycleOwner: LifecycleOwner, rootView: View) {
        insetsChangedLiveData.observe(lifecycleOwner) {
            val insets = checkNotNull(windowInsets.value)

            rootView.setPadding(
                insets.left,
                0,
                insets.right,
                insets.bottom
            )
        }
    }

    fun restoreTabState(state: TabCommunityState?) {
        state ?: return
        if (binding.bottomNavigationView.selectedItemId != R.id.main) {
            binding.bottomNavigationView.selectedItemId = R.id.main
        }
        executeWhenMainFragmentAvailable { mainFragment ->
            mainFragment.restoreTabState(state)
        }
    }

    inline fun <reified T> setupForFragment() {
        when (T::class) {
            CommunityFragment::class -> {
                hideActionBar()
                enableBottomNavViewScrolling()
                showBottomNav()
                showNotificationBarBg()
                enableCustomAppBar()
            }
            PostFragment::class -> {
                hideActionBar()
                disableBottomNavViewScrolling()
                hideBottomNav()
                showNotificationBarBg()
                disableCustomAppBar()
            }
            VideoViewerFragment::class -> {
                hideActionBar()
                disableBottomNavViewScrolling()
                hideBottomNav()
                disableCustomAppBar()
                hideNotificationBarBg()
            }
            ImageViewerFragment::class -> {
                showActionBar()
                disableBottomNavViewScrolling()
                hideBottomNav()
                disableCustomAppBar()
                hideNotificationBarBg()
            }
            OfflineFragment::class -> {
                hideActionBar()
                disableBottomNavViewScrolling()
                showBottomNav()
                disableCustomAppBar()
                showNotificationBarBg()
            }
            HistoryFragment::class -> {
                hideActionBar()
                disableBottomNavViewScrolling()
                showBottomNav()
                showNotificationBarBg()
                disableCustomAppBar()
            }
            SettingsFragment::class -> {
                hideActionBar()
                disableBottomNavViewScrolling()
                showBottomNav()
                disableCustomAppBar()
                showNotificationBarBg()
            }
            AccountSettingsFragment::class -> {
                hideActionBar()
                disableBottomNavViewScrolling()
                showBottomNav()
                disableCustomAppBar()
                showNotificationBarBg()
            }
            LoginFragment::class -> {
                hideActionBar()
                disableBottomNavViewScrolling()
                showBottomNav()
                disableCustomAppBar()
                showNotificationBarBg()
            }
            SavedFragment::class -> {
                hideActionBar()
                disableBottomNavViewScrolling()
                showBottomNav()
                disableCustomAppBar()
                showNotificationBarBg()
            }
            else -> throw RuntimeException("No setup instructions for type: ${T::class.java.canonicalName}")
        }
    }

    fun getSnackbarContainer(): View = binding.snackbarContainer
    fun getToolbarHeight() = binding.toolbar.layoutParams.height

    private fun registerCurrentAccountListener() {
        viewModel.currentAccount.observe(this) {
            lemmyAppBarController.onAccountChanged(it)
        }
    }

    fun runOnReady(lifecycleOwner: LifecycleOwner, cb: () -> Unit) {
        viewModel.isReady.observe(lifecycleOwner) {
            if (it == true) {
                cb()
            }
        }
    }

    fun showBottomMenu(bottomMenu: BottomMenu) {
        currentBottomMenu?.close()
        bottomMenu.setInsets(lastInsets.topInset, lastInsets.bottomInset)
        bottomMenu.show(binding.bottomSheetContainer)
        currentBottomMenu = bottomMenu
    }
}