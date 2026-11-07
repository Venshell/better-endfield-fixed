buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        // AGP 9 compiles Kotlin itself and pins the Kotlin Gradle plugin at its
        // own minimum (2.2.10). The Compose compiler plugin is versioned with
        // Kotlin and refuses to run against a different compiler, so the newer
        // plugin is raised here rather than left at AGP's default.
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.20")
    }
}

plugins {
    id("com.android.application") version "9.1.1" apply false
    // Built-in Kotlin replaces org.jetbrains.kotlin.android from AGP 9.0 on;
    // applying it again is an error. Only the Compose compiler plugin is added.
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.20" apply false
}

apply(from = "workspace.gradle.kts")
