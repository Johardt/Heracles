package me.johardt.heracles.core;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestDiagnosticsTest {
    @Test
    void returnsEveryBlockingDiagnosticAndNonBlockingWarnings() {
        JsonObject quest = JsonParser.parseString("""
            {"display":{"icon":{"item":"missing:item"}},"tasks":{"bad":{"type":"heracles:item","item":"bad id","amount":0}}}
            """).getAsJsonObject();

        var diagnostics = QuestDiagnostics.validate("Bad ID", quest, item -> false);

        assertTrue(diagnostics.stream().anyMatch(diagnostic -> diagnostic.code().equals("invalid_quest_id")));
        assertTrue(diagnostics.stream().anyMatch(diagnostic -> diagnostic.path().equals("display.icon.item")));
        assertTrue(diagnostics.stream().anyMatch(diagnostic -> diagnostic.path().equals("tasks.bad.amount")));
        assertTrue(diagnostics.stream().anyMatch(diagnostic -> diagnostic.code().equals("empty_rewards") && !diagnostic.blocksSave()));
    }

    @Test
    void detectsDuplicateKeysBeforeGsonCanDiscardThem() {
        assertEquals(java.util.List.of("$.tasks.item.type"), JsonDuplicateKeyDetector.findDuplicates("""
            {"tasks":{"item":{"type":"heracles:item","type":"heracles:check"}}}
            """));
    }

    @Test
    void rejectsCompositeDepthPastDefensiveLimit() {
        JsonObject root = new JsonObject();
        JsonObject tasks = new JsonObject(); root.add("tasks", tasks);
        JsonObject current = tasks;
        for (int depth = 0; depth <= QuestDiagnostics.MAX_NESTING_DEPTH; depth++) {
            JsonObject composite = new JsonObject(); composite.addProperty("type", "heracles:composite"); composite.addProperty("amount", 1);
            JsonObject children = new JsonObject(); composite.add("tasks", children); current.add("child" + depth, composite); current = children;
        }
        assertTrue(QuestDiagnostics.validate("quest", root).stream().anyMatch(diagnostic -> diagnostic.code().equals("nesting_too_deep")));
    }

    @Test
    void diagnosticWireFormatRemainsValidWhenBounded() {
        var diagnostics = java.util.List.of(
            new QuestDiagnostics.Diagnostic(QuestDiagnostics.Severity.ERROR, "bad", "quest", "tasks.a", "A recoverable message", "Fix it"),
            new QuestDiagnostics.Diagnostic(QuestDiagnostics.Severity.WARNING, "warn", "quest", "tasks.b", "Another message", null)
        );
        var decoded = QuestDiagnostics.decode(QuestDiagnostics.encode(diagnostics, 180));
        assertTrue(!decoded.isEmpty());
        assertEquals("bad", decoded.getFirst().code());
    }
}
