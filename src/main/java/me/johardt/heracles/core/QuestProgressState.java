package me.johardt.heracles.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Mutable progress for one player and one quest.
 *
 * <p>The state deliberately knows nothing about players, persistence files, or
 * networking. It owns the progress invariants that are shared by gameplay,
 * migration, and operator resets.</p>
 */
public final class QuestProgressState {
    private final Map<String, Integer> taskProgress = new LinkedHashMap<>();
    private final Set<String> claimedRewards = new LinkedHashSet<>();
    private boolean pinned;

    public QuestProgressState() {}

    public QuestProgressState(Map<String, Integer> taskProgress, Set<String> claimedRewards, boolean pinned) {
        if (taskProgress != null) taskProgress.forEach(this::setTaskProgress);
        if (claimedRewards != null) this.claimedRewards.addAll(claimedRewards);
        this.pinned = pinned;
    }

    public int getTaskProgress(String path) {
        return taskProgress.getOrDefault(path, 0);
    }

    public void setTaskProgress(String path, int value) {
        if (path == null || path.isBlank()) throw new IllegalArgumentException("Task path is required");
        taskProgress.put(path, Math.max(0, value));
    }

    public Map<String, Integer> taskProgress() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(taskProgress));
    }

    /** Removes the exact task path and all nested progress paths. */
    public boolean resetTaskPath(String path) {
        if (path == null || path.isBlank()) return false;
        String prefix = path + "/";
        return taskProgress.keySet().removeIf(candidate -> candidate.equals(path) || candidate.startsWith(prefix));
    }

    /** Clears task and reward progress while leaving the pin unchanged. */
    public void clearProgress() {
        taskProgress.clear();
        claimedRewards.clear();
    }

    public Set<String> claimedRewards() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(claimedRewards));
    }

    public boolean markRewardClaimed(String rewardId) {
        if (rewardId == null || rewardId.isBlank()) throw new IllegalArgumentException("Reward ID is required");
        return claimedRewards.add(rewardId);
    }

    public boolean unmarkRewardClaimed(String rewardId) {
        if (rewardId == null || rewardId.isBlank()) return false;
        return claimedRewards.remove(rewardId);
    }

    public boolean allRewardsClaimed(QuestDefinition quest) {
        if (quest == null || quest.rewards().isEmpty()) return false;
        return quest.rewards().keySet().stream().allMatch(claimedRewards::contains);
    }

    public boolean isPinned() {
        return pinned;
    }

    public void setPinned(boolean pinned) {
        this.pinned = pinned;
    }

    /** Serializes the current, post-migration progress-file shape. */
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        JsonObject tasks = new JsonObject();
        taskProgress.forEach(tasks::addProperty);
        json.add("tasks", tasks);
        JsonArray rewards = new JsonArray();
        claimedRewards.stream().sorted().forEach(rewards::add);
        json.add("claimed_rewards", rewards);
        json.addProperty("pinned", pinned);
        return json;
    }

    /** Parses both the current progress shape and the legacy claimed boolean. */
    public static QuestProgressState fromJson(QuestDefinition quest, JsonObject json) {
        if (quest == null) throw new IllegalArgumentException("Quest definition is required");
        if (json == null) throw new IllegalArgumentException("Progress entry must be an object");

        QuestProgressState state = new QuestProgressState();
        if (json.has("tasks")) {
            JsonElement tasksElement = json.get("tasks");
            if (!tasksElement.isJsonObject()) throw new IllegalArgumentException("Progress tasks must be an object");
            tasksElement.getAsJsonObject().entrySet().forEach(entry -> {
                if (!entry.getValue().isJsonPrimitive() || !entry.getValue().getAsJsonPrimitive().isNumber()) {
                    throw new IllegalArgumentException("Task progress for '" + entry.getKey() + "' must be a number");
                }
                if (hasTaskPath(quest.tasks(), entry.getKey())) {
                    state.setTaskProgress(entry.getKey(), entry.getValue().getAsInt());
                }
            });
        }

        if (json.has("claimed_rewards")) {
            JsonElement rewardsElement = json.get("claimed_rewards");
            if (!rewardsElement.isJsonArray()) throw new IllegalArgumentException("claimed_rewards must be an array");
            for (JsonElement reward : rewardsElement.getAsJsonArray()) {
                if (!reward.isJsonPrimitive() || !reward.getAsJsonPrimitive().isString()) {
                    throw new IllegalArgumentException("claimed_rewards entries must be strings");
                }
                String id = reward.getAsString();
                if (quest.rewards().containsKey(id)) state.claimedRewards.add(id);
            }
        } else if (json.has("claimed")) {
            JsonElement claimed = json.get("claimed");
            if (!claimed.isJsonPrimitive() || !claimed.getAsJsonPrimitive().isBoolean()) {
                throw new IllegalArgumentException("claimed must be a boolean");
            }
            if (claimed.getAsBoolean()) state.claimedRewards.addAll(quest.rewards().keySet());
        }

        if (json.has("pinned")) {
            JsonElement pinned = json.get("pinned");
            if (!pinned.isJsonPrimitive() || !pinned.getAsJsonPrimitive().isBoolean()) {
                throw new IllegalArgumentException("pinned must be a boolean");
            }
            state.pinned = pinned.getAsBoolean();
        }
        return state;
    }

    private static boolean hasTaskPath(Map<String, QuestDefinition.Task> tasks, String path) {
        if (path == null || path.isBlank()) return false;
        String[] parts = path.split("/", -1);
        Map<String, QuestDefinition.Task> current = tasks;
        for (String part : parts) {
            if (part.isBlank()) return false;
            QuestDefinition.Task task = current.get(part);
            if (task == null) return false;
            current = task.tasks();
        }
        return true;
    }
}
