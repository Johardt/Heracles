package me.johardt.heracles.core;

import com.google.gson.JsonObject;

/** Client-independent clipboard state. CUT is only a staged intent; it never deletes by itself. */
public final class QuestClipboard {
    private QuestDraft draft;
    private String sourceId;
    private boolean move;

    public void copy(String id, JsonObject quest) { capture(id, quest, false); }
    public void cut(String id, JsonObject quest) { capture(id, quest, true); }
    public boolean hasContent() { return draft != null; }
    public QuestDraft draft() { return draft == null ? null : draft.copy(); }
    public String sourceId() { return sourceId; }
    public boolean isMove() { return move; }
    /** Returns a lossless configuration snapshot without runtime-only progress fields. */
    public JsonObject transferSnapshot() {
        if (draft == null) return null;
        return draft.transferSnapshot();
    }
    public void clear() { draft = null; sourceId = null; move = false; }

    private void capture(String id, JsonObject quest, boolean move) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Clipboard source ID is required");
        if (quest == null) throw new IllegalArgumentException("Clipboard quest document is required");
        draft = QuestDraft.fromClientSnapshot(id, quest);
        sourceId = id;
        this.move = move;
    }
}
