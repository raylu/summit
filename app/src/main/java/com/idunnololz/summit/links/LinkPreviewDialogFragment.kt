package com.idunnololz.summit.links

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.navArgs
import coil.load
import com.idunnololz.summit.R
import com.idunnololz.summit.databinding.DialogFragmentLinkPreviewBinding
import com.idunnololz.summit.util.BaseDialogFragment
import com.idunnololz.summit.util.StatefulData
import com.idunnololz.summit.util.Utils
import com.idunnololz.summit.util.ext.getColorFromAttribute
import com.idunnololz.summit.util.ext.showAllowingStateLoss
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class LinkPreviewDialogFragment : BaseDialogFragment<DialogFragmentLinkPreviewBinding>() {

    companion object {
        fun show(fragmentManager: FragmentManager, url: String) {
            LinkPreviewDialogFragment()
                .apply {
                    arguments = LinkPreviewDialogFragmentArgs(url).toBundle()
                }
                .showAllowingStateLoss(fragmentManager, "LinkPreviewDialogFragment")
        }
    }

    private val args by navArgs<LinkPreviewDialogFragmentArgs>()

    private val viewModel: LinkPreviewViewModel by viewModels()

    override fun onStart() {
        super.onStart()
        val dialog = dialog
        if (dialog != null) {
            val width = ViewGroup.LayoutParams.MATCH_PARENT
            val height = ViewGroup.LayoutParams.WRAP_CONTENT

            val window = checkNotNull(dialog.window)
            window.setLayout(width, height)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        super.onCreateView(inflater, container, savedInstanceState)

        setBinding(DialogFragmentLinkPreviewBinding.inflate(inflater, container, false))

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val context = requireContext()

        if (savedInstanceState == null) {
            viewModel.loadLinkMetadata(args.url)
        }

        with(binding) {
            toolbar.setNavigationIcon(R.drawable.baseline_close_24)
            toolbar.setNavigationOnClickListener {
                dismiss()
            }
            toolbar.setNavigationIconTint(
                context.getColorFromAttribute(io.noties.markwon.R.attr.colorControlNormal),
            )

            url.text = args.url

            viewModel.linkMetadata.observe(viewLifecycleOwner) {
                when (it) {
                    is StatefulData.Error -> loadingView.showDefaultErrorMessageFor(it.error)
                    is StatefulData.Loading -> loadingView.showProgressBar()
                    is StatefulData.NotStarted -> {}
                    is StatefulData.Success -> {
                        loadingView.hideAll()

                        setup(it.data)
                    }
                }
            }
        }
    }

    private fun setup(data: LinkPreviewViewModel.LinkMetadata) = with(binding) {
        val context = requireContext()
        domain.text = data.host
        title.text = data.title

        imageError.visibility = View.GONE
        image.load(data.imageUrl) {
            error(R.drawable.thumbnail_placeholder_16_9)
            listener(
                onError = { _, _ ->
                    imageError.visibility = View.VISIBLE
                    imageError.text = getString(R.string.error_no_preview_image)
                },
            )
        }

        if (data.description.isNullOrBlank()) {
            description.visibility = View.GONE
        } else {
            description.visibility = View.VISIBLE
            description.text = data.description
        }

        launchUrl.setOnClickListener {
            Utils.openExternalLink(context, args.url)
        }
        copyLink.setOnClickListener {
            Utils.copyToClipboard(context, args.url)
            copyLink.text = getString(R.string.copied)
        }
    }
}
