package it.vercruysse.lemmyapi.v0.x19.x3.datatypes

import it.vercruysse.lemmyapi.dto.SortType
import kotlinx.serialization.Serializable

@Serializable
internal data class GetPersonDetails(
    val person_id: PersonId? = null,
    val username: String? = null,
    // "Active" | "Hot" | "New" | "Old" | "TopDay" | "TopWeek" | "TopMonth" | "TopYear" | "TopAll" | "MostComments" | "NewComments" | "TopHour" | "TopSixHour" | "TopTwelveHour" | "TopThreeMonths" | "TopSixMonths" | "TopNineMonths" | "Controversial" | "Scaled"
    val sort: SortType? = null,
    val page: Long? = null,
    val limit: Long? = null,
    val community_id: CommunityId? = null,
    val saved_only: Boolean? = null,
)
