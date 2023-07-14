package com.idunnololz.summit.api.dto

import android.os.Parcelable
import com.squareup.moshi.JsonClass
import kotlinx.parcelize.Parcelize

@Parcelize
@JsonClass(generateAdapter = true)
data class PostView(
    val post: Post,
    val creator: Person,
    val community: Community,
    val creator_banned_from_community: Boolean,
    val counts: PostAggregates,
    val subscribed: SubscribedType /* "Subscribed" | "NotSubscribed" | "Pending" */,
    val saved: Boolean,
    val read: Boolean,
    val creator_blocked: Boolean,
    val my_vote: Int? = null,
    val unread_comments: Int,
) : Parcelable
