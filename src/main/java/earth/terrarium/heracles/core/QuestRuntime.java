package earth.terrarium.heracles.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import earth.terrarium.heracles.Heracles;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.network.PacketDistributor;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class QuestRuntime {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final TaskEngine TASK_ENGINE = TaskEngine.defaults();
    private static QuestRuntime instance;

    private final MinecraftServer server;
    private final Path progressFile;
    private QuestCatalog catalog;
    private final Map<UUID, Map<String, QuestProgress>> progress = new HashMap<>();

    private QuestRuntime(MinecraftServer server) {
        this.server = server;
        this.progressFile = server.getWorldPath(LevelResource.ROOT).resolve("data/heracles_progress.json");
        this.catalog = QuestCatalog.load(FMLPaths.CONFIGDIR.get());
        loadProgress();
    }

    public static void start(MinecraftServer server) {
        instance = new QuestRuntime(server);
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
    }

    public boolean triggerDummy(ServerPlayer player, String value) {
        return signal(player, new TaskEngine.Signal.Manual(value));
    }

    public void updateInventoryTasks(ServerPlayer player) {
        boolean changed = false;
        for (QuestDefinition quest : catalog.quests().values()) {
            if (!isUnlocked(player, quest)) continue;
            for (QuestDefinition.Task task : quest.tasks().values()) {
                if (task.kind() != QuestDefinition.TaskKind.ITEM) continue;
                changed |= applyTask(player, quest, task, new TaskEngine.Signal.Inventory(task.value(), countItems(player, task.value()), false));
            }
        }
        if (changed) changed(player);
        signal(player, playerState(player));
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
            signal = new TaskEngine.Signal.Inventory(task.value(), countItems(player, task.value()), true);
        } else if (task.kind() == QuestDefinition.TaskKind.XP) {
            signal = new TaskEngine.Signal.Experience(player.experienceLevel, player.totalExperience, true);
        } else if (task.kind() == QuestDefinition.TaskKind.CHECK) {
            signal = new TaskEngine.Signal.Check(true);
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
        TaskEngine.Result result = TASK_ENGINE.apply(task, current, signal);
        if (result.consumeAmount() > 0) consume(player, task, result.consumeAmount());
        return setTaskProgress(player, quest, task, result.progress());
    }

    private static int countItems(ServerPlayer player, String itemId) {
        int count = 0;
        net.minecraft.resources.Identifier id = net.minecraft.resources.Identifier.parse(itemId);
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(id)) count += stack.getCount();
        }
        return count;
    }

    private static void consume(ServerPlayer player, QuestDefinition.Task task, int amount) {
        if (task.kind() == QuestDefinition.TaskKind.XP) {
            String unit = task.source().has("xpType") ? task.source().get("xpType").getAsString().toLowerCase(java.util.Locale.ROOT) : "level";
            if (unit.endsWith("points")) player.giveExperiencePoints(-amount);
            else player.giveExperienceLevels(-amount);
            return;
        }
        if (task.kind() != QuestDefinition.TaskKind.ITEM) return;
        net.minecraft.resources.Identifier id = net.minecraft.resources.Identifier.parse(task.value());
        int remaining = amount;
        for (int slot = 0; slot < player.getInventory().getContainerSize() && remaining > 0; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(id)) continue;
            int removed = Math.min(stack.getCount(), remaining);
            stack.shrink(removed);
            remaining -= removed;
        }
    }

    private static TaskEngine.Signal.WorldState playerState(ServerPlayer player) {
        String biome = player.level().getBiome(player.blockPosition()).unwrapKey()
            .map(key -> key.identifier().toString()).orElse("");
        return new TaskEngine.Signal.WorldState(
            player.level().dimension().identifier().toString(), biome,
            player.getX(), player.getY(), player.getZ()
        );
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
