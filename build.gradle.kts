import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    java
    id("net.neoforged.moddev") version "1.0.21"
    kotlin("jvm") version "2.1.0"
}

// Project properties from gradle.properties. In Gradle Kotlin DSL these are NOT
// automatically in scope as bare identifiers (unlike Gradle Groovy DSL), so each
// one must be declared explicitly via the `by project` delegate.
val mod_id: String by project
val mod_name: String by project
val mod_license: String by project
val mod_version: String by project
val mod_group_id: String by project
val mod_authors: String by project
val mod_description: String by project

val minecraft_version: String by project
val minecraft_version_range: String by project
val neo_version: String by project
val neo_version_range: String by project
val loader_version_range: String by project

val kotlinforforge_version: String by project
val kotlinforforge_version_range: String by project

val parchment_minecraft: String by project
val parchment_version: String by project

version = mod_version
group = mod_group_id

base {
    archivesName = mod_id
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
        freeCompilerArgs.add("-Xjvm-default=all")
    }
}

// Configure ModDevGradle
neoForge {
    version = neo_version

    parchment {
        mappingsVersion = parchment_version
        minecraftVersion = parchment_minecraft
    }

    runs {
        configureEach {
            systemProperty("neoforge.enabledGameTestNamespaces", mod_id)
        }

        create("client") {
            client()
        }

        create("server") {
            server()
        }
    }

    mods {
        create(mod_id) {
            sourceSet(sourceSets.main.get())
        }
    }
}

repositories {
    // Domestic Chinese mirrors (Aliyun) — tried first for faster downloads from inside
    // mainland China. The original upstream repositories are retained as fallbacks.
    maven(url = "https://maven.aliyun.com/repository/public")
    maven(url = "https://maven.aliyun.com/repository/central")
    mavenCentral()
    // Kotlin for Forge — required language provider
    maven(url = "https://thedarkcolour.github.io/KotlinForForge/")
    // NeoForge artifacts — required by ModDevGradle
    maven(url = "https://maven.neoforged.net/releases")
}

dependencies {
    // Kotlin for Forge: required runtime language provider for Kotlin-based mods.
    implementation("thedarkcolour:kotlinforforge-neoforge:$kotlinforforge_version")
}

tasks.processResources {
    val replaceProperties = mapOf(
        "minecraft_version" to minecraft_version,
        "minecraft_version_range" to minecraft_version_range,
        "neo_version" to neo_version,
        "neo_version_range" to neo_version_range,
        "loader_version_range" to loader_version_range,
        "kotlinforforge_version_range" to kotlinforforge_version_range,
        "mod_id" to mod_id,
        "mod_name" to mod_name,
        "mod_license" to mod_license,
        "mod_version" to mod_version,
        "mod_authors" to mod_authors,
        "mod_description" to mod_description,
    )
    inputs.properties(replaceProperties)

    filesMatching(listOf("META-INF/neoforge.mods.toml", "pack.mcmeta")) {
        expand(replaceProperties)
    }
}

tasks.jar {
    manifest {
        attributes(
            mapOf(
                "Specification-Title" to mod_id,
                "Specification-Vendor" to mod_authors,
                "Specification-Version" to "1",
                "Implementation-Title" to project.name,
                "Implementation-Version" to project.version,
                "Implementation-Vendor" to mod_authors,
                "Built-On-Minecraft" to minecraft_version,
            )
        )
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}
