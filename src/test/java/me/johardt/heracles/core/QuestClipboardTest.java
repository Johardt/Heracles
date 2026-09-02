package me.johardt.heracles.core;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestClipboardTest {
    @Test
    void cutDoesNotMutateSourceAndCopiesUnknownFields() {
        var clipboard = new QuestClipboard();
        var source = JsonParser.parseString("{\"custom\":{\"x\":1},\"tasks\":{}}").getAsJsonObject();
        clipboard.cut("source", source);
        var copied = clipboard.draft();
        copied.apply("custom", JsonParser.parseString("{\"x\":2}").getAsJsonObject());
        assertEquals(1, source.getAsJsonObject("custom").get("x").getAsInt());
        assertEquals(1, clipboard.draft().snapshot().getAsJsonObject("custom").get("x").getAsInt());
        assertEquals(2, copied.snapshot().getAsJsonObject("custom").get("x").getAsInt());
        assertTrue(clipboard.isMove());
    }

    @Test
    void transferSnapshotPreservesConfigurationButDropsRuntimeState() {
        var clipboard = new QuestClipboard();
        var source = JsonParser.parseString("""
            {"settings":{"repeatable":true},"dependencies":["prerequisite"],"groups":{"Main":{"position":[2,3]}},
             "tasks":{"unknown":{"type":"example:task","value":{"keep":true}}},"rewards":{"unknown":{"type":"example:reward"}},
             "progress":{"unknown":4},"complete":true,"custom":{"keep":"yes"}}
            """).getAsJsonObject();
        clipboard.copy("source", source);

        var transfer = clipboard.transferSnapshot();
        assertTrue(transfer.has("settings"));
        assertTrue(transfer.has("dependencies"));
        assertTrue(transfer.has("groups"));
        assertTrue(transfer.has("tasks"));
        assertTrue(transfer.has("rewards"));
        assertTrue(transfer.has("custom"));
        assertFalse(transfer.has("progress"));
        assertFalse(transfer.has("complete"));

        transfer.getAsJsonObject("custom").addProperty("keep", "changed");
        assertEquals("yes", clipboard.transferSnapshot().getAsJsonObject("custom").get("keep").getAsString());
    }

    @Test
    void captureRejectsMissingSourceData() {
        var clipboard = new QuestClipboard();
        assertThrows(IllegalArgumentException.class, () -> clipboard.copy("", new com.google.gson.JsonObject()));
        assertThrows(IllegalArgumentException.class, () -> clipboard.cut("quest", null));
    }
}
