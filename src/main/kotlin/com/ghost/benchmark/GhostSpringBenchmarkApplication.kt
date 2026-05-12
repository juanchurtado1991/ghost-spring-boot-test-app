package com.ghost.benchmark

import com.ghost.serialization.Ghost
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

private const val LOCALHOST_URL = "http://localhost:8081"
private const val PROP_OS_NAME = "os.name"
private const val OS_WINDOWS = "win"
private const val OS_MAC = "mac"
private const val OS_NIX = "nix"
private const val OS_NUX = "nux"
private const val CMD_WINDOWS = "rundll32 url.dll,FileProtocolHandler"
private const val CMD_MAC = "open"
private const val CMD_LINUX = "xdg-open"

@SpringBootApplication
class GhostSpringBenchmarkApplication

fun main(args: Array<String>) {
    // Registry is auto-discovered via KSP → META-INF/services (JVM). prewarm() is optional (demo latency).
    Ghost.prewarm()
    runApplication<GhostSpringBenchmarkApplication>(*args)
    openLocalBrowser()
}

private fun openLocalBrowser() {
    val os = System.getProperty(PROP_OS_NAME).lowercase()
    val runtime = Runtime.getRuntime()
    try {
        when {
            os.contains(OS_WINDOWS) ->
                runtime.exec("$CMD_WINDOWS $LOCALHOST_URL")
            os.contains(OS_MAC) ->
                runtime.exec("$CMD_MAC $LOCALHOST_URL")
            os.contains(OS_NIX) || os.contains(OS_NUX) ->
                runtime.exec("$CMD_LINUX $LOCALHOST_URL")
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
