package me.johardt.heracles.client;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;
import me.johardt.heracles.core.QuestDefinition;
import me.johardt.heracles.core.QuestDiagnostics;
import me.johardt.heracles.core.QuestDraft;
import me.johardt.heracles.core.QuestIconDefinition;
import me.johardt.heracles.core.RegistryValidation;

/**
 * Mutable state for authoring one quest.
 *
 * <p>This is deliberately independent of widgets and rendering. A screen may be
 * rebuilt around a copied session without reassembling the authored document
 * field-by-field.</p>
 */
final class QuestAuthoringSession {
    private QuestDraft baseline;
    boolean open;
    boolean editingExisting;
    String originalId;
    int x;
    int y;
    String xText = "0";
    String yText = "0";
    boolean xInvalid;
    boolean yInvalid;
    String id = "";
    String title = "";
    String subtitle = "";
    String body = "";
    String icon = "minecraft:map";
    boolean descriptionTouched;
    boolean iconTouched;
    String background = "heracles:textures/gui/quest_backgrounds/default.png";
    boolean individualProgress;
    QuestDefinition.Visibility hiddenUntil = QuestDefinition.Visibility.LOCKED;
    boolean unlockNotification;
    boolean showDependencyArrow = true;
    boolean repeatable;
    boolean autoClaimRewards;
    JsonObject groups = new JsonObject();
    int iconSize;
    String iconSizeText;
    boolean iconSizeTouched;
    boolean iconSizeInvalid;
    final List<TaskDraft> tasks = new ArrayList<>();
    int editingTaskIndex = -1;
    TaskDraft editingTask;
    final List<TaskDraft> taskEditorParents = new ArrayList<>();
    final List<Integer> taskEditorParentIndexes = new ArrayList<>();
    boolean nestedTasksOpen;
    int nestedTaskScroll;
    boolean nestedTaskChooserOpen;
    String taskEditorError = "";
    int taskDeleteConfirmation = -1;
    final List<RewardDraft> rewards = new ArrayList<>();
    int editingRewardIndex = -1;
    RewardDraft editingReward;
    String rewardEditorError = "";
    boolean nestedRewardsOpen;
    boolean nestedRewardChooserOpen;
    int nestedRewardScroll;
    int editingNestedRewardIndex = -1;
    RewardDraft editingNestedReward;

    QuestAuthoringSession(int defaultIconSize) {
        iconSize = defaultIconSize;
        iconSizeText = Integer.toString(defaultIconSize);
    }

    QuestAuthoringSession copy() {
        QuestAuthoringSession copy = new QuestAuthoringSession(iconSize);
        copy.baseline = baseline == null ? null : baseline.copy();
        copy.open = open;
        copy.editingExisting = editingExisting;
        copy.originalId = originalId;
        copy.x = x;
        copy.y = y;
        copy.xText = xText;
        copy.yText = yText;
        copy.xInvalid = xInvalid;
        copy.yInvalid = yInvalid;
        copy.id = id;
        copy.title = title;
        copy.subtitle = subtitle;
        copy.body = body;
        copy.icon = icon;
        copy.descriptionTouched = descriptionTouched;
        copy.iconTouched = iconTouched;
        copy.background = background;
        copy.individualProgress = individualProgress;
        copy.hiddenUntil = hiddenUntil;
        copy.unlockNotification = unlockNotification;
        copy.showDependencyArrow = showDependencyArrow;
        copy.repeatable = repeatable;
        copy.autoClaimRewards = autoClaimRewards;
        copy.groups = groups.deepCopy();
        copy.iconSizeText = iconSizeText;
        copy.iconSizeTouched = iconSizeTouched;
        copy.iconSizeInvalid = iconSizeInvalid;
        tasks.forEach(task -> copy.tasks.add(task.copy()));
        copy.editingTaskIndex = editingTaskIndex;
        copy.editingTask = editingTask == null ? null : editingTask.copy();
        taskEditorParents.forEach(parent -> copy.taskEditorParents.add(parent.copy()));
        copy.taskEditorParentIndexes.addAll(taskEditorParentIndexes);
        copy.nestedTasksOpen = nestedTasksOpen;
        copy.nestedTaskScroll = nestedTaskScroll;
        copy.nestedTaskChooserOpen = nestedTaskChooserOpen;
        copy.taskEditorError = taskEditorError;
        copy.taskDeleteConfirmation = taskDeleteConfirmation;
        rewards.forEach(reward -> copy.rewards.add(reward.copy()));
        copy.editingRewardIndex = editingRewardIndex;
        copy.editingReward = editingReward == null ? null : editingReward.copy();
        copy.rewardEditorError = rewardEditorError;
        copy.nestedRewardsOpen = nestedRewardsOpen;
        copy.nestedRewardChooserOpen = nestedRewardChooserOpen;
        copy.nestedRewardScroll = nestedRewardScroll;
        copy.editingNestedRewardIndex = editingNestedRewardIndex;
        copy.editingNestedReward = editingNestedReward == null ? null : editingNestedReward.copy();
        return copy;
    }

    void begin(QuestDraft draft) {
        baseline = draft.copy();
        open = true;
    }

    void discard() {
        open = false;
        editingExisting = false;
        originalId = null;
        baseline = null;
    }

    boolean hasBaseline() {
        return baseline != null;
    }

    void setGroupPosition(String group, int x, int y) {
        if (baseline != null) baseline.setGroupPosition(group, x, y);
    }

    QuestDraft draft() {
        QuestDraft result = baseline == null ? QuestDraft.create(null) : baseline.copy();
        if (id != null && !id.isBlank()) result.rename(id);
        result.setDisplayBasics(title, subtitle, background, groups);
        if (descriptionTouched) result.setDescription(body);
        if (iconTouched) result.setIcon(QuestIconDefinition.item(icon).source());
        if (iconSizeTouched && !iconSizeInvalid) result.setIconSize(iconSize);
        result.setSettings(
            individualProgress,
            hiddenUntil,
            unlockNotification,
            showDependencyArrow,
            repeatable,
            autoClaimRewards
        );
        JsonObject taskDocument = new JsonObject();
        tasks.forEach(task -> taskDocument.add(task.id, task.source.deepCopy()));
        result.replaceTasks(taskDocument);
        JsonObject rewardDocument = new JsonObject();
        rewards.forEach(reward -> rewardDocument.add(reward.id, reward.source.deepCopy()));
        result.replaceRewards(rewardDocument);
        return result;
    }

    String validationError(
        Predicate<String> validItem,
        RegistryValidation.Resolver validRegistryTarget
    ) {
        if (!id.matches("[a-z0-9_.-]+")) return "Quest ID may only contain lowercase letters, numbers, ., _, and -.";
        if (title.trim().isEmpty()) return "Quest title is required.";
        if (xInvalid) return "Position X must be a valid integer.";
        if (yInvalid) return "Position Y must be a valid integer.";
        if (iconSizeInvalid) return "Icon size must be an integer from 8 to 64.";
        Set<String> taskIds = new HashSet<>();
        for (TaskDraft task : tasks) {
            if (task.id == null || !task.id.matches("[a-z0-9_.-]+")) return "Task IDs may only contain lowercase letters, numbers, ., _, and -.";
            if (!taskIds.add(task.id)) return "Duplicate task ID: " + task.id;
        }
        Set<String> rewardIds = new HashSet<>();
        for (RewardDraft reward : rewards) {
            if (reward.id == null || !reward.id.matches("[a-z0-9_.-]+")) return "Reward IDs may only contain lowercase letters, numbers, ., _, and -.";
            if (!rewardIds.add(reward.id)) return "Duplicate reward ID: " + reward.id;
        }
        return draft().diagnostics(validItem, validRegistryTarget).stream()
            .filter(QuestDiagnostics.Diagnostic::blocksSave)
            .map(diagnostic -> diagnostic.path() + ": " + diagnostic.message())
            .collect(java.util.stream.Collectors.joining("\n"));
    }

    static final class TaskDraft {
        String id;
        final String type;
        final JsonObject source;

        TaskDraft(String id, String type, JsonObject source) {
            this.id = id;
            this.type = type;
            this.source = source;
        }

        TaskDraft copy() { return new TaskDraft(id, type, source.deepCopy()); }
        boolean sameAs(TaskDraft other) {
            return other != null && java.util.Objects.equals(id, other.id)
                && type.equals(other.type) && source.equals(other.source);
        }
    }

    static final class RewardDraft {
        String id;
        final String type;
        final JsonObject source;

        RewardDraft(String id, String type, JsonObject source) {
            this.id = id;
            this.type = type;
            this.source = source;
        }

        RewardDraft copy() { return new RewardDraft(id, type, source.deepCopy()); }
        boolean sameAs(RewardDraft other) {
            return other != null && java.util.Objects.equals(id, other.id)
                && type.equals(other.type) && source.equals(other.source);
        }
    }
}
