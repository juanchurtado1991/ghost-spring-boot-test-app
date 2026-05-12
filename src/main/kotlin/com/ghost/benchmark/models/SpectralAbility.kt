package com.ghost.benchmark.models

import com.ghost.serialization.annotations.GhostSerialization
import kotlinx.serialization.Serializable

@Serializable
@GhostSerialization
data class SpectralAbility(
    val name: String,
    val powerLevel: Int,
    val description: String
)