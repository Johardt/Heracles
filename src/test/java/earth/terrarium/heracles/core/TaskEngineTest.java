package earth.terrarium.heracles.core;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TaskEngineTest {
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
}
