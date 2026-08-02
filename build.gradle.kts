plugins {
    `java-library`
    `maven-publish`
    id("net.neoforged.moddev") version "2.0.143"
    idea
}

version = property("modVersion") as String
group = property("modGroup") as String

base {
    archivesName.set("heracles-neoforge-${property("minecraftVersion")}")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
    withSourcesJar()
}

sourceSets.main {
    // The 1.21 implementation remains in common/ and neoforge/ as migration input.
    // Only the 26.2 bootstrap sources are compiled until their APIs are ported.
    java.setSrcDirs(listOf("src/main/java"))
    resources.setSrcDirs(listOf("common/src/main/resources", "neoforge/src/main/resources", "examples/heracles-demo"))
}

repositories {
    mavenCentral()
}

neoForge {
    version = property("neoForgeVersion") as String

    runs {
        create("client") {
            client()
            systemProperty("neoforge.enabledGameTestNamespaces", "heracles")
            providers.gradleProperty("quickPlayWorld").orNull?.let { world ->
                programArguments.addAll("--quickPlaySingleplayer", world)
            }
        }

        create("server") {
            server()
            programArgument("--nogui")
            systemProperty("neoforge.enabledGameTestNamespaces", "heracles")
        }

        configureEach {
            logLevel = org.slf4j.event.Level.INFO
        }
    }

    mods {
        create("heracles") {
            sourceSet(sourceSets.main.get())
        }
    }
}

val minecraftVersion: String by project
val neoForgeVersion: String by project

tasks.processResources {
    val replacements = mapOf(
        "version" to project.version,
        "minecraftVersion" to minecraftVersion,
        "neoForgeVersion" to neoForgeVersion,
    )
    inputs.properties(replacements)
    filesMatching("META-INF/neoforge.mods.toml") {
        expand(replacements)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
        }
    }
}
