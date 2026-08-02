package earth.terrarium.heracles.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public record QuestDefinition(
    String id,
    String title,
    String subtitle,
    List<String> description,
    String group,
    int x,
    int y,
    List<String> dependencies,
    List<Task> tasks,
    List<Reward> rewards
) {
    public static QuestDefinition parse(String id, JsonObject root) {
        JsonObject display = object(root, "display");
        JsonObject groups = object(display, "groups");
        String group = groups.keySet().stream().findFirst().orElse("Main");
        JsonArray position = object(groups, group).has("position") ? object(groups, group).getAsJsonArray("position") : new JsonArray();

        List<Task> tasks = new ArrayList<>();
        for (Map.Entry<String, JsonElement> entry : object(root, "tasks").entrySet()) {
            JsonObject task = entry.getValue().getAsJsonObject();
            String type = string(task, "type", "");
            if (type.equals("heracles:dummy")) {
                tasks.add(new Task(entry.getKey(), TaskKind.DUMMY, string(task, "title", "Manual task"), string(task, "value", ""), 1));
            } else if (type.equals("heracles:item")) {
                tasks.add(new Task(entry.getKey(), TaskKind.ITEM, string(task, "title", "Item task"), string(task, "item", "minecraft:air"), integer(task, "amount", 1)));
            }
        }

        List<Reward> rewards = new ArrayList<>();
        for (Map.Entry<String, JsonElement> entry : object(root, "rewards").entrySet()) {
            JsonObject reward = entry.getValue().getAsJsonObject();
            String type = string(reward, "type", "");
            if (type.equals("heracles:xp")) {
                rewards.add(new Reward(entry.getKey(), RewardKind.XP, string(reward, "title", "Experience"), string(reward, "xptype", "level"), integer(reward, "amount", 1)));
            } else if (type.equals("heracles:item")) {
                JsonElement itemElement = reward.get("item");
                String item = itemElement != null && itemElement.isJsonObject() ? string(itemElement.getAsJsonObject(), "id", "minecraft:air") : itemElement.getAsString();
                int count = itemElement != null && itemElement.isJsonObject() ? integer(itemElement.getAsJsonObject(), "count", 1) : 1;
                rewards.add(new Reward(entry.getKey(), RewardKind.ITEM, string(reward, "title", "Item"), item, count));
            }
        }

        return new QuestDefinition(
            id,
            componentText(display.get("title"), id),
            componentText(display.get("subtitle"), ""),
            strings(display.get("description")),
            group,
            position.size() > 0 ? position.get(0).getAsInt() : 0,
            position.size() > 1 ? position.get(1).getAsInt() : 0,
            strings(root.get("dependencies")),
            List.copyOf(tasks),
            List.copyOf(rewards)
        );
    }

    private static JsonObject object(JsonObject object, String key) {
        return object.has(key) && object.get(key).isJsonObject() ? object.getAsJsonObject(key) : new JsonObject();
    }

    private static String string(JsonObject object, String key, String fallback) {
        return object.has(key) ? object.get(key).getAsString() : fallback;
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

    public record Task(String id, TaskKind kind, String title, String value, int target) {}
    public record Reward(String id, RewardKind kind, String title, String value, int amount) {}
    public enum TaskKind { DUMMY, ITEM }
    public enum RewardKind { XP, ITEM }

    public Identifier itemId(Task task) {
        return Identifier.parse(task.value());
    }
}
