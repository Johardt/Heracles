package me.johardt.heracles.client;

import com.google.gson.JsonObject;
import me.johardt.heracles.Heracles;
import me.johardt.heracles.client.theme.ClientTheme;
import me.johardt.heracles.client.theme.ClientThemeLoader;
import me.johardt.heracles.core.QuestDefinition;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

final class QuestHud {

    static final SystemToast.SystemToastId UNLOCK_TOAST =
        new SystemToast.SystemToastId();
    static final SystemToast.SystemToastId COMPLETE_TOAST =
        new SystemToast.SystemToastId();
    static final SystemToast.SystemToastId REWARD_TOAST =
        new SystemToast.SystemToastId();
    private static final Identifier CHECK_ICON = Identifier.fromNamespaceAndPath(
        Heracles.MOD_ID,
        "textures/item/check.png"
    );
    private static final Identifier TRACKER_HEADER = sprite("pinned/pinned_fake_popup_background");
    private static final Identifier TRACKER_BODY = sprite("pinned/pinned_fake_popup_border");

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

        ClientTheme.Tracker theme = ClientThemeLoader.active().tracker();

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
                          taskRows(entry.getKey(), entry.getValue().getAsJsonObject()).size() *
                          11
                  )
                  .sum();
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, TRACKER_HEADER, x, 6, width, 10);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, TRACKER_BODY, x, 16, width, height - 10);
        Component trackerTitle = Component.literal(
            "Pinned quests " + (collapsed ? "[J] +" : "[J] −")
        );
        graphics.text(
            minecraft.font,
            trackerTitle,
            x + (width - minecraft.font.width(trackerTitle)) / 2,
            8,
            theme.title(),
            true
        );
        if (collapsed) return;
        int y = 18;
        for (var entry : pinned) {
            JsonObject json = entry.getValue().getAsJsonObject();
            QuestDefinition quest = QuestDefinition.parse(entry.getKey(), json);
            graphics.text(
                minecraft.font,
                Component.literal(quest.title()),
                x + 6,
                y,
                theme.quest(),
                true
            );
            y += 12;
            JsonObject progress = json.getAsJsonObject("progress");
            for (TaskRow row : taskRows(entry.getKey(), json)) {
                int value = progress.has(row.path())
                    ? progress.get(row.path()).getAsInt()
                    : 0;
                boolean complete = value >= row.task().target();
                String marker = complete ? "" : "• ";
                String progressText = complete
                    ? "Done"
                    : value + "/" + row.task().target();
                int progressX = x + width - minecraft.font.width(progressText) - 6;
                int labelX = x + (complete ? 20 : 10);
                int labelWidth = progressX - labelX - 4;
                String label = minecraft.font.plainSubstrByWidth(
                    marker + QuestPresentation.taskTitle(row.task()),
                    labelWidth
                );
                if (complete) graphics.blit(
                    CHECK_ICON,
                    x + 10,
                    y,
                    x + 18,
                    y + 8,
                    0,
                    0,
                    1,
                    1
                );
                graphics.text(
                    minecraft.font,
                    Component.literal(label),
                    labelX,
                    y,
                    complete ? theme.completed() : theme.task(),
                    false
                );
                graphics.text(
                    minecraft.font,
                    Component.literal(progressText),
                    progressX,
                    y,
                    complete ? theme.completed() : theme.progress(),
                    false
                );
                y += 11;
            }
            y += 3;
        }
    }

    private static List<TaskRow> taskRows(String id, JsonObject json) {
        QuestDefinition quest = QuestDefinition.parse(id, json);
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

    private static Identifier sprite(String path) {
        return Identifier.fromNamespaceAndPath(Heracles.MOD_ID, path);
    }
}
