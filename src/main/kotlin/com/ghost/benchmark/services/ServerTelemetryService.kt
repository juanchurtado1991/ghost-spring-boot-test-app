package com.ghost.benchmark.services

import org.springframework.stereotype.Service
import java.lang.management.ManagementFactory
import com.sun.management.ThreadMXBean

@Service
class ServerTelemetryService {
    private val runtime = Runtime.getRuntime()
    private val threadBean = ManagementFactory.getThreadMXBean() as ThreadMXBean

    init {
        if (threadBean.isThreadAllocatedMemorySupported) {
            threadBean.isThreadAllocatedMemoryEnabled = true
        }
    }

    data class ServerStats(
        val usedHeapMb: Double,
        val maxHeapMb: Double,
        val activeThreads: Int,
        val uptimeMs: Long,
        val totalAllocatedGb: Double
    )

    fun getCurrentStats(): ServerStats {
        val usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / (1024.0 * 1024.0)
        val maxMemory = runtime.maxMemory() / (1024.0 * 1024.0)
        
        // Cumulative allocation across all living threads (approximation of server stress)
        val totalAllocated = threadBean.allThreadIds.sumOf { 
            val bytes = threadBean.getThreadAllocatedBytes(it)
            if (bytes == -1L) 0L else bytes
        } / (1024.0 * 1024.0 * 1024.0)

        return ServerStats(
            usedHeapMb = usedMemory,
            maxHeapMb = maxMemory,
            activeThreads = ManagementFactory.getThreadMXBean().threadCount,
            uptimeMs = ManagementFactory.getRuntimeMXBean().uptime,
            totalAllocatedGb = totalAllocated
        )
    }

    fun getThreadAllocatedBytes(): Long {
        return if (threadBean.isThreadAllocatedMemoryEnabled) {
            threadBean.getThreadAllocatedBytes(Thread.currentThread().id)
        } else 0L
    }
}
