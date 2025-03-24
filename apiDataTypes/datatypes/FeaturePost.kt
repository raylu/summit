package it.vercruysse.lemmyapi.v0.x19.x3.datatypes

import it.vercruysse.lemmyapi.dto.PostFeatureType
import kotlinx.serialization.Serializable

@Serializable
internal data class FeaturePost(
    val post_id: PostId,
    val featured: Boolean,
    // "Local" | "Community"
    val feature_type: PostFeatureType,
)
