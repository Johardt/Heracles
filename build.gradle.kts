plugins {
    `java-library`
    `maven-publish`
    id("net.neoforged.moddev") version "2.0.146"
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
    java.setSrcDirs(listOf("neoforge/main/java"))
    resources.setSrcDirs(listOf("common/src/main/resources", "neoforge/main/resources", "examples/heracles-demo"))
}

sourceSets.test {
    java.setSrcDirs(listOf("neoforge/test/java"))
    resources.setSrcDirs(listOf("neoforge/test/resources"))
}

repositories {
    mavenCentral()
    maven("https://maven.teamresourceful.com/repository/maven-public/")
    maven("https://maven.blamejared.com")
    maven("https://maven.shedaniel.me")
    maven("https://maven.architectury.dev")
}

dependencies {
    implementation("com.teamresourceful.resourcefullib:resourcefullib-neoforge-26.2:${property("resourcefulLibVersion")}")
    compileOnly("com.teamresourceful.resourcefulconfig:resourcefulconfig-neoforge-26.2:${property("resourcefulConfigVersion")}")
    runtimeOnly("com.teamresourceful.resourcefulconfig:resourcefulconfig-neoforge-26.2:${property("resourcefulConfigVersion")}")
    implementation("earth.terrarium.olympus:olympus-neoforge-26.2:${property("olympusVersion")}")
    jarJar("earth.terrarium.olympus:olympus-neoforge-26.2:${property("olympusVersion")}")
    compileOnly("mezz.jei:jei-26.2-common-api:30.32.0.221")
    compileOnly("mezz.jei:jei-26.2-neoforge-api:30.32.0.221")
    compileOnly("me.shedaniel:RoughlyEnoughItems-neoforge:26.2.821")
    // REI 26.2.821 declares Architectury 21.0.2, which references a NeoForge event
    // removed before the project's 26.2.0.86 target.  21.0.7 is the compatible
    // 26.2 NeoForge build and wins Gradle's same-module version selection.
    compileOnly("dev.architectury:architectury-neoforge:21.0.7")

    when (providers.gradleProperty("recipeViewer").orNull?.lowercase()) {
        "rei" -> {
            runtimeOnly("me.shedaniel:RoughlyEnoughItems-neoforge:26.2.821")
            runtimeOnly("dev.architectury:architectury-neoforge:21.0.7")
        }
        "jei" -> runtimeOnly("mezz.jei:jei-26.2-neoforge:30.32.0.221")
    }

    testImplementation(platform("org.junit:junit-bom:6.0.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("com.google.code.gson:gson:2.13.2")
    testCompileOnly(files(sourceSets.main.get().compileClasspath))
    testRuntimeOnly(files(sourceSets.main.get().runtimeClasspath))
    testRuntimeOnly(files(layout.buildDirectory.file(
        "moddev/artifacts/minecraft-patched-${property("neoForgeVersion")}-merged.jar"
    )))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
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
            if (providers.gradleProperty("openQuestScreen").isPresent) {
                systemProperty("heracles.openQuestScreen", "true")
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

tasks.test {
    useJUnitPlatform()
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
        }
    }
}
