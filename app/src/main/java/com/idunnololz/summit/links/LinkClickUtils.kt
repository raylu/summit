package com.idunnololz.summit.links

import android.content.Context
import androidx.fragment.app.FragmentManager
import com.idunnololz.summit.MainApplication
import com.idunnololz.summit.links.PreviewLinkOptions.PreviewAllLinks
import com.idunnololz.summit.links.PreviewLinkOptions.PreviewNoLinks
import com.idunnololz.summit.util.BaseActivity
import com.idunnololz.summit.util.BaseFragment
import com.idunnololz.summit.util.Utils

fun BaseFragment<*>.onLinkClick(url: String, text: String?, linkType: LinkType) {
    onLinkClick(
        context ?: return,
        (activity?.application as? MainApplication) ?: return,
        childFragmentManager,
        url,
        text,
        linkType,
    )
}

fun BaseActivity.onLinkClick(url: String, text: String?, linkType: LinkType) {
    onLinkClick(
        this,
        (application as? MainApplication) ?: return,
        supportFragmentManager,
        url,
        text,
        linkType,
    )
}

fun onLinkClick(
    context: Context,
    application: MainApplication,
    fragmentManager: FragmentManager,
    url: String,
    text: String?,
    linkType: LinkType,
) {
    val preferences = application.preferences

    when (preferences.previewLinks) {
        PreviewNoLinks -> {
            Utils.openExternalLink(context, url)
        }
        PreviewAllLinks -> {
            LinkPreviewDialogFragment.show(fragmentManager, url)
        }
        else -> {
            if (linkType == LinkType.Text) {
                LinkPreviewDialogFragment.show(fragmentManager, url)
            } else {
                Utils.openExternalLink(context, url)
            }
        }
    }
}

/**
 * The location of the link. Special logic may be triggered based on where the click was.
 */
enum class LinkType {
    Text,

    /**
     * Eg. the user tapped on a button that said "Open link"
     */
    Action,

    /**
     * Eg. the user tapped on a special view with an image and other information.
     */
    Rich,
}

object PreviewLinkOptions {
    const val PreviewTextLinks = 0
    const val PreviewNoLinks = 1
    const val PreviewAllLinks = 2
}
