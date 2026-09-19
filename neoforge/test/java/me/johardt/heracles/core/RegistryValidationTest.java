package me.johardt.heracles.core;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RegistryValidationTest {
    @Test
    void validatesNestedTasksAndAllConcreteIdentifierKinds() {
        var root = JsonParser.parseString("""
            {"tasks":{"nested":{"type":"heracles:composite","tasks":{"biome":{"type":"heracles:biome","biomes":["minecraft:plains","missing:biome"]},"adv":{"type":"heracles:advancement","advancements":["missing:advancement"]}}}},"rewards":{"loot":{"type":"heracles:loottable","loot_table":"missing:table"}}}
            """).getAsJsonObject();

        var diagnostics = RegistryValidation.validate("quest", root, (target, id) -> id.startsWith("minecraft:"));

        assertTrue(diagnostics.stream().anyMatch(value -> value.path().equals("tasks.nested.tasks.biome.biomes[1]")));
        assertTrue(diagnostics.stream().anyMatch(value -> value.path().equals("tasks.nested.tasks.adv.advancements[0]")));
        assertTrue(diagnostics.stream().anyMatch(value -> value.path().equals("rewards.loot.loot_table")));
    }
}
