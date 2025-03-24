package com.idunnololz.summit.api.dto

data class Search(
    val q: String,
    val community_id: CommunityId? = null,
    val community_name: String? = null,
    val creator_id: PersonId? = null,
    val type_: SearchType? /* "All" | "Comments" | "Posts" | "Communities" | "Users" | "Url" */ = null,
    /* "Active" | "Hot" | "New" | "Old" | "TopDay" | "TopWeek" | "TopMonth" | "TopYear" | "TopAll" | "MostComments" | "NewComments" */
    val sort: SortType? = null,
    val listing_type: ListingType? /* "All" | "Local" | "Subscribed" */ = null,
    val page: Int? = null,
    val limit: Int? = null,
    val auth: String? = null,
)
