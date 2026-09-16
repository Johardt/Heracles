package me.johardt.heracles.client;

import java.util.HashMap;
import java.util.Map;

/** Deep interaction state for graph selection, linking, panning, and zoom. */
public final class QuestGraphEditor {
    private String selectedId;
    private String linkSourceId;
    private String draggingQuestId;
    private QuestGraphLayout.ViewportState viewport = QuestGraphLayout.ViewportState.DEFAULT;
    private final Map<String, QuestGraphLayout.ViewportState> chapterViewports = new HashMap<>();
    private String activeChapter;
    private boolean panning;

    public String selectedId() { return selectedId; }
    public String linkSourceId() { return linkSourceId; }
    public String draggingQuestId() { return draggingQuestId; }
    public double centerWorldX() { return viewport.centerWorldX(); }
    public double centerWorldY() { return viewport.centerWorldY(); }
    public double zoom() { return viewport.zoom(); }
    public QuestGraphLayout.ViewportState viewportState() { return viewport; }
    public String activeChapter() { return activeChapter; }
    public boolean panning() { return panning; }

    public void select(String id) { selectedId = id; }
    public void clearSelection() { selectedId = null; }
    public void linkFrom(String id) { linkSourceId = id; }
    public void clearLink() { linkSourceId = null; }
    public void beginPan() { panning = true; }
    public void endPointerAction() { panning = false; draggingQuestId = null; }
    public void dragQuest(String id) { draggingQuestId = id; }

    /** Moves the world under the cursor by a screen-space drag delta. */
    public void panByScreenDelta(double screenDeltaX, double screenDeltaY) {
        viewport = new QuestGraphLayout.ViewportState(
            viewport.centerWorldX() - screenDeltaX / viewport.zoom(),
            viewport.centerWorldY() - screenDeltaY / viewport.zoom(),
            viewport.zoom()
        );
    }

    public void panBy(double screenDeltaX, double screenDeltaY) {
        panByScreenDelta(screenDeltaX, screenDeltaY);
    }

    public void centerOn(double worldX, double worldY) {
        viewport = new QuestGraphLayout.ViewportState(worldX, worldY, viewport.zoom());
    }

    public void setZoom(double value) {
        viewport = new QuestGraphLayout.ViewportState(
            viewport.centerWorldX(),
            viewport.centerWorldY(),
            value
        );
    }

    public void moveZoom(double amount) {
        setZoom(viewport.zoom() + amount);
    }

    /** Zooms around a screen point without changing the world point below it. */
    public void zoomAroundScreenPoint(
        QuestGraphLayout.CanvasBounds canvas,
        double screenX,
        double screenY,
        double amount
    ) {
        QuestGraphLayout.Point worldUnderCursor = screenToWorld(canvas, screenX, screenY);
        double nextZoom = QuestGraphLayout.clampZoom(viewport.zoom() + amount);
        if (nextZoom == viewport.zoom()) return;

        double nextCenterX = worldUnderCursor.x() -
            (screenX - canvas.centerX()) / nextZoom;
        double nextCenterY = worldUnderCursor.y() -
            (screenY - canvas.centerY()) / nextZoom;
        viewport = new QuestGraphLayout.ViewportState(nextCenterX, nextCenterY, nextZoom);
    }

    public QuestGraphLayout.Point screenToWorld(
        QuestGraphLayout.CanvasBounds canvas,
        double screenX,
        double screenY
    ) {
        return QuestGraphLayout.screenToWorld(canvas, viewport, screenX, screenY);
    }

    public QuestGraphLayout.Point worldToScreen(
        QuestGraphLayout.CanvasBounds canvas,
        double worldX,
        double worldY
    ) {
        return QuestGraphLayout.worldToScreen(canvas, viewport, worldX, worldY);
    }

    public QuestGraphLayout.WorldBounds visibleWorld(QuestGraphLayout.CanvasBounds canvas) {
        return QuestGraphLayout.visibleWorld(canvas, viewport);
    }

    public void fitToContent(
        QuestGraphLayout.CanvasBounds canvas,
        QuestGraphLayout.WorldBounds bounds
    ) {
        viewport = QuestGraphLayout.fitViewport(canvas, bounds);
        rememberActiveChapter();
    }

    /** Selects a chapter and gives first visits a useful deterministic viewport. */
    public void activateChapter(
        String chapter,
        QuestGraphLayout.CanvasBounds canvas,
        QuestGraphLayout.WorldBounds bounds
    ) {
        if (chapter == null) return;
        if (chapter.equals(activeChapter)) return;
        rememberActiveChapter();
        activeChapter = chapter;
        QuestGraphLayout.ViewportState saved = chapterViewports.get(chapter);
        if (saved != null) {
            viewport = saved;
        } else if (QuestGraphLayout.fitsAtZoomOne(canvas, bounds)) {
            viewport = QuestGraphLayout.ViewportState.DEFAULT;
        } else {
            viewport = QuestGraphLayout.fitViewport(canvas, bounds);
        }
        rememberActiveChapter();
    }

    public void saveChapterViewport(String chapter) {
        if (chapter != null) chapterViewports.put(chapter, viewport);
    }

    private void rememberActiveChapter() {
        if (activeChapter != null) chapterViewports.put(activeChapter, viewport);
    }

    public void setPanning(boolean value) { panning = value; }

    public QuestGraphEditor copy() {
        QuestGraphEditor copy = new QuestGraphEditor();
        copy.selectedId = selectedId;
        copy.linkSourceId = linkSourceId;
        copy.draggingQuestId = draggingQuestId;
        copy.viewport = viewport;
        copy.chapterViewports.putAll(chapterViewports);
        copy.activeChapter = activeChapter;
        copy.panning = panning;
        return copy;
    }
}
