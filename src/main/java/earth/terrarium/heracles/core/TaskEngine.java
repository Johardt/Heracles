package earth.terrarium.heracles.core;

import java.util.LinkedHashMap;
import java.util.Map;

/** Pure task evaluation module. NeoForge adapters translate game events into signals. */
public final class TaskEngine {
    private final Map<String, Handler> handlers;

    private TaskEngine(Map<String, Handler> handlers) {
        this.handlers = Map.copyOf(handlers);
    }

    public static TaskEngine defaults() {
        Map<String, Handler> handlers = new LinkedHashMap<>();
        handlers.put("heracles:dummy", (task, progress, signal) ->
            new Result(signal instanceof Signal.Manual manual && task.value().equals(manual.value()) ? 1 : progress, 0));
        handlers.put("heracles:check", (task, progress, signal) ->
            new Result(signal instanceof Signal.Check check && check.complete() ? 1 : progress, 0));
        handlers.put("heracles:kill_entity", (task, progress, signal) -> {
            if (signal instanceof Signal.EntityKilled killed && task.value().equals(killed.entity())) {
                return new Result(Math.min(task.target(), progress + 1), 0);
            }
            return new Result(progress, 0);
        });
        handlers.put("heracles:item", (task, progress, signal) -> {
            if (!(signal instanceof Signal.Inventory inventory) || !task.value().equals(inventory.item())) {
                return new Result(progress, 0);
            }
            String collection = suffix(task.source().has("collection") ? task.source().get("collection").getAsString() : "automatic");
            int remaining = Math.max(0, task.target() - progress);
            return switch (collection) {
                case "consume" -> inventory.count() >= remaining
                    ? new Result(task.target(), remaining)
                    : new Result(progress, 0);
                case "manual" -> inventory.submit()
                    ? new Result(progress + Math.min(remaining, inventory.count()), Math.min(remaining, inventory.count()))
                    : new Result(progress, 0);
                default -> new Result(Math.min(task.target(), inventory.count()), 0);
            };
        });
        handlers.put("heracles:advancement", (task, progress, signal) -> {
            if (!(signal instanceof Signal.AdvancementGranted advancement)) return new Result(progress, 0);
            var configured = task.source().get("advancements");
            boolean matches = configured != null && configured.isJsonArray()
                ? configured.getAsJsonArray().asList().stream().anyMatch(value -> value.getAsString().equals(advancement.advancement()))
                : task.value().equals(advancement.advancement());
            return new Result(matches ? 1 : progress, 0);
        });
        handlers.put("heracles:xp", (task, progress, signal) -> {
            if (!(signal instanceof Signal.Experience experience)) return new Result(progress, 0);
            String unit = suffix(string(task, "xpType", "level"));
            String collection = suffix(string(task, "collectionType", string(task, "collection", "consume")));
            int available = unit.equals("points") ? experience.points() : experience.levels();
            int remaining = Math.max(0, task.target() - progress);
            return switch (collection) {
                case "automatic" -> new Result(Math.min(task.target(), Math.max(progress, available)), 0);
                case "manual" -> experience.submit() && available >= remaining ? new Result(task.target(), remaining) : new Result(progress, 0);
                default -> available >= remaining ? new Result(task.target(), remaining) : new Result(progress, 0);
            };
        });
        handlers.put("heracles:block_interaction", (task, progress, signal) ->
            new Result(signal instanceof Signal.BlockInteracted block && task.value().equals(block.block()) ? 1 : progress, 0));
        handlers.put("heracles:entity_interaction", (task, progress, signal) ->
            new Result(signal instanceof Signal.EntityInteracted entity && task.value().equals(entity.entity()) ? 1 : progress, 0));
        handlers.put("heracles:item_interaction", (task, progress, signal) ->
            new Result(signal instanceof Signal.ItemInteracted item && task.value().equals(item.item()) ? 1 : progress, 0));
        handlers.put("heracles:item_use", (task, progress, signal) ->
            new Result(signal instanceof Signal.ItemUsed item && task.value().equals(item.item()) ? 1 : progress, 0));
        handlers.put("heracles:biome", (task, progress, signal) ->
            new Result(signal instanceof Signal.WorldState world && matches(task.source().get("biomes"), task.value(), world.biome()) ? 1 : progress, 0));
        handlers.put("heracles:changed_dimension", (task, progress, signal) -> {
            if (!(signal instanceof Signal.DimensionChanged changed)) return new Result(progress, 0);
            String from = string(task, "from", "");
            String to = string(task, "to", "");
            boolean matches = (from.isBlank() || from.equals(changed.from())) && (to.isBlank() || to.equals(changed.to()));
            return new Result(matches ? 1 : progress, 0);
        });
        handlers.put("heracles:location", (task, progress, signal) -> {
            if (!(signal instanceof Signal.WorldState world)) return new Result(progress, 0);
            var predicate = object(task.source(), "predicate");
            String dimension = jsonString(predicate, "dimension", "");
            var position = object(predicate, "position");
            boolean matches = (dimension.isBlank() || dimension.equals(world.dimension()))
                && inRange(world.x(), object(position, "x"))
                && inRange(world.y(), object(position, "y"))
                && inRange(world.z(), object(position, "z"));
            return new Result(matches ? 1 : progress, 0);
        });
        return new TaskEngine(handlers);
    }

    private static String suffix(String value) {
        int separator = Math.max(value.lastIndexOf('.'), value.lastIndexOf(':'));
        return value.substring(separator + 1).toLowerCase(java.util.Locale.ROOT);
    }

    private static String string(QuestDefinition.Task task, String key, String fallback) {
        return task.source().has(key) ? task.source().get(key).getAsString() : fallback;
    }

    private static boolean matches(com.google.gson.JsonElement configured, String fallback, String actual) {
        if (configured == null) return fallback.equals(actual);
        if (configured.isJsonArray()) return configured.getAsJsonArray().asList().stream().anyMatch(value -> value.getAsString().equals(actual));
        if (configured.isJsonPrimitive()) return configured.getAsString().equals(actual);
        return fallback.equals(actual);
    }

    private static com.google.gson.JsonObject object(com.google.gson.JsonObject parent, String key) {
        return parent.has(key) && parent.get(key).isJsonObject() ? parent.getAsJsonObject(key) : new com.google.gson.JsonObject();
    }

    private static String jsonString(com.google.gson.JsonObject parent, String key, String fallback) {
        return parent.has(key) && parent.get(key).isJsonPrimitive() ? parent.get(key).getAsString() : fallback;
    }

    private static boolean inRange(double value, com.google.gson.JsonObject range) {
        double minimum = range.has("min") ? range.get("min").getAsDouble() : Double.NEGATIVE_INFINITY;
        double maximum = range.has("max") ? range.get("max").getAsDouble() : Double.POSITIVE_INFINITY;
        return value >= minimum && value <= maximum;
    }

    public Result apply(QuestDefinition.Task task, int progress, Signal signal) {
        Handler handler = handlers.get(task.type());
        return handler == null ? new Result(progress, 0) : handler.apply(task, progress, signal);
    }

    @FunctionalInterface
    private interface Handler {
        Result apply(QuestDefinition.Task task, int progress, Signal signal);
    }

    public record Result(int progress, int consumeAmount) {}

    public sealed interface Signal {
        record Manual(String value) implements Signal {}
        record Check(boolean complete) implements Signal {}
        record EntityKilled(String entity) implements Signal {}
        record Inventory(String item, int count, boolean submit) implements Signal {}
        record AdvancementGranted(String advancement) implements Signal {}
        record Experience(int levels, int points, boolean submit) implements Signal {}
        record BlockInteracted(String block) implements Signal {}
        record EntityInteracted(String entity) implements Signal {}
        record ItemInteracted(String item) implements Signal {}
        record ItemUsed(String item) implements Signal {}
        record WorldState(String dimension, String biome, double x, double y, double z) implements Signal {}
        record DimensionChanged(String from, String to) implements Signal {}
    }
}
