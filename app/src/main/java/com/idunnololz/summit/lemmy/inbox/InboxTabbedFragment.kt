package com.idunnololz.summit.lemmy.inbox

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.core.view.size
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentFactory
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.commit
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager.widget.ViewPager
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import coil.load
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.idunnololz.summit.R
import com.idunnololz.summit.databinding.TabbedFragmentInboxBinding
import com.idunnololz.summit.lemmy.community.ViewPagerController
import com.idunnololz.summit.lemmy.person.PersonTabbedFragment
import com.idunnololz.summit.lemmy.postAndCommentView.PostAndCommentViewBuilder
import com.idunnololz.summit.util.BaseFragment
import com.idunnololz.summit.util.DepthPageTransformer
import com.idunnololz.summit.util.DepthPageTransformer2
import com.idunnololz.summit.util.Item
import com.idunnololz.summit.util.ViewPagerAdapter
import com.idunnololz.summit.util.ext.attachWithAutoDetachUsingLifecycle
import com.idunnololz.summit.util.ext.getColorCompat
import com.idunnololz.summit.util.ext.getDrawableCompat
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class InboxTabbedFragment : BaseFragment<TabbedFragmentInboxBinding>() {

    private var adapter: InboxPagerAdapter? = null

    private val viewModel: InboxTabbedViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        super.onCreateView(inflater, container, savedInstanceState)

        setBinding(TabbedFragmentInboxBinding.inflate(inflater, container, false))

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val context = requireContext()

        val pagerAdapter =
            adapter ?: InboxPagerAdapter(
                context,
                childFragmentManager,
                viewLifecycleOwner.lifecycle,
                onPageCountChanged = { pageCount ->
                    binding.viewPager.isUserInputEnabled = pageCount > 1
                }
            ).also { adapter ->
                adapter.addFrag(
                    InboxFragment::class.java,
                    InboxViewModel.PageType.All.getName(context),
                    InboxFragmentArgs(InboxViewModel.PageType.All).toBundle(),
                )

                this@InboxTabbedFragment.adapter = adapter
            }.apply {
                stateRestorationPolicy = RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY
            }

        binding.viewPager.offscreenPageLimit = 99
        binding.viewPager.isSaveEnabled = false
        binding.viewPager.adapter = pagerAdapter
        binding.viewPager.setPageTransformer(DepthPageTransformer2())
        binding.viewPager.setCurrentItem(viewModel.pagePosition, false)
        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageScrolled(
                position: Int,
                positionOffset: Float,
                positionOffsetPixels: Int
            ) {
                super.onPageScrolled(position, positionOffset, positionOffsetPixels)
                if (!binding.viewPager.isLaidOut) {
                    return
                }
                if (position == 0) {
                    getMainActivity()?.setNavUiOpenness(positionOffset)
                }
            }

            override fun onPageScrollStateChanged(state: Int) {
                super.onPageScrollStateChanged(state)

                if (state == ViewPager2.SCROLL_STATE_IDLE) {
                    if (binding.viewPager.currentItem == 0) {
                        adapter?.removeAllButFirst()

                        getMainActivity()?.setNavUiOpenness(0f)
                    } else {

                        getMainActivity()?.setNavUiOpenness(1f)
                    }
                }
            }
        })

        viewModel.updateUnreadCount()
    }

    override fun onDestroyView() {
        viewModel.pagePosition = binding.viewPager.currentItem
        binding.viewPager.adapter = null

        super.onDestroyView()
    }

    fun openMessage(item: InboxItem, instance: String) {
        adapter?.apply {
            if (this.itemCount > 1) {
                this.removeAllButFirst()
            }
            addFrag(
                MessageFragment::class.java,
                "Message",
                MessageFragmentArgs(item, instance).toBundle(),
            )

            binding.viewPager.post {
                binding.viewPager.currentItem = 1
            }
        }
    }

    fun closeMessage() {
        binding.viewPager.currentItem = 0
    }

    class InboxPagerAdapter(
        private val context: Context,
        fragmentManager: FragmentManager,
        lifecycle: Lifecycle,
        private val onPageCountChanged: (Int) -> Unit,
    ) : FragmentStateAdapter(fragmentManager, lifecycle), TabLayoutMediator.TabConfigurationStrategy {

        private val fragmentFactory: FragmentFactory = fragmentManager.fragmentFactory
        private val items = ArrayList<Item>()

        init {
            lifecycle.addObserver(object : LifecycleEventObserver {
                override fun onStateChanged(
                    source: LifecycleOwner,
                    event: Lifecycle.Event,
                ) {
                    if (event == Lifecycle.Event.ON_DESTROY) {
                        source.lifecycle.removeObserver(this)
                    }
                }
            })
        }

        override fun createFragment(position: Int): Fragment {
            val fragment = fragmentFactory.instantiate(
                context::class.java.classLoader!!,
                items[position].clazz.name,
            )
            fragment.arguments = items[position].args

            // DO NOT DO THIS. New ViewPager2 made this method terribly inconsistent
            // createdFragments.put(position, fragment)

            return fragment
        }

        override fun getItemCount(): Int = items.size

        fun addFrag(
            clazz: Class<*>,
            title: String,
            args: Bundle? = null,
            @DrawableRes drawableRes: Int? = null,
        ) {
            items.add(Item(clazz, args, title, drawableRes))

            onPageCountChanged(items.size)

            notifyItemChanged(items.size - 1)
        }

        fun findIndexOfPage(className: String): Int =
            items.indexOfFirst { it.clazz.name == className }

        fun getIdForPosition(position: Int): String = items[position].clazz.simpleName

        fun getTitleForPosition(position: Int): CharSequence = items[position].title

        fun getClassForPosition(position: Int): Class<*> = items[position].clazz

        override fun onConfigureTab(tab: TabLayout.Tab, position: Int) {
            val item = items[position]
            tab.text = item.title
            if (item.drawable != null) {
                tab.icon = context.getDrawableCompat(item.drawable)?.apply {
                    setTint(context.getColorCompat(R.color.colorTextTitle))
                }
            }
        }

        fun removeAllButFirst() {
            val originalSize = items.size
            val first = items[0]
            items.clear()
            items.add(first)

            onPageCountChanged(items.size)

            notifyItemRangeRemoved(1, originalSize - 1)
        }
    }

}