# Heracles
A tree style questing mod allowing creators to set completable quests for their users

Also see [Odysseus](https://github.com/terrarium-earth/odysseus), a Project Odyssey tool for converting FTB and HQM quest-packs to the Heracles format.

## NeoForge 26.2 port

This branch targets Minecraft 26.2 and NeoForge 26.2.0.41-beta. It requires a
Java 25 toolchain; Gradle can provision one automatically.

```shell
./gradlew build
./gradlew runClient
```

The 26.2 bootstrap entrypoint is active and launches in a NeoForge client. The
1.21 quest implementation remains under `common/` and `neoforge/` as porting
input, but is intentionally not compiled until its Minecraft APIs and external
library dependencies have been migrated. Fabric support has been removed.

## For Mod Developers
<hr>

Be sure to add our maven to your `build.gradle`:
```gradle
repositories {
    maven { url = "https://maven.teamresourceful.com/repository/maven-public/" }
    <--- other repositories here --->
}
```
You can then add our mod as a dependency:

```gradle
dependencies {
    <--- Other dependencies here --->
    modImplementation "earth.terrarium.heracles:heracles-${modloader}-${mc_version}:${heracles_version}"
}
```
