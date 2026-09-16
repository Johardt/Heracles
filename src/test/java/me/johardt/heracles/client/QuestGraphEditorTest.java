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
        editor.panByScreenDelta(10, -5);
        editor.setZoom(1.5);

        QuestGraphEditor copy = editor.copy();
        editor.clearLink();
        editor.endPointerAction();

        assertEquals("prerequisite", copy.linkSourceId());
        assertEquals("quest", copy.selectedId());
        assertEquals(-10, copy.centerWorldX());
        assertEquals(5, copy.centerWorldY());
        assertEquals(1.5, copy.zoom());
        assertNull(editor.linkSourceId());
    }

    @Test
    void selectionCanBeClearedWhenTheDetailsDockCloses() {
        QuestGraphEditor editor = new QuestGraphEditor();
        editor.select("quest");

        editor.clearSelection();

        assertNull(editor.selectedId());
    }

    @Test
    void zoomAroundCursorKeepsTheWorldPointStableAndClamps() {
        QuestGraphLayout.CanvasBounds canvas = new QuestGraphLayout.CanvasBounds(0, 0, 300, 200);
        QuestGraphLayout.Point before = editorPoint(new QuestGraphEditor(), canvas, 210, 75);
        QuestGraphEditor editor = new QuestGraphEditor();

        editor.zoomAroundScreenPoint(canvas, 210, 75, 0.75);
        QuestGraphLayout.Point after = editorPoint(editor, canvas, 210, 75);

        assertEquals(before.x(), after.x(), 0.000001);
        assertEquals(before.y(), after.y(), 0.000001);
        editor.zoomAroundScreenPoint(canvas, 210, 75, -10);
        assertEquals(0.15, editor.zoom());
        editor.zoomAroundScreenPoint(canvas, 210, 75, 10);
        assertEquals(2.0, editor.zoom());
    }

    @Test
    void screenDeltaPanningScalesWithTheCurrentZoom() {
        QuestGraphEditor editor = new QuestGraphEditor();

        editor.setZoom(0.5);
        editor.panByScreenDelta(20, -10);
        assertEquals(-40, editor.centerWorldX());
        assertEquals(20, editor.centerWorldY());

        editor.setZoom(2.0);
        editor.panByScreenDelta(20, -10);
        assertEquals(-50, editor.centerWorldX());
        assertEquals(25, editor.centerWorldY());
    }

    @Test
    void chapterViewportsAreSavedRestoredAndCopied() {
        QuestGraphLayout.CanvasBounds canvas = new QuestGraphLayout.CanvasBounds(0, 0, 300, 200);
        QuestGraphLayout.WorldBounds large = new QuestGraphLayout.WorldBounds(-1000, -1000, 1000, 1000);

        QuestGraphEditor editor = new QuestGraphEditor();
        editor.activateChapter("one", canvas, large);
        editor.centerOn(120, -40);
        editor.setZoom(0.5);
        editor.activateChapter("two", canvas, QuestGraphLayout.WorldBounds.empty());
        editor.activateChapter("one", canvas, large);

        assertEquals(120, editor.centerWorldX());
        assertEquals(-40, editor.centerWorldY());
        assertEquals(0.5, editor.zoom());

        QuestGraphEditor copy = editor.copy();
        editor.centerOn(0, 0);
        assertEquals(120, copy.centerWorldX());
        assertEquals(-40, copy.centerWorldY());
        assertEquals(0.5, copy.zoom());
    }

    private static QuestGraphLayout.Point editorPoint(
        QuestGraphEditor editor,
        QuestGraphLayout.CanvasBounds canvas,
        double x,
        double y
    ) {
        return editor.screenToWorld(canvas, x, y);
    }
}
