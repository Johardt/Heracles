package me.johardt.theseus.core;

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
            {"display":{"icon":{"item":"missing:item"}},"tasks":{"bad":{"type":"theseus:item","item":"bad id","amount":0}}}
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
            {"tasks":{"item":{"type":"theseus:item","type":"theseus:check"}}}
            """));
    }

    @Test
    void rejectsCompositeDepthPastDefensiveLimit() {
        JsonObject root = new JsonObject();
        JsonObject tasks = new JsonObject(); root.add("tasks", tasks);
        JsonObject current = tasks;
        for (int depth = 0; depth <= QuestDiagnostics.MAX_NESTING_DEPTH; depth++) {
            JsonObject composite = new JsonObject(); composite.addProperty("type", "theseus:composite"); composite.addProperty("amount", 1);
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

    @Test
    void customIconsRemainNonBlockingAndItemOverridesAreValidatedRecursively() {
        JsonObject quest = JsonParser.parseString("""
            {
              "display":{"title":"Icons","icon":{"type":"example:animated","frames":[1]}},
              "tasks":{"outer":{"type":"theseus:composite","amount":1,"tasks":{
                "child":{"type":"theseus:check","icon":{"type":"theseus:item","item":"missing:item"}}
              }}},
              "rewards":{}
            }
            """).getAsJsonObject();

        var diagnostics = QuestDiagnostics.validate("icons", quest, item -> !item.startsWith("missing:"));

        assertTrue(diagnostics.stream().anyMatch(value -> value.code().equals("unknown_icon_type") && !value.blocksSave()));
        assertTrue(diagnostics.stream().anyMatch(value -> value.path().equals("tasks.outer.tasks.child.icon.item") && value.blocksSave()));
    }

    @Test
    void iconSizeDiagnosticsBlockInvalidImportsAndAcceptTheInclusiveRange() {
        JsonObject invalid = JsonParser.parseString("""
            {"display":{"title":"Icons","icon_size":65},"tasks":{},"rewards":{}}
            """).getAsJsonObject();
        var diagnostics = QuestDiagnostics.validate("icons", invalid);
        var size = diagnostics.stream().filter(value -> value.code().equals("invalid_icon_size")).findFirst().orElseThrow();
        assertTrue(size.blocksSave());
        assertEquals("display.icon_size", size.path());

        JsonObject valid = JsonParser.parseString("""
            {"display":{"title":"Icons","icon_size":8},"tasks":{},"rewards":{}}
            """).getAsJsonObject();
        assertTrue(QuestDiagnostics.validate("icons", valid).stream().noneMatch(value -> value.code().equals("invalid_icon_size")));
    }

    @Test
    void legacyDisplayValidationUsesTheSameIconSizeDiagnostic() {
        JsonObject draft = JsonParser.parseString("""
            {"title":"Icons","icon_size":7}
            """).getAsJsonObject();

        var diagnostics = QuestDiagnostics.validateDisplay(draft, null, ignored -> true);
        var size = diagnostics.stream().filter(value -> value.code().equals("invalid_icon_size")).findFirst().orElseThrow();
        assertEquals("display.icon_size", size.path());
        assertTrue(size.blocksSave());
    }
}
