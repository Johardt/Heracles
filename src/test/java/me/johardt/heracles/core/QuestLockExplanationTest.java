package me.johardt.heracles.core;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class QuestLockExplanationTest {
    @Test
    void listsIncompleteDependenciesAndMarksCurrentChapterTargetsSelectable() {
        QuestDefinition quest = QuestDefinition.parse("next", JsonParser.parseString("""
            {"dependencies":["first","missing"],"settings":{"hidden":"locked"}}
            """).getAsJsonObject());
        var result = QuestLockExplanation.explain(quest, Map.of(
            "first", new QuestLockExplanation.State("First steps", false, Set.of("Main"))
        ), "Main");

        assertEquals(QuestLockExplanation.Kind.DEPENDENCY, result.kind());
        assertEquals(2, result.blockers().size());
        assertTrue(result.blockers().getFirst().selectable());
        assertFalse(result.blockers().getLast().selectable());
    }

    @Test
    void identifiesPolicyLockWhenAllDependenciesAreComplete() {
        QuestDefinition quest = QuestDefinition.parse("next", JsonParser.parseString("""
            {"dependencies":"first","settings":{"hidden":"in_progress"}}
            """).getAsJsonObject());
        var result = QuestLockExplanation.explain(quest, Map.of(
            "first", new QuestLockExplanation.State("First", true, Set.of("Main"))
        ), "Main");

        assertEquals(QuestLockExplanation.Kind.POLICY, result.kind());
        assertTrue(result.summary().contains("progression policy"));
    }
}
