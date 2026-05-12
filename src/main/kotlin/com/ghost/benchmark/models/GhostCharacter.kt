package com.ghost.benchmark.models

import com.ghost.serialization.annotations.GhostSerialization
import kotlinx.serialization.Serializable

@Serializable
@GhostSerialization
data class GhostCharacter(
    val id: Long,
    val name: String,
    val status: String,
    val species: String,
    val type: String,
    val gender: String,
    val spectralMetadata: String,
    val abilities: List<SpectralAbility>
)