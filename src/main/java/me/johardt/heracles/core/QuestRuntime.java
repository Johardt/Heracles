package me.johardt.heracles.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import me.johardt.heracles.Heracles;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.commands.Commands;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.network.PacketDistributor;

public final class QuestRuntime {

    private static final Gson GSON = new GsonBuilder()
        .setPrettyPrinting()
        .create();
    private static final TaskEngine.Builder TASKS = TaskEngine.defaultBuilder();
    private static QuestRuntime instance;

    private final MinecraftServer server;
    private final TaskEngine taskEngine;
    private final Path progressFile;
    private QuestCatalog catalog;
    private final Map<UUID, Map<String, QuestProgress>> progress =
        new HashMap<>();
    private final Set<UUID> suppressNotifications = new java.util.HashSet<>();

    private QuestRuntime(MinecraftServer server) {
        this.server = server;
        this.taskEngine = TASKS.build();
        this.progressFile = server
            .getWorldPath(LevelResource.ROOT)
            .resolve("data/heracles_progress.json");
        this.catalog = QuestCatalog.load(FMLPaths.CONFIGDIR.get());
        loadProgress();
    }

    public static void start(MinecraftServer server) {
        instance = new QuestRuntime(server);
    }

    /** Registers an additional task handler. Call during mod initialization, before a server starts. */
    public static void registerTaskHandler(
        String type,
        TaskEngine.Handler handler
    ) {
        if (instance != null) throw new IllegalStateException(
            "Task handlers must be registered before the server starts"
        );
        TASKS.register(type, handler);
    }

    public static void stop() {
        if (instance != null) instance.saveProgress();
        instance = null;
    }

    public static QuestRuntime get() {
        if (instance == null) throw new IllegalStateException(
            "Heracles quest runtime is not started"
        );
        return instance;
    }

    public static boolean isStarted() {
        return instance != null;
    }

    public int reload() {
        catalog = QuestCatalog.load(FMLPaths.CONFIGDIR.get());
        server
            .getPlayerList()
            .getPlayers()
            .forEach(player -> sync(player, false));
        return catalog.quests().size();
    }

    public MutationResult createQuest(ServerPlayer player, JsonObject draft) {
        if (!Commands.LEVEL_GAMEMASTERS.check(player.permissions())) return MutationResult.failure("You do not have permission to edit quests");
        String id = draft.has("id") ? draft.get("id").getAsString().trim() : "";
        if (!id.matches("[a-z0-9_.-]+")) {
            return MutationResult.failure("Quest ID must contain only lowercase letters, numbers, dots, underscores, or hyphens");
        }
        Path directory = FMLPaths.CONFIGDIR.get()
            .resolve(Heracles.MOD_ID)
            .resolve("quests");
        Path target = directory.resolve(id + ".json");
        if (Files.exists(target) || catalog.quests().containsKey(id)) {
            return MutationResult.failure("A quest with ID '" + id + "' already exists");
        }
        MutationResult basicValidation = validateDraftDisplay(draft, null);
        if (!basicValidation.success()) return basicValidation;
        String title = draft.get("title").getAsString().trim();
        String icon = draft.has("icon") ? draft.get("icon").getAsString() : "minecraft:map";
        String background = draft.has("background")
            ? draft.get("background").getAsString()
            : "heracles:textures/gui/quest_backgrounds/default.png";

        JsonObject root = new JsonObject();
        JsonObject display = new JsonObject();
        JsonObject iconJson = new JsonObject();
        iconJson.addProperty("type", "heracles:item");
        iconJson.addProperty("item", icon);
        display.add("icon", iconJson);
        display.addProperty("icon_background", background);
        display.addProperty("title", title);
        display.addProperty("subtitle", draft.has("subtitle") ? draft.get("subtitle").getAsString() : "");
        com.google.gson.JsonArray description = new com.google.gson.JsonArray();
        String body = draft.has("body") ? draft.get("body").getAsString() : "";
        body.lines().forEach(description::add);
        display.add("description", description);
        JsonObject groups = new JsonObject();
        JsonObject placement = new JsonObject();
        com.google.gson.JsonArray position = new com.google.gson.JsonArray();
        position.add(draft.has("x") ? draft.get("x").getAsInt() : 0);
        position.add(draft.has("y") ? draft.get("y").getAsInt() : 0);
        placement.add("position", position);
        groups.add(draft.has("group") ? draft.get("group").getAsString() : "Main", placement);
        display.add("groups", groups);
        root.add("display", display);
        root.add(
            "tasks",
            draft.has("tasks") && draft.get("tasks").isJsonObject()
                ? draft.getAsJsonObject("tasks").deepCopy()
                : new JsonObject()
        );
        root.add(
            "rewards",
            draft.has("rewards") && draft.get("rewards").isJsonObject()
                ? draft.getAsJsonObject("rewards").deepCopy()
                : new JsonObject()
        );

        QuestDefinition parsed = QuestDefinition.parse(id, root);
        if (parsed.issues().stream().anyMatch(issue ->
            issue.severity() == QuestDefinition.Severity.ERROR
        )) {
            return MutationResult.failure(firstValidationError(parsed));
        }

        try {
            Files.createDirectories(directory);
            Files.writeString(
                target,
                GSON.toJson(root),
                StandardCharsets.UTF_8,
                java.nio.file.StandardOpenOption.CREATE_NEW
            );
            reload();
            return MutationResult.success("Quest '" + id + "' created");
        } catch (java.nio.file.FileAlreadyExistsException exception) {
            return MutationResult.failure("A quest with ID '" + id + "' already exists");
        } catch (java.io.IOException exception) {
            Heracles.LOGGER.error("Failed to create quest {}", id, exception);
            return MutationResult.failure("Failed to write quest '" + id + "'");
        }
    }

    public MutationResult updateQuest(ServerPlayer player, JsonObject draft) {
        if (!Commands.LEVEL_GAMEMASTERS.check(player.permissions())) return MutationResult.failure("You do not have permission to edit quests");
        String oldId = draft.has("original_id") ? draft.get("original_id").getAsString() : "";
        String newId = draft.has("id") ? draft.get("id").getAsString().trim() : "";
        if (!catalog.quests().containsKey(oldId)) return MutationResult.failure("The original quest no longer exists");
        if (!newId.matches("[a-z0-9_.-]+")) return MutationResult.failure("Quest ID is invalid");
        if (!oldId.equals(newId) && catalog.quests().containsKey(newId)) {
            return MutationResult.failure("A quest with ID '" + newId + "' already exists");
        }
        JsonObject changedFields = draft.has("changed_fields") && draft.get("changed_fields").isJsonObject()
            ? draft.getAsJsonObject("changed_fields") : new JsonObject();
        MutationResult basicValidation = validateDraftDisplay(draft, changedFields);
        if (!basicValidation.success()) return basicValidation;
        try {
            Path source = findQuestPath(oldId);
            JsonObject root = JsonParser.parseString(Files.readString(source, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonObject previousTasks = root.has("tasks") && root.get("tasks").isJsonObject() ? root.getAsJsonObject("tasks").deepCopy() : new JsonObject();
            JsonObject previousRewards = root.has("rewards") && root.get("rewards").isJsonObject() ? root.getAsJsonObject("rewards").deepCopy() : new JsonObject();
            JsonObject display = root.has("display") && root.get("display").isJsonObject()
                ? root.getAsJsonObject("display") : new JsonObject();
            JsonObject changed = changedFields;
            if (changed.has("title")) display.addProperty("title", draft.get("title").getAsString());
            if (changed.has("subtitle")) display.addProperty("subtitle", draft.has("subtitle") ? draft.get("subtitle").getAsString() : "");
            if (changed.has("body")) {
                com.google.gson.JsonArray description = new com.google.gson.JsonArray();
                String body = draft.has("body") ? draft.get("body").getAsString() : "";
                body.lines().forEach(description::add);
                display.add("description", description);
            }
            if (changed.has("icon")) {
                JsonObject icon = new JsonObject();
                icon.addProperty("type", "heracles:item");
                icon.addProperty("item", draft.get("icon").getAsString());
                display.add("icon", icon);
            }
            if (changed.has("background")) display.addProperty("icon_background", draft.get("background").getAsString());
            if (changed.has("groups") && draft.has("groups") && draft.get("groups").isJsonObject()) {
                JsonObject groups = display.has("groups") && display.get("groups").isJsonObject()
                    ? display.getAsJsonObject("groups") : new JsonObject();
                draft.getAsJsonObject("groups").entrySet().forEach(entry -> {
                    if (!entry.getValue().isJsonObject()) return;
                    JsonObject placement = groups.has(entry.getKey()) && groups.get(entry.getKey()).isJsonObject()
                        ? groups.getAsJsonObject(entry.getKey()) : new JsonObject();
                    JsonObject edited = entry.getValue().getAsJsonObject();
                    if (edited.has("position")) placement.add("position", edited.get("position").deepCopy());
                    groups.add(entry.getKey(), placement);
                });
                display.add("groups", groups);
            }
            root.add("display", display);
            root.add("tasks", draft.getAsJsonObject("tasks").deepCopy());
            root.add("rewards", draft.getAsJsonObject("rewards").deepCopy());
            QuestDefinition parsed = QuestDefinition.parse(newId, root);
            if (parsed.issues().stream().anyMatch(issue -> issue.severity() == QuestDefinition.Severity.ERROR)) {
                return MutationResult.failure(firstValidationError(parsed));
            }
            Path target = source.resolveSibling(newId + ".json");
            if (!source.equals(target) && Files.exists(target)) throw new java.nio.file.FileAlreadyExistsException(target.toString());
            writeJsonAtomically(target, root);
            if (!source.equals(target)) {
                Files.delete(source);
                replaceDependencyReferences(oldId, newId);
            }
            boolean progressAffecting = !previousTasks.equals(root.getAsJsonObject("tasks")) ||
                !previousRewards.equals(root.getAsJsonObject("rewards"));
            if (progressAffecting) resetQuestProgress(oldId, newId);
            else if (!oldId.equals(newId)) migrateQuestProgress(oldId, newId);
            reload();
            return MutationResult.success("Quest '" + newId + "' saved");
        } catch (Exception exception) {
            Heracles.LOGGER.error("Failed to update quest {}", oldId, exception);
            return MutationResult.failure("Failed to update quest '" + oldId + "'");
        }
    }

    static MutationResult validateDraftDisplay(JsonObject draft, JsonObject changedFields) {
        String error = QuestDraftValidator.validateDisplay(draft, changedFields, icon -> {
            try {
                return BuiltInRegistries.ITEM.containsKey(net.minecraft.resources.Identifier.parse(icon));
            } catch (RuntimeException exception) {
                return false;
            }
        });
        return error.isEmpty() ? MutationResult.success("") : MutationResult.failure(error);
    }

    private static String firstValidationError(QuestDefinition definition) {
        return definition.issues().stream()
            .filter(issue -> issue.severity() == QuestDefinition.Severity.ERROR)
            .map(issue -> issue.path() + ": " + issue.message())
            .findFirst()
            .orElse("Quest contains invalid configuration");
    }

    public record MutationResult(boolean success, String message) {
        public static MutationResult success(String message) { return new MutationResult(true, message); }
        public static MutationResult failure(String message) { return new MutationResult(false, message); }
    }

    public void deleteQuest(ServerPlayer player, String id) {
        if (!Commands.LEVEL_GAMEMASTERS.check(player.permissions()) || !catalog.quests().containsKey(id)) return;
        try {
            Files.delete(findQuestPath(id));
            replaceDependencyReferences(id, null);
            resetQuestProgress(id, null);
            reload();
        } catch (Exception exception) {
            Heracles.LOGGER.error("Failed to delete quest {}", id, exception);
            player.sendSystemMessage(Component.literal("Failed to delete quest '" + id + "'"));
        }
    }

    public void removeQuestFromGroup(ServerPlayer player, String id, String group) {
        if (!Commands.LEVEL_GAMEMASTERS.check(player.permissions())) return;
        try {
            Path path = findQuestPath(id);
            JsonObject root = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonObject groups = root.getAsJsonObject("display").getAsJsonObject("groups");
            if (groups.size() <= 1 || !groups.has(group)) return;
            groups.remove(group);
            writeJsonAtomically(path, root);
            reload();
        } catch (Exception exception) {
            Heracles.LOGGER.error("Failed to remove quest {} from chapter {}", id, group, exception);
        }
    }

    public void chapterAction(ServerPlayer player, JsonObject action) {
        if (!Commands.LEVEL_GAMEMASTERS.check(player.permissions())) return;
        String operation = action.has("operation") ? action.get("operation").getAsString() : "";
        List<String> order = new java.util.ArrayList<>(catalog.groupOrder());
        Map<String, QuestCatalog.ChapterSettings> settings = new java.util.LinkedHashMap<>(catalog.chapterSettings());
        try {
            switch (operation) {
                case "create" -> {
                    String name = validChapterName(action.get("name").getAsString());
                    if (order.contains(name)) return;
                    order.add(name);
                    settings.put(name, chapterSettings(action));
                }
                case "update" -> {
                    String oldName = action.get("old_name").getAsString();
                    String newName = validChapterName(action.get("name").getAsString());
                    if (!order.contains(oldName) || (!oldName.equals(newName) && order.contains(newName))) return;
                    order.set(order.indexOf(oldName), newName);
                    settings.remove(oldName);
                    settings.put(newName, chapterSettings(action));
                    if (!oldName.equals(newName)) renameChapterInQuests(oldName, newName);
                }
                case "delete" -> {
                    String name = action.get("name").getAsString();
                    if (!order.remove(name)) return;
                    settings.remove(name);
                    deleteChapterFromQuests(name);
                    if (order.isEmpty()) {
                        order.add("Main");
                        settings.put("Main", new QuestCatalog.ChapterSettings("minecraft:map", ""));
                    }
                }
                case "reorder" -> {
                    List<String> requested = new java.util.ArrayList<>();
                    action.getAsJsonArray("order").forEach(value -> requested.add(value.getAsString()));
                    if (requested.size() != order.size() || !new java.util.HashSet<>(requested).equals(new java.util.HashSet<>(order))) return;
                    order = requested;
                }
                default -> { return; }
            }
            writeChapterMetadata(order, settings);
            reload();
        } catch (Exception exception) {
            Heracles.LOGGER.error("Failed chapter operation {}", operation, exception);
        }
    }

    private static String validChapterName(String value) {
        String name = value == null ? "" : value.trim();
        if (name.isEmpty() || name.length() > 64 || name.contains("\n") || name.contains("\r")) {
            throw new IllegalArgumentException("Invalid chapter name");
        }
        return name;
    }

    private static QuestCatalog.ChapterSettings chapterSettings(JsonObject action) {
        String icon = action.has("icon") ? action.get("icon").getAsString() : "minecraft:map";
        if (!BuiltInRegistries.ITEM.containsKey(net.minecraft.resources.Identifier.parse(icon))) icon = "minecraft:map";
        return new QuestCatalog.ChapterSettings(icon, action.has("background") ? action.get("background").getAsString() : "");
    }

    private void renameChapterInQuests(String oldName, String newName) throws java.io.IOException {
        mutateQuestGroups(oldName, (groups, placement) -> groups.add(newName, placement));
    }

    private void deleteChapterFromQuests(String name) throws java.io.IOException {
        mutateQuestGroups(name, (groups, placement) -> { });
    }

    private void mutateQuestGroups(String name, java.util.function.BiConsumer<JsonObject, com.google.gson.JsonElement> replacement) throws java.io.IOException {
        Path directory = FMLPaths.CONFIGDIR.get().resolve(Heracles.MOD_ID).resolve("quests");
        try (var files = Files.walk(directory)) {
            for (Path path : files.filter(file -> file.getFileName().toString().endsWith(".json")).toList()) {
                JsonObject root = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
                if (!root.has("display") || !root.getAsJsonObject("display").has("groups")) continue;
                JsonObject groups = root.getAsJsonObject("display").getAsJsonObject("groups");
                if (!groups.has(name)) continue;
                com.google.gson.JsonElement placement = groups.remove(name);
                replacement.accept(groups, placement);
                writeJsonAtomically(path, root);
            }
        }
    }

    private static void writeChapterMetadata(List<String> order, Map<String, QuestCatalog.ChapterSettings> settings) throws java.io.IOException {
        Path directory = FMLPaths.CONFIGDIR.get().resolve(Heracles.MOD_ID);
        Files.createDirectories(directory);
        Path orderTarget = directory.resolve("groups.txt");
        Path temporary = Files.createTempFile(directory, "groups-", ".tmp");
        try {
            Files.write(temporary, order, StandardCharsets.UTF_8);
            Files.move(temporary, orderTarget, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
        JsonObject root = new JsonObject();
        settings.forEach((name, value) -> {
            JsonObject json = new JsonObject();
            json.addProperty("icon", value.icon());
            json.addProperty("iconEnabled", true);
            json.addProperty("background", value.background());
            json.addProperty("backgroundOpacity", 100);
            root.add(name, json);
        });
        writeJsonAtomically(directory.resolve("group_settings.json"), root);
    }

    private Path findQuestPath(String id) throws java.io.IOException {
        Path directory = net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get().resolve(Heracles.MOD_ID).resolve("quests");
        try (var files = Files.walk(directory)) {
            List<Path> matches = files.filter(path -> path.getFileName().toString().equals(id + ".json")).toList();
            if (matches.size() != 1) throw new java.io.IOException("Expected one quest file for " + id);
            return matches.getFirst();
        }
    }

    private void replaceDependencyReferences(String oldId, String newId) throws java.io.IOException {
        Path directory = net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get().resolve(Heracles.MOD_ID).resolve("quests");
        try (var files = Files.walk(directory)) {
            for (Path path : files.filter(file -> file.getFileName().toString().endsWith(".json")).toList()) {
                JsonObject root = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
                if (!root.has("dependencies") || !root.get("dependencies").isJsonArray()) continue;
                com.google.gson.JsonArray updated = new com.google.gson.JsonArray();
                boolean changed = false;
                for (var value : root.getAsJsonArray("dependencies")) {
                    String dependency = value.getAsString();
                    if (dependency.equals(oldId)) {
                        changed = true;
                        if (newId != null) updated.add(newId);
                    } else updated.add(dependency);
                }
                if (changed) {
                    root.add("dependencies", updated);
                    writeJsonAtomically(path, root);
                }
            }
        }
    }

    private static void writeJsonAtomically(Path target, JsonObject root) throws java.io.IOException {
        Path temporary = Files.createTempFile(target.getParent(), target.getFileName().toString(), ".tmp");
        try {
            Files.writeString(temporary, GSON.toJson(root), StandardCharsets.UTF_8);
            try {
                Files.move(temporary, target, java.nio.file.StandardCopyOption.ATOMIC_MOVE, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private void resetQuestProgress(String oldId, String newId) {
        progress.values().forEach(quests -> {
            quests.remove(oldId);
            if (newId != null) quests.remove(newId);
        });
        saveProgress();
    }

    private void migrateQuestProgress(String oldId, String newId) {
        progress.values().forEach(quests -> {
            QuestProgress state = quests.remove(oldId);
            if (state != null) quests.put(newId, state);
        });
        saveProgress();
    }

    public void setDependency(
        ServerPlayer player,
        String prerequisiteId,
        String dependentId,
        boolean remove
    ) {
        if (!Commands.LEVEL_GAMEMASTERS.check(player.permissions())) return;
        QuestDefinition prerequisite = catalog.quests().get(prerequisiteId);
        QuestDefinition dependent = catalog.quests().get(dependentId);
        if (prerequisite == null || dependent == null) {
            player.sendSystemMessage(Component.literal("Unknown quest in dependency link"));
            return;
        }
        if (prerequisiteId.equals(dependentId)) {
            player.sendSystemMessage(Component.literal("A quest cannot depend on itself"));
            return;
        }
        Set<String> dependencies = new java.util.LinkedHashSet<>(dependent.dependencies());
        if (remove) {
            if (!dependencies.remove(prerequisiteId)) return;
        } else {
            if (dependencies.contains(prerequisiteId)) return;
            if (QuestCatalog.wouldCreateCycle(
                catalog.quests(),
                prerequisiteId,
                dependentId
            )) {
                player.sendSystemMessage(Component.literal("That link would create a dependency cycle"));
                return;
            }
            dependencies.add(prerequisiteId);
        }
        try {
            QuestCatalog.writeDependencies(
                FMLPaths.CONFIGDIR.get(),
                dependentId,
                dependencies
            );
            reload();
        } catch (java.io.IOException exception) {
            Heracles.LOGGER.error(
                "Failed to update dependencies for quest {}",
                dependentId,
                exception
            );
            player.sendSystemMessage(Component.literal("Failed to update quest dependencies"));
        }
    }


    public List<QuestDefinition.ValidationIssue> validationIssues() {
        return catalog.issues();
    }

    public void initialize(ServerPlayer player) {
        suppressNotifications.add(player.getUUID());
        try {
            updateInventoryTasks(player);
            for (QuestDefinition quest : catalog.quests().values()) {
                if (!isUnlocked(player, quest)) continue;
                for (QuestDefinition.Task task : flattenTasks(quest.tasks())) {
                    if (
                        task.kind() != QuestDefinition.TaskKind.ADVANCEMENT
                    ) continue;
                    for (String advancement : configuredStrings(
                        task,
                        "advancements",
                        task.value()
                    )) {
                        var holder = server
                            .getAdvancements()
                            .get(
                                net.minecraft.resources.Identifier.parse(
                                    advancement
                                )
                            );
                        if (
                            holder != null &&
                            player
                                .getAdvancements()
                                .getOrStartProgress(holder)
                                .isDone()
                        ) {
                            signal(
                                player,
                                new TaskEngine.Signal.AdvancementGranted(
                                    advancement
                                )
                            );
                        }
                    }
                }
            }
            updatePassiveTasks(player);
        } finally {
            suppressNotifications.remove(player.getUUID());
        }
    }

    public boolean triggerDummy(ServerPlayer player, String value) {
        return signal(player, new TaskEngine.Signal.Manual(value));
    }

    public String lockedDummyReason(ServerPlayer player, String value) {
        for (QuestDefinition quest : catalog.quests().values()) {
            boolean matches = flattenTasks(quest.tasks())
                .stream()
                .anyMatch(
                    task ->
                        task.kind() == QuestDefinition.TaskKind.DUMMY &&
                        task.value().equals(value)
                );
            if (!matches || isUnlocked(player, quest)) continue;
            String dependencies = quest
                .dependencies()
                .stream()
                .filter(dependency -> {
                    QuestDefinition required = catalog.quests().get(dependency);
                    return required == null || !isComplete(player, required);
                })
                .map(dependency -> {
                    QuestDefinition required = catalog.quests().get(dependency);
                    if (required == null) return (
                        "Unknown chapter › " + dependency
                    );
                    String chapter = required
                        .display()
                        .groups()
                        .keySet()
                        .stream()
                        .sorted()
                        .findFirst()
                        .orElse("Main");
                    return chapter + " › " + required.display().title();
                })
                .collect(Collectors.joining(", "));
            return (
                "Task '" +
                value +
                "' is locked by: " +
                (dependencies.isBlank() ? "quest dependencies" : dependencies) +
                "."
            );
        }
        return null;
    }

    public void updateInventoryTasks(ServerPlayer player) {
        TaskEngine.Signal.Inventory inventory = inventory(player, false);
        signal(player, inventory);
        signal(player, playerState(player));
        updatePassiveTasks(player);
    }

    public boolean signal(ServerPlayer player, TaskEngine.Signal signal) {
        Map<String, Boolean> wasUnlocked = questStates(player, false);
        Map<String, Boolean> wasComplete = questStates(player, true);
        boolean changed = false;
        for (QuestDefinition quest : catalog.quests().values()) {
            if (!isUnlocked(player, quest)) continue;
            for (QuestDefinition.Task task : quest.tasks().values()) {
                changed |= applyTask(player, quest, task, signal);
            }
        }
        if (changed) changed(player, wasUnlocked, wasComplete);
        return changed;
    }

    public boolean submit(ServerPlayer player, String questId, String taskId) {
        Map<String, Boolean> wasUnlocked = questStates(player, false);
        Map<String, Boolean> wasComplete = questStates(player, true);
        QuestDefinition quest = catalog.quests().get(questId);
        if (quest == null || !isUnlocked(player, quest)) return false;
        QuestDefinition.Task task = resolveTask(quest.tasks(), taskId);
        if (task == null) return false;
        TaskEngine.Signal signal;
        if (task.kind() == QuestDefinition.TaskKind.ITEM) {
            signal = inventory(player, true);
        } else if (task.kind() == QuestDefinition.TaskKind.XP) {
            signal = new TaskEngine.Signal.Experience(
                player.experienceLevel,
                player.totalExperience,
                true
            );
        } else if (task.kind() == QuestDefinition.TaskKind.CHECK) {
            signal = new TaskEngine.Signal.Check(playerData(player), true);
        } else {
            return false;
        }
        boolean changed = applyTask(player, quest, task, taskId, signal);
        if (changed) refreshCompositeProgress(player, quest);
        if (changed) changed(player, wasUnlocked, wasComplete);
        return changed;
    }

    private static QuestDefinition.Task resolveTask(
        Map<String, QuestDefinition.Task> tasks,
        String path
    ) {
        String[] parts = path.split("/");
        Map<String, QuestDefinition.Task> current = tasks;
        QuestDefinition.Task task = null;
        for (String part : parts) {
            task = current.get(part);
            if (task == null) return null;
            current = task.tasks();
        }
        return task;
    }

    private void refreshCompositeProgress(
        ServerPlayer player,
        QuestDefinition quest
    ) {
        for (QuestDefinition.Task task : quest.tasks().values()) {
            if (
                task.kind() == QuestDefinition.TaskKind.COMPOSITE
            ) updateCompositeSummary(player, quest, task, task.id());
        }
    }

    private boolean updateCompositeSummary(
        ServerPlayer player,
        QuestDefinition quest,
        QuestDefinition.Task task,
        String progressKey
    ) {
        boolean changed = false;
        for (QuestDefinition.Task child : task.tasks().values()) {
            if (child.kind() == QuestDefinition.TaskKind.COMPOSITE) {
                changed |= updateCompositeSummary(
                    player,
                    quest,
                    child,
                    progressKey + "/" + child.id()
                );
            }
        }
        double total = task
            .tasks()
            .values()
            .stream()
            .mapToDouble(child ->
                taskFraction(
                    player,
                    quest.id(),
                    child,
                    progressKey + "/" + child.id()
                )
            )
            .sum();
        return (
            setTaskProgress(
                player,
                quest,
                task,
                progressKey,
                Math.min(task.target(), (int) Math.floor(total + 0.000001))
            ) || changed
        );
    }

    public boolean claim(ServerPlayer player, String questId) {
        return claim(player, questId, Map.of());
    }

    public boolean claim(
        ServerPlayer player,
        String questId,
        Map<String, List<String>> selections
    ) {
        QuestDefinition quest = catalog.quests().get(questId);
        if (
            quest == null ||
            !isComplete(player, quest) ||
            progress(player, questId).claimed
        ) return false;
        for (QuestDefinition.Reward reward : quest.rewards().values()) {
            if (
                !canClaimReward(
                    player,
                    reward,
                    selections.getOrDefault(reward.id(), List.of())
                )
            ) {
                player.sendSystemMessage(
                    Component.literal(
                        "Cannot claim unsupported or incomplete reward: " +
                            reward.title()
                    )
                );
                return false;
            }
        }
        List<String> granted = new java.util.ArrayList<>();
        for (QuestDefinition.Reward reward : quest.rewards().values())
            grantReward(
                player,
                reward,
                selections.getOrDefault(reward.id(), List.of()),
                granted
            );
        progress(player, questId).claimed = true;
        changed(player);
        notify(
            player,
            "reward",
            "Rewards claimed",
            granted.isEmpty() ? quest.title() : String.join(", ", granted)
        );
        return true;
    }

    public boolean togglePinned(ServerPlayer player, String questId) {
        QuestDefinition quest = catalog.quests().get(questId);
        if (quest == null || !isUnlocked(player, quest)) return false;
        QuestProgress state = progress(player, questId);
        state.pinned = !state.pinned;
        changed(player);
        return true;
    }

    public void reset(ServerPlayer player) {
        progress.remove(player.getUUID());
        changed(player);
    }

    public boolean isUnlocked(ServerPlayer player, QuestDefinition quest) {
        return quest
            .dependencies()
            .stream()
            .allMatch(dependency -> {
                QuestDefinition required = catalog.quests().get(dependency);
                return required != null && isComplete(player, required);
            });
    }

    public boolean isComplete(ServerPlayer player, QuestDefinition quest) {
        QuestProgress progress = progress(player, quest.id());
        return quest
            .tasks()
            .values()
            .stream()
            .allMatch(
                task ->
                    progress.tasks.getOrDefault(task.id(), 0) >= task.target()
            );
    }

    private boolean setTaskProgress(
        ServerPlayer player,
        QuestDefinition quest,
        QuestDefinition.Task task,
        String progressKey,
        int value
    ) {
        QuestProgress progress = progress(player, quest.id());
        int previous = progress.tasks.getOrDefault(progressKey, 0);
        if (previous == value) return false;
        progress.tasks.put(progressKey, value);
        return true;
    }

    private boolean applyTask(
        ServerPlayer player,
        QuestDefinition quest,
        QuestDefinition.Task task,
        TaskEngine.Signal signal
    ) {
        return applyTask(player, quest, task, task.id(), signal);
    }

    private boolean applyTask(
        ServerPlayer player,
        QuestDefinition quest,
        QuestDefinition.Task task,
        String progressKey,
        TaskEngine.Signal signal
    ) {
        if (task.kind() == QuestDefinition.TaskKind.COMPOSITE) {
            boolean changed = false;
            for (QuestDefinition.Task child : task.tasks().values()) {
                changed |= applyTask(
                    player,
                    quest,
                    child,
                    progressKey + "/" + child.id(),
                    signal
                );
            }
            double total = task
                .tasks()
                .values()
                .stream()
                .mapToDouble(child ->
                    taskFraction(
                        player,
                        quest.id(),
                        child,
                        progressKey + "/" + child.id()
                    )
                )
                .sum();
            int summarized = Math.min(
                task.target(),
                (int) Math.floor(total + 0.000001)
            );
            changed |= setTaskProgress(
                player,
                quest,
                task,
                progressKey,
                summarized
            );
            return changed;
        }
        int current = progress(player, quest.id()).tasks.getOrDefault(
            progressKey,
            0
        );
        TaskEngine.Result result = taskEngine.apply(task, current, signal);
        if (result.consumeAmount() > 0) consume(
            player,
            task,
            result.consumeAmount()
        );
        return setTaskProgress(
            player,
            quest,
            task,
            progressKey,
            result.progress()
        );
    }

    private double taskFraction(
        ServerPlayer player,
        String questId,
        QuestDefinition.Task task,
        String progressKey
    ) {
        if (task.kind() == QuestDefinition.TaskKind.COMPOSITE) {
            double total = task
                .tasks()
                .values()
                .stream()
                .mapToDouble(child ->
                    taskFraction(
                        player,
                        questId,
                        child,
                        progressKey + "/" + child.id()
                    )
                )
                .sum();
            return Math.min(1, total / Math.max(1, task.target()));
        }
        return Math.min(
            1,
            progress(player, questId).tasks.getOrDefault(progressKey, 0) /
                (double) Math.max(1, task.target())
        );
    }

    private void updatePassiveTasks(ServerPlayer player) {
        for (QuestDefinition quest : catalog.quests().values()) {
            if (!isUnlocked(player, quest)) continue;
            for (QuestDefinition.Task task : flattenTasks(quest.tasks())) {
                if (task.kind() == QuestDefinition.TaskKind.RECIPE) {
                    for (String recipe : configuredStrings(
                        task,
                        "recipes",
                        task.value()
                    )) {
                        ResourceKey<Recipe<?>> key = ResourceKey.create(
                            Registries.RECIPE,
                            net.minecraft.resources.Identifier.parse(recipe)
                        );
                        if (player.getRecipeBook().contains(key)) signal(
                            player,
                            new TaskEngine.Signal.RecipeUnlocked(recipe)
                        );
                    }
                } else if (
                    task.kind() == QuestDefinition.TaskKind.STAT &&
                    !task.value().isBlank()
                ) {
                    var id = net.minecraft.resources.Identifier.parse(
                        task.value()
                    );
                    signal(
                        player,
                        new TaskEngine.Signal.Statistic(
                            task.value(),
                            player.getStats().getValue(Stats.CUSTOM, id)
                        )
                    );
                }
            }
        }
        Set<TaskEngine.Signal.RegistryEntry> structures = structuresAt(player);
        if (!structures.isEmpty()) signal(
            player,
            new TaskEngine.Signal.Structures(structures)
        );
    }

    private static void consume(
        ServerPlayer player,
        QuestDefinition.Task task,
        int amount
    ) {
        if (task.kind() == QuestDefinition.TaskKind.XP) {
            String unit = task.source().has("xpType")
                ? task
                      .source()
                      .get("xpType")
                      .getAsString()
                      .toLowerCase(java.util.Locale.ROOT)
                : "level";
            if (unit.endsWith("points")) player.giveExperiencePoints(-amount);
            else player.giveExperienceLevels(-amount);
            return;
        }
        if (task.kind() != QuestDefinition.TaskKind.ITEM) return;
        int remaining = amount;
        for (
            int slot = 0;
            slot < player.getInventory().getContainerSize() && remaining > 0;
            slot++
        ) {
            ItemStack stack = player.getInventory().getItem(slot);
            TaskEngine.Signal.RegistryEntry entry = itemEntry(player, stack);
            if (
                !RegistryPredicate.matches(
                    task.source().get("item"),
                    task.value(),
                    entry
                )
            ) continue;
            if (
                !RegistryPredicate.contains(
                    task.source().get("components"),
                    entry.data()
                )
            ) continue;
            if (
                !RegistryPredicate.contains(
                    task.source().get("nbt"),
                    entry.data()
                )
            ) continue;
            int removed = Math.min(stack.getCount(), remaining);
            stack.shrink(removed);
            remaining -= removed;
        }
    }

    private boolean canClaimReward(
        ServerPlayer player,
        QuestDefinition.Reward reward,
        List<String> selected
    ) {
        return switch (reward.kind()) {
            case XP, COMMAND -> true;
            case ITEM -> validIdentifier(reward.value());
            case LOOT_TABLE -> {
                if (!validIdentifier(reward.value())) yield false;
                ResourceKey<LootTable> key = ResourceKey.create(
                    Registries.LOOT_TABLE,
                    net.minecraft.resources.Identifier.parse(reward.value())
                );
                yield server.reloadableRegistries().getLootTable(key) !=
                    LootTable.EMPTY;
            }
            case SELECTABLE -> !selected.isEmpty() &&
                selected.size() <= reward.amount() &&
                selected.stream().distinct().count() == selected.size() &&
                selected.stream().allMatch(id -> {
                    QuestDefinition.Reward choice = reward.rewards().get(id);
                    return (
                        choice != null &&
                        choice.kind() !=
                            QuestDefinition.RewardKind.SELECTABLE &&
                        canClaimReward(player, choice, List.of())
                    );
                });
            case UNSUPPORTED -> false;
        };
    }

    private void grantReward(
        ServerPlayer player,
        QuestDefinition.Reward reward,
        List<String> selected,
        List<String> granted
    ) {
        switch (reward.kind()) {
            case XP -> {
                if (
                    reward
                        .value()
                        .toLowerCase(java.util.Locale.ROOT)
                        .endsWith("points")
                ) player.giveExperiencePoints(reward.amount());
                else player.giveExperienceLevels(reward.amount());
                granted.add(
                    reward.amount() +
                        (reward
                            .value()
                            .toLowerCase(java.util.Locale.ROOT)
                            .endsWith("points")
                            ? " XP"
                            : " levels")
                );
            }
            case ITEM -> {
                ItemStack stack = new ItemStack(
                    BuiltInRegistries.ITEM.getValue(
                        net.minecraft.resources.Identifier.parse(reward.value())
                    ),
                    reward.amount()
                );
                giveItem(player, stack);
                granted.add(
                    stack.getCount() + "× " + stack.getHoverName().getString()
                );
            }
            case COMMAND -> server
                .getCommands()
                .performPrefixedCommand(
                    player
                        .createCommandSourceStack()
                        .withSuppressedOutput()
                        .withPermission(
                            net.minecraft.server.permissions.PermissionSet.ALL_PERMISSIONS
                        ),
                    reward.value()
                );
            case LOOT_TABLE -> {
                ResourceKey<LootTable> key = ResourceKey.create(
                    Registries.LOOT_TABLE,
                    net.minecraft.resources.Identifier.parse(reward.value())
                );
                LootTable table = server
                    .reloadableRegistries()
                    .getLootTable(key);
                LootParams params = new LootParams.Builder(player.level())
                    .withParameter(LootContextParams.ORIGIN, player.position())
                    .withOptionalParameter(
                        LootContextParams.THIS_ENTITY,
                        player
                    )
                    .create(LootContextParamSets.CHEST);
                table.getRandomItems(params, stack -> {
                    giveItem(player, stack.copy());
                    granted.add(
                        stack.getCount() +
                            "× " +
                            stack.getHoverName().getString()
                    );
                });
            }
            case SELECTABLE -> selected
                .stream()
                .map(reward.rewards()::get)
                .forEach(choice ->
                    grantReward(player, choice, List.of(), granted)
                );
            case UNSUPPORTED -> throw new IllegalStateException(
                "Unsupported reward passed validation: " + reward.type()
            );
        }
    }

    private static void giveItem(ServerPlayer player, ItemStack stack) {
        if (!player.addItem(stack.copy())) {
            var dropped = player.drop(stack.copy(), false);
            if (dropped != null) {
                dropped.setNoPickUpDelay();
                dropped.setTarget(player.getUUID());
            }
        }
    }

    private static boolean validIdentifier(String value) {
        try {
            net.minecraft.resources.Identifier.parse(value);
            return !value.isBlank();
        } catch (Exception ignored) {
            return false;
        }
    }

    private static TaskEngine.Signal.WorldState playerState(
        ServerPlayer player
    ) {
        var biome = player.level().getBiome(player.blockPosition());
        return new TaskEngine.Signal.WorldState(
            player.level().dimension().identifier().toString(),
            registryEntry(biome, new JsonObject(), 1),
            player.getX(),
            player.getY(),
            player.getZ()
        );
    }

    private static TaskEngine.Signal.Inventory inventory(
        ServerPlayer player,
        boolean submit
    ) {
        java.util.List<TaskEngine.Signal.RegistryEntry> entries =
            new java.util.ArrayList<>();
        for (
            int slot = 0;
            slot < player.getInventory().getContainerSize();
            slot++
        ) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.isEmpty()) entries.add(itemEntry(player, stack));
        }
        return new TaskEngine.Signal.Inventory(entries, submit);
    }

    public static TaskEngine.Signal.RegistryEntry itemEntry(
        ServerPlayer player,
        ItemStack stack
    ) {
        JsonObject data = new JsonObject();
        ItemStack.CODEC.encodeStart(
            player
                .registryAccess()
                .createSerializationContext(JsonOps.INSTANCE),
            stack
        )
            .result()
            .filter(com.google.gson.JsonElement::isJsonObject)
            .map(com.google.gson.JsonElement::getAsJsonObject)
            .ifPresent(encoded -> {
                if (
                    encoded.has("components") &&
                    encoded.get("components").isJsonObject()
                ) data.add("components", encoded.get("components"));
                if (
                    encoded.has("components") &&
                    encoded.get("components").isJsonObject()
                ) {
                    encoded
                        .getAsJsonObject("components")
                        .entrySet()
                        .forEach(entry ->
                            data.add(entry.getKey(), entry.getValue())
                        );
                }
            });
        return registryEntry(stack.typeHolder(), data, stack.getCount());
    }

    public static <T> TaskEngine.Signal.RegistryEntry registryEntry(
        net.minecraft.core.Holder<T> holder,
        JsonObject data,
        int count
    ) {
        String id = holder
            .unwrapKey()
            .map(key -> key.identifier().toString())
            .orElse("");
        Set<String> tags = holder
            .tags()
            .map(tag -> tag.location().toString())
            .collect(Collectors.toSet());
        return new TaskEngine.Signal.RegistryEntry(id, tags, data, count);
    }

    private Set<TaskEngine.Signal.RegistryEntry> structuresAt(
        ServerPlayer player
    ) {
        java.util.List<QuestDefinition.Task> tasks = catalog
            .quests()
            .values()
            .stream()
            .filter(quest -> isUnlocked(player, quest))
            .flatMap(quest -> flattenTasks(quest.tasks()).stream())
            .filter(task -> task.kind() == QuestDefinition.TaskKind.STRUCTURE)
            .toList();
        if (tasks.isEmpty()) return Set.of();
        var lookup = server
            .registryAccess()
            .lookupOrThrow(Registries.STRUCTURE);
        return lookup
            .listElements()
            .filter(holder -> {
                TaskEngine.Signal.RegistryEntry entry = registryEntry(
                    holder,
                    new JsonObject(),
                    1
                );
                return tasks
                    .stream()
                    .anyMatch(task ->
                        RegistryPredicate.matches(
                            task.source().get("structures"),
                            task.value(),
                            entry
                        )
                    );
            })
            .filter(holder ->
                player
                    .level()
                    .structureManager()
                    .getStructureWithPieceAt(
                        player.blockPosition(),
                        holder.value()
                    )
                    .isValid()
            )
            .map(holder -> registryEntry(holder, new JsonObject(), 1))
            .collect(Collectors.toSet());
    }

    private static JsonObject playerData(ServerPlayer player) {
        TagValueOutput output = TagValueOutput.createWithContext(
            ProblemReporter.DISCARDING,
            player.registryAccess()
        );
        player.saveWithoutId(output);
        com.google.gson.JsonElement json = NbtOps.INSTANCE.convertTo(
            JsonOps.INSTANCE,
            output.buildResult()
        );
        return json.isJsonObject() ? json.getAsJsonObject() : new JsonObject();
    }

    private static java.util.List<String> configuredStrings(
        QuestDefinition.Task task,
        String key,
        String fallback
    ) {
        if (!task.source().has(key)) return fallback.isBlank()
            ? java.util.List.of()
            : java.util.List.of(fallback);
        var value = task.source().get(key);
        if (value.isJsonArray()) return value
            .getAsJsonArray()
            .asList()
            .stream()
            .map(com.google.gson.JsonElement::getAsString)
            .toList();
        return java.util.List.of(value.getAsString());
    }

    private static List<QuestDefinition.Task> flattenTasks(
        Map<String, QuestDefinition.Task> tasks
    ) {
        List<QuestDefinition.Task> result = new java.util.ArrayList<>();
        for (QuestDefinition.Task task : tasks.values()) {
            result.add(task);
            if (
                task.kind() == QuestDefinition.TaskKind.COMPOSITE
            ) result.addAll(flattenTasks(task.tasks()));
        }
        return result;
    }

    private QuestProgress progress(ServerPlayer player, String questId) {
        return progress
            .computeIfAbsent(player.getUUID(), ignored -> new HashMap<>())
            .computeIfAbsent(questId, ignored -> new QuestProgress());
    }

    private void changed(ServerPlayer player) {
        saveProgress();
        sync(player, false);
    }

    private void changed(
        ServerPlayer player,
        Map<String, Boolean> wasUnlocked,
        Map<String, Boolean> wasComplete
    ) {
        saveProgress();
        sync(player, false);
        if (suppressNotifications.contains(player.getUUID())) return;
        for (QuestDefinition quest : catalog.quests().values()) {
            boolean unlocked = isUnlocked(player, quest);
            boolean complete = isComplete(player, quest);
            if (
                !wasComplete.getOrDefault(quest.id(), false) && complete
            ) notify(player, "complete", "Quest completed", quest.title());
            if (
                !wasUnlocked.getOrDefault(quest.id(), false) &&
                unlocked &&
                quest.settings().unlockNotification()
            ) notify(player, "unlock", "Quest unlocked", quest.title());
        }
    }

    private Map<String, Boolean> questStates(
        ServerPlayer player,
        boolean complete
    ) {
        Map<String, Boolean> states = new HashMap<>();
        catalog
            .quests()
            .forEach((id, quest) ->
                states.put(
                    id,
                    complete
                        ? isComplete(player, quest)
                        : isUnlocked(player, quest)
                )
            );
        return states;
    }

    private static void notify(
        ServerPlayer player,
        String kind,
        String title,
        String detail
    ) {
        PacketDistributor.sendToPlayer(
            player,
            new QuestNetwork.NotificationPayload(kind, title, detail)
        );
    }

    public void sync(ServerPlayer player, boolean open) {
        PacketDistributor.sendToPlayer(
            player,
            new QuestNetwork.SyncPayload(snapshot(player), open)
        );
    }

    private String snapshot(ServerPlayer player) {
        JsonObject root = new JsonObject();
        JsonObject chapters = new JsonObject();
        chapters.add("order", GSON.toJsonTree(catalog.groupOrder()));
        chapters.add("settings", GSON.toJsonTree(catalog.chapterSettings()));
        root.add("__chapters", chapters);
        for (QuestDefinition quest : catalog.quests().values()) {
            JsonObject json = GSON.toJsonTree(quest).getAsJsonObject();
            QuestProgress state = progress(player, quest.id());
            json.addProperty("unlocked", isUnlocked(player, quest));
            json.addProperty("complete", isComplete(player, quest));
            json.addProperty("claimed", state.claimed);
            json.addProperty("pinned", state.pinned);
            json.add("progress", GSON.toJsonTree(state.tasks));
            root.add(quest.id(), json);
        }
        return GSON.toJson(root);
    }

    private void loadProgress() {
        if (!Files.exists(progressFile)) return;
        try {
            JsonObject root = JsonParser.parseString(
                Files.readString(progressFile, StandardCharsets.UTF_8)
            ).getAsJsonObject();
            root.entrySet().forEach(player ->
                progress.put(
                    UUID.fromString(player.getKey()),
                    parsePlayerProgress(player.getValue().getAsJsonObject())
                )
            );
        } catch (Exception exception) {
            Heracles.LOGGER.error(
                "Failed to load quest progress from {}",
                progressFile,
                exception
            );
        }
    }

    private static Map<String, QuestProgress> parsePlayerProgress(
        JsonObject root
    ) {
        Map<String, QuestProgress> result = new HashMap<>();
        root.entrySet().forEach(entry ->
            result.put(
                entry.getKey(),
                GSON.fromJson(entry.getValue(), QuestProgress.class)
            )
        );
        return result;
    }

    private void saveProgress() {
        try {
            Files.createDirectories(progressFile.getParent());
            Files.writeString(
                progressFile,
                GSON.toJson(progress),
                StandardCharsets.UTF_8
            );
        } catch (Exception exception) {
            Heracles.LOGGER.error(
                "Failed to save quest progress to {}",
                progressFile,
                exception
            );
        }
    }

    private static final class QuestProgress {

        private final Map<String, Integer> tasks = new HashMap<>();
        private boolean claimed;
        private boolean pinned;
    }
}
