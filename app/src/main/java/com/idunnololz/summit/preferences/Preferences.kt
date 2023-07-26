package com.idunnololz.summit.preferences

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.idunnololz.summit.lemmy.CommentsSortOrder
import com.idunnololz.summit.lemmy.CommunityRef
import com.idunnololz.summit.lemmy.CommunitySortOrder
import com.idunnololz.summit.lemmy.community.CommunityLayout
import com.idunnololz.summit.lemmy.postListView.PostAndCommentsUiConfig
import com.idunnololz.summit.lemmy.postListView.PostInListUiConfig
import com.idunnololz.summit.lemmy.postListView.getDefaultPostAndCommentsUiConfig
import com.idunnololz.summit.lemmy.postListView.getDefaultPostUiConfig
import com.idunnololz.summit.util.PreferenceUtil
import com.idunnololz.summit.util.PreferenceUtil.KEY_BASE_THEME
import com.idunnololz.summit.util.PreferenceUtil.KEY_BLUR_NSFW_POSTS
import com.idunnololz.summit.util.PreferenceUtil.KEY_COMMENT_GESTURE_ACTION_1
import com.idunnololz.summit.util.PreferenceUtil.KEY_COMMENT_GESTURE_ACTION_2
import com.idunnololz.summit.util.PreferenceUtil.KEY_COMMENT_GESTURE_ACTION_3
import com.idunnololz.summit.util.PreferenceUtil.KEY_COMMENT_THREAD_STYLE
import com.idunnololz.summit.util.PreferenceUtil.KEY_DEFAULT_COMMENTS_SORT_ORDER
import com.idunnololz.summit.util.PreferenceUtil.KEY_DEFAULT_COMMUNITY_SORT_ORDER
import com.idunnololz.summit.util.PreferenceUtil.KEY_GLOBAL_FONT_SIZE
import com.idunnololz.summit.util.PreferenceUtil.KEY_HIDE_COMMENT_ACTIONS
import com.idunnololz.summit.util.PreferenceUtil.KEY_INFINITY
import com.idunnololz.summit.util.PreferenceUtil.KEY_MARK_POSTS_AS_READ_ON_SCROLL
import com.idunnololz.summit.util.PreferenceUtil.KEY_POST_AND_COMMENTS_UI_CONFIG
import com.idunnololz.summit.util.PreferenceUtil.KEY_POST_GESTURE_ACTION_1
import com.idunnololz.summit.util.PreferenceUtil.KEY_POST_GESTURE_ACTION_2
import com.idunnololz.summit.util.PreferenceUtil.KEY_POST_GESTURE_ACTION_3
import com.idunnololz.summit.util.PreferenceUtil.KEY_SHOW_IMAGE_POSTS
import com.idunnololz.summit.util.PreferenceUtil.KEY_SHOW_LINK_POSTS
import com.idunnololz.summit.util.PreferenceUtil.KEY_SHOW_NSFW_POSTS
import com.idunnololz.summit.util.PreferenceUtil.KEY_SHOW_TEXT_POSTS
import com.idunnololz.summit.util.PreferenceUtil.KEY_SHOW_VIDEO_POSTS
import com.idunnololz.summit.util.PreferenceUtil.KEY_TAP_COMMENT_TO_COLLAPSE
import com.idunnololz.summit.util.PreferenceUtil.KEY_USE_GESTURE_ACTIONS
import com.idunnololz.summit.util.ext.fromJsonSafe
import com.idunnololz.summit.util.ext.toJsonSafe
import com.idunnololz.summit.util.moshi
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Preferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    companion object {
        private const val TAG = "Preferences"
    }

    private val prefs = PreferenceUtil.preferences

    fun getDefaultPage(): CommunityRef {
        val communityJson = prefs.getString(PreferenceUtil.KEY_DEFAULT_PAGE, null)
        val communityRef = if (communityJson == null) {
            null
        } else {
            try {
                moshi.adapter(CommunityRef::class.java)
                    .fromJson(communityJson)
            } catch (e: Exception) {
                null
            }
        }

        return communityRef ?: CommunityRef.All()
    }

    fun setDefaultPage(communityRef: CommunityRef) {
        prefs.edit()
            .putString(
                PreferenceUtil.KEY_DEFAULT_PAGE,
                moshi.adapter(CommunityRef::class.java).toJson(communityRef))
            .apply()
    }

    fun getPostsLayout(): CommunityLayout =
        try {
            CommunityLayout.valueOf(
                PreferenceUtil.preferences
                    .getString(PreferenceUtil.KEY_SUBREDDIT_LAYOUT, null) ?: ""
            )
        } catch (e: IllegalArgumentException) {
            CommunityLayout.List
        }

    fun setPostsLayout(layout: CommunityLayout) {
        PreferenceUtil.preferences.edit()
            .putString(PreferenceUtil.KEY_SUBREDDIT_LAYOUT, layout.name)
            .apply()
    }

    fun getPostInListUiConfig(): PostInListUiConfig {
        return prefs.getMoshiValue<PostInListUiConfig>(getPostUiConfigKey())
            ?: getPostsLayout().getDefaultPostUiConfig()
    }

    fun setPostInListUiConfig(config: PostInListUiConfig) {
        prefs.putMoshiValue(getPostUiConfigKey(), config)
    }

    fun getPostAndCommentsUiConfig(): PostAndCommentsUiConfig {
        return prefs.getMoshiValue<PostAndCommentsUiConfig>(KEY_POST_AND_COMMENTS_UI_CONFIG)
            ?: getDefaultPostAndCommentsUiConfig()
    }

    fun setPostAndCommentsUiConfig(config: PostAndCommentsUiConfig) {
        prefs.putMoshiValue(KEY_POST_AND_COMMENTS_UI_CONFIG, config)
    }

    private fun getPostUiConfigKey() =
        when (getPostsLayout()) {
            CommunityLayout.Compact ->
                PreferenceUtil.KEY_POST_UI_CONFIG_COMPACT
            CommunityLayout.List ->
                PreferenceUtil.KEY_POST_UI_CONFIG_LIST
            CommunityLayout.Card ->
                PreferenceUtil.KEY_POST_UI_CONFIG_CARD
            CommunityLayout.Card2 ->
                PreferenceUtil.KEY_POST_UI_CONFIG_CARD2
            CommunityLayout.Full ->
                PreferenceUtil.KEY_POST_UI_CONFIG_FULL
        }

    fun getBaseTheme(): BaseTheme {
        return prefs.getMoshiValue<BaseTheme>(KEY_BASE_THEME)
            ?: BaseTheme.Dark
    }

    fun setBaseTheme(baseTheme: BaseTheme) {
        prefs.putMoshiValue(KEY_BASE_THEME, baseTheme)
    }

    fun isUseMaterialYou(): Boolean {
        return prefs.getBoolean(PreferenceUtil.KEY_USE_MATERIAL_YOU, false)
    }

    fun setUseMaterialYou(b: Boolean) {
        prefs.edit().putBoolean(PreferenceUtil.KEY_USE_MATERIAL_YOU, b).apply()
    }

    fun isBlackTheme(): Boolean {
        return prefs.getBoolean(PreferenceUtil.KEY_USE_BLACK_THEME, false)
    }

    fun setUseBlackTheme(b: Boolean) {
        prefs.edit().putBoolean(PreferenceUtil.KEY_USE_BLACK_THEME, b).apply()
    }

    var markPostsAsReadOnScroll: Boolean
        get() = prefs.getBoolean(KEY_MARK_POSTS_AS_READ_ON_SCROLL, false)
        set(value) {
            prefs.edit().putBoolean(KEY_MARK_POSTS_AS_READ_ON_SCROLL, value).apply()
        }

    var useGestureActions: Boolean
        get() = prefs.getBoolean(KEY_USE_GESTURE_ACTIONS, true)
        set(value) {
            prefs.edit().putBoolean(KEY_USE_GESTURE_ACTIONS, value).apply()
        }

    var hideCommentActions: Boolean
        get() = prefs.getBoolean(KEY_HIDE_COMMENT_ACTIONS, false)
        set(value) {
            prefs.edit().putBoolean(KEY_HIDE_COMMENT_ACTIONS, value).apply()
        }

    var tapCommentToCollapse: Boolean
        get() = prefs.getBoolean(KEY_TAP_COMMENT_TO_COLLAPSE, false)
        set(value) {
            prefs.edit().putBoolean(KEY_TAP_COMMENT_TO_COLLAPSE, value).apply()
        }

    var infinity: Boolean
        get() = prefs.getBoolean(KEY_INFINITY, true)
        set(value) {
            prefs.edit().putBoolean(KEY_INFINITY, value).apply()
        }

    var postGestureAction1: Int
        get() = prefs.getInt(KEY_POST_GESTURE_ACTION_1, PostGestureAction.Upvote)
        set(value) {
            prefs.edit().putInt(KEY_POST_GESTURE_ACTION_1, value).apply()
        }

    var postGestureAction2: Int
        get() = prefs.getInt(KEY_POST_GESTURE_ACTION_2, PostGestureAction.Reply)
        set(value) {
            prefs.edit().putInt(KEY_POST_GESTURE_ACTION_2, value).apply()
        }

    var postGestureAction3: Int
        get() = prefs.getInt(KEY_POST_GESTURE_ACTION_3, PostGestureAction.MarkAsRead)
        set(value) {
            prefs.edit().putInt(KEY_POST_GESTURE_ACTION_3, value).apply()
        }

    var commentGestureAction1: Int
        get() = prefs.getInt(KEY_COMMENT_GESTURE_ACTION_1, CommentGestureAction.Upvote)
        set(value) {
            prefs.edit().putInt(KEY_COMMENT_GESTURE_ACTION_1, value).apply()
        }

    var commentGestureAction2: Int
        get() = prefs.getInt(KEY_COMMENT_GESTURE_ACTION_2, CommentGestureAction.Downvote)
        set(value) {
            prefs.edit().putInt(KEY_COMMENT_GESTURE_ACTION_2, value).apply()
        }

    var commentGestureAction3: Int
        get() = prefs.getInt(KEY_COMMENT_GESTURE_ACTION_3, CommentGestureAction.Reply)
        set(value) {
            prefs.edit().putInt(KEY_COMMENT_GESTURE_ACTION_3, value).apply()
        }

    var commentThreadStyle: CommentThreadStyleId
        get() = prefs.getInt(KEY_COMMENT_THREAD_STYLE, CommentsThreadStyle.Modern)
        set(value) {
            prefs.edit().putInt(KEY_COMMENT_THREAD_STYLE, value).apply()
        }

    var blurNsfwPosts: Boolean
        get() = prefs.getBoolean(KEY_BLUR_NSFW_POSTS, true)
        set(value) {
            prefs.edit().putBoolean(KEY_BLUR_NSFW_POSTS, value).apply()
        }

    var showLinkPosts: Boolean
        get() = prefs.getBoolean(KEY_SHOW_LINK_POSTS, true)
        set(value) {
            prefs.edit().putBoolean(KEY_SHOW_LINK_POSTS, value).apply()
        }
    var showImagePosts: Boolean
        get() = prefs.getBoolean(KEY_SHOW_IMAGE_POSTS, true)
        set(value) {
            prefs.edit().putBoolean(KEY_SHOW_IMAGE_POSTS, value).apply()
        }
    var showVideoPosts: Boolean
        get() = prefs.getBoolean(KEY_SHOW_VIDEO_POSTS, true)
        set(value) {
            prefs.edit().putBoolean(KEY_SHOW_VIDEO_POSTS, value).apply()
        }
    var showTextPosts: Boolean
        get() = prefs.getBoolean(KEY_SHOW_TEXT_POSTS, true)
        set(value) {
            prefs.edit().putBoolean(KEY_SHOW_TEXT_POSTS, value).apply()
        }
    var showNsfwPosts: Boolean
        get() = prefs.getBoolean(KEY_SHOW_NSFW_POSTS, true)
        set(value) {
            prefs.edit().putBoolean(KEY_SHOW_NSFW_POSTS, value).apply()
        }

    var globalFontSize: Int
        get() = prefs.getInt(KEY_GLOBAL_FONT_SIZE, GlobalFontSizeId.Normal)
        set(value) {
            prefs.edit().putInt(KEY_GLOBAL_FONT_SIZE, value).apply()
        }

    var defaultCommunitySortOrder: CommunitySortOrder?
        get() = moshi.fromJsonSafe(prefs.getString(KEY_DEFAULT_COMMUNITY_SORT_ORDER, null))
        set(value) {
            prefs.edit().putString(KEY_DEFAULT_COMMUNITY_SORT_ORDER, moshi.toJsonSafe(value)).apply()
        }

    var defaultCommentsSortOrder: CommentsSortOrder?
        get() = moshi.fromJsonSafe(prefs.getString(KEY_DEFAULT_COMMENTS_SORT_ORDER, null))
        set(value) {
            prefs.edit().putString(KEY_DEFAULT_COMMENTS_SORT_ORDER, moshi.toJsonSafe(value)).apply()
        }

    fun reset(key: String) {
        prefs.edit().remove(key).apply()
    }

    private inline fun <reified T> SharedPreferences.getMoshiValue(key: String): T? {
        return try {
            val json = this.getString(key, null)
                ?: return null
            moshi.adapter(T::class.java).fromJson(
                json
            )
        } catch (e: Exception) {
            Log.e(TAG, "", e)
            null
        }
    }

    private inline fun <reified T> SharedPreferences.putMoshiValue(key: String, value: T) {
        this.edit()
            .putString(key, moshi.adapter(T::class.java).toJson(value))
            .apply()
    }
}