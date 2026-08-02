package earth.terrarium.heracles.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import earth.terrarium.heracles.Heracles;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.TagValueOutput;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.network.PacketDistributor;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public final class QuestRuntime {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final TaskEngine.Builder TASKS = TaskEngine.defaultBuilder();
    private static QuestRuntime instance;

    private final MinecraftServer server;
    private final TaskEngine taskEngine;
    private final Path progressFile;
    private QuestCatalog catalog;
    private final Map<UUID, Map<String, QuestProgress>> progress = new HashMap<>();

    private QuestRuntime(MinecraftServer server) {
        this.server = server;
        this.taskEngine = TASKS.build();
        this.progressFile = server.getWorldPath(LevelResource.ROOT).resolve("data/heracles_progress.json");
        this.catalog = QuestCatalog.load(FMLPaths.CONFIGDIR.get());
        loadProgress();
    }

    public static void start(MinecraftServer server) {
        instance = new QuestRuntime(server);
    }

    /** Registers an additional task handler. Call during mod initialization, before a server starts. */
    public static void registerTaskHandler(String type, TaskEngine.Handler handler) {
        if (instance != null) throw new IllegalStateException("Task handlers must be registered before the server starts");
        TASKS.register(type, handler);
    }

    public static void stop() {
        if (instance != null) instance.saveProgress();
        instance = null;
    }

    public static QuestRuntime get() {
        if (instance == null) throw new IllegalStateException("Heracles quest runtime is not started");
        return instance;
    }

    public int reload() {
        catalog = QuestCatalog.load(FMLPaths.CONFIGDIR.get());
        server.getPlayerList().getPlayers().forEach(player -> sync(player, false));
        return catalog.quests().size();
    }

    public void initialize(ServerPlayer player) {
        updateInventoryTasks(player);
        for (QuestDefinition quest : catalog.quests().values()) {
            if (!isUnlocked(player, quest)) continue;
            for (QuestDefinition.Task task : quest.tasks().values()) {
                if (task.kind() != QuestDefinition.TaskKind.ADVANCEMENT) continue;
                for (String advancement : configuredStrings(task, "advancements", task.value())) {
                    var holder = server.getAdvancements().get(net.minecraft.resources.Identifier.parse(advancement));
                    if (holder != null && player.getAdvancements().getOrStartProgress(holder).isDone()) {
                        signal(player, new TaskEngine.Signal.AdvancementGranted(advancement));
                    }
                }
            }
        }
        updatePassiveTasks(player);
    }

    public boolean triggerDummy(ServerPlayer player, String value) {
        return signal(player, new TaskEngine.Signal.Manual(value));
    }

    public void updateInventoryTasks(ServerPlayer player) {
        TaskEngine.Signal.Inventory inventory = inventory(player, false);
        boolean changed = false;
        for (QuestDefinition quest : catalog.quests().values()) {
            if (!isUnlocked(player, quest)) continue;
            for (QuestDefinition.Task task : quest.tasks().values()) {
                if (task.kind() != QuestDefinition.TaskKind.ITEM) continue;
                changed |= applyTask(player, quest, task, inventory);
            }
        }
        if (changed) changed(player);
        signal(player, playerState(player));
        updatePassiveTasks(player);
    }

    public boolean signal(ServerPlayer player, TaskEngine.Signal signal) {
        boolean changed = false;
        for (QuestDefinition quest : catalog.quests().values()) {
            if (!isUnlocked(player, quest)) continue;
            for (QuestDefinition.Task task : quest.tasks().values()) {
                changed |= applyTask(player, quest, task, signal);
            }
        }
        if (changed) changed(player);
        return changed;
    }

    public boolean submit(ServerPlayer player, String questId, String taskId) {
        QuestDefinition quest = catalog.quests().get(questId);
        if (quest == null || !isUnlocked(player, quest)) return false;
        QuestDefinition.Task task = quest.tasks().get(taskId);
        if (task == null) return false;
        TaskEngine.Signal signal;
        if (task.kind() == QuestDefinition.TaskKind.ITEM) {
            signal = inventory(player, true);
        } else if (task.kind() == QuestDefinition.TaskKind.XP) {
            signal = new TaskEngine.Signal.Experience(player.experienceLevel, player.totalExperience, true);
        } else if (task.kind() == QuestDefinition.TaskKind.CHECK) {
            signal = new TaskEngine.Signal.Check(playerData(player), true);
        } else {
            return false;
        }
        boolean changed = applyTask(player, quest, task, signal);
        if (changed) changed(player);
        return changed;
    }

    public boolean claim(ServerPlayer player, String questId) {
        QuestDefinition quest = catalog.quests().get(questId);
        if (quest == null || !isComplete(player, quest) || progress(player, questId).claimed) return false;
        for (QuestDefinition.Reward reward : quest.rewards().values()) {
            if (reward.kind() == QuestDefinition.RewardKind.XP) {
                if (reward.value().equalsIgnoreCase("points")) player.giveExperiencePoints(reward.amount());
                else player.giveExperienceLevels(reward.amount());
            } else if (reward.kind() == QuestDefinition.RewardKind.ITEM) {
                Item item = BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.parse(reward.value()));
                player.addItem(new ItemStack(item, reward.amount()));
            }
        }
        progress(player, questId).claimed = true;
        changed(player);
        return true;
    }

    public void reset(ServerPlayer player) {
        progress.remove(player.getUUID());
        changed(player);
    }

    public boolean isUnlocked(ServerPlayer player, QuestDefinition quest) {
        return quest.dependencies().stream().allMatch(dependency -> {
            QuestDefinition required = catalog.quests().get(dependency);
            return required != null && isComplete(player, required);
        });
    }

    public boolean isComplete(ServerPlayer player, QuestDefinition quest) {
        QuestProgress progress = progress(player, quest.id());
        return quest.tasks().values().stream().allMatch(task -> progress.tasks.getOrDefault(task.id(), 0) >= task.target());
    }

    private boolean setTaskProgress(ServerPlayer player, QuestDefinition quest, QuestDefinition.Task task, int value) {
        QuestProgress progress = progress(player, quest.id());
        int previous = progress.tasks.getOrDefault(task.id(), 0);
        if (previous == value) return false;
        progress.tasks.put(task.id(), value);
        if (value >= task.target() && previous < task.target()) {
            player.sendSystemMessage(Component.literal("Quest task complete: " + task.title()));
        }
        return true;
    }

    private boolean applyTask(ServerPlayer player, QuestDefinition quest, QuestDefinition.Task task, TaskEngine.Signal signal) {
        int current = progress(player, quest.id()).tasks.getOrDefault(task.id(), 0);
        TaskEngine.Result result = taskEngine.apply(task, current, signal);
        if (result.consumeAmount() > 0) consume(player, task, result.consumeAmount());
        return setTaskProgress(player, quest, task, result.progress());
    }

    private void updatePassiveTasks(ServerPlayer player) {
        for (QuestDefinition quest : catalog.quests().values()) {
            if (!isUnlocked(player, quest)) continue;
            for (QuestDefinition.Task task : quest.tasks().values()) {
                if (task.kind() == QuestDefinition.TaskKind.RECIPE) {
                    for (String recipe : configuredStrings(task, "recipes", task.value())) {
                        ResourceKey<Recipe<?>> key = ResourceKey.create(Registries.RECIPE, net.minecraft.resources.Identifier.parse(recipe));
                        if (player.getRecipeBook().contains(key)) signal(player, new TaskEngine.Signal.RecipeUnlocked(recipe));
                    }
                } else if (task.kind() == QuestDefinition.TaskKind.STAT && !task.value().isBlank()) {
                    var id = net.minecraft.resources.Identifier.parse(task.value());
                    signal(player, new TaskEngine.Signal.Statistic(task.value(), player.getStats().getValue(Stats.CUSTOM, id)));
                }
            }
        }
        Set<TaskEngine.Signal.RegistryEntry> structures = structuresAt(player);
        if (!structures.isEmpty()) signal(player, new TaskEngine.Signal.Structures(structures));
    }

    private static void consume(ServerPlayer player, QuestDefinition.Task task, int amount) {
        if (task.kind() == QuestDefinition.TaskKind.XP) {
            String unit = task.source().has("xpType") ? task.source().get("xpType").getAsString().toLowerCase(java.util.Locale.ROOT) : "level";
            if (unit.endsWith("points")) player.giveExperiencePoints(-amount);
            else player.giveExperienceLevels(-amount);
            return;
        }
        if (task.kind() != QuestDefinition.TaskKind.ITEM) return;
        int remaining = amount;
        for (int slot = 0; slot < player.getInventory().getContainerSize() && remaining > 0; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            TaskEngine.Signal.RegistryEntry entry = itemEntry(player, stack);
            if (!RegistryPredicate.matches(task.source().get("item"), task.value(), entry)) continue;
            if (!RegistryPredicate.contains(task.source().get("components"), entry.data())) continue;
            if (!RegistryPredicate.contains(task.source().get("nbt"), entry.data())) continue;
            int removed = Math.min(stack.getCount(), remaining);
            stack.shrink(removed);
            remaining -= removed;
        }
    }

    private static TaskEngine.Signal.WorldState playerState(ServerPlayer player) {
        var biome = player.level().getBiome(player.blockPosition());
        return new TaskEngine.Signal.WorldState(
            player.level().dimension().identifier().toString(), registryEntry(biome, new JsonObject(), 1),
            player.getX(), player.getY(), player.getZ()
        );
    }

    private static TaskEngine.Signal.Inventory inventory(ServerPlayer player, boolean submit) {
        java.util.List<TaskEngine.Signal.RegistryEntry> entries = new java.util.ArrayList<>();
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.isEmpty()) entries.add(itemEntry(player, stack));
        }
        return new TaskEngine.Signal.Inventory(entries, submit);
    }

    public static TaskEngine.Signal.RegistryEntry itemEntry(ServerPlayer player, ItemStack stack) {
        JsonObject data = new JsonObject();
        ItemStack.CODEC.encodeStart(player.registryAccess().createSerializationContext(JsonOps.INSTANCE), stack)
            .result().filter(com.google.gson.JsonElement::isJsonObject).map(com.google.gson.JsonElement::getAsJsonObject)
            .ifPresent(encoded -> {
                if (encoded.has("components") && encoded.get("components").isJsonObject()) data.add("components", encoded.get("components"));
                if (encoded.has("components") && encoded.get("components").isJsonObject()) {
                    encoded.getAsJsonObject("components").entrySet().forEach(entry -> data.add(entry.getKey(), entry.getValue()));
                }
            });
        return registryEntry(stack.typeHolder(), data, stack.getCount());
    }

    public static <T> TaskEngine.Signal.RegistryEntry registryEntry(net.minecraft.core.Holder<T> holder, JsonObject data, int count) {
        String id = holder.unwrapKey().map(key -> key.identifier().toString()).orElse("");
        Set<String> tags = holder.tags().map(tag -> tag.location().toString()).collect(Collectors.toSet());
        return new TaskEngine.Signal.RegistryEntry(id, tags, data, count);
    }

    private Set<TaskEngine.Signal.RegistryEntry> structuresAt(ServerPlayer player) {
        java.util.List<QuestDefinition.Task> tasks = catalog.quests().values().stream()
            .filter(quest -> isUnlocked(player, quest))
            .flatMap(quest -> quest.tasks().values().stream())
            .filter(task -> task.kind() == QuestDefinition.TaskKind.STRUCTURE)
            .toList();
        if (tasks.isEmpty()) return Set.of();
        var lookup = server.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        return lookup.listElements()
            .filter(holder -> {
                TaskEngine.Signal.RegistryEntry entry = registryEntry(holder, new JsonObject(), 1);
                return tasks.stream().anyMatch(task -> RegistryPredicate.matches(task.source().get("structures"), task.value(), entry));
            })
            .filter(holder -> player.level().structureManager().getStructureWithPieceAt(player.blockPosition(), holder.value()).isValid())
            .map(holder -> registryEntry(holder, new JsonObject(), 1))
            .collect(Collectors.toSet());
    }

    private static JsonObject playerData(ServerPlayer player) {
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, player.registryAccess());
        player.saveWithoutId(output);
        com.google.gson.JsonElement json = NbtOps.INSTANCE.convertTo(JsonOps.INSTANCE, output.buildResult());
        return json.isJsonObject() ? json.getAsJsonObject() : new JsonObject();
    }

    private static java.util.List<String> configuredStrings(QuestDefinition.Task task, String key, String fallback) {
        if (!task.source().has(key)) return fallback.isBlank() ? java.util.List.of() : java.util.List.of(fallback);
        var value = task.source().get(key);
        if (value.isJsonArray()) return value.getAsJsonArray().asList().stream().map(com.google.gson.JsonElement::getAsString).toList();
        return java.util.List.of(value.getAsString());
    }

    private QuestProgress progress(ServerPlayer player, String questId) {
        return progress.computeIfAbsent(player.getUUID(), ignored -> new HashMap<>()).computeIfAbsent(questId, ignored -> new QuestProgress());
    }

    private void changed(ServerPlayer player) {
        saveProgress();
        sync(player, false);
    }

    public void sync(ServerPlayer player, boolean open) {
        PacketDistributor.sendToPlayer(player, new QuestNetwork.SyncPayload(snapshot(player), open));
    }

    private String snapshot(ServerPlayer player) {
        JsonObject root = new JsonObject();
        for (QuestDefinition quest : catalog.quests().values()) {
            JsonObject json = GSON.toJsonTree(quest).getAsJsonObject();
            QuestProgress state = progress(player, quest.id());
            json.addProperty("unlocked", isUnlocked(player, quest));
            json.addProperty("complete", isComplete(player, quest));
            json.addProperty("claimed", state.claimed);
            json.add("progress", GSON.toJsonTree(state.tasks));
            root.add(quest.id(), json);
        }
        return GSON.toJson(root);
    }

    private void loadProgress() {
        if (!Files.exists(progressFile)) return;
        try {
            JsonObject root = JsonParser.parseString(Files.readString(progressFile, StandardCharsets.UTF_8)).getAsJsonObject();
            root.entrySet().forEach(player -> progress.put(UUID.fromString(player.getKey()), parsePlayerProgress(player.getValue().getAsJsonObject())));
        } catch (Exception exception) {
            Heracles.LOGGER.error("Failed to load quest progress from {}", progressFile, exception);
        }
    }

    private static Map<String, QuestProgress> parsePlayerProgress(JsonObject root) {
        Map<String, QuestProgress> result = new HashMap<>();
        root.entrySet().forEach(entry -> result.put(entry.getKey(), GSON.fromJson(entry.getValue(), QuestProgress.class)));
        return result;
    }

    private void saveProgress() {
        try {
            Files.createDirectories(progressFile.getParent());
            Files.writeString(progressFile, GSON.toJson(progress), StandardCharsets.UTF_8);
        } catch (Exception exception) {
            Heracles.LOGGER.error("Failed to save quest progress to {}", progressFile, exception);
        }
    }

    private static final class QuestProgress {
        private final Map<String, Integer> tasks = new HashMap<>();
        private boolean claimed;
    }
}
