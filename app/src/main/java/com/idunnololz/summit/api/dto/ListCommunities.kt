package com.idunnololz.summit.api.dto

data class ListCommunities(
    val type_: ListingType? /* "All" | "Local" | "Subscribed" */ = null,
    /* "Active" | "Hot" | "New" | "Old" | "TopDay" | "TopWeek" | "TopMonth" | "TopYear" | "TopAll" | "MostComments" | "NewComments" */
    val sort: SortType? = null,
    val page: Int? = null,
    val limit: Int? = null,
    val auth: String? = null,
)
