package me.johardt.heracles.core;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestDefinitionCompatibilityTest {
    @Test
    void bundledRewardShowcaseIsValid() throws Exception {
        String json;
        try (var stream = getClass().getResourceAsStream("/config/heracles/quests/getting_started/reward_showcase.json")) {
            json = new String(java.util.Objects.requireNonNull(stream).readAllBytes(), StandardCharsets.UTF_8);
        }
        QuestDefinition quest = parse(json);
        assertTrue(quest.issues().isEmpty(), () -> quest.issues().toString());
    }

    @Test
    void parsesCompositeTasksAndAllBuiltInRewardTypes() {
        QuestDefinition quest = parse("""
            {
              "tasks": {
                "combined": {
                  "type": "heracles:composite",
                  "amount": 1,
                  "tasks": {
                    "logs": {"type":"heracles:item","item":"minecraft:oak_log","amount":2},
                    "check": {"type":"heracles:check"}
                  }
                }
              },
              "rewards": {
                "command": {"type":"heracles:command","command":"say complete"},
                "loot": {"type":"heracles:loottable","loot_table":"minecraft:chests/simple_dungeon"},
                "choice": {
                  "type":"heracles:selectable",
                  "amount":1,
                  "rewards": {
                    "item":{"type":"heracles:item","item":{"id":"minecraft:diamond","count":1}},
                    "xp":{"type":"heracles:xp","amount":3}
                  }
                }
              }
            }
            """);

        QuestDefinition.Task composite = quest.tasks().get("combined");
        assertEquals(QuestDefinition.TaskKind.COMPOSITE, composite.kind());
        assertEquals(2, composite.tasks().size());
        assertEquals(QuestDefinition.RewardKind.COMMAND, quest.rewards().get("command").kind());
        assertEquals(QuestDefinition.RewardKind.LOOT_TABLE, quest.rewards().get("loot").kind());
        assertEquals(2, quest.rewards().get("choice").rewards().size());
        assertTrue(quest.issues().isEmpty(), () -> quest.issues().toString());
    }

    @Test
    void preservesUnsupportedTypesAsWarnings() {
        QuestDefinition quest = parse("""
            {
              "tasks":{"custom":{"type":"example:machine_task"}},
              "rewards":{"custom":{"type":"example:currency_reward"}}
            }
            """);

        assertEquals(QuestDefinition.TaskKind.UNSUPPORTED, quest.tasks().get("custom").kind());
        assertEquals(QuestDefinition.RewardKind.UNSUPPORTED, quest.rewards().get("custom").kind());
        assertEquals(2, quest.issues().stream().filter(issue -> issue.severity() == QuestDefinition.Severity.WARNING).count());
    }

    @Test
    void reportsPreciseMalformedNestedPaths() {
        QuestDefinition quest = parse("""
            {
              "display":{"groups":{"Main":{"position":[0]}}},
              "tasks":{"combined":{"type":"heracles:composite","amount":3,"tasks":{"bad":false}}},
              "rewards":{"choice":{"type":"heracles:selectable","amount":2,"rewards":{}}}
            }
            """);

        assertTrue(quest.issues().stream().anyMatch(issue -> issue.path().equals("display.groups.Main.position")));
        assertTrue(quest.issues().stream().anyMatch(issue -> issue.path().equals("tasks.combined.tasks.bad")));
        assertTrue(quest.issues().stream().anyMatch(issue -> issue.path().equals("rewards.choice.rewards")));
    }

    private static QuestDefinition parse(String json) {
        return QuestDefinition.parse("compatibility", JsonParser.parseString(json).getAsJsonObject());
    }
}
