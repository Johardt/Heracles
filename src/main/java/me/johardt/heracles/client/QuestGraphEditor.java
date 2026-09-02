package me.johardt.heracles.client;

/** Deep interaction state for graph selection, linking, panning, and zoom. */
public final class QuestGraphEditor {
    private String selectedId;
    private String linkSourceId;
    private String draggingQuestId;
    private int panX;
    private int panY;
    private double zoom = 1.0;
    private boolean panning;

    public String selectedId() { return selectedId; }
    public String linkSourceId() { return linkSourceId; }
    public String draggingQuestId() { return draggingQuestId; }
    public int panX() { return panX; }
    public int panY() { return panY; }
    public double zoom() { return zoom; }
    public boolean panning() { return panning; }

    public void select(String id) { selectedId = id; }
    public void linkFrom(String id) { linkSourceId = id; }
    public void clearLink() { linkSourceId = null; }
    public void beginPan() { panning = true; }
    public void endPointerAction() { panning = false; draggingQuestId = null; }
    public void dragQuest(String id) { draggingQuestId = id; }
    public void panBy(double x, double y) {
        panX += (int) Math.round(x / zoom);
        panY += (int) Math.round(y / zoom);
    }
    public void moveZoom(double amount) { zoom = Math.max(0.5, Math.min(2.0, zoom + amount)); }
    public void resetPan() { panX = 0; panY = 0; }
    public void setPanning(boolean value) { panning = value; }

    public QuestGraphEditor copy() {
        QuestGraphEditor copy = new QuestGraphEditor();
        copy.selectedId = selectedId;
        copy.linkSourceId = linkSourceId;
        copy.draggingQuestId = draggingQuestId;
        copy.panX = panX;
        copy.panY = panY;
        copy.zoom = zoom;
        copy.panning = panning;
        return copy;
    }
}
