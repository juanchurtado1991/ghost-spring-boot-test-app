package com.ghost.benchmark.models

import com.ghost.serialization.annotations.GhostSerialization
import kotlinx.serialization.Serializable

@Serializable
@GhostSerialization
data class CharacterResponse(
    val characters: List<GhostCharacter>,
    val serverStats: ServerStats
)