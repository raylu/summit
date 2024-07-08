package it.vercruysse.lemmyapi.v0.x19.x3.datatypes

import kotlinx.serialization.Serializable

@Serializable
internal data class LocalImageView(
    val local_image: LocalImage,
    val person: Person,
)
