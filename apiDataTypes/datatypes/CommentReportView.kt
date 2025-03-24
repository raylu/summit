package it.vercruysse.lemmyapi.v0.x19.x3.datatypes

import it.vercruysse.lemmyapi.dto.SubscribedType
import kotlinx.serialization.Serializable

@Serializable
internal data class CommentReportView(
    val comment_report: CommentReport,
    val comment: Comment,
    val post: Post,
    val community: Community,
    val creator: Person,
    val comment_creator: Person,
    val counts: CommentAggregates,
    val creator_banned_from_community: Boolean,
    val creator_is_moderator: Boolean,
    val creator_is_admin: Boolean,
    val creator_blocked: Boolean,
    // "Subscribed" | "NotSubscribed" | "Pending"
    val subscribed: SubscribedType,
    val saved: Boolean,
    val my_vote: Int = 0,
    val resolver: Person? = null,
)
