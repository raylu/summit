package com.idunnololz.summit.api.dto

data class GetPersonDetails(
    val person_id: PersonId? = null,
    val username: String? = null,
    /* "Active" | "Hot" | "New" | "Old" | "TopDay" | "TopWeek" | "TopMonth" | "TopYear" | "TopAll" | "MostComments" | "NewComments" */
    val sort: SortType? = null,
    val page: Int? = null,
    val limit: Int? = null,
    val community_id: CommunityId? = null,
    val saved_only: Boolean? = null,
    val auth: String? = null,
)
