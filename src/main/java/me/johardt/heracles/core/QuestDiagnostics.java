package me.johardt.heracles.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Shared, lossless-editor validation.  This deliberately validates the raw
 * document: parsing it into {@link QuestDefinition} is still useful, but it
 * must not be the only editor validation path.
 */
public final class QuestDiagnostics {
    public static final int MAX_NESTING_DEPTH = 32;
    private static final EditorTypeRegistry EDITOR_TYPES = EditorTypeRegistry.defaults();

    private QuestDiagnostics() {}

    public static List<Diagnostic> validate(String questId, JsonObject root) {
        return validate(questId, root, value -> true);
    }

    public static List<Diagnostic> validate(
        String questId, JsonObject root, Predicate<String> validItem
    ) {
        List<Diagnostic> results = new ArrayList<>();
        if (questId == null || !questId.matches("[a-z0-9_.-]+")) {
            results.add(error("invalid_quest_id", questId, "id", "Quest ID must contain only lowercase letters, numbers, dots, underscores, or hyphens", "Choose a lowercase file name."));
        }
        requireObject(root, "display", questId, results);
        requireObject(root, "tasks", questId, results);
        requireObject(root, "rewards", questId, results);
        JsonObject display = object(root, "display");
        if (display.has("title") && (!display.get("title").isJsonPrimitive() || display.get("title").getAsString().trim().isEmpty())) {
            results.add(error("missing_title", questId, "display.title", "Quest title must not be empty", "Enter a title."));
        } else if (!display.has("title")) {
            results.add(error("missing_title", questId, "display.title", "Quest title is required", "Add a display.title field."));
        }
        if (display.has("icon")) {
            JsonObject icon = object(display, "icon");
            String item = string(icon, "item", "minecraft:map");
            if (!validItem.test(item)) results.add(error("unknown_item", questId, "display.icon.item", "Unknown item '" + item + "'", "Choose an item registered on this server."));
        }
        JsonObject tasks = object(root, "tasks");
        if (tasks.isEmpty()) results.add(warning("empty_tasks", questId, "tasks", "This quest completes immediately when unlocked", "Add a task if immediate completion is not intended."));
        if (object(root, "rewards").isEmpty()) results.add(warning("empty_rewards", questId, "rewards", "This quest has no rewards", "This is valid for progression-only quests."));
        try {
            addDefinitionIssues(questId, QuestDefinition.parse(questId == null ? "invalid" : questId, root), results);
        } catch (RuntimeException exception) {
            results.add(error("invalid_structure", questId, "$", "Quest structure could not be parsed: " + exception.getMessage(), "Fix the malformed field."));
        }
        validateEditorTypes(questId, tasks, "tasks", EditorTypeRegistry.Kind.TASK, results);
        validateEditorTypes(questId, object(root, "rewards"), "rewards", EditorTypeRegistry.Kind.REWARD, results);
        validateDepth(questId, tasks, "tasks", 1, results);
        return List.copyOf(results);
    }

    /** Compact wire representation used by editor acknowledgements. */
    public static String encode(List<Diagnostic> diagnostics) {
        return encode(diagnostics, Integer.MAX_VALUE);
    }

    public static String encode(List<Diagnostic> diagnostics, int maxLength) {
        JsonArray values = new JsonArray();
        if (diagnostics != null) for (Diagnostic diagnostic : diagnostics) {
            JsonObject value = new JsonObject();
            value.addProperty("severity", diagnostic.severity().name());
            value.addProperty("code", diagnostic.code());
            value.addProperty("questId", diagnostic.questId());
            value.addProperty("path", diagnostic.path());
            value.addProperty("message", diagnostic.message());
            if (diagnostic.suggestedFix() != null) value.addProperty("suggestedFix", diagnostic.suggestedFix());
            JsonArray candidate = values.deepCopy();
            candidate.add(value);
            if (candidate.toString().length() > maxLength && values.size() > 0) break;
            values.add(value);
        }
        return values.toString();
    }

    public static List<Diagnostic> decode(String encoded) {
        if (encoded == null || encoded.isBlank()) return List.of();
        try {
            JsonElement parsed = JsonParser.parseString(encoded);
            if (!parsed.isJsonArray()) return List.of();
            List<Diagnostic> values = new ArrayList<>();
            parsed.getAsJsonArray().forEach(value -> {
                if (!value.isJsonObject()) return;
                JsonObject object = value.getAsJsonObject();
                Severity severity;
                try { severity = Severity.valueOf(string(object, "severity", "INFO")); }
                catch (IllegalArgumentException ignored) { severity = Severity.INFO; }
                values.add(new Diagnostic(
                    severity,
                    string(object, "code", "diagnostic"),
                    string(object, "questId", ""),
                    string(object, "path", "$"),
                    string(object, "message", ""),
                    object.has("suggestedFix") ? string(object, "suggestedFix", "") : null
                ));
            });
            return List.copyOf(values);
        } catch (RuntimeException ignored) {
            return List.of();
        }
    }

    public static List<Diagnostic> validateDisplay(JsonObject draft, JsonObject changedFields, Predicate<String> validItem) {
        List<Diagnostic> results = new ArrayList<>();
        String title = string(draft, "title", "").trim();
        if (title.isEmpty()) results.add(error("missing_title", "", "title", "Quest title must not be empty", "Enter a title."));
        if (changedFields == null || changedFields.has("icon")) {
            String icon = string(draft, "icon", "minecraft:map");
            if (!validItem.test(icon)) results.add(error("unknown_item", "", "icon", "Invalid quest icon", "Choose an item registered on this server."));
        }
        if (changedFields == null || changedFields.has("background")) {
            String background = string(draft, "background", "");
            if (!background.matches("heracles:textures/gui/quest_backgrounds/[a-z0-9_-]+\\.png")) results.add(error("invalid_background", "", "background", "Invalid quest background", "Choose a built-in quest background."));
        }
        return List.copyOf(results);
    }

    private static void addDefinitionIssues(String questId, QuestDefinition definition, List<Diagnostic> results) {
        definition.issues().forEach(issue -> results.add(new Diagnostic(
            issue.severity() == QuestDefinition.Severity.ERROR ? Severity.ERROR : Severity.WARNING,
            "invalid_" + issue.path().replaceAll("[^a-zA-Z0-9]+", "_").replaceAll("_$", ""), questId, issue.path(), issue.message(), null
        )));
    }

    private static void validateDepth(String questId, JsonObject tasks, String path, int depth, List<Diagnostic> results) {
        if (depth > MAX_NESTING_DEPTH) {
            results.add(error("nesting_too_deep", questId, path, "Composite task nesting exceeds " + MAX_NESTING_DEPTH, "Flatten this composite task."));
            return;
        }
        tasks.entrySet().forEach(entry -> {
            if (!entry.getValue().isJsonObject()) return;
            JsonObject task = entry.getValue().getAsJsonObject();
            if ("heracles:composite".equals(string(task, "type", ""))) validateDepth(questId, object(task, "tasks"), path + "." + entry.getKey() + ".tasks", depth + 1, results);
        });
    }

    private static void validateEditorTypes(
        String questId,
        JsonObject values,
        String path,
        EditorTypeRegistry.Kind kind,
        List<Diagnostic> results
    ) {
        values.entrySet().forEach(entry -> {
            if (!entry.getValue().isJsonObject()) return;
            JsonObject value = entry.getValue().getAsJsonObject();
            String type = string(value, "type", "");
            EditorTypeRegistry.Descriptor descriptor = EDITOR_TYPES.resolve(kind, type);
            if (!descriptor.editable()) {
                results.add(new Diagnostic(
                    Severity.WARNING,
                    "unknown_" + kind.name().toLowerCase(java.util.Locale.ROOT) + "_type",
                    questId,
                    path + "." + entry.getKey() + ".type",
                    descriptor.availabilityReason() + ": " + type,
                    "Keep this value read-only or install an editor for the type."
                ));
            }
            if (kind == EditorTypeRegistry.Kind.TASK && "heracles:composite".equals(type)) {
                validateEditorTypes(questId, object(value, "tasks"), path + "." + entry.getKey() + ".tasks", kind, results);
            } else if (kind == EditorTypeRegistry.Kind.REWARD && "heracles:selectable".equals(type)) {
                validateEditorTypes(questId, object(value, "rewards"), path + "." + entry.getKey() + ".rewards", kind, results);
            }
        });
    }

    private static JsonObject object(JsonObject root, String key) { return root.has(key) && root.get(key).isJsonObject() ? root.getAsJsonObject(key) : new JsonObject(); }
    private static void requireObject(JsonObject root, String key, String questId, List<Diagnostic> results) {
        if (!root.has(key)) results.add(error("missing_" + key, questId, key, "Required field '" + key + "' is missing", "Add a JSON object for '" + key + "'."));
        else if (!root.get(key).isJsonObject()) results.add(error("invalid_" + key, questId, key, "Field '" + key + "' must be an object", "Replace it with a JSON object."));
    }
    private static String string(JsonObject root, String key, String fallback) { return root.has(key) && root.get(key).isJsonPrimitive() ? root.get(key).getAsString() : fallback; }
    private static Diagnostic error(String code, String id, String path, String message, String fix) { return new Diagnostic(Severity.ERROR, code, id, path, message, fix); }
    private static Diagnostic warning(String code, String id, String path, String message, String fix) { return new Diagnostic(Severity.WARNING, code, id, path, message, fix); }

    public enum Severity { INFO, WARNING, ERROR }
    public record Diagnostic(Severity severity, String code, String questId, String path, String message, String suggestedFix) {
        public boolean blocksSave() { return severity == Severity.ERROR; }
    }
}
