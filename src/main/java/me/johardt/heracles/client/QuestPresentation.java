package me.johardt.heracles.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import me.johardt.heracles.core.QuestDefinition;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;

import java.util.Locale;

/** Converts loader-neutral quest data into compact, player-facing client content. */
final class QuestPresentation {
    private QuestPresentation() {}

    static ItemStack questIcon(QuestDefinition quest) {
        return item(quest.display().icon(), Items.MAP);
    }

    static ItemStack taskIcon(QuestDefinition.Task task) {
        JsonObject icon = object(task.source(), "icon");
        if (icon.has("item")) return item(icon.get("item"), Items.PAPER);
        return switch (task.kind()) {
            case ITEM, ITEM_INTERACTION, ITEM_USE -> item(task.source().get("item"), Items.PAPER);
            case BLOCK_INTERACTION -> blockItem(task.source().get("block"), Items.STONE_BUTTON);
            case KILL_ENTITY, ENTITY_INTERACTION -> entityItem(task.source().get("entity"), Items.IRON_SWORD);
            case ADVANCEMENT -> new ItemStack(Items.WRITABLE_BOOK);
            case RECIPE -> new ItemStack(Items.KNOWLEDGE_BOOK);
            case XP -> new ItemStack(Items.EXPERIENCE_BOTTLE);
            case STAT -> new ItemStack(Items.FEATHER);
            case STRUCTURE, LOCATION -> new ItemStack(Items.COMPASS);
            case CHANGED_DIMENSION -> new ItemStack(Items.ENDER_PEARL);
            case BIOME -> new ItemStack(Items.GRASS_BLOCK);
            case CHECK -> new ItemStack(Items.EMERALD);
            default -> new ItemStack(Items.PAPER);
        };
    }

    static ItemStack rewardIcon(QuestDefinition.Reward reward) {
        return reward.kind() == QuestDefinition.RewardKind.ITEM
            ? item(reward.value(), Items.CHEST)
            : new ItemStack(Items.EXPERIENCE_BOTTLE);
    }

    static String taskTitle(QuestDefinition.Task task) {
        if (!task.title().equals(task.id())) return task.title();
        return switch (task.kind()) {
            case ITEM -> "Collect " + displayValue(task.source().get("item"), task.value());
            case KILL_ENTITY -> "Defeat " + displayValue(task.source().get("entity"), task.value());
            case BLOCK_INTERACTION -> "Interact with " + displayValue(task.source().get("block"), task.value());
            case ENTITY_INTERACTION -> "Interact with " + displayValue(task.source().get("entity"), task.value());
            case ITEM_INTERACTION, ITEM_USE -> "Use " + displayValue(task.source().get("item"), task.value());
            case ADVANCEMENT -> "Complete an advancement";
            case RECIPE -> "Unlock a recipe";
            case XP -> "Gather experience";
            case STAT -> "Increase " + friendly(task.value());
            case STRUCTURE -> "Visit a structure";
            case LOCATION -> "Reach the location";
            case CHANGED_DIMENSION -> "Travel between dimensions";
            case BIOME -> "Visit " + displayValue(task.source().get("biomes"), task.value());
            case CHECK -> "Complete the check";
            default -> task.id();
        };
    }

    static String taskDescription(QuestDefinition.Task task) {
        return switch (task.kind()) {
            case ITEM -> "Collect " + task.target() + " matching item" + plural(task.target());
            case KILL_ENTITY -> "Defeat " + task.target() + " matching entit" + (task.target() == 1 ? "y" : "ies");
            case XP -> "Reach or submit " + task.target() + " experience " + suffix(string(task.source(), "xpType", "levels"));
            case STAT -> "Reach a value of " + task.target();
            case RECIPE -> "Discover one of the configured recipes";
            case STRUCTURE -> "Enter the configured structure";
            case LOCATION -> "Enter the configured area";
            case ADVANCEMENT -> "Complete one of the configured advancements";
            case BIOME -> "Enter the configured biome";
            case CHANGED_DIMENSION -> "Travel through the configured dimensions";
            case CHECK -> "Submit this task when its conditions are met";
            case BLOCK_INTERACTION, ENTITY_INTERACTION, ITEM_INTERACTION, ITEM_USE -> "Perform the configured interaction";
            default -> task.kind() == QuestDefinition.TaskKind.UNSUPPORTED ? "Unsupported task type: " + task.type() : "Complete this task";
        };
    }

    static String rewardTitle(QuestDefinition.Reward reward) {
        if (!reward.title().equals(reward.id())) return reward.title();
        return reward.kind() == QuestDefinition.RewardKind.ITEM ? friendly(reward.value()) : "Experience";
    }

    private static ItemStack item(JsonElement element, Item fallback) {
        if (element == null) return new ItemStack(fallback);
        if (element.isJsonObject()) return item(element.getAsJsonObject().get("item"), fallback);
        if (!element.isJsonPrimitive()) return new ItemStack(fallback);
        return item(element.getAsString(), fallback);
    }

    private static ItemStack item(String value, Item fallback) {
        if (value == null || value.isBlank()) return new ItemStack(fallback);
        try {
            if (value.startsWith("#")) {
                TagKey<Item> tag = TagKey.create(Registries.ITEM, Identifier.parse(value.substring(1)));
                for (var holder : BuiltInRegistries.ITEM.getTagOrEmpty(tag)) return new ItemStack(holder.value());
                return new ItemStack(fallback);
            }
            return new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(value)));
        } catch (Exception ignored) {
            return new ItemStack(fallback);
        }
    }

    private static ItemStack blockItem(JsonElement element, Item fallback) {
        if (element == null || !element.isJsonPrimitive() || element.getAsString().startsWith("#")) return new ItemStack(fallback);
        try {
            Item item = BuiltInRegistries.BLOCK.getValue(Identifier.parse(element.getAsString())).asItem();
            return item == Items.AIR ? new ItemStack(fallback) : new ItemStack(item);
        } catch (Exception ignored) {
            return new ItemStack(fallback);
        }
    }

    private static ItemStack entityItem(JsonElement element, Item fallback) {
        if (element == null) return new ItemStack(fallback);
        if (element.isJsonObject()) element = element.getAsJsonObject().get("type");
        if (element == null || !element.isJsonPrimitive() || element.getAsString().startsWith("#")) return new ItemStack(fallback);
        try {
            var type = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse(element.getAsString()));
            return SpawnEggItem.byId(type).map(holder -> new ItemStack(holder.value())).orElseGet(() -> new ItemStack(fallback));
        } catch (Exception ignored) {
            return new ItemStack(fallback);
        }
    }

    private static String displayValue(JsonElement element, String fallback) {
        if (element == null) return friendly(fallback);
        if (element.isJsonArray() && !element.getAsJsonArray().isEmpty()) return displayValue(element.getAsJsonArray().get(0), fallback);
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            if (object.has("tag")) return "#" + friendly(object.get("tag").getAsString());
            if (object.has("id")) return friendly(object.get("id").getAsString());
            if (object.has("type")) return displayValue(object.get("type"), fallback);
        }
        return element.isJsonPrimitive() ? friendly(element.getAsString()) : friendly(fallback);
    }

    private static String friendly(String value) {
        if (value == null || value.isBlank()) return "configured target";
        String path = value.startsWith("#") ? value.substring(1) : value;
        int separator = path.indexOf(':');
        if (separator >= 0) path = path.substring(separator + 1);
        path = path.substring(path.lastIndexOf('/') + 1).replace('_', ' ');
        if (path.isBlank()) return "configured target";
        return path.substring(0, 1).toUpperCase(Locale.ROOT) + path.substring(1);
    }

    private static String plural(int amount) {
        return amount == 1 ? "" : "s";
    }

    private static String suffix(String value) {
        int separator = Math.max(value.lastIndexOf('.'), value.lastIndexOf(':'));
        return value.substring(separator + 1).toLowerCase(Locale.ROOT);
    }

    private static String string(JsonObject object, String key, String fallback) {
        return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsString() : fallback;
    }

    private static JsonObject object(JsonObject parent, String key) {
        return parent.has(key) && parent.get(key).isJsonObject() ? parent.getAsJsonObject(key) : new JsonObject();
    }
}
