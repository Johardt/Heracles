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

The 26.2 branch now contains a small, playable NeoForge-native quest core. Start
a world and press `H` (or run `/heracles open`) to view quests. A three-quest demo
pack is installed automatically when `run/config/heracles/quests` is empty.

Useful commands:

```text
/heracles                  Show status
/heracles open             Open the quest screen
/heracles demo             Complete the bundled dummy task
/heracles dummy <value>    Complete a matching dummy task
/heracles claim <quest>    Claim a completed quest's rewards
/heracles reload           Reload quest JSON (game masters)
/heracles reset            Reset your progress (game masters)
```

The quest browser uses Olympus 1.9.1 and renders group-specific node positions,
dependency paths, visibility states, panning, and zoom. The domain model retains
quest settings, every group placement, dependencies, typed task/reward maps, and
the original JSON for unsupported types so they can be implemented incrementally.

This milestone executes legacy-format `heracles:dummy` and `heracles:item`
tasks, plus item and experience rewards. Item tasks update from player
inventories, progress is saved per world, and server progress is synchronized
to the client with native NeoForge payloads. The basic quest screen is local to
Heracles, so Hermes is not required.

The original 1.21 implementation remains under `common/` and `neoforge/` as
porting input. Its editor, richer executable task/reward types, minimap, and
polished description rendering have not yet been migrated. Fabric support has
been removed.

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
