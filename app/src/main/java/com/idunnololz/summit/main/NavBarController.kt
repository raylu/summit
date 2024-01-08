package com.idunnololz.summit.main

import android.app.Activity
import android.util.Log
import android.view.Gravity
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.MarginLayoutParams
import android.widget.FrameLayout
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.doOnPreDraw
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.core.view.updatePaddingRelative
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.MutableLiveData
import androidx.window.layout.WindowMetricsCalculator
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.divider.MaterialDivider
import com.google.android.material.navigation.NavigationBarView
import com.google.android.material.navigation.NavigationBarView.LABEL_VISIBILITY_LABELED
import com.google.android.material.navigationrail.NavigationRailView
import com.idunnololz.summit.R
import com.idunnololz.summit.preferences.NavigationRailModeId
import com.idunnololz.summit.preferences.Preferences
import com.idunnololz.summit.settings.navigation.NavBarConfig
import com.idunnololz.summit.settings.navigation.NavBarDestinations
import com.idunnololz.summit.util.Utils
import com.idunnololz.summit.util.ext.getColorFromAttribute
import com.idunnololz.summit.util.ext.getDimen

class NavBarController(
    val activity: Activity,
    val contentView: CoordinatorLayout,
    val lifecycleOwner: LifecycleOwner,
    val onNavBarChanged: () -> Unit,
) {

    companion object {
        private const val TAG = "BottomNavController"
    }

    private val context = activity

    private var _useNavigationRail: Boolean? = null

    val useNavigationRail: Boolean
        get() = _useNavigationRail!!

    var useBottomNavBar = true
    var navRailMode = NavigationRailModeId.Auto

    lateinit var navBar: NavigationBarView

    var newLeftInset: Int = 0
    var newRightInset: Int = 0

    val bottomNavHeight: Int
        get() =
            if (useBottomNavBar) {
                if (useNavigationRail) {
                    0
                } else {
                    navBar.height
                }
            } else {
                0
            }

    val navRailWidth: Int
        get() =
            if (useBottomNavBar) {
                if (useNavigationRail) {
                    if (navBar.width == 0) {
                        activity.getDimen(com.google.android.material.R.dimen.m3_navigation_rail_default_width)
                    } else {
                        navBar.width
                    }
                } else {
                    0
                }
            } else {
                0
            }

    val bottomNavViewOffset = MutableLiveData<Int>(0)
    val bottomNavViewAnimationOffset = MutableLiveData<Float>(0f)
    val bottomNavOpenness: Float
        get() = (
            bottomNavViewOffset.value!!.toFloat() +
                bottomNavViewAnimationOffset.value!!.toFloat()
            ) / navBar.height
    var useCustomNavBar: Boolean = false

    private var enableBottomNavViewScrolling = false
    private var navBarConfig: NavBarConfig = NavBarConfig()

    init {
        bottomNavViewOffset.observe(lifecycleOwner) {
            updateOpenness(bottomNavOpenness)
        }
        bottomNavViewAnimationOffset.observe(lifecycleOwner) {
            updateOpenness(bottomNavOpenness)
        }
    }

    fun setup() {
        onWindowSizeChanged()
    }

    private fun onWindowSizeChanged() {
        val metrics = WindowMetricsCalculator.getOrCreate()
            .computeCurrentWindowMetrics(activity)

        val widthDp = metrics.bounds.width() /
            activity.resources.displayMetrics.density
        val widthWindowSizeClass = when {
            widthDp < 600f -> WindowSizeClass.COMPACT
            widthDp < 840f -> WindowSizeClass.MEDIUM
            else -> WindowSizeClass.EXPANDED
        }

        val heightDp = metrics.bounds.height() /
            activity.resources.displayMetrics.density
        val heightWindowSizeClass = when {
            heightDp < 480f -> WindowSizeClass.COMPACT
            heightDp < 900f -> WindowSizeClass.MEDIUM
            else -> WindowSizeClass.EXPANDED
        }

        val useNavigationRail: Boolean =
            when (navRailMode) {
                NavigationRailModeId.ManualOn -> true
                NavigationRailModeId.ManualOff -> false
                NavigationRailModeId.Auto -> {
                    widthWindowSizeClass != WindowSizeClass.COMPACT
                }
                else -> false
            }

        if (useNavigationRail == _useNavigationRail) {
            return
        }
        _useNavigationRail = useNavigationRail

        val navBarChanged = ::navBar.isInitialized
        if (navBarChanged) {
            contentView.removeView(navBar)
        }

        navBar = if (useNavigationRail) {
            val v = NavigationRailView(activity).apply {
                setBackgroundColor(
                    activity.getColorFromAttribute(
                        com.google.android.material.R.attr.backgroundColor,
                    ),
                )
                inflateMenu(R.menu.bottom_navigation_menu)
                labelVisibilityMode = LABEL_VISIBILITY_LABELED
            }

            contentView.addView(v)
//            val divider = MaterialDivider(context)
//            v.addView(divider)
//            divider.apply {
//                updateLayoutParams<FrameLayout.LayoutParams> {
//                    width = Utils.convertDpToPixel(1f).toInt()
//                    height = MarginLayoutParams.MATCH_PARENT
//                    gravity = Gravity.END
//                }
//            }
//            contentView.doOnPreDraw {
//                divider.translationX = navBar.width.toFloat()
//            }

            v.updateLayoutParams<CoordinatorLayout.LayoutParams> {
                width = CoordinatorLayout.LayoutParams.WRAP_CONTENT
                height = CoordinatorLayout.LayoutParams.MATCH_PARENT
                gravity = Gravity.START
            }

            v
        } else {
            val v = BottomNavigationView(activity).apply {
                setBackgroundColor(
                    activity.getColorFromAttribute(
                        com.google.android.material.R.attr.colorSurface,
                    ),
                )
                inflateMenu(R.menu.bottom_navigation_menu)
                labelVisibilityMode = LABEL_VISIBILITY_LABELED
            }

            contentView.addView(v)

            v.updateLayoutParams<CoordinatorLayout.LayoutParams> {
                width = CoordinatorLayout.LayoutParams.MATCH_PARENT
                height = CoordinatorLayout.LayoutParams.WRAP_CONTENT
                gravity = Gravity.BOTTOM
            }

            v
        }

        if (navBarChanged) {
            onNavBarChanged()
        }
    }

    fun onInsetsChanged(
        leftInset: Int,
        topInset: Int,
        rightInset: Int,
        bottomInset: Int,
    ) {
        val isRtl = navBar.layoutDirection == FrameLayout.LAYOUT_DIRECTION_RTL

        if (useNavigationRail) {
            navBar.updatePadding(top = topInset, bottom = bottomInset)

            val width = navRailWidth

            if (isRtl) {
                newLeftInset = leftInset
                newRightInset = rightInset + width
            } else {
                newLeftInset = leftInset + width
                newRightInset = rightInset
            }
        } else {
            navBar.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                leftMargin = leftInset
                rightMargin = rightInset
            }
            navBar.updatePadding(bottom = bottomInset)

            newLeftInset = leftInset
            newRightInset = rightInset
        }
    }

    fun updateOpenness(navOpenness: Float) {
        if (!useBottomNavBar) return

        if (useNavigationRail) {
            navBar.translationX = -navOpenness * navBar.width
        } else {
            navBar.translationY = navOpenness * navBar.height
        }
    }

    fun enableBottomNavViewScrolling() {
        if (!useBottomNavBar) return
        if (useNavigationRail) return

        enableBottomNavViewScrolling = true
        navBar.visibility = View.VISIBLE
    }

    fun disableBottomNavViewScrolling() {
        enableBottomNavViewScrolling = false
    }

    fun showBottomNav(supportOpenness: Boolean = false) {
        if (!useBottomNavBar) return
        if (enableBottomNavViewScrolling && navBar.visibility == View.VISIBLE) {
            return
        }

        val finalY =
            if (supportOpenness) {
                bottomNavOpenness
            } else {
                0f
            }

        bottomNavViewOffset.value = 0
        if (navBar.visibility != View.VISIBLE) {
            navBar.visibility = View.VISIBLE
            navBar.translationY =
                navBar.height.toFloat()
            Log.d(TAG, "bottomNavigationView.height: ${navBar.height}")

            navBar.post {
                navBar.animate()
                    .translationY(finalY).duration = 250
            }
        } else {
            if (navBar.translationY == 0f) return

            navBar.animate()
                .translationY(finalY).duration = 250
        }
    }

    fun hideNavBar(animate: Boolean) {
        if (!useBottomNavBar) return

        Log.d(TAG, "bottomNavigationView.height: ${navBar.height}")

        if (useNavigationRail) {
            if (animate) {
                if (navBar.translationX > -navBar.width.toFloat()) {
                    navBar.animate()
                        .translationX(-navBar.height.toFloat())
                        .apply {
                            duration = 250
                        }
                }
            } else {
                navBar.translationX =
                    -navBar.width.toFloat()
            }
        } else {
            if (animate) {
                if (navBar.translationY < navBar.height.toFloat()) {
                    navBar.animate()
                        .translationY(navBar.height.toFloat())
                        .apply {
                            duration = 250
                        }
                }
            } else {
                navBar.translationY =
                    navBar.height.toFloat()
            }
        }
    }

    fun updatePaddingForNavBar(contentContainer: View) {
        if (useNavigationRail) {
            val width = if (navBar.width == 0) {
                activity.getDimen(com.google.android.material.R.dimen.m3_navigation_rail_default_width)
            } else {
                navBar.width
            }

            contentContainer.updatePaddingRelative(start = width)
        } else {
            contentContainer.updatePadding(bottom = bottomNavHeight)
        }
    }

    fun onPreferencesChanged(preferences: Preferences) {
        useBottomNavBar = preferences.useBottomNavBar
        navRailMode = preferences.navigationRailMode
        useCustomNavBar = preferences.useCustomNavBar
        navBarConfig = preferences.navBarConfig

        onWindowSizeChanged()

        if (!useBottomNavBar) {
            navBar.visibility = View.GONE
        } else if (useCustomNavBar) {
            navBar.setTag(R.id.custom_nav_bar, true)
            navBar.menu.apply {
                clear()
                val navBarDestinations = navBarConfig.navBarDestinations
                for (dest in navBarDestinations) {
                    when (dest) {
                        NavBarDestinations.Home -> {
                            add(
                                Menu.NONE,
                                R.id.mainFragment,
                                Menu.NONE,
                                context.getString(R.string.home),
                            ).apply {
                                setIcon(R.drawable.baseline_home_24)
                            }
                        }
                        NavBarDestinations.Saved -> {
                            add(
                                Menu.NONE,
                                R.id.savedFragment,
                                Menu.NONE,
                                context.getString(R.string.saved),
                            ).apply {
                                setIcon(R.drawable.baseline_bookmark_24)
                            }
                        }
                        NavBarDestinations.Search -> {
                            add(
                                Menu.NONE,
                                R.id.searchFragment,
                                Menu.NONE,
                                context.getString(R.string.search),
                            ).apply {
                                setIcon(R.drawable.baseline_search_24)
                            }
                        }
                        NavBarDestinations.History -> {
                            add(
                                Menu.NONE,
                                R.id.historyFragment,
                                Menu.NONE,
                                context.getString(R.string.history),
                            ).apply {
                                setIcon(R.drawable.baseline_history_24)
                            }
                        }
                        NavBarDestinations.Inbox -> {
                            add(
                                Menu.NONE,
                                R.id.inboxTabbedFragment,
                                Menu.NONE,
                                context.getString(R.string.inbox),
                            ).apply {
                                setIcon(R.drawable.baseline_inbox_24)
                            }
                        }
                        NavBarDestinations.None -> {
                        }
                    }
                }
            }
        } else {
            if (navBar.getTag(R.id.custom_nav_bar) == true) {
                navBar.menu.clear()
                navBar.inflateMenu(R.menu.bottom_navigation_menu)
                navBar.setTag(R.id.custom_nav_bar, false)
            }
        }

        if (!useBottomNavBar) {
            navBar.visibility = View.GONE
        }
    }

    enum class WindowSizeClass { COMPACT, MEDIUM, EXPANDED }
}
