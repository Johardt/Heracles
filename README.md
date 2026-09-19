# Theseus
A tree style questing mod allowing creators to set completable quests for their users.

> Theseus is an independent fork and continuation of Heracles for Minecraft 26.2 on NeoForge.
> It is not affiliated with or supported by the original Heracles maintainers.

Also see [Odysseus](https://github.com/terrarium-earth/odysseus), a Project Odyssey tool for converting FTB and HQM quest packs to the original Heracles format.

## NeoForge 26.2 fork

This branch targets Minecraft 26.2 on NeoForge. It requires a
Java 25 toolchain; Gradle can provision one automatically.

```shell
./gradlew build
./gradlew runClient
```

The 26.2-neoforge branch contains a playable NeoForge-native quest core. Start a
world and press `H` (or run `/theseus open`) to view quests. A six-quest demo
pack is installed automatically when `run/config/theseus/quests` is empty.

Useful commands:

```text
/theseus                  Show status
/theseus open             Open the quest screen
/theseus demo             Complete the bundled dummy task
/theseus dummy <value>    Complete a matching dummy task
/theseus claim <quest>    Claim a completed quest's rewards
/theseus submit <quest> <task>  Submit manual item, XP, or check tasks
/theseus reload           Reload quest JSON (game masters)
/theseus reset            Reset your progress (game masters)
```

The quest browser uses Olympus and renders group-specific node positions,
dependency paths, visibility states, panning, and zoom. The domain model retains
quest settings, every group placement, dependencies, typed task/reward maps, and
unrecognized JSON so content can round-trip while support expands.

The extensible task engine executes dummy/check, item, advancement, recipe,
statistic, structure, XP, entity-kill, block/entity/item interaction, item-use,
dimension, biome, and location tasks. Item and XP tasks support automatic,
consuming, and manual collection modes. Registry values accept exact IDs, tags,
and lists; item components and legacy NBT/player checks use recursive subset
matching. Location predicates support dimension, biome, and coordinate bounds.
Other mods can add handlers during initialization through
`QuestRuntime.registerTaskHandler(...)`; standalone engines can be composed with
`TaskEngine.builder()` or `TaskEngine.defaultBuilder()`.

Item and experience rewards are supported. Progress is saved per world and
synchronized to the client with native NeoForge payloads. The quest screen is
local to Theseus, so Hermes is not required.

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
    modImplementation "me.johardt.theseus:theseus-${modloader}-${mc_version}:${theseus_version}"
}
```
