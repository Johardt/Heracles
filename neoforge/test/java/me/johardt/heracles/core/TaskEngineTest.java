package me.johardt.heracles.core;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TaskEngineTest {
    @Test
    void legacyPassiveTaskFixtureLoadsWithoutUnsupportedWarnings() throws Exception {
        String json;
        try (var stream = getClass().getResourceAsStream("/fixtures/legacy_tasks.json")) {
            json = new String(java.util.Objects.requireNonNull(stream).readAllBytes(), StandardCharsets.UTF_8);
        }

        QuestDefinition quest = QuestDefinition.parse("legacy", JsonParser.parseString(json).getAsJsonObject());

        assertEquals(5, quest.tasks().size());
        assertEquals(java.util.List.of(), quest.issues());
    }

    @Test
    void legacyNonManualItemTaskConsumesMatchingItems() {
        QuestDefinition.Task task = task("""
            {"type":"heracles:item","item":"minecraft:oak_log","amount":4,"manual":false}
            """);

        TaskEngine.Result result = TaskEngine.defaults().apply(task, 0, new TaskEngine.Signal.Inventory("minecraft:oak_log", 4, false));

        assertEquals(new TaskEngine.Result(4, 4), result);
    }


    @Test
    void thirdPartyHandlerCanBeRegisteredWithoutEditingTheEngine() {
        QuestDefinition.Task task = task("""
            {"type":"example:counter","amount":4}
            """);
        TaskEngine engine = TaskEngine.builder()
            .register("example:counter", (definition, progress, signal) -> new TaskEngine.Result(progress + ((CustomSignal) signal).amount(), 0))
            .build();

        TaskEngine.Result result = engine.apply(task, 1, new CustomSignal(2));

        assertEquals(new TaskEngine.Result(3, 0), result);
    }

    @Test
    void itemTaskAcceptsARegistryTagAndRequiredComponents() {
        QuestDefinition.Task task = task("""
            {"type":"heracles:item","item":{"tag":"minecraft:logs"},"components":{"minecraft:custom_name":"Quest Log"},"amount":2}
            """);
        TaskEngine.Signal.RegistryEntry stack = new TaskEngine.Signal.RegistryEntry(
            "minecraft:oak_log", Set.of("minecraft:logs"),
            JsonParser.parseString("{\"minecraft:custom_name\":\"Quest Log\",\"minecraft:max_stack_size\":64}").getAsJsonObject(), 3
        );

        TaskEngine.Result result = TaskEngine.defaults().apply(task, 0, new TaskEngine.Signal.Inventory(java.util.List.of(stack), false));

        assertEquals(new TaskEngine.Result(2, 0), result);
    }

    @Test
    void recipeTaskCompletesForAnyConfiguredRecipe() {
        QuestDefinition.Task task = task("""
            {"type":"heracles:recipe","recipes":["minecraft:bread","minecraft:crafting_table"]}
            """);

        TaskEngine.Result result = TaskEngine.defaults().apply(task, 0, new TaskEngine.Signal.RecipeUnlocked("minecraft:bread"));

        assertEquals(new TaskEngine.Result(1, 0), result);
    }

    @Test
    void statisticTaskTracksTheHighestObservedValue() {
        QuestDefinition.Task task = task("""
            {"type":"heracles:stat","stat":"minecraft:jump","target":10}
            """);

        TaskEngine.Result result = TaskEngine.defaults().apply(task, 4, new TaskEngine.Signal.Statistic("minecraft:jump", 7));

        assertEquals(new TaskEngine.Result(7, 0), result);
    }

    @Test
    void structureTaskAcceptsLegacyRegistryTag() {
        QuestDefinition.Task task = task("""
            {"type":"heracles:structure","structures":{"tag":"minecraft:village"}}
            """);
        TaskEngine.Signal.RegistryEntry structure = new TaskEngine.Signal.RegistryEntry(
            "minecraft:village_plains", Set.of("minecraft:village"), new com.google.gson.JsonObject(), 1
        );

        TaskEngine.Result result = TaskEngine.defaults().apply(task, 0, new TaskEngine.Signal.Structures(Set.of(structure)));

        assertEquals(new TaskEngine.Result(1, 0), result);
    }

    @Test
    void checkTaskCompletesWhenPlayerDataContainsLegacyPredicate() {
        QuestDefinition.Task task = task("""
            {"type":"heracles:check","nbt":{"abilities":{"mayfly":true}}}
            """);
        var playerData = JsonParser.parseString("{\"abilities\":{\"mayfly\":true,\"flying\":false}}").getAsJsonObject();

        TaskEngine.Result result = TaskEngine.defaults().apply(task, 0, new TaskEngine.Signal.Check(playerData, true));

        assertEquals(new TaskEngine.Result(1, 0), result);
    }

    @Test
    void nestedDemoTaskCompletesThroughEitherSupportedRoute() {
        QuestDefinition.Task composite = task("""
            {
              "type":"heracles:composite",
              "amount":1,
              "tasks":{
                "dummy":{"type":"heracles:dummy","value":"reward_showcase"},
                "check":{"type":"heracles:check"}
              }
            }
            """);
        TaskEngine engine = TaskEngine.defaults();

        assertEquals(new TaskEngine.Result(1, 0), engine.apply(
            composite.tasks().get("dummy"), 0, new TaskEngine.Signal.Manual("reward_showcase")));
        assertEquals(new TaskEngine.Result(1, 0), engine.apply(
            composite.tasks().get("check"), 0, new TaskEngine.Signal.Check(true)));
    }

    @Test
    void matchingKillSignalAdvancesRegisteredTask() {
        QuestDefinition.Task task = task("""
            {"type":"heracles:kill_entity","entity":"minecraft:zombie","amount":3}
            """);

        TaskEngine.Result result = TaskEngine.defaults().apply(task, 1, new TaskEngine.Signal.EntityKilled("minecraft:zombie"));

        assertEquals(new TaskEngine.Result(2, 0), result);
    }

    @Test
    void consumingItemTaskRequestsOnlyItsRemainingItems() {
        QuestDefinition.Task task = task("""
            {"type":"heracles:item","item":"minecraft:oak_log","amount":8,"collection":"consume"}
            """);

        TaskEngine.Result result = TaskEngine.defaults().apply(task, 3, new TaskEngine.Signal.Inventory("minecraft:oak_log", 12, true));

        assertEquals(new TaskEngine.Result(8, 5), result);
    }

    @Test
    void advancementTaskCompletesForAnyConfiguredAdvancement() {
        QuestDefinition.Task task = task("""
            {"type":"heracles:advancement","advancements":["minecraft:story/mine_stone","minecraft:story/smelt_iron"]}
            """);

        TaskEngine.Result result = TaskEngine.defaults().apply(task, 0, new TaskEngine.Signal.AdvancementGranted("minecraft:story/smelt_iron"));

        assertEquals(new TaskEngine.Result(1, 0), result);
    }

    @Test
    void automaticXpTaskTracksTheConfiguredXpUnit() {
        QuestDefinition.Task task = task("""
            {"type":"heracles:xp","amount":12,"xpType":"points","collectionType":"automatic"}
            """);

        TaskEngine.Result result = TaskEngine.defaults().apply(task, 2, new TaskEngine.Signal.Experience(3, 9, false));

        assertEquals(new TaskEngine.Result(9, 0), result);
    }

    @Test
    void blockInteractionCompletesOnlyForConfiguredBlock() {
        QuestDefinition.Task task = task("""
            {"type":"heracles:block_interaction","block":"minecraft:crafting_table"}
            """);

        TaskEngine.Result result = TaskEngine.defaults().apply(task, 0, new TaskEngine.Signal.BlockInteracted("minecraft:crafting_table"));

        assertEquals(new TaskEngine.Result(1, 0), result);
    }

    @Test
    void biomeTaskCompletesFromCurrentWorldState() {
        QuestDefinition.Task task = task("""
            {"type":"heracles:biome","biomes":"minecraft:desert"}
            """);

        TaskEngine.Result result = TaskEngine.defaults().apply(task, 0, new TaskEngine.Signal.WorldState("minecraft:overworld", "minecraft:desert", 10, 64, -20));

        assertEquals(new TaskEngine.Result(1, 0), result);
    }

    @Test
    void dimensionTaskHonorsBothOptionalEndpoints() {
        QuestDefinition.Task task = task("""
            {"type":"heracles:changed_dimension","from":"minecraft:overworld","to":"minecraft:the_nether"}
            """);

        TaskEngine.Result result = TaskEngine.defaults().apply(task, 0, new TaskEngine.Signal.DimensionChanged("minecraft:overworld", "minecraft:the_nether"));

        assertEquals(new TaskEngine.Result(1, 0), result);
    }

    @Test
    void locationTaskEvaluatesDimensionAndCoordinateBounds() {
        QuestDefinition.Task task = task("""
            {"type":"heracles:location","predicate":{"dimension":"minecraft:overworld","position":{"x":{"min":0,"max":10},"y":{"min":60},"z":{"max":0}}}}
            """);

        TaskEngine.Result result = TaskEngine.defaults().apply(task, 0, new TaskEngine.Signal.WorldState("minecraft:overworld", "minecraft:plains", 5, 64, -20));

        assertEquals(new TaskEngine.Result(1, 0), result);
    }

    private static QuestDefinition.Task task(String json) {
        return QuestDefinition.parse("test", JsonParser.parseString("""
            {"display":{"groups":{"Main":{"position":[0,0]}}},"tasks":{"task":%s}}
            """.formatted(json)).getAsJsonObject()).tasks().get("task");
    }

    private record CustomSignal(int amount) implements TaskEngine.Signal {}
}
