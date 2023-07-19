package com.idunnololz.summit.settings

import android.content.Context
import com.idunnololz.summit.R
import com.idunnololz.summit.preferences.CommentsThreadStyle
import com.idunnololz.summit.preferences.PostGestureAction
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MainSettings @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val settingTheme = BasicSettingItem(
        R.drawable.baseline_palette_24,
        context.getString(R.string.theme),
        context.getString(R.string.theme_settings_desc)
    )
    val settingViewType = BasicSettingItem(
        R.drawable.baseline_view_agenda_24,
        context.getString(R.string.view_type),
        context.getString(R.string.view_type_settings_desc)
    )
    val settingPostAndComment = BasicSettingItem(
        R.drawable.baseline_mode_comment_24,
        context.getString(R.string.post_and_comments),
        context.getString(R.string.post_and_comments_settings_desc)
    )
    val settingLemmyWeb = BasicSettingItem(
        R.drawable.ic_lemmy_24,
        context.getString(R.string.lemmy_web_preferences),
        context.getString(R.string.lemmy_web_preferences_desc)
    )
    val settingGestures = BasicSettingItem(
        R.drawable.baseline_gesture_24,
        context.getString(R.string.gestures),
        context.getString(R.string.gestures_desc)
    )
    val settingHistory = BasicSettingItem(
        R.drawable.baseline_history_24,
        context.getString(R.string.history),
        context.getString(R.string.history_setting_desc)
    )
    val settingCache = BasicSettingItem(
        R.drawable.baseline_cached_24,
        context.getString(R.string.cache),
        context.getString(R.string.cache_info_and_preferences)
    )
    val settingHiddenPosts = BasicSettingItem(
        R.drawable.baseline_hide_24,
        context.getString(R.string.hidden_posts),
        context.getString(R.string.hidden_posts_desc)
    )
    val settingPostList = BasicSettingItem(
        R.drawable.baseline_pages_24,
        context.getString(R.string.post_list),
        context.getString(R.string.setting_post_list_desc)
    )
    val settingAbout = BasicSettingItem(
        R.drawable.outline_info_24,
        context.getString(R.string.about_summit),
        context.getString(R.string.about_summit_desc)
    )
    val settingSummitCommunity = BasicSettingItem(
        R.drawable.ic_logo_mono_24,
        context.getString(R.string.c_summit),
        context.getString(R.string.summit_community_desc)
    )
}

@Singleton
class LemmyWebSettings @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    val instanceSetting = TextValueSettingItem(
        title = context.getString(R.string.instance),
        supportsRichText = false,
        isEnabled = false,
    )

    val displayNameSetting = TextValueSettingItem(
        title = context.getString(R.string.display_name),
        supportsRichText = false
    )

    val bioSetting = TextValueSettingItem(
        title = context.getString(R.string.biography),
        supportsRichText = true
    )

    val emailSetting = TextValueSettingItem(
        title = context.getString(R.string.email),
        supportsRichText = false
    )

    val matrixSetting = TextValueSettingItem(
        title = context.getString(R.string.matrix_user),
        supportsRichText = false,
        hint = "@user:example.com"
    )

    val avatarSetting = ImageValueSettingItem(
        title = context.getString(R.string.profile_image),
        description = null,
        isSquare = true,
    )

    val bannerSetting = ImageValueSettingItem(
        title = context.getString(R.string.banner_image),
        description = null,
        isSquare = false,
    )

    val defaultSortType = RadioGroupSettingItem(
        null,
        context.getString(R.string.default_sort_type),
        null,
        listOf(
            RadioGroupSettingItem.RadioGroupOption(
                R.id.sort_order_active,
                context.getString(R.string.sort_order_active),
                null,
                null,
            ),
            RadioGroupSettingItem.RadioGroupOption(
                R.id.sort_order_hot,
                context.getString(R.string.sort_order_hot),
                null,
                null,
            ),
            RadioGroupSettingItem.RadioGroupOption(
                R.id.sort_order_new,
                context.getString(R.string.sort_order_new),
                null,
                null,
            ),
            RadioGroupSettingItem.RadioGroupOption(
                R.id.sort_order_old,
                context.getString(R.string.sort_order_old),
                null,
                null,
            ),
            RadioGroupSettingItem.RadioGroupOption(
                R.id.sort_order_most_comments,
                context.getString(R.string.sort_order_most_comments),
                null,
                null,
            ),
            RadioGroupSettingItem.RadioGroupOption(
                R.id.sort_order_new_comments,
                context.getString(R.string.sort_order_new_comments),
                null,
                null,
            ),

            RadioGroupSettingItem.RadioGroupOption(
                R.id.sort_order_top_last_hour,
                context.getString(R.string.time_frame_last_hour),
                null,
                null,
            ),
            RadioGroupSettingItem.RadioGroupOption(
                R.id.sort_order_top_last_six_hour,
                context.getString(R.string.time_frame_last_hours_format, "6"),
                null,
                null,
            ),
            RadioGroupSettingItem.RadioGroupOption(
                R.id.sort_order_top_last_twelve_hour,
                context.getString(R.string.time_frame_last_hours_format, "12"),
                null,
                null,
            ),
            RadioGroupSettingItem.RadioGroupOption(
                R.id.sort_order_top_day,
                context.getString(R.string.time_frame_today),
                null,
                null,
            ),
            RadioGroupSettingItem.RadioGroupOption(
                R.id.sort_order_top_week,
                context.getString(R.string.time_frame_this_week),
                null,
                null,
            ),
            RadioGroupSettingItem.RadioGroupOption(
                R.id.sort_order_top_month,
                context.getString(R.string.time_frame_this_month),
                null,
                null,
            ),
            RadioGroupSettingItem.RadioGroupOption(
                R.id.sort_order_top_last_three_month,
                context.getString(R.string.time_frame_last_months_format, "3"),
                null,
                null,
            ),
            RadioGroupSettingItem.RadioGroupOption(
                R.id.sort_order_top_last_six_month,
                context.getString(R.string.time_frame_last_months_format, "6"),
                null,
                null,
            ),
            RadioGroupSettingItem.RadioGroupOption(
                R.id.sort_order_top_last_nine_month,
                context.getString(R.string.time_frame_last_months_format, "9"),
                null,
                null,
            ),
            RadioGroupSettingItem.RadioGroupOption(
                R.id.sort_order_top_year,
                context.getString(R.string.time_frame_this_year),
                null,
                null,
            ),
            RadioGroupSettingItem.RadioGroupOption(
                R.id.sort_order_top_all_time,
                context.getString(R.string.time_frame_all_time),
                null,
                null,
            ),
        ),
    )

    val showNsfwSetting = OnOffSettingItem(
        context.getString(R.string.show_nsfw),
        null,
    )

    val showReadPostsSetting = OnOffSettingItem(
        context.getString(R.string.show_read_posts),
        null,
    )

    val botAccountSetting = OnOffSettingItem(
        context.getString(R.string.bot_account),
        null,
    )

    val showBotAccountsSetting = OnOffSettingItem(
        context.getString(R.string.show_bot_accounts),
        null,
    )

    val sendNotificationsToEmailSetting = OnOffSettingItem(
        context.getString(R.string.send_notifications_to_email),
        null,
    )

}

@Singleton
class GestureSettings @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val postGestureActionOptions =
        listOf(
            RadioGroupSettingItem.RadioGroupOption(
                PostGestureAction.Upvote,
                context.getString(R.string.upvote),
                null,
                R.drawable.baseline_arrow_upward_24,
            ),
            RadioGroupSettingItem.RadioGroupOption(
                PostGestureAction.Downvote,
                context.getString(R.string.downvote),
                null,
                R.drawable.baseline_arrow_downward_24,
            ),
            RadioGroupSettingItem.RadioGroupOption(
                PostGestureAction.Reply,
                context.getString(R.string.reply),
                null,
                R.drawable.baseline_reply_24,
            ),
            RadioGroupSettingItem.RadioGroupOption(
                PostGestureAction.MarkAsRead,
                context.getString(R.string.mark_as_read),
                null,
                R.drawable.baseline_check_24,
            ),
            RadioGroupSettingItem.RadioGroupOption(
                PostGestureAction.Hide,
                context.getString(R.string.hide_post),
                null,
                R.drawable.baseline_hide_24,
            ),
            RadioGroupSettingItem.RadioGroupOption(
                PostGestureAction.Bookmark,
                context.getString(R.string.bookmark),
                null,
                R.drawable.baseline_bookmark_add_24,
            ),
        )


    val postGestureAction1 = RadioGroupSettingItem(
        null,
        context.getString(R.string.gesture_action_1),
        null,
        postGestureActionOptions,
    )

    val postGestureAction2 = RadioGroupSettingItem(
        null,
        context.getString(R.string.gesture_action_2),
        null,
        postGestureActionOptions,
    )

    val postGestureAction3 = RadioGroupSettingItem(
        null,
        context.getString(R.string.gesture_action_3),
        null,
        postGestureActionOptions,
    )
}

@Singleton
class PostAndCommentsSettings @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    val showCommentActions = OnOffSettingItem(
        context.getString(R.string.show_comment_actions),
        null,
    )

    val commentsThreadStyle = RadioGroupSettingItem(
        null,
        context.getString(R.string.comments_thread_style),
        null,
        listOf(
            RadioGroupSettingItem.RadioGroupOption(
                CommentsThreadStyle.Modern,
                context.getString(R.string.modern),
                null,
                null,
            ),
            RadioGroupSettingItem.RadioGroupOption(
                CommentsThreadStyle.Legacy,
                context.getString(R.string.classic),
                null,
                null,
            ),
        ),
    )

    val tapCommentToCollapse = OnOffSettingItem(
        context.getString(R.string.tap_comment_to_collapse),
        context.getString(R.string.tap_comment_to_collapse_desc),
    )
}
