package me.johardt.heracles.client;

import com.google.gson.JsonObject;
import me.johardt.heracles.core.QuestDraft;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestAuthoringSessionTest {
    @Test
    void composesTheAuthoringStateIntoOneLosslessDraft() {
        QuestAuthoringSession session = new QuestAuthoringSession(16);
        session.begin(QuestDraft.create(null));
        session.id = "first_quest";
        session.title = "First quest";
        session.iconSize = 24;
        session.iconSizeTouched = true;
        JsonObject task = new JsonObject();
        task.addProperty("type", "heracles:dummy");
        session.tasks.add(new QuestAuthoringSession.TaskDraft("check", "heracles:dummy", task));

        QuestDraft draft = session.draft();

        assertEquals("first_quest", draft.id());
        assertEquals("First quest", draft.definition().title());
        assertEquals(24, draft.definition().display().iconSize());
        assertTrue(draft.snapshot().getAsJsonObject("tasks").has("check"));
        assertTrue(draft.isDirty());
    }

    @Test
    void copyDetachesNestedEditorState() {
        QuestAuthoringSession session = new QuestAuthoringSession(16);
        session.begin(QuestDraft.create(null));
        JsonObject source = new JsonObject();
        source.addProperty("type", "heracles:item");
        session.rewards.add(new QuestAuthoringSession.RewardDraft("reward", "heracles:item", source));

        QuestAuthoringSession copy = session.copy();
        copy.rewards.getFirst().source.addProperty("item", "minecraft:diamond");
        copy.discard();

        assertFalse(session.rewards.getFirst().source.has("item"));
        assertTrue(session.open);
        assertFalse(copy.open);
    }
}
