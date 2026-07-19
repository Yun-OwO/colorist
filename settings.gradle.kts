pluginManagement {
    repositories {
        // Domestic Chinese mirrors (Aliyun) — tried first for faster downloads from inside
        // mainland China. The original upstream repositories are retained as fallbacks so
        // the build still works outside China.
        maven(url = "https://maven.aliyun.com/repository/gradle-plugin")
        maven(url = "https://maven.aliyun.com/repository/public")
        gradlePluginPortal()
        maven(url = "https://maven.neoforged.net/releases")
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

rootProject.name = "colorist"
