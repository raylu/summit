package com.idunnololz.summit.lemmy

import android.content.Context
import android.graphics.RectF
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.util.Linkify
import android.util.Log
import android.widget.TextView
import coil.imageLoader
import com.idunnololz.summit.R
import com.idunnololz.summit.spans.SpoilerSpan
import com.idunnololz.summit.util.CoilImagesPlugin
import com.idunnololz.summit.util.ContentUtils.isUrlVideo
import com.idunnololz.summit.util.CustomLinkMovementMethod
import com.idunnololz.summit.util.DefaultLinkLongClickListener
import com.idunnololz.summit.util.LinkUtils
import com.idunnololz.summit.util.ext.getColorCompat
import com.idunnololz.summit.util.ext.getColorFromAttribute
import com.idunnololz.summit.util.markwon.DetailsTagHandler
import com.idunnololz.summit.util.markwon.postProcessDetails
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.MarkwonPlugin
import io.noties.markwon.core.MarkwonTheme
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.html.span.SuperScriptSpan
import io.noties.markwon.linkify.LinkifyPlugin
import io.noties.markwon.simple.ext.SimpleExtPlugin
import java.util.Locale
import java.util.regex.Matcher
import java.util.regex.Pattern

object LemmyTextHelper {

    private const val TAG = "LemmyTextHelper"

    private var markwon: Markwon? = null

    var autoLinkPhoneNumbers: Boolean = true

    fun bindText(
        textView: TextView,
        text: String,
        instance: String,
        queryHighlight: String? = null,

        onImageClick: (url: String) -> Unit,
        onVideoClick: (url: String) -> Unit,
        onPageClick: (PageRef) -> Unit,
        onLinkLongClick: (url: String, text: String) -> Unit,
    ) {
        bindLemmyText(textView, text, instance, queryHighlight)
        textView.movementMethod = CustomLinkMovementMethod().apply {
            onLinkClickListener = object : CustomLinkMovementMethod.OnLinkClickListener {
                override fun onClick(
                    textView: TextView,
                    url: String,
                    text: String,
                    rect: RectF,
                ): Boolean {
                    val pageRef = LinkResolver.parseUrl(url, instance)

                    if (pageRef != null) {
                        onPageClick(pageRef)
                    }
                    return pageRef != null
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
    }

    private fun bindLemmyText(textView: TextView, text: String, instance: String, queryHighlight: String?) {
        getMarkwon(textView.context).let {
            val spanned = SpannableStringBuilder(it.toMarkdown(text))
            postProcessDetails(spanned, textView)

            if (queryHighlight != null) {
                val highlightColor = textView.context.getColorFromAttribute(
                    androidx.appcompat.R.attr.colorPrimary)
                val queryLength = queryHighlight.length
                var curStartIndex = 0
                while (true) {
                    val index = spanned.indexOf(queryHighlight, curStartIndex, ignoreCase = true)

                    if (index < 0) {
                        break
                    }

                    spanned.setSpan(
                        BackgroundColorSpan(highlightColor), index, index + queryLength,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    curStartIndex = index + queryLength
                }
            }

            it.setParsedMarkdown(textView, spanned)
        }
    }

    private class LemmyPlugin(
        private val context: Context,
    ) : AbstractMarkwonPlugin() {

        /**
         * Combination of 4 regexes "or'd" (|) together.
         * 1) Matches against words where the first character is a caret. Eg. '^hello'
         * 2) Matches against spoiler blocks
         * 3) Matches against poorly formatted header tags. Eg. `##Asdf` (proper would be `## Asdf`)
         * 4) Matches against full community names (!a@b.com)
         */
        private val largeRegex = Pattern.compile("""\^(\S+)|:::\s*spoiler\s+(.*)\n((?:.*\n*)*?)(:::\s+\n|${'$'})\s*|(?m)^(#+)(\S*.*)${'$'}|(!|[cC]/|@|[uU]/)([^@\s]+)@([^@\s]+\.[^@\s]+)""")

        private fun processAll(s: String): String {
            val matcher = largeRegex.matcher(s)
            val sb = StringBuffer()
            while (matcher.find()) {
                val g1 = matcher.group(1)
                if (g1 != null && !g1.endsWith("^")) {
                    matcher.appendReplacement(sb, "^${matcher.group(1)}^")
                    continue
                }

                val spoilerTitle: String? = matcher.group(2)?.trim()
                val spoilerText: String? = matcher.group(3)?.trim()
                if (!spoilerTitle.isNullOrBlank() && !spoilerText.isNullOrBlank()) {
                    matcher.appendReplacement(
                        sb,
                        "<br><details><summary>$spoilerTitle</summary>$spoilerText</details>",
                    )
                    continue
                }

                val formattingChar = matcher.group(5)?.trim()
                val rest = matcher.group(6)?.trim()
                if (formattingChar != null && rest != null) {
                    matcher.appendReplacement(
                        sb,
                        Matcher.quoteReplacement("$formattingChar $rest"),
                    )
                    Log.d(TAG, "Fixed ${"$formattingChar $rest"}")
                }

                val referenceTypeToken = matcher.group(7)
                val name = matcher.group(8)
                val instance = matcher.group(9)

                if (referenceTypeToken != null &&
                    name != null &&
                    instance != null &&
                    !instance.contains("]") /* make sure we are not within a link def */
                ) {
                    when (referenceTypeToken.lowercase(Locale.US)) {
                        "!", "c/" -> {
                            val communityRef = CommunityRef.CommunityRefByName(name, instance)

                            matcher.appendReplacement(
                                sb,
                                "[${matcher.group(0)}](${LinkUtils.getLinkForCommunity(communityRef)})",
                            )
                        }
                        "@", "u/" -> {
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

        override fun processMarkdown(markdown: String): String =
            processAll(markdown)

        override fun configureTheme(builder: MarkwonTheme.Builder) {
            builder.blockQuoteColor(context.getColorCompat(R.color.colorQuoteLine))
                .thematicBreakColor(context.getColorCompat(R.color.colorQuoteLine))
                .headingBreakHeight(0)
        }
    }

    fun resetMarkwon(context: Context) {
        markwon = createMarkwon(context)
    }

    private fun getMarkwon(context: Context) =
        markwon ?: createMarkwon(context).also {
            markwon = it
        }

    private fun createMarkwon(context: Context): Markwon =
        Markwon.builder(context)
            .usePlugin(CoilImagesPlugin.create(context, context.applicationContext.imageLoader))
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
            .usePlugin(HtmlPlugin.create())
            .usePlugin(
                object : AbstractMarkwonPlugin() {
                    override fun configure(registry: MarkwonPlugin.Registry) {
                        registry.require(HtmlPlugin::class.java) {
                            it.addHandler(DetailsTagHandler())
                        }
                    }
                },
            )
            .usePlugin(
                SimpleExtPlugin.create().apply {
                    addExtension(1, '^') { _, _ ->
                        SuperScriptSpan()
                    }
                    addExtension(2, '+') { configuration, _ ->
                        configuration.theme()

                        val spoilerSpan = SpoilerSpan(
                            context.getColorCompat(R.color.colorTextTitle),
                            context.getColorCompat(R.color.colorSpoilerRevealed),
                            context.getColorCompat(R.color.colorTextTitle),
                        )

                        spoilerSpan
                    }
                },
            )
            .build()
}
