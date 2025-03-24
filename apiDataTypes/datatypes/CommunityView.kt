package it.vercruysse.lemmyapi.v0.x19.x3.datatypes

import it.vercruysse.lemmyapi.dto.SubscribedType
import kotlinx.serialization.Serializable

@Serializable
internal data class CommunityView(
    val community: Community,
    // "Subscribed" | "NotSubscribed" | "Pending"
    val subscribed: SubscribedType,
    val blocked: Boolean,
    val counts: CommunityAggregates,
    val banned_from_community: Boolean,
)
