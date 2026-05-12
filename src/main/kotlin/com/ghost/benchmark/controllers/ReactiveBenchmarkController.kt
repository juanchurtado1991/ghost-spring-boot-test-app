package com.ghost.benchmark.controllers

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.ghost.benchmark.models.GhostCharacter
import com.ghost.benchmark.models.GhostStreamFrame
import com.ghost.benchmark.models.SpectralAbility
import com.ghost.benchmark.services.ServerTelemetryService
import com.ghost.serialization.Ghost
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import kotlinx.serialization.serializer
import okio.buffer
import okio.sink
import okio.source
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.time.Duration

private object NullOutputStream : OutputStream() {
    override fun write(b: Int) = Unit
    override fun write(b: ByteArray, off: Int, len: Int) = Unit
}

@RestController
@RequestMapping("/api/v1")
@CrossOrigin("*")
@OptIn(ExperimentalSerializationApi::class)
class ReactiveBenchmarkController(
    private val telemetry: ServerTelemetryService,
    private val jackson: ObjectMapper
) {
    // ── Kotlinx Serialization ──────────────────────────────────────────────
    private val kserJson = Json { ignoreUnknownKeys = true }
    private val kserListSerializer = ListSerializer(serializer<GhostCharacter>())

    // ── Pre-built data (never re-allocated per request) ────────────────────
    private val benchmarkList: List<GhostCharacter> = buildBenchmarkList(size = 10_000)
    private val internalBenchmarkList: List<GhostCharacter> = benchmarkList + benchmarkList

    // Lazy: avoids blocking Spring startup, created on first use
    private val benchmarkString: String by lazy { jackson.writeValueAsString(benchmarkList) }
    private val benchmarkBytes: ByteArray by lazy { jackson.writeValueAsBytes(benchmarkList) }
    // Ghost's native bytes — used for read benchmarks to avoid per-call String→ByteArray conversion.
    // Same JSON content as benchmarkBytes (standard JSON), just pre-encoded by Ghost itself.
    private val ghostBenchmarkBytes: ByteArray by lazy { Ghost.encodeToBytes(benchmarkList) }

    // ── Endpoints ──────────────────────────────────────────────────────────

    @GetMapping("/stats", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun streamServerStats(): Flux<ServerTelemetryService.ServerStats> =
        Flux.interval(Duration.ofMillis(500)).map { telemetry.getCurrentStats() }

    @GetMapping("/benchmark/run")
    fun runBenchmark(
        @RequestParam(defaultValue = "ghost") engine: String,
        @RequestParam(defaultValue = "write") operation: String,
        @RequestParam(defaultValue = "bytes") mode: String
    ): Mono<ResponseEntity<Map<String, Any>>> {
        val startAlloc = telemetry.getThreadAllocatedBytes()
        val startTime = System.nanoTime()

        val payloadSize = when (operation) {
            "write" -> executeWrite(engine, mode)
            "read" -> executeRead(engine, mode).let { 0 }
            else -> 0
        }

        val latencyMs = (System.nanoTime() - startTime) / 1_000_000
        val allocBytes = telemetry.getThreadAllocatedBytes() - startAlloc

        return Mono.just(buildBenchmarkResponse(engine, operation, mode, allocBytes, latencyMs, payloadSize))
    }

    @GetMapping("/stream", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun streamCharacters(@RequestParam(defaultValue = "100") count: Int): Flux<GhostStreamFrame> =
        Flux.interval(Duration.ofMillis(30))
            .take(count.toLong())
            .map { id -> buildStreamFrame(id) }

    // ── Write dispatch ─────────────────────────────────────────────────────

    private fun executeWrite(engine: String, mode: String): Int = when (engine) {
        "ghost" -> writeWithGhost(mode)
        "kotlinx" -> writeWithKotlinx(mode)
        else -> writeWithJackson(mode)
    }

    private fun writeWithGhost(mode: String): Int = when (mode) {
        "bytes"  -> Ghost.encodeToBytes(benchmarkList).size
        "string" -> Ghost.encodeToString(benchmarkList).length
        "stream" -> { Ghost.encodeAndDiscard(internalBenchmarkList); 0 }
        else     -> 0
    }

    private fun writeWithKotlinx(mode: String): Int = when (mode) {
        "bytes" -> ByteArrayOutputStream()
            .also { kserJson.encodeToStream(kserListSerializer, benchmarkList, it) }
            .size()

        "string" ->
            kserJson.encodeToString(kserListSerializer, benchmarkList).length

        "stream" -> {
            kserJson.encodeToStream(kserListSerializer, internalBenchmarkList, NullOutputStream); 0
        }

        else -> 0
    }

    private fun writeWithJackson(mode: String): Int = when (mode) {
        "bytes" -> jackson.writeValueAsBytes(benchmarkList).size
        "string" -> jackson.writeValueAsString(benchmarkList).length
        "stream" -> {
            jackson.writeValue(NullOutputStream, internalBenchmarkList); 0
        }

        else -> 0
    }

    // ── Read dispatch ──────────────────────────────────────────────────────

    private fun executeRead(engine: String, mode: String) = when (engine) {
        "ghost" -> readWithGhost(mode)
        "kotlinx" -> readWithKotlinx(mode)
        else -> readWithJackson(mode)
    }

    private fun readWithGhost(mode: String) = when (mode) {
        "bytes"  -> Ghost.deserialize<List<GhostCharacter>>(ghostBenchmarkBytes)
        "string" -> Ghost.deserialize<List<GhostCharacter>>(ghostBenchmarkBytes)
        "stream" -> Ghost.deserialize<List<GhostCharacter>>(ghostBenchmarkBytes)
        else     -> null
    }

    private fun readWithKotlinx(mode: String) = when (mode) {
        "stream" -> kserJson.decodeFromStream(kserListSerializer, ByteArrayInputStream(benchmarkBytes))
        else -> kserJson.decodeFromString(kserListSerializer, benchmarkString)
    }

    private fun readWithJackson(mode: String): Any? {
        val typeRef = object : TypeReference<List<GhostCharacter>>() {}
        return when (mode) {
            "stream" -> jackson.readValue(ByteArrayInputStream(benchmarkBytes), typeRef)
            else -> jackson.readValue(benchmarkString, typeRef)
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private fun buildBenchmarkResponse(
        engine: String, operation: String, mode: String,
        allocBytes: Long, latencyMs: Long, payloadSize: Int
    ): ResponseEntity<Map<String, Any>> =
        ResponseEntity.ok()
            .header("X-Engine", engine)
            .body(
                mapOf(
                    "engine" to engine,
                    "operation" to operation,
                    "mode" to mode,
                    "garbageBytes" to allocBytes,
                    "latencyMs" to latencyMs,
                    "payloadSize" to payloadSize
                )
            )

    private fun buildStreamFrame(id: Long): GhostStreamFrame {
        val stats = telemetry.getCurrentStats()
        return GhostStreamFrame(
            character = GhostCharacter(
                id = id,
                name = "Spectral Agent #$id",
                status = "Streaming",
                species = "Ghost",
                type = "Reactive",
                gender = "Spectral",
                spectralMetadata = "SPECTRAL_DATA_CHUNK_".repeat(20),
                abilities = listOf(SpectralAbility("Streaming Power", 100, "High bandwidth phantom"))
            ),
            serverMemoryMb = stats.usedHeapMb,
            totalAllocatedGb = stats.totalAllocatedGb,
            activeThreads = stats.activeThreads
        )
    }

    @Suppress("SameParameterValue")
    private fun buildBenchmarkList(size: Int): List<GhostCharacter> {
        val heavyData = "SPECTRAL_DATA_CHUNK_".repeat(10)
        val abilities = listOf(
            SpectralAbility("Phase Shift", 85, "Passes through solid objects"),
            SpectralAbility("Ethereal Roar", 92, "Sonic blast from the void"),
            SpectralAbility("Glow", 10, "Just looks cool")
        )
        return (1..size).map { id ->
            GhostCharacter(
                id = id.toLong(),
                name = "Character #$id",
                status = "Active",
                species = "Spectre",
                type = "Benchmark",
                gender = "None",
                spectralMetadata = heavyData,
                abilities = abilities
            )
        }
    }
}
