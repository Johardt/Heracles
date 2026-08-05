package me.johardt.heracles.client;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.teamresourceful.resourcefullib.common.color.Color;
import earth.terrarium.olympus.client.components.Widgets;
import earth.terrarium.olympus.client.components.buttons.Button;
import earth.terrarium.olympus.client.components.renderers.WidgetRenderers;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import me.johardt.heracles.Heracles;
import me.johardt.heracles.core.QuestDefinition;
import me.johardt.heracles.core.QuestNetwork;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** Player-facing quest graph. Olympus supplies controls; Heracles owns graph semantics. */
public final class QuestScreen extends Screen {

    private static final Gson GSON = new Gson();
    private static final int COLLAPSED_SIDEBAR_WIDTH = 18;
    private static final int NODE_WIDTH = 24;
    private static final int NODE_HEIGHT = 24;
    private static final int CARD_HEIGHT = 48;
    private static final Identifier DEPENDENCY_ARROW = Identifier.fromNamespaceAndPath(
        Heracles.MOD_ID,
        "textures/gui/arrow.png"
    );
    private static final Identifier QUEST_FRAME = Identifier.fromNamespaceAndPath(
        Heracles.MOD_ID,
        "textures/gui/quest_backgrounds/default.png"
    );
    private static final Identifier CHECK_ICON = Identifier.fromNamespaceAndPath(
        Heracles.MOD_ID,
        "textures/item/check.png"
    );
    private static final WidgetSprites CLOSE_BUTTON = new WidgetSprites(
        sprite("heading/close"),
        sprite("heading/close_selected")
    );
    private static final Identifier PROGRESS_ACTIVE = sprite("widgets/progress_bar_0");
    private static final Identifier PROGRESS_COMPLETE = sprite("widgets/progress_bar_1");
    private static final Identifier PROGRESS_FILL = sprite("widgets/progress_bar_2");
    private static final Identifier HEADING_IN_PROGRESS_LEFT = sprite("headings/in_progress_left");
    private static final Identifier HEADING_IN_PROGRESS_RIGHT = sprite("headings/in_progress_right");
    private static final Identifier HEADING_COMPLETED_LEFT = sprite("headings/claimed_left");
    private static final Identifier HEADING_COMPLETED_RIGHT = sprite("headings/claimed_right");

    private final List<ClientQuest> quests = new ArrayList<>();
    private final Map<String, NodeBounds> nodeBounds = new HashMap<>();
    private final List<RewardChoiceBounds> rewardChoiceBounds =
        new ArrayList<>();
    private final Map<String, Set<String>> rewardSelections = new HashMap<>();
    private String group;
    private String selectedId;
    private int panX;
    private int panY;
    private double zoom = 1.0;
    private boolean panning;
    private DetailTab detailTab = DetailTab.OVERVIEW;
    private int detailScroll;
    private int detailMaxScroll;
    private boolean detailsOpen = true;
    private boolean sidebarOpen = true;

    public QuestScreen(JsonObject snapshot) {
        this(snapshot, null);
    }

    public QuestScreen(JsonObject snapshot, QuestScreen previous) {
        super(Component.literal("Heracles Quests"));
        readSnapshot(snapshot);
        Set<String> groups = groups();
        this.group =
            previous != null && groups.contains(previous.group)
                ? previous.group
                : groups.stream().findFirst().orElse("Main");
        this.selectedId =
            previous == null
                ? quests
                      .stream()
                      .findFirst()
                      .map(quest -> quest.definition.id())
                      .orElse(null)
                : previous.selectedId;
        this.panX = previous == null ? 0 : previous.panX;
        this.panY = previous == null ? 0 : previous.panY;
        this.zoom = previous == null ? 1.0 : previous.zoom;
        this.detailTab =
            previous == null ? DetailTab.OVERVIEW : previous.detailTab;
        this.detailScroll = previous == null ? 0 : previous.detailScroll;
        this.detailsOpen = previous == null || previous.detailsOpen;
        this.sidebarOpen = previous == null || previous.sidebarOpen;
        if (previous != null) previous.rewardSelections.forEach((key, value) ->
            this.rewardSelections.put(key, new LinkedHashSet<>(value))
        );
    }

    private void readSnapshot(JsonObject snapshot) {
        snapshot.entrySet().forEach(entry -> {
            JsonObject json = entry.getValue().getAsJsonObject();
            QuestDefinition definition = GSON.fromJson(
                json,
                QuestDefinition.class
            );
            Map<String, Integer> progress = new HashMap<>();
            json.getAsJsonObject("progress")
                .entrySet()
                .forEach(task ->
                    progress.put(task.getKey(), task.getValue().getAsInt())
                );
            quests.add(
                new ClientQuest(
                    definition,
                    Map.copyOf(progress),
                    json.get("unlocked").getAsBoolean(),
                    json.get("complete").getAsBoolean(),
                    json.get("claimed").getAsBoolean(),
                    json.has("pinned") && json.get("pinned").getAsBoolean()
                )
            );
        });
        quests.sort(Comparator.comparing(quest -> quest.definition.id()));
    }

    @Override
    protected void init() {
        nodeBounds.clear();
        int sidebarWidth = sidebarWidth();
        Button sidebarToggle = Widgets.button(widget -> {
            widget
                .withPosition(sidebarOpen ? sidebarWidth - 13 : 3, 2)
                .withSize(11, 11);
            widget.withRenderer(
                WidgetRenderers.text(
                    Component.literal(sidebarOpen ? "‹" : "›")
                ).withColor(Color.parse("#FFFFFF"))
            );
            widget.withCallback(() -> {
                sidebarOpen = !sidebarOpen;
                rebuildWidgets();
            });
            widget.withTooltip(
                Component.literal(
                    sidebarOpen ? "Collapse quest groups" : "Show quest groups"
                )
            );
        });
        addRenderableWidget(sidebarToggle);
        if (sidebarOpen) {
            int y = 34;
            for (String candidate : groups()) {
                int groupY = y;
                Button button = Widgets.button(widget -> {
                    widget
                        .withPosition(8, groupY)
                        .withSize(sidebarWidth - 16, 20);
                    widget.withRenderer(
                        WidgetRenderers.text(
                            Component.literal(candidate)
                        ).withColor(Color.parse("#FFFFFF"))
                    );
                    widget.withCallback(() -> {
                        group = candidate;
                        panX = 0;
                        panY = 0;
                        rebuildWidgets();
                    });
                });
                addRenderableWidget(button);
                y += 23;
            }
        }

        // Keep the graph anchored to the docked layout even while the details panel is hidden.
        // The newly exposed area remains usable for panning without shifting every quest node.
        int centerX = treeCenterX() + panX;
        int centerY = treeCenterY() + panY;
        for (ClientQuest quest : visibleQuests()) {
            QuestDefinition.GroupDisplay position = quest.definition.position(
                group
            );
            int x =
                centerX +
                position.x() -
                NODE_WIDTH / 2;
            int nodeY =
                centerY +
                position.y() -
                NODE_HEIGHT / 2;
            NodeBounds bounds = new NodeBounds(
                x,
                nodeY,
                NODE_WIDTH,
                NODE_HEIGHT
            );
            nodeBounds.put(quest.definition.id(), bounds);
        }

        if (!detailsOpen) return;
        ClientQuest selected = selected();
        int detailsWidth = detailsWidth();
        int detailsLeft = width - detailsWidth;
        int pinLeft = width - 50;
        int tabRight = pinLeft - 4;
        int tabWidth = (tabRight - (detailsLeft + 8)) / DetailTab.values().length;
        for (int index = 0; index < DetailTab.values().length; index++) {
            DetailTab tab = DetailTab.values()[index];
            int tabX = detailsLeft + 8 + index * tabWidth;
            Button tabButton = Widgets.button(widget -> {
                widget.withPosition(tabX, 8).withSize(tabWidth - 3, 20);
                widget.withRenderer(
                    WidgetRenderers.text(
                        Component.literal(tab.label)
                    ).withColor(
                        tab == detailTab
                            ? Color.parse("#5A4300")
                            : Color.parse("#FFFFFF")
                    )
                );
                widget.withCallback(() -> {
                    detailTab = tab;
                    detailScroll = 0;
                    rebuildWidgets();
                });
            });
            addRenderableWidget(tabButton);
        }
        Button closeDetails = Widgets.button(widget -> {
            widget.withPosition(width - 27, 8).withSize(19, 20);
            widget.withRenderer(
                WidgetRenderers.center(11, 11, WidgetRenderers.sprite(CLOSE_BUTTON))
            );
            widget.withCallback(() -> {
                detailsOpen = false;
                rebuildWidgets();
            });
            widget.withTooltip(Component.literal("Close quest details"));
        });
        addRenderableWidget(closeDetails);
        Button pin = Widgets.button(widget -> {
            widget.withPosition(pinLeft, 8).withSize(19, 20);
            widget.withRenderer(
                WidgetRenderers.text(
                    Component.literal(
                        selected != null && selected.pinned ? "★" : "☆"
                    )
                ).withColor(Color.parse("#FFFFFF"))
            );
            widget.withCallback(() -> {
                if (selected != null) ClientPacketDistributor.sendToServer(
                    new QuestNetwork.ActionPayload(
                        "pin",
                        selected.definition.id()
                    )
                );
            });
            widget.active = selected != null && selected.unlocked;
            widget.withTooltip(
                Component.literal(
                    selected != null && selected.pinned
                        ? "Unpin quest"
                        : "Pin quest"
                )
            );
        });
        addRenderableWidget(pin);
        TaskRef submittable =
            selected == null || !selected.unlocked
                ? null
                : findSubmittable(
                      selected.definition.tasks(),
                      selected.progress,
                      ""
                  );
        int actionWidth =
            submittable == null ? detailsWidth - 18 : (detailsWidth - 27) / 2;
        Button claim = Widgets.button(widget -> {
            widget
                .withPosition(detailsLeft + 9, height - 36)
                .withSize(actionWidth, 20);
            widget.withRenderer(
                WidgetRenderers.text(Component.literal("Claim rewards"))
            );
            widget.withCallback(this::claimSelected);
            widget.active =
                selected != null &&
                selected.complete &&
                !selected.claimed &&
                canClaimRewards(selected);
            if (
                selected != null &&
                selected.complete &&
                !selected.claimed &&
                !canClaimRewards(selected)
            ) {
                widget.withTooltip(
                    Component.literal(claimBlockedReason(selected))
                );
            }
        });
        addRenderableWidget(claim);
        if (submittable != null) {
            Button submit = Widgets.button(widget -> {
                widget
                    .withPosition(detailsLeft + 18 + actionWidth, height - 36)
                    .withSize(actionWidth, 20);
                widget.withRenderer(
                    WidgetRenderers.text(Component.literal("Submit task"))
                );
                widget.withCallback(() -> submitTask(selected, submittable));
            });
            addRenderableWidget(submit);
        }
    }

    @Override
    public void extractBackground(
        GuiGraphicsExtractor graphics,
        int mouseX,
        int mouseY,
        float partialTick
    ) {
        graphics.fill(0, 0, width, height, 0xD915171C);
    }

    @Override
    public void extractRenderState(
        GuiGraphicsExtractor graphics,
        int mouseX,
        int mouseY,
        float partialTick
    ) {
        graphics.enableScissor(0, 30, width, height);
        graphics.pose().pushMatrix();
        graphics.pose().translate(treeCenterX(), treeCenterY());
        graphics.pose().scale((float) zoom);
        graphics.pose().translate(-treeCenterX(), -treeCenterY());
        drawDependencyPaths(graphics);
        drawQuestNodes(graphics);
        graphics.pose().popMatrix();
        graphics.disableScissor();
        drawPanelScrims(graphics);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        if (sidebarOpen) graphics.text(
            font,
            Component.literal("Heracles"),
            8,
            4,
            0xFFFFFFFF,
            true
        );
        graphics.text(
            font,
            Component.literal(group + "  •  " + Math.round(zoom * 100) + "%"),
            sidebarWidth() + 10,
            10,
            0xFFB8C0CC,
            false
        );
        if (detailsOpen) drawDetails(graphics);
    }

    private void drawPanelScrims(GuiGraphicsExtractor graphics) {
        int sidebarWidth = sidebarWidth();
        graphics.fill(0, 0, sidebarWidth, height, 0xF020242B);
        graphics.verticalLine(sidebarWidth, 0, height, 0xFF49515E);
        if (detailsOpen) {
            int detailsLeft = width - detailsWidth();
            graphics.fill(detailsLeft, 0, width, height, 0xF020242B);
            graphics.verticalLine(detailsLeft, 0, height, 0xFF49515E);
        }
    }

    private void drawDependencyPaths(GuiGraphicsExtractor graphics) {
        for (ClientQuest quest : visibleQuests()) {
            NodeBounds child = nodeBounds.get(quest.definition.id());
            if (
                child == null ||
                !quest.definition.settings().showDependencyArrow()
            ) continue;
            for (String dependency : quest.definition.dependencies()) {
                NodeBounds parent = nodeBounds.get(dependency);
                if (parent == null) continue;
                PathPoint parentCenter = new PathPoint(
                    parent.x + parent.width / 2.0,
                    parent.y + parent.height / 2.0
                );
                PathPoint childCenter = new PathPoint(
                    child.x + child.width / 2.0,
                    child.y + child.height / 2.0
                );
                // Nodes render after connectors, so center-to-center paths disappear cleanly beneath the frames.
                PathPoint start = parentCenter;
                PathPoint tip = childCenter;
                double dx = tip.x - start.x;
                double dy = tip.y - start.y;
                double length = Math.hypot(dx, dy);
                if (length < 4.0) continue;
                drawTexturedPath(graphics, start, tip, quest.unlocked);
            }
        }
    }

    private void drawQuestNodes(GuiGraphicsExtractor graphics) {
        for (ClientQuest quest : visibleQuests()) {
            NodeBounds bounds = nodeBounds.get(quest.definition.id());
            if (bounds == null) continue;
            int frame = quest.claimed ? 3 : quest.complete ? 2 : quest.unlocked ? 1 : 0;
            graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                QUEST_FRAME,
                bounds.x,
                bounds.y,
                frame * NODE_WIDTH,
                0.0f,
                NODE_WIDTH,
                NODE_HEIGHT,
                NODE_WIDTH * 5,
                NODE_HEIGHT,
                0xFFFFFFFF
            );
            if (quest.definition.id().equals(selectedId)) {
                graphics.outline(
                    bounds.x - 2,
                    bounds.y - 2,
                    bounds.width + 4,
                    bounds.height + 4,
                    0xFFFFD966
                );
            }
            graphics.item(
                QuestPresentation.questIcon(quest.definition),
                bounds.x + 4,
                bounds.y + 4
            );
        }
    }

    private static void drawTexturedPath(
        GuiGraphicsExtractor graphics,
        PathPoint start,
        PathPoint end,
        boolean unlocked
    ) {
        double dx = end.x - start.x;
        double dy = end.y - start.y;
        double length = Math.hypot(dx, dy);
        if (length < 1.0) return;
        int pixelLength = (int) Math.ceil(length);

        graphics.pose().pushMatrix();
        graphics.pose().translate((float) start.x, (float) start.y);
        graphics.pose().rotate((float) Math.atan2(dy, dx));
        graphics.fill(0, -3, pixelLength, 3, 0xB0111318);
        graphics.fill(
            0,
            -2,
            pixelLength,
            2,
            unlocked ? 0x80636F66 : 0x80535A64
        );
        int tint = unlocked ? 0x8876A77B : 0x776F7782;
        for (int x = 0; x < pixelLength; x += 3) {
            int tileWidth = Math.min(3, pixelLength - x);
            graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                DEPENDENCY_ARROW,
                x,
                -2,
                0.0f,
                0.0f,
                tileWidth,
                5,
                3,
                5,
                tint
            );
        }
        graphics.pose().popMatrix();
    }

    private record PathPoint(double x, double y) {}

    private void drawDetails(GuiGraphicsExtractor graphics) {
        int detailsWidth = detailsWidth();
        int panelLeft = width - detailsWidth;
        int x = panelLeft + 12;
        int contentWidth = detailsWidth - 24;
        ClientQuest quest = selected();
        if (quest == null) {
            graphics.text(
                font,
                Component.literal("Select a quest"),
                x,
                42,
                0xFFAAAAAA,
                false
            );
            return;
        }
        graphics.textWithWordWrap(
            font,
            Component.literal(quest.definition.title()),
            x,
            38,
            contentWidth,
            0xFFFFFFFF,
            true
        );
        int y =
            50 +
            font.wordWrapHeight(
                Component.literal(quest.definition.title()),
                contentWidth
            );
        if (!quest.definition.subtitle().isBlank()) {
            graphics.textWithWordWrap(
                font,
                Component.literal(quest.definition.subtitle()),
                x,
                y,
                contentWidth,
                0xFFB8C0CC
            );
            y +=
                font.wordWrapHeight(
                    Component.literal(quest.definition.subtitle()),
                    contentWidth
                ) + 6;
        }
        graphics.horizontalLine(x, x + contentWidth, y, 0xFF49515E);
        int contentTop = y + 7;
        int contentBottom = height - 38;
        graphics.enableScissor(panelLeft + 1, contentTop, width, contentBottom);
        int contentHeight = switch (detailTab) {
            case OVERVIEW -> drawOverview(
                graphics,
                quest,
                x,
                contentTop - detailScroll,
                contentWidth
            );
            case TASKS -> drawTasks(
                graphics,
                quest,
                x,
                contentTop - detailScroll,
                contentWidth
            );
            case REWARDS -> drawRewards(
                graphics,
                quest,
                x,
                contentTop - detailScroll,
                contentWidth
            );
        };
        graphics.disableScissor();
        detailMaxScroll = Math.max(
            0,
            contentHeight - (contentBottom - contentTop)
        );
        detailScroll = Math.min(detailScroll, detailMaxScroll);
    }

    private int drawOverview(
        GuiGraphicsExtractor graphics,
        ClientQuest quest,
        int x,
        int y,
        int contentWidth
    ) {
        int startY = y;
        if (!quest.unlocked) y += drawLockedBanner(
            graphics,
            quest,
            x,
            y,
            contentWidth
        );
        int completed = (int) quest.definition
            .tasks()
            .values()
            .stream()
            .filter(
                task ->
                    quest.progress.getOrDefault(task.id(), 0) >= task.target()
            )
            .count();
        graphics.text(
            font,
            Component.literal("Quest progress"),
            x,
            y,
            0xFFFFD966,
            true
        );
        graphics.text(
            font,
            Component.literal(
                completed + "/" + quest.definition.tasks().size()
            ),
            x + contentWidth - 34,
            y,
            0xFFFFFFFF,
            false
        );
        y += 14;
        drawProgressBar(
            graphics,
            x,
            y,
            contentWidth,
            questProgress(quest)
        );
        y += 13;
        for (String paragraph : quest.definition.description()) {
            Component text = Component.literal(stripMarkdown(paragraph));
            graphics.textWithWordWrap(
                font,
                text,
                x,
                y,
                contentWidth,
                0xFFE1E4E8
            );
            y += font.wordWrapHeight(text, contentWidth) + 6;
        }
        y += 4;
        graphics.text(
            font,
            Component.literal("Status"),
            x,
            y,
            0xFFFFD966,
            true
        );
        y += 14;
        graphics.text(
            font,
            Component.literal(status(quest).trim()),
            x,
            y,
            nodeStateColor(quest),
            false
        );
        return y - startY + 18;
    }

    private int drawTasks(
        GuiGraphicsExtractor graphics,
        ClientQuest quest,
        int x,
        int y,
        int contentWidth
    ) {
        int startY = y;
        if (!quest.unlocked) y += drawLockedBanner(
            graphics,
            quest,
            x,
            y,
            contentWidth
        );
        List<QuestDefinition.Task> active = quest.definition
            .tasks()
            .values()
            .stream()
            .filter(
                task ->
                    quest.progress.getOrDefault(task.id(), 0) < task.target()
            )
            .toList();
        List<QuestDefinition.Task> complete = quest.definition
            .tasks()
            .values()
            .stream()
            .filter(
                task ->
                    quest.progress.getOrDefault(task.id(), 0) >= task.target()
            )
            .toList();
        if (!active.isEmpty()) {
            y = drawSectionHeading(
                graphics,
                "In progress",
                active.size(),
                x,
                y,
                contentWidth,
                0xFF4C9AFF
            );
            for (QuestDefinition.Task task : active) {
                y = drawTaskTree(
                    graphics,
                    quest,
                    task,
                    task.id(),
                    x,
                    y,
                    contentWidth,
                    false
                );
            }
        }
        if (!complete.isEmpty()) {
            y = drawSectionHeading(
                graphics,
                "Completed",
                complete.size(),
                x,
                y + (active.isEmpty() ? 0 : 4),
                contentWidth,
                0xFF55D86A
            );
            for (QuestDefinition.Task task : complete) {
                y = drawTaskTree(
                    graphics,
                    quest,
                    task,
                    task.id(),
                    x,
                    y,
                    contentWidth,
                    true
                );
            }
        }
        if (active.isEmpty() && complete.isEmpty()) graphics.text(
            font,
            Component.literal("No tasks"),
            x,
            y,
            0xFF9AA1AC,
            false
        );
        return y - startY + 6;
    }

    private int drawLockedBanner(
        GuiGraphicsExtractor graphics,
        ClientQuest quest,
        int x,
        int y,
        int width
    ) {
        List<String> blockers = quest.definition
            .dependencies()
            .stream()
            .filter(id -> {
                ClientQuest dependency = questById(id);
                return dependency == null || !dependency.complete;
            })
            .map(id -> {
                ClientQuest dependency = questById(id);
                return dependency == null
                    ? "Unknown chapter › " + id
                    : chapterName(dependency.definition) +
                          " › " +
                          dependency.definition.title();
            })
            .toList();
        if (blockers.isEmpty()) blockers = List.of("Quest dependencies");
        List<Component> lines = blockers
            .stream()
            .<Component>map(name -> Component.literal("Complete " + name))
            .toList();
        int textWidth = width - 14;
        int height =
            17 +
            lines
                .stream()
                .mapToInt(line -> font.wordWrapHeight(line, textWidth) + 3)
                .sum();
        graphics.fill(x, y, x + width, y + height, 0xFF302D27);
        graphics.outline(x, y, width, height, 0xFFFFD966);
        graphics.text(
            font,
            Component.literal("Locked"),
            x + 7,
            y + 5,
            0xFFFFD966,
            true
        );
        int lineY = y + 16;
        for (Component line : lines) {
            graphics.textWithWordWrap(
                font,
                line,
                x + 7,
                lineY,
                textWidth,
                0xFFB8C0CC
            );
            lineY += font.wordWrapHeight(line, textWidth) + 3;
        }
        return height + 6;
    }

    private ClientQuest questById(String id) {
        return quests
            .stream()
            .filter(quest -> quest.definition.id().equals(id))
            .findFirst()
            .orElse(null);
    }

    private static String chapterName(QuestDefinition definition) {
        return definition
            .display()
            .groups()
            .keySet()
            .stream()
            .sorted()
            .findFirst()
            .orElse("Main");
    }

    private int drawRewards(
        GuiGraphicsExtractor graphics,
        ClientQuest quest,
        int x,
        int y,
        int contentWidth
    ) {
        rewardChoiceBounds.clear();
        int startY = y;
        if (quest.definition.rewards().isEmpty()) {
            graphics.text(
                font,
                Component.literal("No rewards"),
                x,
                y,
                0xFF9AA1AC,
                false
            );
            return 18;
        }
        y = drawSectionHeading(
            graphics,
            quest.claimed ? "Claimed" : "Quest rewards",
            quest.definition.rewards().size(),
            x,
            y,
            contentWidth,
            quest.claimed ? 0xFF55D86A : 0xFFFFD966
        );
        for (QuestDefinition.Reward reward : quest.definition
            .rewards()
            .values()) {
            int border =
                reward.kind() == QuestDefinition.RewardKind.UNSUPPORTED
                    ? 0xFFE57373
                    : quest.claimed
                      ? 0xFF55D86A
                      : 0xFF626A76;
            graphics.fill(x, y, x + contentWidth, y + 40, 0xFF30353D);
            graphics.outline(x, y, contentWidth, 40, border);
            graphics.item(QuestPresentation.rewardIcon(reward), x + 7, y + 11);
            graphics.text(
                font,
                Component.literal(QuestPresentation.rewardTitle(reward)),
                x + 30,
                y + 8,
                0xFFFFFFFF,
                false
            );
            String detail = switch (reward.kind()) {
                case SELECTABLE -> "Choose up to " + reward.amount();
                case UNSUPPORTED -> "Not supported by this port: " +
                    reward.type();
                default -> "Amount: " + reward.amount();
            };
            graphics.text(
                font,
                Component.literal(detail),
                x + 30,
                y + 22,
                reward.kind() == QuestDefinition.RewardKind.UNSUPPORTED
                    ? 0xFFFFA0A0
                    : 0xFFB8C0CC,
                false
            );
            y += 45;
            if (reward.kind() == QuestDefinition.RewardKind.SELECTABLE) {
                String selectionKey = quest.definition.id() + "|" + reward.id();
                Set<String> selected = rewardSelections.computeIfAbsent(
                    selectionKey,
                    ignored -> new LinkedHashSet<>()
                );
                for (QuestDefinition.Reward choice : reward
                    .rewards()
                    .values()) {
                    boolean chosen = selected.contains(choice.id());
                    int choiceHeight = 34;
                    graphics.fill(
                        x + 10,
                        y,
                        x + contentWidth,
                        y + choiceHeight,
                        chosen ? 0xFF344637 : 0xFF292E35
                    );
                    graphics.outline(
                        x + 10,
                        y,
                        contentWidth - 10,
                        choiceHeight,
                        chosen ? 0xFF55D86A : 0xFF626A76
                    );
                    graphics.item(
                        QuestPresentation.rewardIcon(choice),
                        x + 16,
                        y + 9
                    );
                    graphics.text(
                        font,
                        Component.literal(
                            QuestPresentation.rewardTitle(choice)
                        ),
                        x + 39,
                        y + 7,
                        0xFFFFFFFF,
                        false
                    );
                    graphics.text(
                        font,
                        Component.literal(
                            chosen ? "Selected" : "Click to select"
                        ),
                        x + 39,
                        y + 20,
                        chosen ? 0xFF7DE68D : 0xFFADB4BF,
                        false
                    );
                    rewardChoiceBounds.add(
                        new RewardChoiceBounds(
                            selectionKey,
                            choice.id(),
                            new NodeBounds(
                                x + 10,
                                y,
                                contentWidth - 10,
                                choiceHeight
                            )
                        )
                    );
                    y += choiceHeight + 4;
                }
                y += 3;
            }
        }
        return y - startY;
    }

    private int drawSectionHeading(
        GuiGraphicsExtractor graphics,
        String title,
        int count,
        int x,
        int y,
        int width,
        int color
    ) {
        boolean completed = title.equals("Completed");
        Identifier left = completed ? HEADING_COMPLETED_LEFT : HEADING_IN_PROGRESS_LEFT;
        Identifier right = completed ? HEADING_COMPLETED_RIGHT : HEADING_IN_PROGRESS_RIGHT;
        int titleWidth = font.width(title) + 14;
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, left, x, y + 1, titleWidth, 13);
        graphics.blitSprite(
            RenderPipelines.GUI_TEXTURED,
            right,
            x + titleWidth,
            y + 1,
            width - titleWidth,
            13
        );
        graphics.text(
            font,
            Component.literal(title),
            x + 7,
            y + 4,
            0xFFFFFFFF,
            true
        );
        String amount = Integer.toString(count);
        graphics.text(
            font,
            Component.literal(amount),
            x + width - font.width(amount) - 5,
            y + 4,
            0xFFB8C0CC,
            false
        );
        return y + 19;
    }

    private int drawTaskTree(
        GuiGraphicsExtractor graphics,
        ClientQuest quest,
        QuestDefinition.Task task,
        String progressKey,
        int x,
        int y,
        int width,
        boolean complete
    ) {
        y = drawTaskCard(
            graphics,
            quest,
            task,
            progressKey,
            x,
            y,
            width,
            complete
        );
        if (
            task.kind() != QuestDefinition.TaskKind.COMPOSITE ||
            task.tasks().isEmpty()
        ) return y;

        int branchTop = y;
        int nestedX = x + 10;
        int nestedWidth = width - 10;
        String requirement =
            "Options · complete " +
            task.target() +
            " of " +
            task.tasks().size();
        graphics.fill(nestedX, y, nestedX + nestedWidth, y + 15, 0xFF252A31);
        graphics.text(
            font,
            Component.literal(requirement),
            nestedX + 7,
            y + 3,
            0xFFB8C0CC,
            false
        );
        y += 19;
        for (QuestDefinition.Task child : task.tasks().values()) {
            String childKey = progressKey + "/" + child.id();
            boolean childComplete =
                quest.progress.getOrDefault(childKey, 0) >= child.target();
            y = drawTaskTree(
                graphics,
                quest,
                child,
                childKey,
                nestedX,
                y,
                nestedWidth,
                childComplete
            );
        }
        graphics.fill(
            x + 3,
            branchTop,
            x + 5,
            y - 5,
            complete ? 0xFF55D86A : 0xFF4C9AFF
        );
        return y + 2;
    }

    private int drawTaskCard(
        GuiGraphicsExtractor graphics,
        ClientQuest quest,
        QuestDefinition.Task task,
        String progressKey,
        int x,
        int y,
        int width,
        boolean complete
    ) {
        int progress = quest.progress.getOrDefault(progressKey, 0);
        int state = complete ? 0xFF55D86A : 0xFF626A76;
        graphics.fill(
            x,
            y,
            x + width,
            y + CARD_HEIGHT,
            complete ? 0xFF2D3932 : 0xFF30353D
        );
        graphics.outline(x, y, width, CARD_HEIGHT, state);
        if (
            task.kind() == QuestDefinition.TaskKind.CHECK &&
            !hasCustomTaskIcon(task)
        ) {
            graphics.blit(CHECK_ICON, x + 7, y + 11, x + 23, y + 27, 0, 0, 1, 1);
        } else {
            graphics.item(QuestPresentation.taskIcon(task), x + 7, y + 11);
        }
        graphics.text(
            font,
            Component.literal(QuestPresentation.taskTitle(task)),
            x + 30,
            y + 6,
            complete ? 0xFFD8F5DD : 0xFFFFFFFF,
            false
        );
        graphics.text(
            font,
            Component.literal(QuestPresentation.taskDescription(task)),
            x + 30,
            y + 18,
            0xFFADB4BF,
            false
        );
        String progressText = progress + "/" + task.target();
        graphics.text(
            font,
            Component.literal(progressText),
            x + width - font.width(progressText) - 5,
            y + 6,
            0xFFFFFFFF,
            false
        );
        drawProgressBar(
            graphics,
            x + 30,
            y + CARD_HEIGHT - 9,
            width - 36,
            task.target() == 0 ? 0 : progress / (double) task.target()
        );
        return y + CARD_HEIGHT + 5;
    }

    private static void drawProgressBar(
        GuiGraphicsExtractor graphics,
        int x,
        int y,
        int width,
        double progress
    ) {
        double clamped = Math.max(0, Math.min(1, progress));
        graphics.blitSprite(
            RenderPipelines.GUI_TEXTURED,
            clamped >= 1 ? PROGRESS_COMPLETE : PROGRESS_ACTIVE,
            x,
            y,
            width,
            5
        );
        int fill = (int) Math.round(width * clamped);
        if (fill > 0 && clamped < 1) graphics.blitSprite(
            RenderPipelines.GUI_TEXTURED,
            PROGRESS_FILL,
            x,
            y,
            fill,
            5
        );
    }

    private static Identifier sprite(String path) {
        return Identifier.fromNamespaceAndPath(Heracles.MOD_ID, path);
    }

    private static boolean hasCustomTaskIcon(QuestDefinition.Task task) {
        return (
            task.source().has("icon") &&
            task.source().get("icon").isJsonObject() &&
            task.source().getAsJsonObject("icon").has("item")
        );
    }

    private List<ClientQuest> visibleQuests() {
        return quests
            .stream()
            .filter(quest ->
                quest.definition.display().groups().containsKey(group)
            )
            .filter(this::isVisible)
            .toList();
    }

    private boolean isVisible(ClientQuest quest) {
        return switch (quest.definition.settings().hiddenUntil()) {
            case LOCKED -> true;
            case IN_PROGRESS -> quest.unlocked;
            case COMPLETED -> quest.complete;
            case DEPENDENCIES_VISIBLE -> quest.definition
                .dependencies()
                .isEmpty() ||
                quest.unlocked ||
                quest.definition
                    .dependencies()
                    .stream()
                    .anyMatch(this::isComplete);
            case NEVER -> true;
        };
    }

    private boolean isComplete(String id) {
        return quests
            .stream()
            .filter(quest -> quest.definition.id().equals(id))
            .findFirst()
            .map(ClientQuest::complete)
            .orElse(false);
    }

    private Set<String> groups() {
        Set<String> result = new LinkedHashSet<>();
        quests.forEach(quest ->
            result.addAll(quest.definition.display().groups().keySet())
        );
        return result;
    }

    private ClientQuest selected() {
        return quests
            .stream()
            .filter(quest -> quest.definition.id().equals(selectedId))
            .findFirst()
            .orElse(null);
    }

    private int canvasRight() {
        return detailsOpen ? width - detailsWidth() : width;
    }

    private int treeCenterX() {
        return (sidebarWidth() + width - detailsWidth()) / 2;
    }

    private int treeCenterY() {
        return height / 2;
    }

    private double toTreeX(double screenX) {
        return (screenX - treeCenterX()) / zoom + treeCenterX();
    }

    private double toTreeY(double screenY) {
        return (screenY - treeCenterY()) / zoom + treeCenterY();
    }

    private int sidebarWidth() {
        if (!sidebarOpen) return COLLAPSED_SIDEBAR_WIDTH;
        return Math.max(96, Math.min(110, Math.round(width * 0.17f)));
    }

    private int detailsWidth() {
        return Math.max(220, Math.min(240, Math.round(width * 0.38f)));
    }

    private void claimSelected() {
        ClientQuest selected = selected();
        if (selected == null) return;
        JsonObject payload = new JsonObject();
        payload.addProperty("quest", selected.definition.id());
        JsonObject selections = new JsonObject();
        for (QuestDefinition.Reward reward : selected.definition
            .rewards()
            .values()) {
            if (
                reward.kind() != QuestDefinition.RewardKind.SELECTABLE
            ) continue;
            selections.add(
                reward.id(),
                GSON.toJsonTree(
                    rewardSelections.getOrDefault(
                        selected.definition.id() + "|" + reward.id(),
                        Set.of()
                    )
                )
            );
        }
        payload.add("selections", selections);
        ClientPacketDistributor.sendToServer(
            new QuestNetwork.ActionPayload("claim", GSON.toJson(payload))
        );
    }

    private static void submitTask(ClientQuest quest, TaskRef task) {
        if (quest != null && task != null) {
            ClientPacketDistributor.sendToServer(
                new QuestNetwork.ActionPayload(
                    "submit",
                    quest.definition.id() + "|" + task.path()
                )
            );
        }
    }

    private boolean canClaimRewards(ClientQuest quest) {
        for (QuestDefinition.Reward reward : quest.definition
            .rewards()
            .values()) {
            if (
                reward.kind() == QuestDefinition.RewardKind.UNSUPPORTED
            ) return false;
            if (reward.kind() == QuestDefinition.RewardKind.SELECTABLE) {
                Set<String> selected = rewardSelections.getOrDefault(
                    quest.definition.id() + "|" + reward.id(),
                    Set.of()
                );
                if (
                    selected.isEmpty() || selected.size() > reward.amount()
                ) return false;
                if (
                    selected
                        .stream()
                        .map(reward.rewards()::get)
                        .anyMatch(
                            choice ->
                                choice == null ||
                                choice.kind() ==
                                    QuestDefinition.RewardKind.UNSUPPORTED ||
                                choice.kind() ==
                                    QuestDefinition.RewardKind.SELECTABLE
                        )
                ) return false;
            }
        }
        return true;
    }

    private String claimBlockedReason(ClientQuest quest) {
        if (
            quest.definition
                .rewards()
                .values()
                .stream()
                .anyMatch(
                    reward ->
                        reward.kind() == QuestDefinition.RewardKind.UNSUPPORTED
                )
        ) {
            return "This quest contains a reward type that is not supported by this port";
        }
        return "Select the required quest reward before claiming";
    }

    private static TaskRef findSubmittable(
        Map<String, QuestDefinition.Task> tasks,
        Map<String, Integer> progress,
        String prefix
    ) {
        for (QuestDefinition.Task task : tasks.values()) {
            String path = prefix.isEmpty()
                ? task.id()
                : prefix + "/" + task.id();
            if (progress.getOrDefault(path, 0) >= task.target()) continue;
            if (task.kind() == QuestDefinition.TaskKind.COMPOSITE) {
                TaskRef nested = findSubmittable(task.tasks(), progress, path);
                if (nested != null) return nested;
            } else if (isSubmittable(task)) {
                return new TaskRef(path, task);
            }
        }
        return null;
    }

    private static boolean isSubmittable(QuestDefinition.Task task) {
        if (task.kind() == QuestDefinition.TaskKind.CHECK) return true;
        if (
            task.kind() != QuestDefinition.TaskKind.ITEM &&
            task.kind() != QuestDefinition.TaskKind.XP
        ) return false;
        String key =
            task.kind() == QuestDefinition.TaskKind.XP
                ? "collectionType"
                : "collection";
        String collection = task.source().has(key)
            ? task
                  .source()
                  .get(key)
                  .getAsString()
                  .toLowerCase(java.util.Locale.ROOT)
            : "automatic";
        return collection.endsWith("manual");
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) return true;
        if (
            event.input() == 0 &&
            detailsOpen &&
            detailTab == DetailTab.REWARDS &&
            event.x() >= width - detailsWidth()
        ) {
            for (RewardChoiceBounds choice : rewardChoiceBounds) {
                if (!choice.bounds().contains(event.x(), event.y())) continue;
                Set<String> selected = rewardSelections.computeIfAbsent(
                    choice.selectionKey(),
                    ignored -> new LinkedHashSet<>()
                );
                ClientQuest quest = selected();
                QuestDefinition.Reward parent =
                    quest == null
                        ? null
                        : quest.definition
                              .rewards()
                              .values()
                              .stream()
                              .filter(reward ->
                                  (
                                      quest.definition.id() +
                                      "|" +
                                      reward.id()
                                  ).equals(choice.selectionKey())
                              )
                              .findFirst()
                              .orElse(null);
                if (selected.remove(choice.choiceId())) {
                    rebuildWidgets();
                    return true;
                }
                if (parent != null) {
                    if (parent.amount() == 1) {
                        selected.clear();
                        selected.add(choice.choiceId());
                        rebuildWidgets();
                    } else if (selected.size() < parent.amount()) {
                        selected.add(choice.choiceId());
                        rebuildWidgets();
                    }
                }
                return true;
            }
        }
        if (
            event.input() == 0 &&
            event.x() > sidebarWidth() &&
            event.x() < canvasRight()
        ) {
            double treeX = toTreeX(event.x());
            double treeY = toTreeY(event.y());
            for (ClientQuest quest : visibleQuests()) {
                NodeBounds bounds = nodeBounds.get(quest.definition.id());
                if (bounds != null && bounds.contains(treeX, treeY)) {
                    selectedId = quest.definition.id();
                    detailScroll = 0;
                    detailsOpen = true;
                    rebuildWidgets();
                    return true;
                }
            }
            if (detailsOpen) {
                detailsOpen = false;
                rebuildWidgets();
            }
            panning = true;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        panning = false;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(
        MouseButtonEvent event,
        double dragX,
        double dragY
    ) {
        if (panning) {
            panX += (int) Math.round(dragX / zoom);
            panY += (int) Math.round(dragY / zoom);
            rebuildWidgets();
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(
        double mouseX,
        double mouseY,
        double scrollX,
        double scrollY
    ) {
        if (detailsOpen && mouseX >= width - detailsWidth()) {
            detailScroll = Math.max(
                0,
                Math.min(
                    detailMaxScroll,
                    detailScroll - (int) Math.round(scrollY * 18)
                )
            );
            return true;
        }
        if (mouseX > sidebarWidth() && mouseX < canvasRight()) {
            zoom = Math.max(0.5, Math.min(2.0, zoom + scrollY * 0.1));
            rebuildWidgets();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private static String status(ClientQuest quest) {
        if (!quest.unlocked) return "[Locked]";
        if (quest.claimed) return "[Claimed]";
        if (quest.complete) return "[Complete]";
        return "[Active]";
    }

    private static int nodeStateColor(ClientQuest quest) {
        if (!quest.unlocked) return 0xFF737B87;
        if (quest.claimed) return 0xFF55D86A;
        if (quest.complete) return 0xFFFFD966;
        return 0xFF4C9AFF;
    }

    private static double questProgress(ClientQuest quest) {
        if (quest.definition.tasks().isEmpty()) return quest.complete ? 1 : 0;
        double progress = 0;
        for (QuestDefinition.Task task : quest.definition.tasks().values()) {
            progress += Math.min(
                1,
                quest.progress.getOrDefault(task.id(), 0) /
                    (double) Math.max(1, task.target())
            );
        }
        return progress / quest.definition.tasks().size();
    }

    private static String stripMarkdown(String text) {
        return text.replace("**", "").replace("__", "").replace("`", "");
    }

    private enum DetailTab {
        OVERVIEW("Overview"),
        TASKS("Tasks"),
        REWARDS("Rewards");

        private final String label;

        DetailTab(String label) {
            this.label = label;
        }
    }

    private record NodeBounds(int x, int y, int width, int height) {
        private boolean contains(double mouseX, double mouseY) {
            return (
                mouseX >= x &&
                mouseX < x + width &&
                mouseY >= y &&
                mouseY < y + height
            );
        }
    }

    private record TaskRef(String path, QuestDefinition.Task task) {}

    private record RewardChoiceBounds(
        String selectionKey,
        String choiceId,
        NodeBounds bounds
    ) {}

    private record ClientQuest(
        QuestDefinition definition,
        Map<String, Integer> progress,
        boolean unlocked,
        boolean complete,
        boolean claimed,
        boolean pinned
    ) {}
}
