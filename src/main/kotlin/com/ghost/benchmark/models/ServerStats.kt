package com.ghost.benchmark.models

import com.ghost.serialization.annotations.GhostSerialization
import kotlinx.serialization.Serializable

@Serializable
@GhostSerialization
data class ServerStats(
    val usedHeapMb: Double,
    val activeThreads: Int,
    val allocationBytes: Long
)