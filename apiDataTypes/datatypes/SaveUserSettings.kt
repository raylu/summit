package it.vercruysse.lemmyapi.v0.x19.x3.datatypes

import it.vercruysse.lemmyapi.dto.ListingType
import it.vercruysse.lemmyapi.dto.PostListingMode
import it.vercruysse.lemmyapi.dto.SortType
import kotlinx.serialization.Serializable

@Serializable
internal data class SaveUserSettings(
    val show_nsfw: Boolean? = null,
    val blur_nsfw: Boolean? = null,
    val auto_expand: Boolean? = null,
    val theme: String? = null,
    // "Active" | "Hot" | "New" | "Old" | "TopDay" | "TopWeek" | "TopMonth" | "TopYear" | "TopAll" | "MostComments" | "NewComments" | "TopHour" | "TopSixHour" | "TopTwelveHour" | "TopThreeMonths" | "TopSixMonths" | "TopNineMonths" | "Controversial" | "Scaled"
    val default_sort_type: SortType? = null,
    // "All" | "Local" | "Subscribed" | "ModeratorView"
    val default_listing_type: ListingType? = null,
    val interface_language: String? = null,
    val avatar: String? = null,
    val banner: String? = null,
    val display_name: String? = null,
    val email: String? = null,
    val bio: String? = null,
    val matrix_user_id: String? = null,
    val show_avatars: Boolean? = null,
    val send_notifications_to_email: Boolean? = null,
    val bot_account: Boolean? = null,
    val show_bot_accounts: Boolean? = null,
    val show_read_posts: Boolean? = null,
    val discussion_languages: List<LanguageId>? = null,
    val open_links_in_new_tab: Boolean? = null,
    val infinite_scroll_enabled: Boolean? = null,
    // "List" | "Card" | "SmallCard"
    val post_listing_mode: PostListingMode? = null,
    val enable_keyboard_navigation: Boolean? = null,
    val enable_animated_images: Boolean? = null,
    val collapse_bot_comments: Boolean? = null,
    val show_scores: Boolean? = null,
    val show_upvotes: Boolean? = null,
    val show_downvotes: Boolean? = null,
    val show_upvote_percentage: Boolean? = null,
)
