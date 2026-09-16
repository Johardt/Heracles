package me.johardt.heracles.core;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestProgressStateTest {
    private static QuestDefinition quest() {
        return QuestDefinition.parse("quest", JsonParser.parseString("""
            {
              "tasks": {
                "root": {
                  "type": "heracles:composite",
                  "amount": 1,
                  "tasks": {
                    "leaf": {"type": "heracles:check"},
                    "branch": {
                      "type": "heracles:composite",
                      "amount": 1,
                      "tasks": {"nested": {"type": "heracles:check"}}
                    }
                  }
                }
              },
              "rewards": {
                "first": {"type": "heracles:xp", "amount": 1},
                "second": {"type": "heracles:xp", "amount": 1},
                "choice": {"type": "heracles:selectable", "rewards": {"one": {"type": "heracles:xp", "amount": 1}}}
              }
            }
            """).getAsJsonObject());
    }

    @Test
    void migratesLegacyClaimedBooleanToCurrentTopLevelRewards() {
        QuestProgressState state = QuestProgressState.fromJson(quest(), JsonParser.parseString("""
            {"tasks":{"root/leaf":1},"claimed":true,"pinned":true}
            """).getAsJsonObject());

        assertEquals(Set.of("first", "second", "choice"), state.claimedRewards());
        assertTrue(state.allRewardsClaimed(quest()));
        assertTrue(state.isPinned());
        assertFalse(state.toJson().has("claimed"));
        assertEquals(3, state.toJson().getAsJsonArray("claimed_rewards").size());
    }

    @Test
    void prefersNewClaimedRewardsAndRoundTripsIt() {
        QuestDefinition quest = quest();
        QuestProgressState state = QuestProgressState.fromJson(quest, JsonParser.parseString("""
            {"claimed":true,"claimed_rewards":["second"],"pinned":false}
            """).getAsJsonObject());

        QuestProgressState roundTrip = QuestProgressState.fromJson(quest, state.toJson());

        assertEquals(Set.of("second"), roundTrip.claimedRewards());
        assertFalse(roundTrip.allRewardsClaimed(quest));
    }

    @Test
    void resetTaskPathRemovesOnlyThePathAndItsDescendants() {
        QuestProgressState state = new QuestProgressState();
        state.setTaskProgress("root", 1);
        state.setTaskProgress("root/leaf", 1);
        state.setTaskProgress("root/branch", 1);
        state.setTaskProgress("root/branch/nested", 1);
        state.setTaskProgress("other", 2);
        state.markRewardClaimed("first");
        state.setPinned(true);

        assertTrue(state.resetTaskPath("root/branch"));
        assertEquals(1, state.getTaskProgress("root"));
        assertEquals(1, state.getTaskProgress("root/leaf"));
        assertEquals(0, state.getTaskProgress("root/branch"));
        assertEquals(0, state.getTaskProgress("root/branch/nested"));
        assertEquals(2, state.getTaskProgress("other"));
        assertEquals(Set.of("first"), state.claimedRewards());

        state.clearProgress();
        assertTrue(state.taskProgress().isEmpty());
        assertTrue(state.claimedRewards().isEmpty());
        assertTrue(state.isPinned());
    }

    @Test
    void progressionOnlyQuestIsNeverMarkedClaimed() {
        QuestDefinition noRewards = QuestDefinition.parse("quest", JsonParser.parseString("{\"tasks\":{}}").getAsJsonObject());
        QuestProgressState state = QuestProgressState.fromJson(noRewards, JsonParser.parseString("{\"claimed\":true}").getAsJsonObject());

        assertTrue(state.claimedRewards().isEmpty());
        assertFalse(state.allRewardsClaimed(noRewards));
    }
}
