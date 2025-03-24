package it.vercruysse.lemmyapi.v0.x19.x3.datatypes

import it.vercruysse.lemmyapi.dto.ListingType
import it.vercruysse.lemmyapi.dto.SortType
import kotlinx.serialization.Serializable

@Serializable
internal data class GetPosts(
    val type_: ListingType? /* "All" | "Local" | "Subscribed" | "ModeratorView" */ = null,
    // "Active" | "Hot" | "New" | "Old" | "TopDay" | "TopWeek" | "TopMonth" | "TopYear" | "TopAll" | "MostComments" | "NewComments" | "TopHour" | "TopSixHour" | "TopTwelveHour" | "TopThreeMonths" | "TopSixMonths" | "TopNineMonths" | "Controversial" | "Scaled"
    val sort: SortType? = null,
    val page: Long? = null,
    val limit: Long? = null,
    val community_id: CommunityId? = null,
    val community_name: String? = null,
    val saved_only: Boolean? = null,
    val liked_only: Boolean? = null,
    val disliked_only: Boolean? = null,
    val show_hidden: Boolean? = null,
    val page_cursor: PaginationCursor? = null,
)
