package me.johardt.heracles.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** A lossless editable quest document used by editor workflows and clipboard transfers. */
public final class QuestDraft {
    private final JsonObject baseline;
    private JsonObject document;

    private QuestDraft(JsonObject source) {
        baseline = source.deepCopy();
        document = source.deepCopy();
    }

    public static QuestDraft open(JsonObject rawQuest) { return new QuestDraft(rawQuest); }
    public static QuestDraft parse(String rawQuest) { return open(JsonParser.parseString(rawQuest).getAsJsonObject()); }
    public JsonObject snapshot() { return document.deepCopy(); }
    public JsonObject baseline() { return baseline.deepCopy(); }
    public boolean isDirty() { return !baseline.equals(document); }

    /** Returns the loader-neutral projection used by forms and validation. */
    public QuestDefinition definition(String id) {
        return QuestDefinition.parse(id == null || id.isBlank() ? "draft" : id, document);
    }

    public JsonElement get(String field) {
        return document.get(field);
    }

    public boolean has(String field) {
        return document.has(field);
    }

    public void remove(String field) {
        if (field != null) document.remove(field);
    }

    /** Applies a shallow named patch while preserving fields not mentioned by the caller. */
    public void applyPatch(JsonObject patch) {
        if (patch == null) return;
        patch.entrySet().forEach(entry -> apply(entry.getKey(), entry.getValue()));
    }
    public QuestDraft copy() { return open(document); }

    /** Replaces one top-level field without discarding fields an editor does not understand. */
    public void apply(String field, JsonElement value) {
        if (field == null || field.isBlank()) throw new IllegalArgumentException("Field name is required");
        if (value == null) document.remove(field);
        else document.add(field, value.deepCopy());
    }
}
