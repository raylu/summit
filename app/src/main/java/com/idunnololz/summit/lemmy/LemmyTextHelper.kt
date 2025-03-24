package com.idunnololz.summit.lemmy

import android.content.Context
import android.graphics.RectF
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.URLSpan
import android.text.util.Linkify
import android.util.Log
import android.widget.TextView
import androidx.core.text.getSpans
import coil3.imageLoader
import coil3.request.Disposable
import coil3.request.ImageRequest
import coil3.request.allowHardware
import com.idunnololz.summit.R
import com.idunnololz.summit.lemmy.post.QueryMatchHelper.HighlightTextData
import com.idunnololz.summit.links.LinkContext
import com.idunnololz.summit.links.LinkResolver
import com.idunnololz.summit.preferences.Preferences
import com.idunnololz.summit.util.ContentUtils.isUrlImage
import com.idunnololz.summit.util.ContentUtils.isUrlVideo
import com.idunnololz.summit.util.CustomLinkMovementMethod
import com.idunnololz.summit.util.DefaultLinkLongClickListener
import com.idunnololz.summit.util.ImagesAsLinksPlugin
import com.idunnololz.summit.util.LinkUtils
import com.idunnololz.summit.util.coil.CoilImagesPlugin
import com.idunnololz.summit.util.coil.CoilImagesPlugin.CoilStore
import com.idunnololz.summit.util.ext.getColorCompat
import com.idunnololz.summit.util.ext.getColorFromAttribute
import com.idunnololz.summit.util.markwon.SpoilerPlugin
import com.idunnololz.summit.util.markwon.SubScriptSpan
import com.idunnololz.summit.util.markwon.SummitInlineParser
import com.idunnololz.summit.util.markwon.SuperScriptSpan
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.core.MarkwonTheme
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.image.AsyncDrawable
import io.noties.markwon.linkify.LinkifyPlugin
import io.noties.markwon.simple.ext.SimpleExtPlugin
import java.util.Locale
import java.util.regex.Pattern
import javax.inject.Inject
import javax.inject.Singleton
import org.commonmark.parser.Parser

@Singleton
class LemmyTextHelper @Inject constructor(
    private val preferences: Preferences,
) {

    companion object {
        private const val TAG = "LemmyTextHelper"
    }

    private var markwon: Markwon? = null
    private var noMediaMarkwon: Markwon? = null

    private val autoLinkPhoneNumbers: Boolean
        get() = preferences.autoLinkPhoneNumbers
    private val autoLinkIpAddresses: Boolean
        get() = preferences.autoLinkIpAddresses

    private val ipPattern = Pattern.compile(
        "((25[0-5]|2[0-4][0-9]|[0-1][0-9]{2}|[1-9][0-9]|[1-9])\\.(25[0-5]|2[0-4]" +
            "[0-9]|[0-1][0-9]{2}|[1-9][0-9]|[1-9]|0)\\.(25[0-5]|2[0-4][0-9]|[0-1]" +
            "[0-9]{2}|[1-9][0-9]|[1-9]|0)\\.(25[0-5]|2[0-4][0-9]|[0-1][0-9]{2}" +
            "|[1-9][0-9]|[0-9]))",
    )

    fun bindText(
        textView: TextView,
        text: String,
        instance: String,
        spannedText: Spanned? = null,
        highlight: HighlightTextData? = null,
        showMediaAsLinks: Boolean = false,
        onImageClick: (url: String) -> Unit,
        onVideoClick: (url: String) -> Unit,
        onPageClick: (PageRef) -> Unit,
        onLinkClick: (url: String, text: String, linkContext: LinkContext) -> Unit,
        onLinkLongClick: (url: String, text: String) -> Unit,
    ): Spanned? {
        val spannable = bindLemmyText(textView, text, spannedText, highlight, showMediaAsLinks)

        textView.movementMethod = CustomLinkMovementMethod().apply {
            onLinkClickListener = object : CustomLinkMovementMethod.OnLinkClickListener {
                override fun onClick(
                    textView: TextView,
                    url: String,
                    text: String,
                    rect: RectF,
                ): Boolean {
                    val pageRef = LinkResolver.parseUrl(url, instance)

                    if (isUrlVideo(url)) {
                        onVideoClick(url)
                    } else if (isUrlImage(url)) {
                        onImageClick(url)
                    } else if (pageRef != null) {
                        onPageClick(pageRef)
                    } else {
                        onLinkClick(url, text, LinkContext.Text)
                    }
                    return true
                }
            }
            onLinkLongClickListener = DefaultLinkLongClickListener(textView.context, onLinkLongClick)
            this.onImageClickListener = { url ->
                if (isUrlVideo(url)) {
                    onVideoClick(url)
                } else {
                    onImageClick(url)
                }
            }
        }

        return spannable
    }

    fun getSpannable(context: Context, text: String): Spanned = try {
        val markwon = getMarkwon(context)

        markwon.toMarkdown(text)
    } catch (e: Exception) {
        // java.lang.IndexOutOfBoundsException: setSpan (2445 ... 2449) ends beyond length 2448

        Log.e(TAG, "Error parsing text", e)

        SpannableStringBuilder(text)
    }

    @Suppress("ktlint:standard:backing-property-naming")
    private fun bindLemmyText(
        textView: TextView,
        text: String,
        spannableText: Spanned? = null,
        highlight: HighlightTextData?,
        showMediaAsLinks: Boolean,
    ): Spanned? {
        val markwon = if (showMediaAsLinks) {
            getNoMediaMarkwon(textView.context)
        } else {
            getMarkwon(textView.context)
        }

        return try {
            val _spanned = spannableText ?: markwon.toMarkdown(text)
            val spanned = SpannableStringBuilder(_spanned)

            if (!autoLinkIpAddresses) {
                val spans = spanned.getSpans<URLSpan>(0, spanned.length)
                for (s in spans) {
                    val linkText = spanned.subSequence(
                        spanned.getSpanStart(s),
                        spanned.getSpanEnd(s),
                    )

                    val matcher = ipPattern.matcher(linkText)
                    if (matcher.find() &&
                        matcher.group().length == linkText.length &&
                        "http://$linkText" == s.url
                    ) {
                        spanned.removeSpan(s)
                    }
                }
            }

            if (highlight != null) {
                val highlightColor = textView.context.getColorFromAttribute(
                    com.google.android.material.R.attr.colorSurfaceInverse,
                )
                val highlightTextColor = textView.context.getColorFromAttribute(
                    com.google.android.material.R.attr.colorOnSurfaceInverse,
                )
                val currentQueryColor = textView.context.getColorFromAttribute(
                    androidx.appcompat.R.attr.colorPrimary,
                )
                val queryHighlight = highlight.query
                val queryLength = queryHighlight.length
                var curStartIndex = 0
                var matchIndex = 0
                while (true) {
                    val index =
                        spanned.indexOf(queryHighlight, curStartIndex, ignoreCase = true)

                    if (index < 0) {
                        break
                    }

                    if (highlight.matchIndex == matchIndex) {
                        spanned.setSpan(
                            BackgroundColorSpan(currentQueryColor),
                            index,
                            index + queryLength,
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
                        )
                    } else {
                        spanned.setSpan(
                            BackgroundColorSpan(highlightColor),
                            index,
                            index + queryLength,
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
                        )
                        spanned.setSpan(
                            ForegroundColorSpan(highlightTextColor),
                            index,
                            index + queryLength,
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
                        )
                    }
                    curStartIndex = index + queryLength

                    matchIndex++
                }
            }

            markwon.setParsedMarkdown(textView, spanned)

            _spanned
        } catch (e: Exception) {
            Log.w(TAG, "Error parsing markdown. Falling back to plain text", e)
            textView.text = text

            null
        }
    }

    private class LemmyPlugin(
        private val context: Context,
    ) : AbstractMarkwonPlugin() {

        companion object {
            private const val STYLE_BLOCK = 1
            private const val STYLE_INLINE = 2
        }

        /**
         * Matches against full community names (!a@b.com)
         */
        private val largeRegex = Pattern.compile(
            """(]\()?(!|/?[cC]/|@|/?[uU]/)([^@\s]+)@([^@\s]+\.[^@\s)]*\w)""",
        )

        private fun processAll(s: String): String {
            val s = fixTicks(s)
            val matcher = largeRegex.matcher(s)
            val sb = StringBuffer()
            while (matcher.find()) {
                val linkStart = matcher.group(1)
                val referenceTypeToken = matcher.group(2)
                val name = matcher.group(3)
                val instance = matcher.group(4)

                if (linkStart == null &&
                    referenceTypeToken != null &&
                    name != null &&
                    instance != null &&
                    !name.contains("]") &&
                    !instance.contains("]") /* make sure we are not within a link def */
                ) {
                    when (referenceTypeToken.lowercase(Locale.US)) {
                        "!", "c/", "/c/" -> {
                            val communityRef = CommunityRef.CommunityRefByName(name, instance)

                            matcher.appendReplacement(
                                sb,
                                "[${matcher.group(
                                    0,
                                )}](${LinkUtils.getLinkForCommunity(communityRef)})",
                            )
                        }
                        "@", "u/", "/u/" -> {
                            val url = LinkUtils.getLinkForPerson(instance = instance, name = name)
                            matcher.appendReplacement(
                                sb,
                                "[${matcher.group(0)}]($url)",
                            )
                        }
                    }
                }
            }
            matcher.appendTail(sb)
            return sb.toString()
        }

        private fun fixTicks(s: String): CharSequence {
            if (!s.contains('`')) {
                return s
            }

            val sb = StringBuilder()
            var lastBlockType: Int = 0
            var index = 0
            while (true) {
                if (index == s.length) {
                    break
                }

                var currentBlockType: Int = 0
                val c = s[index]
                if (c == '`') {
                    if (s.getOrNull(index + 1) == '`' && s.getOrNull(index + 2) == '`') {
                        currentBlockType = STYLE_BLOCK
                        index += 2
                        sb.append("```")
                    } else {
                        currentBlockType = STYLE_INLINE
                        sb.append("`")
                    }
                } else {
                    if (lastBlockType == STYLE_INLINE) {
                        if (c == '\n') {
                            sb.append(" ")
                        } else {
                            sb.append(c)
                        }
                    } else {
                        sb.append(c)
                    }
                }

                if (lastBlockType == 0) {
                    lastBlockType = currentBlockType
                } else if (lastBlockType == currentBlockType) {
                    lastBlockType = 0
                }

                index++
            }

            return sb.toString()
        }

        override fun processMarkdown(markdown: String): String = processAll(markdown)

        override fun configureTheme(builder: MarkwonTheme.Builder) {
            builder.blockQuoteColor(context.getColorCompat(R.color.colorQuoteLine))
                .thematicBreakColor(context.getColorCompat(R.color.colorQuoteLine))
                .headingBreakHeight(0)
        }
    }

    fun resetMarkwon(context: Context) {
        markwon = createMarkwon(context, inlineMedia = true)
        noMediaMarkwon = null
    }

    private fun getMarkwon(context: Context) =
        markwon ?: createMarkwon(context.applicationContext, inlineMedia = true).also {
            markwon = it
        }

    private fun getNoMediaMarkwon(context: Context) =
        noMediaMarkwon ?: createMarkwon(context.applicationContext, inlineMedia = false).also {
            noMediaMarkwon = it
        }

    private fun createMarkwon(context: Context, inlineMedia: Boolean): Markwon =
        Markwon.builder(context)
            .apply {
                if (inlineMedia) {
                    usePlugin(
                        CoilImagesPlugin(
                            context,
                            object : CoilStore {
                                override fun load(drawable: AsyncDrawable): ImageRequest {
                                    return ImageRequest.Builder(context)
                                        // Needed for the "take screenshot" feature
                                        .allowHardware(false)
                                        .data(drawable.destination)
                                        .build()
                                }

                                override fun cancel(disposable: Disposable) {
                                    disposable.dispose()
                                }
                            },
                            context.applicationContext.imageLoader,
                        ),
                    )
                } else {
                    usePlugin(ImagesAsLinksPlugin())
                }
            }
            .usePlugin(
                LinkifyPlugin.create(
                    if (autoLinkPhoneNumbers) {
                        Linkify.EMAIL_ADDRESSES or Linkify.PHONE_NUMBERS or Linkify.WEB_URLS
                    } else {
                        Linkify.EMAIL_ADDRESSES or Linkify.WEB_URLS
                    },
                ),
            )
            .usePlugin(TablePlugin.create(context))
            .usePlugin(LemmyPlugin(context))
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(SpoilerPlugin())
            .usePlugin(
                object : AbstractMarkwonPlugin() {
                    override fun configureParser(builder: Parser.Builder) {
                        super.configureParser(builder)
                        builder.inlineParserFactory {
                            SummitInlineParser(it)
                        }
                    }
                },
            )
            .usePlugin(
                SimpleExtPlugin.create().apply {
                    addExtension(1, '^') { _, _ ->
                        SuperScriptSpan()
                    }
                },
            )
            .usePlugin(
                SimpleExtPlugin.create().apply {
                    addExtension(1, '~') { _, _ ->
                        SubScriptSpan()
                    }
                },
            )
            .build()
}
