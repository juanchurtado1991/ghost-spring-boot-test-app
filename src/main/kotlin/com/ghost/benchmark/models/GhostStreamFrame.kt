package com.ghost.benchmark.models

import com.ghost.serialization.annotations.GhostSerialization
import kotlinx.serialization.Serializable

@Serializable
@GhostSerialization
data class GhostStreamFrame(
    val character: GhostCharacter,
    val serverMemoryMb: Double,
    val totalAllocatedGb: Double,
    val activeThreads: Int
)