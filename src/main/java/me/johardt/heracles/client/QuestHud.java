package me.johardt.heracles.client;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import me.johardt.heracles.core.QuestDefinition;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

final class QuestHud {

    static final SystemToast.SystemToastId UNLOCK_TOAST =
        new SystemToast.SystemToastId();
    static final SystemToast.SystemToastId COMPLETE_TOAST =
        new SystemToast.SystemToastId();
    static final SystemToast.SystemToastId REWARD_TOAST =
        new SystemToast.SystemToastId();
    private static final Gson GSON = new Gson();

    private QuestHud() {}

    static void render(
        GuiGraphicsExtractor graphics,
        JsonObject snapshot,
        boolean collapsed
    ) {
        Minecraft minecraft = Minecraft.getInstance();
        if (
            minecraft.player == null ||
            minecraft.gui.hud.isHidden() ||
            snapshot == null
        ) return;
        var pinned = snapshot
            .entrySet()
            .stream()
            .filter(entry -> {
                JsonObject quest = entry.getValue().getAsJsonObject();
                return (
                    quest.has("pinned") && quest.get("pinned").getAsBoolean()
                );
            })
            .limit(5)
            .toList();
        if (pinned.isEmpty()) return;

        int width = 168;
        int x = graphics.guiWidth() - width - 6;
        int height = collapsed
            ? 19
            : 19 +
              pinned
                  .stream()
                  .mapToInt(
                      entry ->
                          15 +
                          taskRows(entry.getValue().getAsJsonObject()).size() *
                          11
                  )
                  .sum();
        graphics.fill(x, 6, x + width, 6 + height, 0xB814171C);
        graphics.outline(x, 6, width, height, 0xCC59616D);
        graphics.text(
            minecraft.font,
            Component.literal(
                "Pinned quests " + (collapsed ? "[J] +" : "[J] −")
            ),
            x + 6,
            11,
            0xFFFFD966,
            true
        );
        if (collapsed) return;
        int y = 27;
        for (var entry : pinned) {
            JsonObject json = entry.getValue().getAsJsonObject();
            QuestDefinition quest = GSON.fromJson(json, QuestDefinition.class);
            graphics.text(
                minecraft.font,
                Component.literal(quest.title()),
                x + 6,
                y,
                0xFFFFD966,
                true
            );
            y += 12;
            JsonObject progress = json.getAsJsonObject("progress");
            for (TaskRow row : taskRows(json)) {
                int value = progress.has(row.path())
                    ? progress.get(row.path()).getAsInt()
                    : 0;
                boolean complete = value >= row.task().target();
                String marker = complete ? "✓ " : "• ";
                String progressText = complete
                    ? "Done"
                    : value + "/" + row.task().target();
                int progressX = x + width - minecraft.font.width(progressText) - 6;
                int labelWidth = progressX - (x + 10) - 4;
                String label = minecraft.font.plainSubstrByWidth(
                    marker + QuestPresentation.taskTitle(row.task()),
                    labelWidth
                );
                graphics.text(
                    minecraft.font,
                    Component.literal(label),
                    x + 10,
                    y,
                    complete ? 0xFF70C779 : 0xFFD0D4DA,
                    false
                );
                graphics.text(
                    minecraft.font,
                    Component.literal(progressText),
                    progressX,
                    y,
                    complete ? 0xFF70C779 : 0xFFFFFFFF,
                    false
                );
                y += 11;
            }
            y += 3;
        }
    }

    private static List<TaskRow> taskRows(JsonObject json) {
        QuestDefinition quest = GSON.fromJson(json, QuestDefinition.class);
        List<TaskRow> rows = new ArrayList<>();
        collectTaskRows(quest.tasks(), "", rows);
        return rows;
    }

    private static void collectTaskRows(
        java.util.Map<String, QuestDefinition.Task> tasks,
        String prefix,
        List<TaskRow> rows
    ) {
        for (QuestDefinition.Task task : tasks.values()) {
            String path = prefix.isEmpty()
                ? task.id()
                : prefix + "/" + task.id();
            if (
                task.kind() == QuestDefinition.TaskKind.COMPOSITE &&
                !task.tasks().isEmpty()
            ) {
                collectTaskRows(task.tasks(), path, rows);
            } else {
                rows.add(new TaskRow(path, task));
            }
        }
    }

    private record TaskRow(String path, QuestDefinition.Task task) {}
}
