package me.johardt.heracles.core;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestDraftTest {
    @Test
    void patchesPreserveUnknownFieldsAndNullRemovesAField() {
        QuestDraft draft = QuestDraft.open(JsonParser.parseString("""
            {"display":{"title":"Quest"},"custom":{"keep":true}}
            """).getAsJsonObject());
        draft.applyPatch(JsonParser.parseString("{\"display\":{\"title\":\"Edited\"}}").getAsJsonObject());
        draft.remove("display");

        assertTrue(draft.snapshot().has("custom"));
        assertFalse(draft.snapshot().has("display"));
        assertTrue(draft.isDirty());
    }
}
