package earth.terrarium.heracles.client;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.teamresourceful.resourcefullib.common.color.Color;
import earth.terrarium.heracles.core.QuestDefinition;
import earth.terrarium.heracles.core.QuestNetwork;
import earth.terrarium.olympus.client.components.Widgets;
import earth.terrarium.olympus.client.components.buttons.Button;
import earth.terrarium.olympus.client.components.renderers.WidgetRenderers;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Player-facing quest graph. Olympus supplies controls; Heracles owns graph semantics. */
public final class QuestScreen extends Screen {
    private static final Gson GSON = new Gson();
    private static final int SIDEBAR_WIDTH = 130;
    private static final int DETAILS_WIDTH = 250;
    private static final int NODE_WIDTH = 112;
    private static final int NODE_HEIGHT = 24;

    private final List<ClientQuest> quests = new ArrayList<>();
    private final Map<String, NodeBounds> nodeBounds = new HashMap<>();
    private String group;
    private String selectedId;
    private int panX;
    private int panY;
    private double zoom = 1.0;
    private boolean panning;

    public QuestScreen(JsonObject snapshot) {
        this(snapshot, null);
    }

    public QuestScreen(JsonObject snapshot, QuestScreen previous) {
        super(Component.literal("Heracles Quests"));
        readSnapshot(snapshot);
        Set<String> groups = groups();
        this.group = previous != null && groups.contains(previous.group) ? previous.group : groups.stream().findFirst().orElse("Main");
        this.selectedId = previous == null ? quests.stream().findFirst().map(quest -> quest.definition.id()).orElse(null) : previous.selectedId;
        this.panX = previous == null ? 0 : previous.panX;
        this.panY = previous == null ? 0 : previous.panY;
        this.zoom = previous == null ? 1.0 : previous.zoom;
    }

    private void readSnapshot(JsonObject snapshot) {
        snapshot.entrySet().forEach(entry -> {
            JsonObject json = entry.getValue().getAsJsonObject();
            QuestDefinition definition = GSON.fromJson(json, QuestDefinition.class);
            Map<String, Integer> progress = new HashMap<>();
            json.getAsJsonObject("progress").entrySet().forEach(task -> progress.put(task.getKey(), task.getValue().getAsInt()));
            quests.add(new ClientQuest(definition, Map.copyOf(progress), json.get("unlocked").getAsBoolean(), json.get("complete").getAsBoolean(), json.get("claimed").getAsBoolean()));
        });
        quests.sort(Comparator.comparing(quest -> quest.definition.id()));
    }

    @Override
    protected void init() {
        nodeBounds.clear();
        int y = 34;
        for (String candidate : groups()) {
            int groupY = y;
            Button button = Widgets.button(widget -> {
                widget.withPosition(8, groupY).withSize(SIDEBAR_WIDTH - 16, 20);
                widget.withRenderer(WidgetRenderers.text(Component.literal(candidate)).withColor(Color.parse("#FFFFFF")));
                widget.withCallback(() -> { group = candidate; panX = 0; panY = 0; rebuildWidgets(); });
            });
            addRenderableWidget(button);
            y += 23;
        }

        int canvasLeft = SIDEBAR_WIDTH;
        int canvasRight = width - DETAILS_WIDTH;
        int centerX = (canvasLeft + canvasRight) / 2 + panX;
        int centerY = height / 2 + panY;
        for (ClientQuest quest : visibleQuests()) {
            QuestDefinition.GroupDisplay position = quest.definition.position(group);
            int x = centerX + (int) Math.round(position.x() * zoom) - NODE_WIDTH / 2;
            int nodeY = centerY + (int) Math.round(position.y() * zoom) - NODE_HEIGHT / 2;
            NodeBounds bounds = new NodeBounds(x, nodeY, NODE_WIDTH, NODE_HEIGHT);
            nodeBounds.put(quest.definition.id(), bounds);
            Button node = Widgets.button(widget -> {
                widget.withPosition(x, nodeY).withSize(NODE_WIDTH, NODE_HEIGHT);
                widget.withRenderer(WidgetRenderers.text(Component.literal(status(quest) + " " + quest.definition.title())).withColor(nodeColor(quest)));
                widget.withCallback(() -> { selectedId = quest.definition.id(); rebuildWidgets(); });
                widget.withTooltip(Component.literal(quest.definition.subtitle()));
            });
            addRenderableWidget(node);
        }

        ClientQuest selected = selected();
        Button claim = Widgets.button(widget -> {
            widget.withPosition(width - DETAILS_WIDTH + 16, height - 34).withSize(120, 20);
            widget.withRenderer(WidgetRenderers.text(Component.literal("Claim rewards")));
            widget.withCallback(this::claimSelected);
            widget.active = selected != null && selected.complete && !selected.claimed;
        });
        addRenderableWidget(claim);
        QuestDefinition.Task submittable = selected == null ? null : selected.definition.tasks().values().stream()
            .filter(task -> selected.progress.getOrDefault(task.id(), 0) < task.target())
            .filter(QuestScreen::isSubmittable)
            .findFirst().orElse(null);
        Button submit = Widgets.button(widget -> {
            widget.withPosition(width - DETAILS_WIDTH + 142, height - 34).withSize(92, 20);
            widget.withRenderer(WidgetRenderers.text(Component.literal("Submit task")));
            widget.withCallback(() -> submitTask(selected, submittable));
            widget.active = submittable != null;
        });
        addRenderableWidget(submit);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xEE15171C);
        graphics.fill(0, 0, SIDEBAR_WIDTH, height, 0xFF20242B);
        graphics.fill(width - DETAILS_WIDTH, 0, width, height, 0xFF20242B);
        graphics.verticalLine(SIDEBAR_WIDTH, 0, height, 0xFF49515E);
        graphics.verticalLine(width - DETAILS_WIDTH, 0, height, 0xFF49515E);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        drawDependencyPaths(graphics);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.text(font, title, 8, 10, 0xFFFFFFFF, true);
        graphics.text(font, Component.literal(group + "  •  " + Math.round(zoom * 100) + "%"), SIDEBAR_WIDTH + 10, 10, 0xFFB8C0CC, false);
        drawDetails(graphics);
    }

    private void drawDependencyPaths(GuiGraphicsExtractor graphics) {
        for (ClientQuest quest : visibleQuests()) {
            NodeBounds child = nodeBounds.get(quest.definition.id());
            if (child == null || !quest.definition.settings().showDependencyArrow()) continue;
            for (String dependency : quest.definition.dependencies()) {
                NodeBounds parent = nodeBounds.get(dependency);
                if (parent == null) continue;
                int startX = parent.x + parent.width;
                int startY = parent.y + parent.height / 2;
                int endX = child.x;
                int endY = child.y + child.height / 2;
                int middleX = (startX + endX) / 2;
                int color = quest.unlocked ? 0xFF70C779 : 0xFF626A76;
                graphics.horizontalLine(startX, middleX, startY, color);
                graphics.verticalLine(middleX, Math.min(startY, endY), Math.max(startY, endY), color);
                graphics.horizontalLine(middleX, endX, endY, color);
            }
        }
    }

    private void drawDetails(GuiGraphicsExtractor graphics) {
        int x = width - DETAILS_WIDTH + 16;
        int contentWidth = DETAILS_WIDTH - 32;
        ClientQuest quest = selected();
        if (quest == null) {
            graphics.text(font, Component.literal("Select a quest"), x, 20, 0xFFAAAAAA, false);
            return;
        }
        graphics.textWithWordWrap(font, Component.literal(quest.definition.title()), x, 18, contentWidth, 0xFFFFFFFF, true);
        int y = 38;
        if (!quest.definition.subtitle().isBlank()) {
            graphics.textWithWordWrap(font, Component.literal(quest.definition.subtitle()), x, y, contentWidth, 0xFFB8C0CC);
            y += font.wordWrapHeight(Component.literal(quest.definition.subtitle()), contentWidth) + 7;
        }
        for (String paragraph : quest.definition.description()) {
            Component text = Component.literal(stripMarkdown(paragraph));
            graphics.textWithWordWrap(font, text, x, y, contentWidth, 0xFFE1E4E8);
            y += font.wordWrapHeight(text, contentWidth) + 6;
        }
        y += 4;
        graphics.text(font, Component.literal("Tasks"), x, y, 0xFFFFD966, true);
        y += 14;
        for (QuestDefinition.Task task : quest.definition.tasks().values()) {
            int progress = quest.progress.getOrDefault(task.id(), 0);
            String unsupported = task.kind() == QuestDefinition.TaskKind.UNSUPPORTED ? " [unsupported]" : "";
            graphics.textWithWordWrap(font, Component.literal("• " + task.title() + " " + progress + "/" + task.target() + unsupported), x, y, contentWidth, progress >= task.target() ? 0xFF70C779 : 0xFFFFFFFF);
            y += 13;
        }
        y += 4;
        graphics.text(font, Component.literal("Rewards"), x, y, 0xFFFFD966, true);
        y += 14;
        for (QuestDefinition.Reward reward : quest.definition.rewards().values()) {
            String unsupported = reward.kind() == QuestDefinition.RewardKind.UNSUPPORTED ? " [unsupported]" : "";
            graphics.textWithWordWrap(font, Component.literal("• " + reward.title() + " × " + reward.amount() + unsupported), x, y, contentWidth, 0xFFFFFFFF);
            y += 13;
        }
        graphics.text(font, Component.literal("Status: " + status(quest).trim()), x, Math.min(y + 8, height - 50), quest.complete ? 0xFF70C779 : 0xFFFFFFFF, false);
    }

    private List<ClientQuest> visibleQuests() {
        return quests.stream().filter(quest -> quest.definition.display().groups().containsKey(group)).filter(this::isVisible).toList();
    }

    private boolean isVisible(ClientQuest quest) {
        return switch (quest.definition.settings().hiddenUntil()) {
            case LOCKED -> true;
            case IN_PROGRESS -> quest.unlocked;
            case COMPLETED -> quest.complete;
            case DEPENDENCIES_VISIBLE -> quest.definition.dependencies().isEmpty() || quest.unlocked || quest.definition.dependencies().stream().anyMatch(this::isComplete);
            case NEVER -> true;
        };
    }

    private boolean isComplete(String id) {
        return quests.stream().filter(quest -> quest.definition.id().equals(id)).findFirst().map(ClientQuest::complete).orElse(false);
    }

    private Set<String> groups() {
        Set<String> result = new LinkedHashSet<>();
        quests.forEach(quest -> result.addAll(quest.definition.display().groups().keySet()));
        return result;
    }

    private ClientQuest selected() {
        return quests.stream().filter(quest -> quest.definition.id().equals(selectedId)).findFirst().orElse(null);
    }

    private void claimSelected() {
        ClientQuest selected = selected();
        if (selected != null) ClientPacketDistributor.sendToServer(new QuestNetwork.ActionPayload("claim", selected.definition.id()));
    }

    private static void submitTask(ClientQuest quest, QuestDefinition.Task task) {
        if (quest != null && task != null) {
            ClientPacketDistributor.sendToServer(new QuestNetwork.ActionPayload("submit", quest.definition.id() + "|" + task.id()));
        }
    }

    private static boolean isSubmittable(QuestDefinition.Task task) {
        if (task.kind() == QuestDefinition.TaskKind.CHECK) return true;
        if (task.kind() != QuestDefinition.TaskKind.ITEM && task.kind() != QuestDefinition.TaskKind.XP) return false;
        String key = task.kind() == QuestDefinition.TaskKind.XP ? "collectionType" : "collection";
        String collection = task.source().has(key) ? task.source().get(key).getAsString().toLowerCase(java.util.Locale.ROOT) : "automatic";
        return collection.endsWith("manual");
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) return true;
        if (event.input() == 0 && event.x() > SIDEBAR_WIDTH && event.x() < width - DETAILS_WIDTH) {
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
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (panning) {
            panX += (int) dragX;
            panY += (int) dragY;
            rebuildWidgets();
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseX > SIDEBAR_WIDTH && mouseX < width - DETAILS_WIDTH) {
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

    private static Color nodeColor(ClientQuest quest) {
        if (!quest.unlocked) return Color.parse("#9AA1AC");
        if (quest.claimed) return Color.parse("#70C779");
        if (quest.complete) return Color.parse("#FFD966");
        return Color.parse("#FFFFFF");
    }

    private static String stripMarkdown(String text) {
        return text.replace("**", "").replace("__", "").replace("`", "");
    }

    private record NodeBounds(int x, int y, int width, int height) {}
    private record ClientQuest(QuestDefinition definition, Map<String, Integer> progress, boolean unlocked, boolean complete, boolean claimed) {}
}
