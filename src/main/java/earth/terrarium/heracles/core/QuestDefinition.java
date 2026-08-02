package earth.terrarium.heracles.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The loader-neutral quest model shared by gameplay, networking, and UI. */
public record QuestDefinition(
    String id,
    Display display,
    Settings settings,
    Set<String> dependencies,
    Map<String, Task> tasks,
    Map<String, Reward> rewards,
    List<ValidationIssue> issues
) {
    public static QuestDefinition parse(String id, JsonObject root) {
        List<ValidationIssue> issues = new ArrayList<>();
        JsonObject displayJson = object(root, "display");
        Map<String, GroupDisplay> groups = new LinkedHashMap<>();
        object(displayJson, "groups").entrySet().forEach(entry -> {
            JsonObject group = entry.getValue().getAsJsonObject();
            JsonArray position = array(group, "position");
            groups.put(entry.getKey(), new GroupDisplay(
                position.size() > 0 ? position.get(0).getAsInt() : 0,
                position.size() > 1 ? position.get(1).getAsInt() : 0
            ));
        });
        if (groups.isEmpty()) groups.put("Main", new GroupDisplay(0, 0));

        Map<String, Task> tasks = new LinkedHashMap<>();
        object(root, "tasks").entrySet().forEach(entry -> {
            JsonObject json = entry.getValue().getAsJsonObject();
            String type = string(json, "type", "heracles:unknown");
            TaskKind kind = TaskKind.from(type);
            if (kind == TaskKind.UNSUPPORTED) {
                issues.add(new ValidationIssue(Severity.WARNING, "tasks." + entry.getKey(), "Unsupported task type " + type));
            }
            int target = kind == TaskKind.ITEM ? positiveInteger(json, "amount", 1, issues, "tasks." + entry.getKey() + ".amount") : 1;
            String value = kind == TaskKind.ITEM ? string(json, "item", "minecraft:air") : string(json, "value", "");
            tasks.put(entry.getKey(), new Task(entry.getKey(), type, kind, string(json, "title", entry.getKey()), value, target, json.deepCopy()));
        });

        Map<String, Reward> rewards = new LinkedHashMap<>();
        object(root, "rewards").entrySet().forEach(entry -> {
            JsonObject json = entry.getValue().getAsJsonObject();
            String type = string(json, "type", "heracles:unknown");
            RewardKind kind = RewardKind.from(type);
            if (kind == RewardKind.UNSUPPORTED) {
                issues.add(new ValidationIssue(Severity.WARNING, "rewards." + entry.getKey(), "Unsupported reward type " + type));
            }
            RewardValue value = rewardValue(kind, json, issues, "rewards." + entry.getKey());
            rewards.put(entry.getKey(), new Reward(entry.getKey(), type, kind, string(json, "title", entry.getKey()), value.value(), value.amount(), json.deepCopy()));
        });

        JsonObject settingsJson = object(root, "settings");
        Settings settings = new Settings(
            bool(settingsJson, "individual_progress", false),
            Visibility.from(string(settingsJson, "hidden", "locked")),
            bool(settingsJson, "unlockNotification", false),
            bool(settingsJson, "showDependencyArrow", true),
            bool(settingsJson, "repeatable", false),
            bool(settingsJson, "autoClaimRewards", false)
        );

        return new QuestDefinition(
            id,
            new Display(
                icon(displayJson),
                string(displayJson, "icon_background", "heracles:textures/gui/quest_backgrounds/default.png"),
                componentText(displayJson.get("title"), id),
                componentText(displayJson.get("subtitle"), ""),
                strings(displayJson.get("description")),
                Map.copyOf(groups)
            ),
            settings,
            Set.copyOf(new LinkedHashSet<>(strings(root.get("dependencies")))),
            Map.copyOf(tasks),
            Map.copyOf(rewards),
            List.copyOf(issues)
        );
    }

    public String title() { return display.title(); }
    public String subtitle() { return display.subtitle(); }
    public List<String> description() { return display.description(); }
    public GroupDisplay position(String group) { return display.groups().getOrDefault(group, new GroupDisplay(0, 0)); }

    public Identifier itemId(Task task) {
        return Identifier.parse(task.value());
    }

    private static String icon(JsonObject display) {
        JsonObject icon = object(display, "icon");
        return string(icon, "item", "minecraft:map");
    }

    private static RewardValue rewardValue(RewardKind kind, JsonObject json, List<ValidationIssue> issues, String path) {
        if (kind == RewardKind.XP) {
            return new RewardValue(string(json, "xptype", "level"), positiveInteger(json, "amount", 1, issues, path + ".amount"));
        }
        if (kind == RewardKind.ITEM) {
            JsonElement item = json.get("item");
            if (item != null && item.isJsonObject()) {
                return new RewardValue(string(item.getAsJsonObject(), "id", "minecraft:air"), positiveInteger(item.getAsJsonObject(), "count", 1, issues, path + ".item.count"));
            }
            return new RewardValue(item == null ? "minecraft:air" : item.getAsString(), 1);
        }
        return new RewardValue("", 1);
    }

    private static int positiveInteger(JsonObject object, String key, int fallback, List<ValidationIssue> issues, String path) {
        int value = integer(object, key, fallback);
        if (value > 0) return value;
        issues.add(new ValidationIssue(Severity.ERROR, path, "Value must be positive"));
        return fallback;
    }

    private static JsonObject object(JsonObject object, String key) {
        return object.has(key) && object.get(key).isJsonObject() ? object.getAsJsonObject(key) : new JsonObject();
    }

    private static JsonArray array(JsonObject object, String key) {
        return object.has(key) && object.get(key).isJsonArray() ? object.getAsJsonArray(key) : new JsonArray();
    }

    private static String string(JsonObject object, String key, String fallback) {
        return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsString() : fallback;
    }

    private static boolean bool(JsonObject object, String key, boolean fallback) {
        return object.has(key) ? object.get(key).getAsBoolean() : fallback;
    }

    private static int integer(JsonObject object, String key, int fallback) {
        return object.has(key) ? object.get(key).getAsInt() : fallback;
    }

    private static List<String> strings(JsonElement element) {
        if (element == null) return List.of();
        if (element.isJsonArray()) {
            List<String> values = new ArrayList<>();
            element.getAsJsonArray().forEach(value -> values.add(value.getAsString()));
            return List.copyOf(values);
        }
        return List.of(element.getAsString());
    }

    private static String componentText(JsonElement element, String fallback) {
        if (element == null) return fallback;
        if (element.isJsonPrimitive()) return element.getAsString();
        JsonObject component = element.getAsJsonObject();
        return string(component, "text", string(component, "translate", fallback));
    }

    private record RewardValue(String value, int amount) {}
    public record Display(String icon, String iconBackground, String title, String subtitle, List<String> description, Map<String, GroupDisplay> groups) {}
    public record GroupDisplay(int x, int y) {}
    public record Settings(boolean individualProgress, Visibility hiddenUntil, boolean unlockNotification, boolean showDependencyArrow, boolean repeatable, boolean autoClaimRewards) {}
    public record Task(String id, String type, TaskKind kind, String title, String value, int target, JsonObject source) {}
    public record Reward(String id, String type, RewardKind kind, String title, String value, int amount, JsonObject source) {}
    public record ValidationIssue(Severity severity, String path, String message) {}
    public enum Severity { WARNING, ERROR }
    public enum Visibility { NEVER, LOCKED, DEPENDENCIES_VISIBLE, IN_PROGRESS, COMPLETED;
        static Visibility from(String value) {
            try { return valueOf(value.toUpperCase(java.util.Locale.ROOT)); }
            catch (IllegalArgumentException ignored) { return LOCKED; }
        }
    }
    public enum TaskKind { DUMMY, ITEM, UNSUPPORTED;
        static TaskKind from(String type) {
            return switch (type) { case "heracles:dummy" -> DUMMY; case "heracles:item" -> ITEM; default -> UNSUPPORTED; };
        }
    }
    public enum RewardKind { XP, ITEM, UNSUPPORTED;
        static RewardKind from(String type) {
            return switch (type) { case "heracles:xp" -> XP; case "heracles:item" -> ITEM; default -> UNSUPPORTED; };
        }
    }
}
