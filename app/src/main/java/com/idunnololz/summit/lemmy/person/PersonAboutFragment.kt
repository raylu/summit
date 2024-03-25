package com.idunnololz.summit.lemmy.person

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.idunnololz.summit.R
import com.idunnololz.summit.databinding.FragmentPersonAboutBinding
import com.idunnololz.summit.lemmy.LemmyTextHelper
import com.idunnololz.summit.links.onLinkClick
import com.idunnololz.summit.preview.VideoType
import com.idunnololz.summit.util.BaseFragment
import com.idunnololz.summit.util.StatefulData
import com.idunnololz.summit.util.dateStringToPretty
import com.idunnololz.summit.util.showMoreLinkOptions

class PersonAboutFragment : BaseFragment<FragmentPersonAboutBinding>() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        super.onCreateView(inflater, container, savedInstanceState)

        setBinding(FragmentPersonAboutBinding.inflate(inflater, container, false))

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val parentFragment = parentFragment as PersonTabbedFragment

        with(binding) {
            parentFragment.viewModel.personData.observe(viewLifecycleOwner) {
                when (it) {
                    is StatefulData.Error -> {
                        swipeRefreshLayout.isRefreshing = false
                        loadingView.showDefaultErrorMessageFor(it.error)
                    }
                    is StatefulData.Loading -> {
                        loadingView.showProgressBar()
                    }
                    is StatefulData.NotStarted -> {}
                    is StatefulData.Success -> {
                        swipeRefreshLayout.isRefreshing = false
                        loadingView.hideAll()

                        setup(it.data)
                    }
                }
            }

            swipeRefreshLayout.setOnRefreshListener {
                parentFragment.viewModel.fetchPage(
                    pageIndex = 0,
                    isPeronInfoFetch = true,
                    force = true,
                )
            }
        }
    }

    private fun setup(data: PersonTabbedViewModel.PersonDetailsData) {
        if (!isBindingAvailable()) return

        val context = requireContext()

        val parentFragment = parentFragment as PersonTabbedFragment
        val personView = data.personView

        LemmyTextHelper.bindText(
            binding.bio,
            personView.person.bio
                ?: buildString {
                    append("*")
                    append(getString(R.string.blank))
                    append("*")
                },
            parentFragment.viewModel.instance,
            onImageClick = { url ->
                getMainActivity()?.openImage(
                    sharedElement = null,
                    appBar = parentFragment.binding.appBar,
                    title = null,
                    url = url,
                    mimeType = null,
                )
            },
            onVideoClick = { url ->
                getMainActivity()?.openVideo(url, VideoType.Unknown, null)
            },
            onPageClick = {
                getMainActivity()?.launchPage(it)
            },
            onLinkClick = { url, text, linkType ->
                onLinkClick(url, text, linkType)
            },
            onLinkLongClick = { url, text ->
                getMainActivity()?.showMoreLinkOptions(url, text)
            },
        )

        binding.accountInfo.text = buildString {
            append(
                if (personView.person.admin) {
                    getString(R.string.admin)
                } else if (personView.person.bot_account) {
                    getString(R.string.bot)
                } else {
                    getString(R.string.normal)
                },
            )

            val status =
                if (personView.person.banned) {
                    if (personView.person.ban_expires != null) {
                        getString(
                            R.string.banned_until_format,
                            dateStringToPretty(context, personView.person.ban_expires),
                        )
                    } else {
                        getString(R.string.banned)
                    }
                } else if (personView.person.deleted) {
                    getString(R.string.deleted)
                } else {
                    null
                }

            if (status != null) {
                append(" (")
                append(status)
                append(")")
            }
        }
    }
}
