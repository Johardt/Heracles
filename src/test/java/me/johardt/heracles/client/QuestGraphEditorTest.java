package me.johardt.heracles.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class QuestGraphEditorTest {
    @Test
    void graphInteractionStateCanBeCopiedWithoutSharingMutableState() {
        QuestGraphEditor editor = new QuestGraphEditor();
        editor.select("quest");
        editor.linkFrom("prerequisite");
        editor.beginPan();
        editor.panBy(10, -5);
        editor.moveZoom(0.5);

        QuestGraphEditor copy = editor.copy();
        editor.clearLink();
        editor.endPointerAction();

        assertEquals("prerequisite", copy.linkSourceId());
        assertEquals("quest", copy.selectedId());
        assertEquals(10, copy.panX());
        assertEquals(-5, copy.panY());
        assertEquals(1.5, copy.zoom());
        assertNull(editor.linkSourceId());
    }
}
