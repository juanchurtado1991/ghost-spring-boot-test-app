# 👻 Ghost Spring Boot Benchmark Dashboard

This is the official testing laboratory for **Ghost Serialization** in Spring Boot environments. It serves as both a performance validation tool and a blueprint for production-grade backend integrations.

**Ghost version:** `1.1.17` from [Maven Central](https://central.sonatype.com/search?q=g:com.ghostserializer) (`com.ghostserializer`). Clone and build — no local checkout of [ghost-serializer](https://github.com/juanchurtado1991/ghost-serializer) required.

**Related projects:**

| Project | Description |
|:---|:---|
| [ghost-serializer](https://github.com/juanchurtado1991/ghost-serializer) | Main library, KMP sample app, JVM benchmarks |
| [ghost-android-test-app](https://github.com/juanchurtado1991/ghost-android-test-app) | On-device Android benchmark vs Gson, Moshi, KSer |
| [ghost-ios-test-app](https://github.com/juanchurtado1991/ghost-ios-test-app) | Native iOS benchmark vs Apple Codable (XCFramework bundled) |

---

## 🚀 How to Run the Benchmark

**Requirements:** Java 17+, Python 3 (for the automated script).

### Option A — Web dashboard (recommended for demos)

```bash
./gradlew bootRun --refresh-dependencies
```

Opens **http://localhost:8081**. Use the UI to compare Ghost vs Jackson on the same endpoints.

### Option B — Automated load test

```bash
# Terminal 1
./gradlew bootRun

# Terminal 2
python3 benchmark.py
```

---

## 📦 Minimal setup

> **Coordinates:** Maven artifacts use `com.ghostserializer`. Kotlin packages use `com.ghost.serialization`.

### Ghost artifacts (`1.1.17` on Maven Central)

| Artifact | Purpose |
|:---|:---|
| `com.ghostserializer:ghost-api` | Annotations (`@GhostSerialization`, etc.) |
| `com.ghostserializer:ghost-serialization` | Runtime engine |
| `com.ghostserializer:ghost-compiler` | KSP code generator |
| `com.ghostserializer:ghost-spring-boot-starter` | Spring MVC + WebFlux codecs |
| `com.ghostserializer.ghost` (Gradle plugin) | Auto-wires KSP + dependencies |

### 1. Gradle plugin + starter

```toml
# gradle/libs.versions.toml
[versions]
ghost = "1.1.17"

[libraries]
ghost-spring-boot-starter = { group = "com.ghostserializer", name = "ghost-spring-boot-starter", version.ref = "ghost" }

[plugins]
ghost = { id = "com.ghostserializer.ghost", version.ref = "ghost" }
```

```kotlin
// build.gradle.kts
plugins {
    alias(libs.plugins.ghost)
    // kotlin-jvm, spring-boot, ksp, etc.
}

ghost {
    version.set(libs.versions.ghost.get())
}

dependencies {
    implementation(libs.ghost.spring.boot.starter)
}

ksp {
    arg("ghost.moduleName", "your_app")  // e.g. benchmark_app → GhostModuleRegistry_your_app
}
```

```kotlin
// settings.gradle.kts
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
}
```

### 2. Annotate DTOs

```kotlin
import com.ghost.serialization.annotations.GhostSerialization

@GhostSerialization
data class UserResponse(val id: Long, val name: String)
```

### 3. Use controllers as usual

```kotlin
@RestController
class UserController {
    @PostMapping("/users")
    fun create(@RequestBody body: CreateUserRequest): UserResponse = /* ... */

    @GetMapping("/users/{id}")
    fun get(@PathVariable id: Long): UserResponse = /* ... */
}
```

**No manual `Ghost.addRegistry()` in `main()`** — on JVM, KSP writes `META-INF/services/com.ghost.serialization.contract.GhostRegistry` and Ghost discovers your module at runtime via `ServiceLoader`.

Optional cold-start tuning only:

```kotlin
fun main(args: Array<String>) {
    Ghost.prewarm()  // optional: warms serializer cache before first request
    runApplication<YourApplication>(*args)
}
```

---

## What each piece does

### Ghost Gradle plugin (`com.ghostserializer.ghost`)

| Responsibility |
|:---|
| Adds `ghost-api`, `ghost-serialization`, `ghost-compiler` (KSP) |
| Runs KSP at compile time → `YourModelSerializer.kt` per `@GhostSerialization` class |
| Generates `GhostModuleRegistry_<moduleName>` + **ServiceLoader** registration file |
| No Spring wiring — only serialization codegen and runtime deps |

### `ghost-spring-boot-starter` (this is the Spring integration)

Auto-configures **`GhostAutoConfiguration`** when Spring Boot starts:

| Stack | What gets registered | Effect |
|:---|:---|:---|
| **Spring MVC** (Servlet) | `GhostHttpMessageConverter` at **index 0** | `@RequestBody` / `@ResponseBody` with `application/json` use Ghost when the type has a generated serializer |
| **Spring WebFlux** (Reactive) | `GhostReactiveDecoder` + `GhostReactiveEncoder` | Same for reactive controllers (`Mono`, `Flux`, codec pipeline) |

**Read path:** HTTP body → `ByteArray` → pooled `GhostJsonReader` (no extra Okio/stream layers).

**Write path:** pooled `GhostJsonFlatWriter` → `ByteArray` → response body in one write.

**Type routing:** A type is handled by Ghost only if `Ghost.getSerializer(clazz) != null` (i.e. `@GhostSerialization` + KSP). Everything else stays on Jackson / default codecs — you can mix both in the same app.

**What the starter does *not* do:**

- Does not generate serializers (that's KSP + plugin).
- Does not register your `GhostModuleRegistry` (that's ServiceLoader from KSP).
- Does not remove Jackson — Jackson remains for non-Ghost types and for this benchmark's comparison endpoints.

### This benchmark app

- **WebFlux** + starter → Ghost codecs on the reactive stack.
- **Benchmark controller** calls `Ghost.deserialize` / `Ghost.encodeToBytes` directly and compares with **Jackson** on the same payloads (~10k `GhostCharacter` records, ~5.6 MB JSON).
- `Ghost.prewarm()` in `main()` is **optional** here to reduce first-hit latency during demos; not required for correctness.

---

## 📊 Benchmark results (10,000 iterations)

> **Methodology:** `benchmark.py`, 16 concurrent workers, 10k requests per engine/op/mode. **Avg Memory (Waste)** = `ThreadMXBean.getThreadAllocatedBytes()` delta on the request thread (bytes allocated during the call, not heap retained).
>
> **Ghost WRITE / ByteArray:** Measures `Ghost.encodeToBytes` (includes `FlatByteArrayWriter` growth + `copyOf` result). With Ghost **1.1.17**, JVM keeps the writer buffer warm up to **8 MB** (`GhostHeuristics.maxWarmWriteBufferCapacity`).
>
> Numbers below are from an earlier **1.1.16-era** run; refresh with `python3 benchmark.py` on **1.1.17** after Central resolves.

| Engine | Operation | Mode | Avg latency | Avg memory | Throughput |
|:---|:---|:---|:---:|:---:|:---:|
| **Jackson** | Write | String | 29.60 ms | 22764 KB | 420 ops/s |
| **Ghost** | Write | String | **16.69 ms** | **5907 KB** | **714 ops/s** |
| **Jackson** | Write | Bytes | 16.65 ms | 11403 KB | 727 ops/s |
| **Ghost** | Write | Bytes | **14.06 ms** | **5867 KB** | **833 ops/s** |
| **Jackson** | Read | String | 80.74 ms | 34462 KB | 157 ops/s |
| **Ghost** | Read | String | **22.14 ms** | **3514 KB** | **566 ops/s** |
| **Jackson** | Read | Bytes | 80.77 ms | 34460 KB | 157 ops/s |
| **Ghost** | Read | Bytes | **22.17 ms** | **3513 KB** | **565 ops/s** |

### Key takeaways

- **Reads ~3.5× faster** than Jackson on this payload.
- **~10× less allocation** on reads (~3.5 MB vs ~34 MB per request).
- Spring Boot API unchanged: starter + annotated models + plugin.

Full docs: [ghost-serializer — Spring Boot](https://github.com/juanchurtado1991/ghost-serializer#usage---spring-boot).

---

## 🏁 Spring vs mobile benchmarks (Ghost)

| Context | Best for demonstrating |
|:---|:---|
| [Android test app](https://github.com/juanchurtado1991/ghost-android-test-app) | GC/jank, Retrofit/Ktor, R8-safe serializers |
| [iOS test app](https://github.com/juanchurtado1991/ghost-ios-test-app) | Codable alternative, XCFramework |
| **This app** | Jackson on **large JVM payloads** and WebFlux throughput |

---

## Troubleshooting

**Plugin `1.1.17` not found:** Sonatype can show PUBLISHED before `repo.maven.apache.org` syncs. Verify on Maven:

```bash
curl -s https://repo.maven.apache.org/maven2/com/ghostserializer/ghost/com.ghostserializer.ghost.gradle.plugin/maven-metadata.xml | grep 1.1.17
```

Then: `./gradlew --stop && ./gradlew bootRun --refresh-dependencies`.

**`Ghost NOT_FOUND` at runtime:** Model missing `@GhostSerialization`, KSP not applied, or `ghost.moduleName` mismatch — rebuild after fixing `ksp { arg("ghost.moduleName", "...") }`.

**Port 8081 in use:** Change `server.port` in `application.properties` or stop the other process.

---

*Part of the [Ghost Serialization](https://github.com/juanchurtado1991/ghost-serializer) ecosystem.* 👻
