package it.vercruysse.lemmyapi.v0.x19.x3.datatypes

import it.vercruysse.lemmyapi.dto.ListingType
import it.vercruysse.lemmyapi.dto.SortType
import kotlinx.serialization.Serializable

@Serializable
internal data class ListCommunities(
    // "All" | "Local" | "Subscribed" | "ModeratorView"
    val type_: ListingType? = null,
    // "Active" | "Hot" | "New" | "Old" | "TopDay" | "TopWeek" | "TopMonth" | "TopYear" | "TopAll" | "MostComments" | "NewComments" | "TopHour" | "TopSixHour" | "TopTwelveHour" | "TopThreeMonths" | "TopSixMonths" | "TopNineMonths" | "Controversial" | "Scaled"
    val sort: SortType? = null,
    val show_nsfw: Boolean? = null,
    val page: Long? = null,
    val limit: Long? = null,
)
