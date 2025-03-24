package it.vercruysse.lemmyapi.v0.x19.x3.datatypes

import it.vercruysse.lemmyapi.dto.CommentSortType
import kotlinx.serialization.Serializable

@Serializable
internal data class GetPersonMentions(
    // "Hot" | "Top" | "New" | "Old" | "Controversial"
    val sort: CommentSortType? = null,
    val page: Long? = null,
    val limit: Long? = null,
    val unread_only: Boolean? = null,
)
