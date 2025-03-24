package com.idunnololz.summit.presets

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView.Adapter
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.idunnololz.summit.R
import com.idunnololz.summit.databinding.FragmentPresetsBinding
import com.idunnololz.summit.databinding.ItemPresetsHeaderBinding
import com.idunnololz.summit.util.BaseFragment
import com.idunnololz.summit.util.insetViewAutomaticallyByPadding
import com.idunnololz.summit.util.recyclerView.AdapterHelper
import com.idunnololz.summit.util.setupToolbar

class PresetsFragment : BaseFragment<FragmentPresetsBinding>() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        super.onCreateView(inflater, container, savedInstanceState)

        setBinding(FragmentPresetsBinding.inflate(inflater, container, false))

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val context = requireContext()

        with(binding) {
            requireMainActivity().apply {
                insetViewAutomaticallyByPadding(viewLifecycleOwner, binding.root)
            }

            setupToolbar(toolbar, getString(R.string.presets))

            val adapter = PresetsAdapter()

            recyclerView.layoutManager = LinearLayoutManager(context)
            recyclerView.setHasFixedSize(true)
            recyclerView.adapter = adapter
        }
    }

    private class PresetsAdapter : Adapter<ViewHolder>() {

        private sealed interface Item {
            data object HeaderItem : Item
        }

        private val adapterHelper = AdapterHelper<Item>({ old, new ->
            old::class == new::class && when (old) {
                Item.HeaderItem -> true
            }
        }).apply {
            addItemType(Item.HeaderItem::class, ItemPresetsHeaderBinding::inflate) { item, b, h ->
            }
        }

        init {
            refreshItems()
        }

        override fun getItemViewType(position: Int): Int = adapterHelper.getItemViewType(position)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
            adapterHelper.onCreateViewHolder(parent, viewType)

        override fun getItemCount(): Int = adapterHelper.itemCount

        override fun onBindViewHolder(holder: ViewHolder, position: Int) =
            adapterHelper.onBindViewHolder(holder, position)

        private fun refreshItems() {
            val newItems = mutableListOf<Item>()

            newItems += Item.HeaderItem

            adapterHelper.setItems(newItems, this)
        }
    }
}
