pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
@Suppress("UnstableApiUsage") // Incubating in Gradle, yet the usual way to set the repositories
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "LMToolbox"
include(":app")
// On-demand Play modules, one per part of the Linux subsystem (libpackage-<name>; core with PRoot too),
// with the pages of the tool on it, if one's alone on it (a tool on several, e.g. the Minecraft server, is the
// app's, as Java's, cloudflared's and bore's API: Java, Tunnel, Bore), see the root build file
val linuxParts = listOf("core", "python", "pypkgs", "ffmpeg", "exiftool", "ytdlp", "jdk8", "jdk17", "jdk21", "jdk25", "cloudflared", "bore", "pdf")
linuxParts.forEach { include(":features:$it") }
// For the app's build (updateLinuxPackages): the modules with a part's archive
gradle.extra["linuxParts"] = linuxParts

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}