import org.springframework.boot.gradle.tasks.run.BootRun

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.ghost)
}

ghost {
    version.set(libs.versions.ghost.get())
}

group = "com.ghost.benchmark"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

dependencies {
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.webflux)
    developmentOnly(libs.spring.boot.devtools)
    implementation(libs.kotlin.reflect)
    implementation(libs.jackson.module.kotlin)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.ghost.spring.boot.starter)
    implementation(libs.okio)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.kotlin.test.junit5)
    testRuntimeOnly(libs.junit.platform.launcher)
}

kotlin {
    jvmToolchain(17)
    compilerOptions {
        freeCompilerArgs.addAll(
            "-Xjsr305=strict",
            "-Xannotation-default-target=param-property",
            "-Xlambdas=indy",
            "-Xskip-metadata-version-check"
        )
    }
}

tasks.withType<BootRun> {
    doFirst { Runtime.getRuntime().exec("fuser -k 8081/tcp") }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

ksp { arg("ghost.moduleName", "benchmark_app") }
