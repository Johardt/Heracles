package me.johardt.heracles.client;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.InputConstants;
import com.teamresourceful.resourcefullib.common.color.Color;
import earth.terrarium.olympus.client.components.Widgets;
import earth.terrarium.olympus.client.components.buttons.Button;
import earth.terrarium.olympus.client.components.base.renderer.WidgetRenderer;
import earth.terrarium.olympus.client.components.compound.LayoutWidget;
import earth.terrarium.olympus.client.components.renderers.WidgetRenderers;
import earth.terrarium.olympus.client.components.string.TextWidget;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import me.johardt.heracles.Heracles;
import me.johardt.heracles.core.QuestDefinition;
import me.johardt.heracles.core.QuestDiagnostics;
import me.johardt.heracles.core.QuestMutationCoordinator;
import me.johardt.heracles.core.RegistryValidation;
import me.johardt.heracles.core.EditorTypeRegistry;
import me.johardt.heracles.core.QuestNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.entity.EntityType;
import net.minecraft.util.TriState;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** Player-facing quest graph. Olympus supplies controls; Heracles owns graph semantics. */
public final class QuestScreen extends Screen {

    private static final Gson GSON = new Gson();
    private static final EditorTypeRegistry EDITOR_TYPES = EditorTypeRegistry.defaults();
    private static final me.johardt.heracles.core.QuestClipboard CLIPBOARD = new me.johardt.heracles.core.QuestClipboard();
    private final QuestImportController importController = new QuestImportController();
    private static final int COLLAPSED_SIDEBAR_WIDTH = 18;
    private static final int NODE_WIDTH = 24;
    private static final int NODE_HEIGHT = 24;
    private static final int CARD_HEIGHT = 48;
    private static final int TASK_CHOOSER_VISIBLE = 6;
    private static final int TASK_CHOOSER_ROW_HEIGHT = 26;
    private static final int HEADER_ROW_Y = 1;
    private static final int HEADER_ROW_HEIGHT = 20;
    private static final int HEADER_ROW_GAP = 3;
    private static final int HEADER_CANVAS_GAP = 9;
    private static final int HEADER_ACTION_WIDTH = 78;
    private static final int HEADER_ACTION_GAP = 7;
    private static final Identifier DEPENDENCY_ARROW = Identifier.fromNamespaceAndPath(
        Heracles.MOD_ID,
        "textures/gui/arrow.png"
    );
    private static final Identifier DEFAULT_QUEST_FRAME = Identifier.fromNamespaceAndPath(
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
    private static final List<Identifier> QUEST_BACKGROUNDS = List.of(
        "default", "circles", "diamonds", "gears", "hearts", "hexagons",
        "octagons", "pentagons", "rounded_squares"
    ).stream().map(name -> Identifier.fromNamespaceAndPath(
        Heracles.MOD_ID,
        "textures/gui/quest_backgrounds/" + name + ".png"
    )).toList();
    private static final List<TaskChoice> TASK_CHOICES = List.of(
        new TaskChoice("heracles:dummy", "Dummy", Items.PAPER, true),
        new TaskChoice("heracles:item", "Acquire Item", Items.CHEST, true),
        new TaskChoice("heracles:xp", "Experience", Items.EXPERIENCE_BOTTLE, true),
        new TaskChoice("heracles:kill_entity", "Kill Entity", Items.IRON_SWORD, true),
        new TaskChoice("heracles:advancement", "Advancement", Items.WRITABLE_BOOK, true),
        new TaskChoice("heracles:biome", "Biome", Items.GRASS_BLOCK, true),
        new TaskChoice("heracles:block_interaction", "Block Interaction", Items.STONE_BUTTON, true),
        new TaskChoice("heracles:changed_dimension", "Changed Dimension", Items.ENDER_PEARL, true),
        new TaskChoice("heracles:check", "Check", Items.EMERALD, true),
        new TaskChoice("heracles:composite", "Composite", Items.BUNDLE, true),
        new TaskChoice("heracles:entity_interaction", "Entity Interaction", Items.LEAD, true),
        new TaskChoice("heracles:item_interaction", "Item Interaction", Items.STICK, true),
        new TaskChoice("heracles:item_use", "Item Use", Items.CARROT_ON_A_STICK, true),
        new TaskChoice("heracles:location", "Location", Items.COMPASS, true),
        new TaskChoice("heracles:recipe", "Recipe", Items.KNOWLEDGE_BOOK, true),
        new TaskChoice("heracles:stat", "Stat", Items.FEATHER, true),
        new TaskChoice("heracles:structure", "Structure", Items.STRUCTURE_BLOCK, true)
    );
    private static final List<RewardChoice> REWARD_CHOICES = List.of(
        new RewardChoice("heracles:xp", "Experience", Items.EXPERIENCE_BOTTLE),
        new RewardChoice("heracles:item", "Item", Items.CHEST),
        new RewardChoice("heracles:loottable", "Loot Table", Items.CHEST),
        new RewardChoice("heracles:command", "Command", Items.COMMAND_BLOCK),
        new RewardChoice("heracles:selectable", "Selectable Reward", Items.BUNDLE)
    );

    private final List<ClientQuest> quests = new ArrayList<>();
    private final List<String> chapters = new ArrayList<>();
    private final Map<String, ChapterDisplay> chapterDisplays = new HashMap<>();
    private final Map<String, NodeBounds> nodeBounds = new HashMap<>();
    private final List<RewardChoiceBounds> rewardChoiceBounds =
        new ArrayList<>();
    private final Map<String, Set<String>> rewardSelections = new HashMap<>();
    private final QuestGraphEditor graph;
    private String group;
    private boolean editMode;
    private EditorTool editorTool = EditorTool.SELECT;
    private boolean createQuestDockOpen;
    private int createQuestX;
    private int createQuestY;
    private DetailTab createQuestTab = DetailTab.OVERVIEW;
    private String createQuestId = "";
    private String createQuestTitle = "";
    private String createQuestSubtitle = "";
    private String createQuestBody = "";
    private String createQuestIcon = "minecraft:map";
    private String createQuestBackground = "heracles:textures/gui/quest_backgrounds/default.png";
    private final List<DraftTask> createQuestTasks = new ArrayList<>();
    private boolean taskChooserOpen;
    private int taskChooserScroll;
    private int createTaskScroll;
    private int editingTaskIndex = -1;
    private DraftTask editingTask;
    private String taskEditorError = "";
    private int taskDeleteConfirmation = -1;
    private final List<DraftReward> createQuestRewards = new ArrayList<>();
    private boolean editingExistingQuest;
    private String originalQuestId;
    private JsonObject createQuestGroups = new JsonObject();
    private boolean deleteQuestConfirmation;
    private boolean rewardChooserOpen;
    private int rewardChooserScroll;
    private int createRewardScroll;
    private int editingRewardIndex = -1;
    private DraftReward editingReward;
    private String rewardEditorError = "";
    private boolean nestedRewardsOpen;
    private boolean nestedRewardChooserOpen;
    private int nestedRewardScroll;
    private int editingNestedRewardIndex = -1;
    private DraftReward editingNestedReward;
    private Picker picker = Picker.NONE;
    private PickerTarget pickerTarget = PickerTarget.QUEST_ICON;
    private EditBox pickerSearch;
    private Button createConfirmButton;
    private JsonObject draftBaseline;
    private String editorMessage = "";
    private boolean editorMessageSuccess;
    private boolean clipboardMutationPending;
    private boolean pasteIdPrompt;
    private EditBox pasteIdField;
    private final QuestMutationCoordinator mutations;
    private final QuestModalHost modalHost;
    private QuestModalHost.Modal lastModal = QuestModalHost.Modal.NONE;
    private boolean lastModalLayerVisible;
    private List<QuestDiagnostics.Diagnostic> diagnostics = List.of();
    private boolean diagnosticsFromImport;
    private int diagnosticsScroll;
    private int importScroll;
    private final Map<String, EditBox> importIdFields = new HashMap<>();
    private boolean discardConfirmation;
    private Runnable discardAction;
    private int pickerScroll;
    private final Map<PickerTarget, Integer> pickerScrollByTarget = new HashMap<>();
    private DetailTab detailTab = DetailTab.OVERVIEW;
    private int detailScroll;
    private int detailMaxScroll;
    private boolean detailsOpen = true;
    private boolean sidebarOpen = true;
    private boolean chapterEditorOpen;
    private String chapterEditorOriginal;
    private String chapterEditorName = "";
    private String chapterEditorIcon = "minecraft:map";
    private String chapterEditorBackground = "";
    private String chapterEditorError = "";
    private boolean chapterDeleteArmed;
    private String chapterEditorBaseline;

    public QuestScreen(JsonObject snapshot) {
        this(snapshot, null);
    }

    public QuestScreen(JsonObject snapshot, QuestScreen previous) {
        super(Component.literal("Heracles Quests"));
        this.graph = previous == null ? new QuestGraphEditor() : previous.graph.copy();
        this.mutations = previous == null ? new QuestMutationCoordinator() : previous.mutations.copy();
        this.modalHost = previous == null ? new QuestModalHost() : previous.modalHost.copy();
        this.lastModal = this.modalHost.active();
        this.lastModalLayerVisible = previous != null && previous.isModalLayerVisible();
        this.diagnostics = previous == null ? List.of() : previous.diagnostics;
        this.diagnosticsFromImport = previous != null && previous.diagnosticsFromImport;
        this.diagnosticsScroll = previous == null ? 0 : previous.diagnosticsScroll;
        this.importScroll = previous == null ? 0 : previous.importScroll;
        readSnapshot(snapshot);
        Set<String> groups = groups();
        this.group =
            previous != null && groups.contains(previous.group)
                ? previous.group
                : groups.stream().findFirst().orElse("Main");
        if (previous == null) {
            graph.select(quests.stream().findFirst().map(quest -> quest.definition.id()).orElse(null));
        }
        this.detailTab =
            previous == null ? DetailTab.OVERVIEW : previous.detailTab;
        this.detailScroll = previous == null ? 0 : previous.detailScroll;
        this.detailsOpen = previous == null || previous.detailsOpen;
        this.sidebarOpen = previous == null || previous.sidebarOpen;
        this.editMode = previous != null && previous.editMode;
        this.editorTool = previous == null
            ? EditorTool.SELECT
            : previous.editorTool;
        this.createQuestDockOpen = previous != null && previous.createQuestDockOpen;
        this.createQuestX = previous == null ? 0 : previous.createQuestX;
        this.createQuestY = previous == null ? 0 : previous.createQuestY;
        this.createQuestTab = previous == null ? DetailTab.OVERVIEW : previous.createQuestTab;
        this.createQuestId = previous == null ? "" : previous.createQuestId;
        this.createQuestTitle = previous == null ? "" : previous.createQuestTitle;
        this.createQuestSubtitle = previous == null ? "" : previous.createQuestSubtitle;
        this.createQuestBody = previous == null ? "" : previous.createQuestBody;
        this.createQuestIcon = previous == null ? "minecraft:map" : previous.createQuestIcon;
        this.createQuestBackground = previous == null
            ? "heracles:textures/gui/quest_backgrounds/default.png"
            : previous.createQuestBackground;
        this.editingExistingQuest = previous != null && previous.editingExistingQuest;
        this.originalQuestId = previous == null ? null : previous.originalQuestId;
        this.createQuestGroups = previous == null ? new JsonObject() : previous.createQuestGroups.deepCopy();
        if (previous != null) previous.createQuestTasks.forEach(task ->
            this.createQuestTasks.add(new DraftTask(task.id, task.type, task.source.deepCopy()))
        );
        this.createTaskScroll = previous == null ? 0 : previous.createTaskScroll;
        this.editingTaskIndex = previous == null ? -1 : previous.editingTaskIndex;
        this.editingTask = previous == null || previous.editingTask == null
            ? null
            : previous.editingTask.copy();
        this.taskEditorError = previous == null ? "" : previous.taskEditorError;
        this.taskDeleteConfirmation = previous == null ? -1 : previous.taskDeleteConfirmation;
        if (previous != null) previous.createQuestRewards.forEach(reward ->
            this.createQuestRewards.add(reward.copy())
        );
        this.createRewardScroll = previous == null ? 0 : previous.createRewardScroll;
        this.editingRewardIndex = previous == null ? -1 : previous.editingRewardIndex;
        this.editingReward = previous == null || previous.editingReward == null ? null : previous.editingReward.copy();
        this.rewardEditorError = previous == null ? "" : previous.rewardEditorError;
        this.nestedRewardsOpen = previous != null && previous.nestedRewardsOpen;
        this.editingNestedRewardIndex = previous == null ? -1 : previous.editingNestedRewardIndex;
        this.editingNestedReward = previous == null || previous.editingNestedReward == null ? null : previous.editingNestedReward.copy();
        this.draftBaseline = previous == null || previous.draftBaseline == null ? null : previous.draftBaseline.deepCopy();
        this.editorMessage = previous == null ? "" : previous.editorMessage;
        this.editorMessageSuccess = previous != null && previous.editorMessageSuccess;
        this.clipboardMutationPending = previous != null && previous.clipboardMutationPending;
        if (previous != null) this.pickerScrollByTarget.putAll(previous.pickerScrollByTarget);
        this.discardConfirmation = previous != null && previous.discardConfirmation;
        this.discardAction = previous == null ? null : previous.discardAction;
        this.chapterEditorBaseline = previous == null ? null : previous.chapterEditorBaseline;
        if (previous != null) previous.rewardSelections.forEach((key, value) ->
            this.rewardSelections.put(key, new LinkedHashSet<>(value))
        );
    }

    private void readSnapshot(JsonObject snapshot) {
        if (snapshot.has("__chapters") && snapshot.get("__chapters").isJsonObject()) {
            JsonObject metadata = snapshot.getAsJsonObject("__chapters");
            if (metadata.has("order") && metadata.get("order").isJsonArray()) {
                metadata.getAsJsonArray("order").forEach(value -> chapters.add(value.getAsString()));
            }
            if (metadata.has("settings") && metadata.get("settings").isJsonObject()) {
                metadata.getAsJsonObject("settings").entrySet().forEach(entry -> {
                    JsonObject value = entry.getValue().getAsJsonObject();
                    chapterDisplays.put(entry.getKey(), new ChapterDisplay(
                        jsonString(value, "icon", "minecraft:map"),
                        jsonString(value, "background", "")
                    ));
                });
            }
        }
        snapshot.entrySet().forEach(entry -> {
            if (entry.getKey().equals("__chapters")) return;
            JsonObject json = entry.getValue().getAsJsonObject();
            QuestDefinition definition = QuestDefinition.parse(entry.getKey(), json);
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
                    json.has("pinned") && json.get("pinned").getAsBoolean(),
                    json.deepCopy()
                )
            );
        });
        quests.sort(Comparator.comparing(quest -> quest.definition.id()));
    }

    @Override
    protected void init() {
        nodeBounds.clear();
        populateNodeBounds();
        // Modal layers own the active widget tree, matching the original editor's
        // TemporaryWidget behavior. Underlying controls are neither rendered above
        // the modal nor eligible for focus/click dispatch.
        if (modalHost.is(QuestModalHost.Modal.DIAGNOSTICS)) {
            addDiagnosticsModalWidgets();
            return;
        }
        if (modalHost.is(QuestModalHost.Modal.FILE_IMPORT)) {
            addImportModalWidgets();
            return;
        }
        if (picker != Picker.NONE) {
            addPickerSearchWidget();
            return;
        }
        if (deleteQuestConfirmation) {
            addDeleteQuestConfirmationWidgets();
            return;
        }
        if (discardConfirmation) {
            addDiscardConfirmationWidgets();
            return;
        }
        if (taskDeleteConfirmation >= 0) {
            addDeleteTaskConfirmationWidgets();
            return;
        }
        if (chapterEditorOpen) {
            addChapterEditorWidgets();
            return;
        }
        if (pasteIdPrompt) {
            addPasteIdPromptWidgets();
            return;
        }
        if (editingNestedReward != null) {
            addRewardEditorWidgets(editingNestedReward, true);
            return;
        }
        if (nestedRewardsOpen) {
            addNestedRewardWidgets();
            return;
        }
        if (editingReward != null) {
            addRewardEditorWidgets(editingReward, false);
            return;
        }
        if (editingTask != null) {
            addTaskEditorWidgets();
            return;
        }
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

        if (!canEdit()) {
            editMode = false;
            createQuestDockOpen = false;
            picker = Picker.NONE;
        } else {
            HeaderLayout header = headerLayout();
            if (!diagnostics.isEmpty()) {
                addRenderableWidget(Widgets.button(widget -> {
                    widget.withPosition(header.diagnosticsX(), header.actionY()).withSize(HEADER_ACTION_WIDTH, HEADER_ROW_HEIGHT);
                    widget.withRenderer(WidgetRenderers.text(Component.literal("Diagnostics")));
                    widget.withCallback(() -> {
                        modalHost.open(QuestModalHost.Modal.DIAGNOSTICS);
                        diagnosticsScroll = 0;
                        rebuildWidgets();
                    });
                    widget.withTooltip(Component.literal("View validation diagnostics"));
                }));
            }
            if (editMode) addRenderableWidget(Widgets.button(widget -> {
                    widget.withPosition(header.importX(), header.actionY()).withSize(HEADER_ACTION_WIDTH, HEADER_ROW_HEIGHT);
                    widget.withRenderer(WidgetRenderers.text(Component.literal("Import")));
                    widget.withCallback(this::openNativeFilePicker);
                    widget.withTooltip(Component.literal("Choose one or more quest JSON files"));
                }));
            addRenderableWidget(editorButton(
                header.editX(),
                "edit",
                editMode,
                editMode ? "Leave quest edit mode" : "Edit quests",
                () -> {
                    requestDiscard(() -> {
                        editMode = !editMode;
                        editorTool = EditorTool.SELECT;
                        closeDraft();
                        graph.clearLink();
                        picker = Picker.NONE;
                        graph.setPanning(false);
                        rebuildWidgets();
                    });
                }
            ));
        }
        if (editMode) {
            int toolX = sidebarWidth + 24;
            for (EditorTool tool : EditorTool.values()) {
                addRenderableWidget(editorButton(
                    toolX,
                    tool.icon,
                    editorTool == tool,
                    tool.tooltip,
                    () -> {
                        requestDiscard(() -> {
                            editorTool = tool;
                            closeDraft();
                            graph.clearLink();
                            graph.setPanning(false);
                            rebuildWidgets();
                        });
                    }
                ));
                toolX += 22;
            }
        }
        if (sidebarOpen) {
            int y = 34;
            List<String> orderedGroups = new ArrayList<>(groups());
            for (int chapterIndex = 0; chapterIndex < orderedGroups.size(); chapterIndex++) {
                String candidate = orderedGroups.get(chapterIndex);
                int index = chapterIndex;
                int groupY = y;
                Button button = Widgets.button(widget -> {
                    widget
                        .withPosition(4, groupY)
                        .withSize(sidebarWidth - (editMode ? 51 : 8), 20);
                    widget.withTexture(null);
                    widget.withRenderer(chapterButtonRenderer(candidate, candidate.equals(group)));
                    widget.withTooltip(Component.literal(candidate));
                    widget.withCallback(() -> {
                        requestDiscard(() -> {
                            group = candidate;
                            graph.resetPan();
                            graph.clearLink();
                            closeDraft();
                            rebuildWidgets();
                        });
                    });
                });
                addRenderableWidget(button);
                if (editMode) {
                    addRenderableWidget(Widgets.button(widget -> {
                        widget.withPosition(sidebarWidth - 45, groupY).withSize(11, 20);
                        widget.withTexture(null);
                        widget.withRenderer(WidgetRenderers.text(Component.literal("↑")));
                        widget.withCallback(() -> reorderChapter(index, -1));
                        widget.active = index > 0;
                    }));
                    addRenderableWidget(Widgets.button(widget -> {
                        widget.withPosition(sidebarWidth - 32, groupY).withSize(11, 20);
                        widget.withTexture(null);
                        widget.withRenderer(WidgetRenderers.text(Component.literal("↓")));
                        widget.withCallback(() -> reorderChapter(index, 1));
                        widget.active = index < orderedGroups.size() - 1;
                    }));
                    addRenderableWidget(Widgets.button(widget -> {
                        widget.withPosition(sidebarWidth - 19, groupY).withSize(11, 20);
                        widget.withTexture(null);
                        widget.withRenderer(WidgetRenderers.text(Component.literal("…")));
                        widget.withCallback(() -> openChapterEditor(candidate));
                        widget.withTooltip(Component.literal("Edit chapter"));
                    }));
                }
                y += 23;
            }
            int addChapterY = y;
            if (editMode) addRenderableWidget(Widgets.button(widget -> {
                widget.withPosition(4, addChapterY).withSize(sidebarWidth - 8, 20);
                widget.withTexture(null);
                widget.withRenderer(chapterButtonRenderer("+  Add chapter", false));
                widget.withCallback(() -> openChapterEditor(null));
            }));
        }
        addDockWidgets();
    }

    @Override
    protected void rebuildWidgets() {
        boolean closingPicker = picker == Picker.NONE && pickerSearch != null;
        QuestModalHost.Modal currentModal = modalHost.active();
        boolean modalVisible = isModalLayerVisible();
        boolean modalChanged = currentModal != lastModal || modalVisible != lastModalLayerVisible;
        super.rebuildWidgets();
        if (closingPicker) {
            pickerSearch = null;
        }
        // Rebuilding a modal replaces the widget tree. Restore focus to the
        // first eligible control on both open and close so keyboard navigation
        // never falls through to the underlying graph.
        if (modalChanged || closingPicker) children().stream().findFirst().ifPresent(this::setInitialFocus);
        lastModal = currentModal;
        lastModalLayerVisible = modalVisible;
    }

    private boolean isModalLayerVisible() {
        return modalHost.isOpen() || picker != Picker.NONE || deleteQuestConfirmation || discardConfirmation
            || taskDeleteConfirmation >= 0 || chapterEditorOpen || pasteIdPrompt || editingTask != null
            || editingReward != null || nestedRewardsOpen || editingNestedReward != null
            || taskChooserOpen || rewardChooserOpen || nestedRewardChooserOpen;
    }

    private void populateNodeBounds() {
        // Keep the graph anchored to the docked layout even while the details panel is hidden.
        // The newly exposed area remains usable for panning without shifting every quest node.
        int centerX = treeCenterX() + graph.panX();
        int centerY = treeCenterY() + graph.panY();
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
    }

    private HeaderLayout headerLayout() {
        int editX = canvasRight() - 23;
        int nextActionX = editX;
        int diagnosticsX = -1;
        int importX = -1;
        if (!diagnostics.isEmpty()) {
            nextActionX -= HEADER_ACTION_GAP + HEADER_ACTION_WIDTH;
            diagnosticsX = nextActionX;
        }
        if (editMode) {
            nextActionX -= HEADER_ACTION_GAP + HEADER_ACTION_WIDTH;
            importX = nextActionX;
        }

        int toolLeft = sidebarWidth() + 24;
        int toolRight = editMode
            ? toolLeft + (EditorTool.values().length - 1) * 22 + 19
            : toolLeft;
        boolean actionsOnSecondRow = (importX >= 0 || diagnosticsX >= 0)
            && nextActionX < toolRight + HEADER_ACTION_GAP;
        int actionRow = actionsOnSecondRow ? 1 : 0;
        int statusRow = !editorMessage.isEmpty() && !createQuestDockOpen
            ? actionRow + 1
            : -1;
        int rows = Math.max(1, Math.max(actionRow + 1, statusRow + 1));
        int canvasTop = HEADER_ROW_Y
            + rows * HEADER_ROW_HEIGHT
            + (rows - 1) * HEADER_ROW_GAP
            + HEADER_CANVAS_GAP;
        return new HeaderLayout(
            editX,
            importX,
            diagnosticsX,
            HEADER_ROW_Y + actionRow * (HEADER_ROW_HEIGHT + HEADER_ROW_GAP),
            statusRow < 0
                ? -1
                : HEADER_ROW_Y + statusRow * (HEADER_ROW_HEIGHT + HEADER_ROW_GAP),
            canvasTop
        );
    }

    private void addDockWidgets() {
        if (createQuestDockOpen) {
            addCreateQuestDockWidgets();
            return;
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

    private Button editorButton(
        int x,
        String icon,
        boolean selected,
        String tooltip,
        Runnable callback
    ) {
        return Widgets.button(widget -> {
            widget.withPosition(x, 1).withSize(19, 20);
            Identifier texture = sprite("heading/" + icon + (selected ? "_selected" : ""));
            widget.withRenderer(WidgetRenderers.center(
                11,
                11,
                WidgetRenderers.sprite(new WidgetSprites(texture, texture))
            ));
            widget.withCallback(callback);
            widget.withTooltip(Component.literal(tooltip));
        });
    }

    private WidgetRenderer<Button> chapterButtonRenderer(String chapter, boolean selected) {
        return (graphics, context, partialTick) -> {
            if (context.getWidget().isHoveredOrFocused()) {
                graphics.fill(
                    context.getX(),
                    context.getY(),
                    context.getX() + context.getWidth(),
                    context.getY() + context.getHeight(),
                    0x224C9AFF
                );
            }
            if (selected) {
                graphics.outline(
                    context.getX(),
                    context.getY(),
                    context.getWidth(),
                    context.getHeight(),
                    0xFF8A929F
                );
            }
            int contentX = context.getX() + 3;
            ChapterDisplay display = chapterDisplays.get(chapter);
            if (display != null) {
                try {
                    Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(display.icon));
                    if (item != null && item != Items.AIR) {
                        graphics.item(new ItemStack(item), contentX, context.getY() + 2);
                        contentX += 19;
                    }
                } catch (RuntimeException ignored) { }
            }
            graphics.enableScissor(contentX, context.getY(), context.getX() + context.getWidth() - 3, context.getY() + context.getHeight());
            int available = Math.max(0, context.getX() + context.getWidth() - 3 - contentX);
            String label = chapter;
            if (font.width(label) > available) {
                label = font.plainSubstrByWidth(label, Math.max(0, available - font.width("…"))) + "…";
            }
            graphics.text(font, Component.literal(label), contentX, context.getY() + 6, 0xFFFFFFFF, false);
            graphics.disableScissor();
        };
    }

    private void addCreateQuestDockWidgets() {
        int detailsLeft = width - detailsWidth();
        int pinLeft = width - 50;
        int tabRight = pinLeft - 4;
        int tabWidth = (tabRight - (detailsLeft + 8)) / DetailTab.values().length;
        for (int index = 0; index < DetailTab.values().length; index++) {
            DetailTab tab = DetailTab.values()[index];
            int tabX = detailsLeft + 8 + index * tabWidth;
            Button tabButton = Widgets.button(widget -> {
                widget.withPosition(tabX, 8).withSize(tabWidth - 3, 20);
                widget.withRenderer(WidgetRenderers.text(
                    Component.literal(tab.label)
                ).withColor(Color.parse(
                    tab == createQuestTab ? "#5A4300" : "#FFFFFF"
                )));
                widget.withCallback(() -> {
                    createQuestTab = tab;
                    picker = Picker.NONE;
                    taskChooserOpen = false;
                    rewardChooserOpen = false;
                    rebuildWidgets();
                });
            });
            addRenderableWidget(tabButton);
        }
        Button close = Widgets.button(widget -> {
            widget.withPosition(width - 27, 8).withSize(19, 20);
            widget.withRenderer(WidgetRenderers.center(
                11,
                11,
                WidgetRenderers.sprite(CLOSE_BUTTON)
            ));
            widget.withCallback(() -> {
                requestDiscard(() -> {
                    closeDraft();
                    rebuildWidgets();
                });
            });
            widget.withTooltip(Component.literal("Close new quest"));
        });
        addRenderableWidget(close);

        int x = detailsLeft + 12;
        int fieldWidth = detailsWidth() - 24;
        createConfirmButton = Widgets.button(widget -> {
            widget.withPosition(x, height - 30).withSize(fieldWidth, 22);
            widget.withRenderer(WidgetRenderers.text(Component.literal(editingExistingQuest ? "Save quest" : "Create quest")));
            widget.withCallback(this::confirmCreateQuest);
            String error = draftValidationError();
            widget.withTooltip(Component.literal(mutations.isPending() ? "Waiting for the server" : error.isEmpty() ? "Save this quest" : error));
        });
        updateCreateConfirmButton();
        addRenderableWidget(createConfirmButton);

        if (createQuestTab == DetailTab.TASKS) {
            addDraftTaskWidgets(x, fieldWidth);
            return;
        }
        if (createQuestTab == DetailTab.REWARDS) {
            addDraftRewardWidgets(x, fieldWidth);
            return;
        }
        if (createQuestTab != DetailTab.OVERVIEW) return;
        // Reserve room for the scroll rail so fields never sit underneath it.
        addOverviewDockWidgets(x, fieldWidth - 8);
    }

    private void addOverviewDockWidgets(int x, int fieldWidth) {
        GridLayout layout = new GridLayout().rowSpacing(4);
        int row = 0;
        layout.addChild(dockLabel("ID", fieldWidth), row++, 0);
        EditBox id = new EditBox(font, 0, 0, fieldWidth, 18, Component.literal("Quest ID"));
        id.setValue(createQuestId);
        id.setResponder(value -> {
            createQuestId = value;
            updateCreateConfirmButton();
        });
        layout.addChild(id, row++, 0);

        layout.addChild(dockLabel("Title", fieldWidth), row++, 0);
        EditBox title = new EditBox(font, 0, 0, fieldWidth, 18, Component.literal("Quest title"));
        title.setValue(createQuestTitle);
        title.setResponder(value -> {
            createQuestTitle = value;
            updateCreateConfirmButton();
        });
        layout.addChild(title, row++, 0);

        layout.addChild(dockLabel("Subtitle", fieldWidth), row++, 0);
        EditBox subtitle = new EditBox(font, 0, 0, fieldWidth, 18, Component.literal("Quest subtitle"));
        subtitle.setValue(createQuestSubtitle);
        subtitle.setResponder(value -> createQuestSubtitle = value);
        layout.addChild(subtitle, row++, 0);

        layout.addChild(dockLabel("Description", fieldWidth), row++, 0);
        MultiLineEditBox body = MultiLineEditBox.builder()
            .setX(0)
            .setY(0)
            .setPlaceholder(Component.literal("Quest body"))
            .build(font, fieldWidth, 108, Component.literal("Quest body"));
        body.setValue(createQuestBody);
        body.setValueListener(value -> createQuestBody = value);
        layout.addChild(body, row++, 0);

        layout.addChild(dockLabel("Appearance", fieldWidth), row++, 0);
        GridLayout appearance = new GridLayout().columnSpacing(6);
        Button icon = Widgets.button(widget -> {
            widget.withSize((fieldWidth - 6) / 2, 24);
            widget.withRenderer(WidgetRenderers.text(Component.literal("Choose icon")));
            widget.withCallback(() -> openPicker(Picker.ICON, PickerTarget.QUEST_ICON));
            widget.withTooltip(Component.literal("Choose quest icon"));
        });
        Button background = Widgets.button(widget -> {
            widget.withSize((fieldWidth - 6) / 2, 24);
            widget.withRenderer(WidgetRenderers.text(Component.literal("Choose background")));
            widget.withCallback(() -> openPicker(Picker.BACKGROUND));
            widget.withTooltip(Component.literal("Choose quest background"));
        });
        appearance.addChild(icon, 0, 0);
        appearance.addChild(background, 0, 1);
        layout.addChild(appearance, row++, 0);

        layout.addChild(dockLabel("Position", fieldWidth), row++, 0);
        GridLayout position = new GridLayout().columnSpacing(6);
        int positionWidth = (fieldWidth - 6) / 2;
        EditBox positionX = new EditBox(font, 0, 0, positionWidth, 18, Component.literal("X"));
        positionX.setValue(Integer.toString(createQuestX));
        positionX.setResponder(value -> {
            try { createQuestX = Integer.parseInt(value); } catch (NumberFormatException ignored) { }
        });
        EditBox positionY = new EditBox(font, 0, 0, positionWidth, 18, Component.literal("Y"));
        positionY.setValue(Integer.toString(createQuestY));
        positionY.setResponder(value -> {
            try { createQuestY = Integer.parseInt(value); } catch (NumberFormatException ignored) { }
        });
        position.addChild(positionX, 0, 0);
        position.addChild(positionY, 0, 1);
        layout.addChild(position, row++, 0);

        if (editingExistingQuest) {
            layout.addChild(dockLabel("Quest actions", fieldWidth), row++, 0);
            GridLayout actions = new GridLayout().columnSpacing(6);
            Button delete = Widgets.button(widget -> {
                widget.withSize((fieldWidth - 6) / 2, 22);
                widget.withRenderer(WidgetRenderers.text(Component.literal("Delete quest")));
                widget.withCallback(() -> {
                    deleteQuestConfirmation = true;
                    rebuildWidgets();
                });
            });
            actions.addChild(delete, 0, 0);
            if (createQuestGroups.size() > 1) actions.addChild(Widgets.button(widget -> {
                widget.withSize((fieldWidth - 6) / 2, 22);
                widget.withRenderer(WidgetRenderers.text(Component.literal("Remove from chapter")));
                widget.withCallback(() -> requestDiscard(this::removeExistingQuestFromChapter));
            }), 0, 1);
            layout.addChild(actions, row, 0);
        }

        LayoutWidget<GridLayout> scrollable = new LayoutWidget<>(layout)
            .withScrollableY(TriState.DEFAULT)
            .withContents(ignored -> { });
        scrollable.setPosition(x, 38);
        scrollable.setSize(fieldWidth + 8, Math.max(40, height - 76));
        addRenderableWidget(scrollable);
    }

    private TextWidget dockLabel(String text, int width) {
        return Widgets.text(Component.literal(text), widget -> {
            widget.withLeftAlignment().withFont(font).withColor(Color.parse("#B8C0CC"));
            widget.setSize(width, 12);
        });
    }

    private void openChapterEditor(String name) {
        chapterEditorOpen = true;
        chapterEditorOriginal = name;
        chapterEditorName = name == null ? "" : name;
        ChapterDisplay display = name == null ? null : chapterDisplays.get(name);
        chapterEditorIcon = display == null ? "minecraft:map" : display.icon;
        chapterEditorBackground = display == null ? "" : display.background;
        chapterEditorError = "";
        chapterDeleteArmed = false;
        chapterEditorBaseline = chapterEditorSnapshot();
        rebuildWidgets();
    }

    private String chapterEditorSnapshot() {
        return chapterEditorName + "\u0000" + chapterEditorIcon + "\u0000" + chapterEditorBackground;
    }

    private boolean hasUnsavedChapterEditor() {
        return chapterEditorOpen && chapterEditorBaseline != null && !chapterEditorBaseline.equals(chapterEditorSnapshot());
    }

    private void addChapterEditorWidgets() {
        int left = (width - 280) / 2;
        int top = (height - 210) / 2;
        EditBox name = new EditBox(font, left + 14, top + 48, 252, 18, Component.literal("Chapter name"));
        name.setValue(chapterEditorName);
        name.setResponder(value -> chapterEditorName = value);
        addRenderableWidget(name);
        addRenderableWidget(Widgets.button(widget -> {
            widget.withPosition(left + 14, top + 81).withSize(34, 24);
            widget.withRenderer((graphics, context, partialTick) -> {
                try {
                    Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(chapterEditorIcon));
                    graphics.item(new ItemStack(item == null ? Items.MAP : item), context.getX() + 9, context.getY() + 4);
                } catch (RuntimeException ignored) { }
            });
            widget.withCallback(() -> openPicker(Picker.ICON, PickerTarget.CHAPTER_ICON));
            widget.withTooltip(Component.literal("Choose chapter icon"));
        }));
        EditBox background = new EditBox(font, left + 14, top + 126, 252, 18, Component.literal("Background path or URL"));
        background.setValue(chapterEditorBackground);
        background.setResponder(value -> chapterEditorBackground = value);
        addRenderableWidget(background);
        if (chapterEditorOriginal != null) addRenderableWidget(Widgets.button(widget -> {
            widget.withPosition(left + 14, top + 169).withSize(72, 22);
            widget.withRenderer(WidgetRenderers.text(Component.literal("Delete")));
            widget.withCallback(this::deleteChapter);
        }));
        addRenderableWidget(Widgets.button(widget -> {
            widget.withPosition(left + 94, top + 169).withSize(82, 22);
            widget.withRenderer(WidgetRenderers.text(Component.literal("Cancel")));
            widget.withCallback(() -> requestModalDiscard(() -> {
                chapterEditorOpen = false;
                chapterEditorBaseline = null;
                rebuildWidgets();
            }));
        }));
        addRenderableWidget(Widgets.button(widget -> {
            widget.withPosition(left + 184, top + 169).withSize(82, 22);
            widget.withRenderer(WidgetRenderers.text(Component.literal("Save")));
            widget.withCallback(this::saveChapter);
        }));
    }

    private void saveChapter() {
        String name = chapterEditorName.trim();
        if (name.isEmpty() || (groups().contains(name) && !name.equals(chapterEditorOriginal))) {
            chapterEditorError = name.isEmpty() ? "Chapter name is required." : "That chapter already exists.";
            return;
        }
        JsonObject action = new JsonObject();
        action.addProperty("operation", chapterEditorOriginal == null ? "create" : "update");
        if (chapterEditorOriginal != null) action.addProperty("old_name", chapterEditorOriginal);
        action.addProperty("name", name);
        action.addProperty("icon", chapterEditorIcon);
        action.addProperty("background", chapterEditorBackground);
        sendChapterAction(action);
        chapterEditorOpen = false;
        chapterEditorBaseline = null;
        group = name;
        rebuildWidgets();
    }

    private void deleteChapter() {
        if (!chapterDeleteArmed) {
            chapterDeleteArmed = true;
            chapterEditorError = "Click Delete again to confirm.";
            rebuildWidgets();
            return;
        }
        JsonObject action = new JsonObject();
        action.addProperty("operation", "delete");
        action.addProperty("name", chapterEditorOriginal);
        sendChapterAction(action);
        chapterEditorOpen = false;
        chapterEditorBaseline = null;
        rebuildWidgets();
    }

    private void reorderChapter(int index, int direction) {
        List<String> order = new ArrayList<>(groups());
        int target = index + direction;
        if (index < 0 || target < 0 || target >= order.size()) return;
        java.util.Collections.swap(order, index, target);
        JsonObject action = new JsonObject();
        action.addProperty("operation", "reorder");
        action.add("order", GSON.toJsonTree(order));
        sendChapterAction(action);
        chapters.clear();
        chapters.addAll(order);
        rebuildWidgets();
    }

    private void sendChapterAction(JsonObject action) {
        sendEditorMutation("chapter_action", action);
    }

    private void addPickerSearchWidget() {
        if (picker == Picker.ICON || picker == Picker.ENTITY) {
            pickerSearch = new EditBox(
                font,
                pickerLeft() + 12,
                pickerTop() + 30,
                176,
                18,
                Component.literal(picker == Picker.ENTITY
                    ? "Search entities"
                    : "Search blocks and items")
            );
            pickerSearch.setResponder(ignored -> pickerScroll = 0);
            addRenderableWidget(pickerSearch);
            setInitialFocus(pickerSearch);
        }
    }

    private void addDraftTaskWidgets(int x, int width) {
        int y = 43;
        int end = Math.min(createQuestTasks.size(), createTaskScroll + taskListCapacity());
        for (int index = createTaskScroll; index < end; index++) {
            int taskIndex = index;
            int cardY = y + (index - createTaskScroll) * 48;
            Button edit = Widgets.button(widget -> {
                widget.withPosition(x + width - 59, cardY + 9).withSize(23, 24);
                widget.withRenderer(WidgetRenderers.center(
                    11,
                    11,
                    WidgetRenderers.sprite(new WidgetSprites(
                        sprite("lists/buttons/edit/normal"),
                        sprite("lists/buttons/edit/hovered")
                    ))
                ));
                widget.withCallback(() -> openTaskEditor(taskIndex));
                widget.active = createQuestTasks.get(taskIndex).isSupported();
                widget.withTooltip(Component.literal(widget.active ? "Edit task" : unsupportedReason(EditorTypeRegistry.Kind.TASK, createQuestTasks.get(taskIndex).type)));
            });
            addRenderableWidget(edit);
            Button delete = Widgets.button(widget -> {
                widget.withPosition(x + width - 31, cardY + 9).withSize(23, 24);
                widget.withRenderer(WidgetRenderers.center(
                    11,
                    11,
                    WidgetRenderers.sprite(new WidgetSprites(
                        sprite("lists/buttons/delete/normal"),
                        sprite("lists/buttons/delete/hovered")
                    ))
                ));
                widget.withCallback(() -> {
                    taskChooserOpen = false;
                    taskDeleteConfirmation = taskIndex;
                    rebuildWidgets();
                });
                widget.withTooltip(Component.literal("Delete task"));
            });
            addRenderableWidget(delete);
        }
        int addIndex = createQuestTasks.size();
        if (addIndex >= createTaskScroll && addIndex < createTaskScroll + taskListCapacity()) {
            int addY = y + (addIndex - createTaskScroll) * 48;
            Button add = Widgets.button(widget -> {
                widget.withPosition(x, addY).withSize(width, 42);
                widget.withRenderer(WidgetRenderers.text(Component.literal("+  Add task")));
                widget.withCallback(() -> {
                    taskChooserOpen = !taskChooserOpen;
                    taskChooserScroll = 0;
                });
                widget.withTooltip(Component.literal("Choose a task type"));
            });
            addRenderableWidget(add);
        }
    }

    private void addDraftRewardWidgets(int x, int width) {
        int y = 43;
        int end = Math.min(createQuestRewards.size(), createRewardScroll + rewardListCapacity());
        for (int index = createRewardScroll; index < end; index++) {
            int rewardIndex = index;
            int cardY = y + (index - createRewardScroll) * 48;
            addRenderableWidget(Widgets.button(widget -> {
                widget.withPosition(x + width - 59, cardY + 9).withSize(23, 24);
                widget.withRenderer(WidgetRenderers.center(11, 11, WidgetRenderers.sprite(new WidgetSprites(
                    sprite("lists/buttons/edit/normal"), sprite("lists/buttons/edit/hovered")
                ))));
                widget.withCallback(() -> openRewardEditor(rewardIndex));
                widget.active = createQuestRewards.get(rewardIndex).isSupported();
                widget.withTooltip(Component.literal(widget.active ? "Edit reward" : unsupportedReason(EditorTypeRegistry.Kind.REWARD, createQuestRewards.get(rewardIndex).type)));
            }));
            addRenderableWidget(Widgets.button(widget -> {
                widget.withPosition(x + width - 31, cardY + 9).withSize(23, 24);
                widget.withRenderer(WidgetRenderers.center(11, 11, WidgetRenderers.sprite(new WidgetSprites(
                    sprite("lists/buttons/delete/normal"), sprite("lists/buttons/delete/hovered")
                ))));
                widget.withCallback(() -> {
                    createQuestRewards.remove(rewardIndex);
                    createRewardScroll = Math.min(createRewardScroll, maxCreateRewardScroll());
                    rewardChooserOpen = false;
                    rebuildWidgets();
                });
                widget.withTooltip(Component.literal("Delete reward"));
            }));
        }
        int addIndex = createQuestRewards.size();
        if (addIndex >= createRewardScroll && addIndex < createRewardScroll + rewardListCapacity()) {
            int addY = y + (addIndex - createRewardScroll) * 48;
            addRenderableWidget(Widgets.button(widget -> {
                widget.withPosition(x, addY).withSize(width, 42);
                widget.withRenderer(WidgetRenderers.text(Component.literal("+  Add reward")));
                widget.withCallback(() -> {
                    rewardChooserOpen = !rewardChooserOpen;
                    rewardChooserScroll = 0;
                });
                widget.withTooltip(Component.literal("Choose a reward type"));
            }));
        }
    }

    private int rewardListCapacity() {
        return Math.max(1, (height - 79) / 48);
    }

    private int maxCreateRewardScroll() {
        return Math.max(0, createQuestRewards.size() + 1 - rewardListCapacity());
    }

    private void openRewardEditor(int index) {
        if (!createQuestRewards.get(index).isSupported()) {
            editorMessage = "Unsupported reward type '" + createQuestRewards.get(index).type + "' is preserved read-only.";
            editorMessageSuccess = false;
            return;
        }
        editingRewardIndex = index;
        editingReward = createQuestRewards.get(index).copy();
        rewardEditorError = "";
        rewardChooserOpen = false;
        rebuildWidgets();
    }

    private void openTaskEditor(int index) {
        if (!createQuestTasks.get(index).isSupported()) {
            editorMessage = "Unsupported task type '" + createQuestTasks.get(index).type + "' is preserved read-only.";
            editorMessageSuccess = false;
            return;
        }
        editingTaskIndex = index;
        editingTask = createQuestTasks.get(index).copy();
        taskEditorError = "";
        taskChooserOpen = false;
        rebuildWidgets();
    }

    private void beginEditQuest(ClientQuest quest) {
        QuestDefinition definition = quest.definition;
        editingExistingQuest = true;
        originalQuestId = definition.id();
        graph.select(definition.id());
        createQuestId = definition.id();
        createQuestTitle = definition.title();
        createQuestSubtitle = definition.subtitle();
        createQuestBody = String.join("\n", definition.description());
        createQuestIcon = definition.display().icon();
        createQuestBackground = definition.display().iconBackground();
        createQuestGroups = new JsonObject();
        definition.display().groups().forEach((name, position) -> {
            JsonObject placement = new JsonObject();
            com.google.gson.JsonArray coordinates = new com.google.gson.JsonArray();
            coordinates.add(position.x());
            coordinates.add(position.y());
            placement.add("position", coordinates);
            createQuestGroups.add(name, placement);
        });
        QuestDefinition.GroupDisplay position = definition.position(group);
        createQuestX = position.x();
        createQuestY = position.y();
        createQuestTasks.clear();
        definition.tasks().values().forEach(task -> createQuestTasks.add(new DraftTask(task.id(), task.type(), task.source().deepCopy())));
        createQuestRewards.clear();
        definition.rewards().values().forEach(reward -> createQuestRewards.add(new DraftReward(reward.id(), reward.type(), reward.source().deepCopy())));
        createQuestTab = DetailTab.OVERVIEW;
        createTaskScroll = 0;
        createRewardScroll = 0;
        detailsOpen = false;
        createQuestDockOpen = true;
        editorMessage = definition.issues().stream().anyMatch(issue -> issue.severity() == QuestDefinition.Severity.WARNING)
            ? "Unsupported configuration is preserved and shown read-only."
            : "";
        editorMessageSuccess = false;
        mutations.cancel();
        draftBaseline = draftSnapshot();
        rebuildWidgets();
    }

    private void beginCreateQuest(double treeX, double treeY) {
        createQuestId = "";
        createQuestTitle = "";
        createQuestSubtitle = "";
        createQuestBody = "";
        createQuestIcon = "minecraft:map";
        createQuestBackground = "heracles:textures/gui/quest_backgrounds/default.png";
        createQuestTasks.clear();
        createQuestRewards.clear();
        editingExistingQuest = false;
        originalQuestId = null;
        createQuestGroups = new JsonObject();
        createTaskScroll = 0;
        taskChooserOpen = false;
        createQuestTab = DetailTab.OVERVIEW;
        createQuestX = (int) Math.round(treeX - treeCenterX() - graph.panX());
        createQuestY = (int) Math.round(treeY - treeCenterY() - graph.panY());
        updateDraftGroupPosition();
        detailsOpen = false;
        createQuestDockOpen = true;
        editorMessage = "";
        editorMessageSuccess = false;
        mutations.cancel();
        draftBaseline = draftSnapshot();
        rebuildWidgets();
    }

    private void updateDraftGroupPosition() {
        JsonObject placement = createQuestGroups.has(group) && createQuestGroups.get(group).isJsonObject()
            ? createQuestGroups.getAsJsonObject(group) : new JsonObject();
        com.google.gson.JsonArray coordinates = new com.google.gson.JsonArray();
        coordinates.add(createQuestX);
        coordinates.add(createQuestY);
        placement.add("position", coordinates);
        createQuestGroups.add(group, placement);
    }

    private void addDeleteQuestConfirmationWidgets() {
        int left = (width - 240) / 2;
        int top = (height - 110) / 2;
        addRenderableWidget(Widgets.button(widget -> {
            widget.withPosition(left + 12, top + 70).withSize(102, 22);
            widget.withRenderer(WidgetRenderers.text(Component.literal("Cancel")));
            widget.withCallback(() -> { deleteQuestConfirmation = false; rebuildWidgets(); });
        }));
        addRenderableWidget(Widgets.button(widget -> {
            widget.withPosition(left + 126, top + 70).withSize(102, 22);
            widget.withRenderer(WidgetRenderers.text(Component.literal("Delete")));
            widget.withCallback(() -> {
                confirmDeleteQuest();
            });
        }));
    }

    private void addDeleteTaskConfirmationWidgets() {
        int left = (width - 240) / 2;
        int top = (height - 110) / 2;
        addRenderableWidget(Widgets.button(widget -> {
            widget.withPosition(left + 12, top + 70).withSize(102, 22);
            widget.withRenderer(WidgetRenderers.text(Component.literal("Cancel")));
            widget.withCallback(() -> { taskDeleteConfirmation = -1; rebuildWidgets(); });
        }));
        addRenderableWidget(Widgets.button(widget -> {
            widget.withPosition(left + 126, top + 70).withSize(102, 22);
            widget.withRenderer(WidgetRenderers.text(Component.literal("Delete")));
            widget.withCallback(() -> {
                confirmDeleteTask();
            });
        }));
    }

    private void confirmDeleteQuest() {
        deleteQuestConfirmation = false;
        JsonObject request = new JsonObject();
        request.addProperty("id", originalQuestId);
        editorMessage = "Deleting…";
        editorMessageSuccess = false;
        sendEditorMutation("delete_quest", request);
        rebuildWidgets();
    }

    private void confirmDeleteTask() {
        if (taskDeleteConfirmation < createQuestTasks.size()) {
            createQuestTasks.remove(taskDeleteConfirmation);
            createTaskScroll = Math.min(createTaskScroll, maxCreateTaskScroll());
        }
        taskDeleteConfirmation = -1;
        rebuildWidgets();
    }

    private void addDiscardConfirmationWidgets() {
        int left = (width - 260) / 2;
        int top = (height - 116) / 2;
        addRenderableWidget(Widgets.button(widget -> {
            widget.withPosition(left + 12, top + 76).withSize(112, 22);
            widget.withRenderer(WidgetRenderers.text(Component.literal("Keep editing")));
            widget.withCallback(() -> {
                discardConfirmation = false;
                discardAction = null;
                rebuildWidgets();
            });
        }));
        addRenderableWidget(Widgets.button(widget -> {
            widget.withPosition(left + 136, top + 76).withSize(112, 22);
            widget.withRenderer(WidgetRenderers.text(Component.literal("Discard changes")));
            widget.withCallback(() -> {
                Runnable action = discardAction;
                discardConfirmation = false;
                discardAction = null;
                if (action != null) action.run();
            });
        }));
    }

    private void removeExistingQuestFromChapter() {
        JsonObject change = new JsonObject();
        change.addProperty("id", originalQuestId);
        change.addProperty("group", group);
        sendEditorMutation("remove_quest_group", change);
        createQuestDockOpen = false;
        editingExistingQuest = false;
        originalQuestId = null;
        rebuildWidgets();
    }

    private void addTaskEditorWidgets() {
        int left = taskEditorLeft();
        int top = taskEditorTop();
        int fieldWidth = 232;

        EditBox id = new EditBox(font, left + 14, top + 38, fieldWidth, 18, Component.literal("Task ID"));
        id.setValue(editingTask.id);
        id.setResponder(value -> editingTask.id = value);
        addRenderableWidget(id);

        EditBox title = new EditBox(font, left + 14, top + 70, fieldWidth, 18, Component.literal("Task title"));
        title.setValue(jsonString(editingTask.source, "title", ""));
        title.setResponder(value -> setOptionalString(editingTask.source, "title", value));
        addRenderableWidget(title);

        Button icon = Widgets.button(widget -> {
            widget.withPosition(left + 14, top + 101).withSize(34, 24);
            widget.withRenderer(WidgetRenderers.text(Component.empty()));
            widget.withCallback(() -> openPicker(Picker.ICON, PickerTarget.TASK_ICON));
            widget.withTooltip(Component.literal("Choose task icon override"));
        });
        addRenderableWidget(icon);
        Button clearIcon = Widgets.button(widget -> {
            widget.withPosition(left + 52, top + 101).withSize(24, 24);
            widget.withRenderer(WidgetRenderers.text(Component.literal("\u00d7")));
            widget.withCallback(() -> {
                editingTask.source.remove("icon");
                rebuildWidgets();
            });
            widget.withTooltip(Component.literal("Use the default task icon"));
        });
        addRenderableWidget(clearIcon);

        switch (editingTask.type) {
            case "heracles:dummy" -> addDummyTaskFields(left, top, fieldWidth);
            case "heracles:item" -> addItemTaskFields(left, top, fieldWidth);
            case "heracles:xp" -> addXpTaskFields(left, top, fieldWidth);
            case "heracles:kill_entity" -> addKillTaskFields(left, top, fieldWidth);
            case "heracles:advancement" -> addStringListTaskField(left, top, fieldWidth, "advancements", "Advancement IDs", "minecraft:story/mine_stone");
            case "heracles:biome" -> addIdentifierTaskField(left, top, fieldWidth, "biomes", "Biome or #tag", "minecraft:plains");
            case "heracles:block_interaction" -> addBlockInteractionTaskFields(left, top, fieldWidth);
            case "heracles:changed_dimension" -> addDimensionTaskFields(left, top, fieldWidth);
            case "heracles:check" -> addJsonTaskField(left, top, fieldWidth, "components", "Player data predicate", new JsonObject());
            case "heracles:composite" -> addCompositeTaskFields(left, top, fieldWidth);
            case "heracles:entity_interaction" -> addPredicateTargetFields(left, top, fieldWidth, "entity", "Entity or #tag", "minecraft:pig", PickerTarget.TASK_ENTITY);
            case "heracles:item_interaction", "heracles:item_use" -> addPredicateTargetFields(left, top, fieldWidth, "item", "Item or #tag", "minecraft:stick", PickerTarget.TASK_ITEM);
            case "heracles:location" -> addLocationTaskFields(left, top, fieldWidth);
            case "heracles:recipe" -> addStringListTaskField(left, top, fieldWidth, "recipes", "Recipe IDs", "minecraft:crafting_table");
            case "heracles:stat" -> addStatTaskFields(left, top, fieldWidth);
            case "heracles:structure" -> addIdentifierTaskField(left, top, fieldWidth, "structures", "Structure or #tag", "#minecraft:village");
            default -> {
            }
        }

        Button cancel = Widgets.button(widget -> {
            widget.withPosition(left + 14, top + 264).withSize(108, 22);
            widget.withRenderer(WidgetRenderers.text(Component.literal("Cancel")));
            widget.withCallback(() -> requestModalDiscard(this::closeTaskEditor));
        });
        addRenderableWidget(cancel);
        Button save = Widgets.button(widget -> {
            widget.withPosition(left + 138, top + 264).withSize(108, 22);
            widget.withRenderer(WidgetRenderers.text(Component.literal("Save task")));
            widget.withCallback(this::saveTaskEditor);
        });
        addRenderableWidget(save);
    }

    private void addDummyTaskFields(int left, int top, int width) {
        EditBox value = new EditBox(font, left + 14, top + 142, width, 18, Component.literal("Trigger value"));
        value.setValue(jsonString(editingTask.source, "value", ""));
        value.setResponder(text -> editingTask.source.addProperty("value", text));
        addRenderableWidget(value);
        EditBox description = new EditBox(font, left + 14, top + 181, width, 18, Component.literal("Description"));
        description.setValue(jsonString(editingTask.source, "description", ""));
        description.setResponder(text -> setOptionalString(editingTask.source, "description", text));
        addRenderableWidget(description);
    }

    private void addItemTaskFields(int left, int top, int width) {
        EditBox item = new EditBox(font, left + 14, top + 142, width - 40, 18, Component.literal("Item or #tag"));
        item.setValue(registryValueString(editingTask.source, "item", "minecraft:stone"));
        item.setResponder(text -> editingTask.source.addProperty("item", text));
        addRenderableWidget(item);
        Button choose = Widgets.button(widget -> {
            widget.withPosition(left + 210, top + 139).withSize(36, 24);
            widget.withRenderer(WidgetRenderers.text(Component.literal("…")));
            widget.withCallback(() -> openPicker(Picker.ICON, PickerTarget.TASK_ITEM));
            widget.withTooltip(Component.literal("Choose item"));
        });
        addRenderableWidget(choose);
        addAmountField(left, top + 181);
        addCycleButton(left + 104, top + 178, 142, "collection", "automatic", List.of(
            "automatic", "manual", "consume"
        ));
    }

    private void addXpTaskFields(int left, int top, int width) {
        addAmountField(left, top + 142);
        addCycleButton(left + 104, top + 139, 142, "xpType", "level", List.of("level", "points"));
        addCycleButton(left + 14, top + 178, 232, "collectionType", "automatic", List.of(
            "automatic", "manual", "consume"
        ));
    }

    private void addKillTaskFields(int left, int top, int width) {
        EditBox entity = new EditBox(font, left + 14, top + 142, width - 40, 18, Component.literal("Entity"));
        entity.setValue(registryValueString(editingTask.source, "entity", "minecraft:pig"));
        entity.setResponder(text -> editingTask.source.addProperty("entity", text));
        addRenderableWidget(entity);
        Button choose = Widgets.button(widget -> {
            widget.withPosition(left + 210, top + 139).withSize(36, 24);
            widget.withRenderer(WidgetRenderers.text(Component.literal("…")));
            widget.withCallback(() -> openPicker(Picker.ENTITY, PickerTarget.TASK_ENTITY));
            widget.withTooltip(Component.literal("Choose entity"));
        });
        addRenderableWidget(choose);
        addAmountField(left, top + 181);
    }

    private void addIdentifierTaskField(int left, int top, int width, String key, String label, String fallback) {
        EditBox field = new EditBox(font, left + 14, top + 142, width, 18, Component.literal(label));
        field.setValue(registryValueString(editingTask.source, key, fallback));
        field.setResponder(text -> editingTask.source.addProperty(key, text));
        addRenderableWidget(field);
    }

    private void addStringListTaskField(int left, int top, int width, String key, String label, String fallback) {
        EditBox field = new EditBox(font, left + 14, top + 142, width, 18, Component.literal(label));
        field.setValue(jsonStringList(editingTask.source, key, fallback));
        field.setResponder(text -> editingTask.source.add(key, stringArray(text)));
        addRenderableWidget(field);
    }

    private void addPredicateTargetFields(
        int left, int top, int width, String key, String label, String fallback, PickerTarget target
    ) {
        EditBox value = new EditBox(font, left + 14, top + 142, width - 40, 18, Component.literal(label));
        value.setValue(registryValueString(editingTask.source, key, fallback));
        value.setResponder(text -> editingTask.source.addProperty(key, text));
        addRenderableWidget(value);
        addRenderableWidget(Widgets.button(widget -> {
            widget.withPosition(left + 210, top + 139).withSize(36, 24);
            widget.withRenderer(WidgetRenderers.text(Component.literal("…")));
            widget.withCallback(() -> openPicker(target == PickerTarget.TASK_ENTITY ? Picker.ENTITY : Picker.ICON, target));
            widget.withTooltip(Component.literal("Choose target"));
        }));
        addJsonTaskField(left, top + 39, width, "components", "Component/data predicate", new JsonObject());
    }

    private void addBlockInteractionTaskFields(int left, int top, int width) {
        addPredicateTargetFields(left, top, width, "block", "Block or #tag", "minecraft:stone", PickerTarget.TASK_BLOCK);
        addJsonTaskField(left, top + 78, width, "state", "Block state predicate", new JsonObject());
    }

    private void addLocationTaskFields(int left, int top, int width) {
        addJsonTaskField(left, top, width, "predicate", "Location predicate", defaultLocationPredicate());
        EditBox description = new EditBox(font, left + 14, top + 181, width, 18, Component.literal("Description"));
        description.setValue(jsonString(editingTask.source, "description", ""));
        description.setResponder(text -> setOptionalString(editingTask.source, "description", text));
        addRenderableWidget(description);
    }

    private void addDimensionTaskFields(int left, int top, int width) {
        EditBox from = new EditBox(font, left + 14, top + 142, 110, 18, Component.literal("From dimension"));
        from.setValue(jsonString(editingTask.source, "from", ""));
        from.setResponder(text -> setOptionalString(editingTask.source, "from", text));
        addRenderableWidget(from);
        EditBox to = new EditBox(font, left + 136, top + 142, 110, 18, Component.literal("To dimension"));
        to.setValue(jsonString(editingTask.source, "to", ""));
        to.setResponder(text -> setOptionalString(editingTask.source, "to", text));
        addRenderableWidget(to);
    }

    private void addJsonTaskField(int left, int top, int width, String key, String label, JsonObject fallback) {
        EditBox field = new EditBox(font, left + 14, top + 142, width, 18, Component.literal(label));
        field.setMaxLength(2048);
        JsonElement current = editingTask.source.get(key);
        field.setValue(current == null ? GSON.toJson(fallback) : current.isJsonPrimitive() ? current.getAsString() : GSON.toJson(current));
        field.setResponder(text -> editingTask.source.addProperty(key, text));
        addRenderableWidget(field);
    }

    private void addCompositeTaskFields(int left, int top, int width) {
        addAmountField(left, top + 142);
        addJsonTaskField(left, top + 39, width, "tasks", "Nested task map", new JsonObject());
    }

    private void addStatTaskFields(int left, int top, int width) {
        EditBox stat = new EditBox(font, left + 14, top + 142, width - 100, 18, Component.literal("Statistic ID"));
        stat.setValue(jsonString(editingTask.source, "stat", "minecraft:jump"));
        stat.setResponder(text -> editingTask.source.addProperty("stat", text));
        addRenderableWidget(stat);
        EditBox target = new EditBox(font, left + width - 76, top + 142, 76, 18, Component.literal("Target"));
        target.setValue(Integer.toString(jsonInt(editingTask.source, "target", 1)));
        target.setResponder(text -> editingTask.source.addProperty("target", parseInteger(text)));
        addRenderableWidget(target);
    }

    private void addAmountField(int left, int y) {
        EditBox amount = new EditBox(font, left + 14, y, 82, 18, Component.literal("Amount"));
        amount.setValue(Integer.toString(jsonInt(editingTask.source, "amount", 1)));
        amount.setResponder(text -> {
            try {
                editingTask.source.addProperty("amount", Integer.parseInt(text));
            } catch (NumberFormatException ignored) {
                editingTask.source.addProperty("amount", 0);
            }
        });
        addRenderableWidget(amount);
    }

    private void addCycleButton(
        int x,
        int y,
        int width,
        String key,
        String fallback,
        List<String> values
    ) {
        String current = jsonString(editingTask.source, key, fallback).toLowerCase(java.util.Locale.ROOT);
        Button cycle = Widgets.button(widget -> {
            widget.withPosition(x, y).withSize(width, 24);
            widget.withRenderer(WidgetRenderers.text(Component.literal(friendly(current))));
            widget.withCallback(() -> {
                int index = Math.max(0, values.indexOf(current));
                editingTask.source.addProperty(key, values.get((index + 1) % values.size()));
                rebuildWidgets();
            });
            widget.withTooltip(Component.literal("Click to change"));
        });
        addRenderableWidget(cycle);
    }

    private void closeTaskEditor() {
        editingTask = null;
        editingTaskIndex = -1;
        taskEditorError = "";
        picker = Picker.NONE;
        rebuildWidgets();
    }

    private void saveTaskEditor() {
        String error = validateTaskDraft(editingTask, editingTaskIndex);
        if (!error.isEmpty()) {
            taskEditorError = error;
            return;
        }
        createQuestTasks.set(editingTaskIndex, editingTask.copy());
        closeTaskEditor();
    }

    private void addRewardEditorWidgets(DraftReward reward, boolean nested) {
        int left = rewardEditorLeft();
        int top = rewardEditorTop();
        int width = 272;
        EditBox id = new EditBox(font, left + 14, top + 38, width, 18, Component.literal("Reward ID"));
        id.setValue(reward.id);
        id.setResponder(value -> reward.id = value);
        addRenderableWidget(id);
        EditBox title = new EditBox(font, left + 14, top + 70, width, 18, Component.literal("Reward title"));
        title.setValue(jsonString(reward.source, "title", ""));
        title.setResponder(value -> setOptionalString(reward.source, "title", value));
        addRenderableWidget(title);
        addRenderableWidget(Widgets.button(widget -> {
            widget.withPosition(left + 14, top + 101).withSize(34, 24);
            widget.withRenderer(WidgetRenderers.text(Component.empty()));
            widget.withCallback(() -> openPicker(Picker.ICON, PickerTarget.REWARD_ICON));
            widget.withTooltip(Component.literal("Choose reward icon override"));
        }));
        addRenderableWidget(Widgets.button(widget -> {
            widget.withPosition(left + 52, top + 101).withSize(24, 24);
            widget.withRenderer(WidgetRenderers.text(Component.literal("\u00d7")));
            widget.withCallback(() -> {
                reward.source.remove("icon");
                rebuildWidgets();
            });
            widget.withTooltip(Component.literal("Use the default reward icon"));
        }));
        switch (reward.type) {
            case "heracles:xp" -> {
                addRewardAmountField(reward, left + 14, top + 142, 92, "amount");
                addRewardCycleButton(reward, left + 114, top + 139, 172, "xptype", "level", List.of("level", "points"));
            }
            case "heracles:item" -> {
                EditBox item = new EditBox(font, left + 14, top + 142, 210, 18, Component.literal("Item"));
                item.setValue(rewardItemId(reward.source));
                item.setResponder(value -> setRewardItem(reward.source, value, rewardItemCount(reward.source)));
                addRenderableWidget(item);
                addRenderableWidget(Widgets.button(widget -> {
                    widget.withPosition(left + 232, top + 139).withSize(54, 24);
                    widget.withRenderer(WidgetRenderers.text(Component.literal("…")));
                    widget.withCallback(() -> openPicker(Picker.ICON, PickerTarget.REWARD_ITEM));
                    widget.withTooltip(Component.literal("Choose item"));
                }));
                addRewardAmountField(reward, left + 14, top + 181, 92, "item.count");
            }
            case "heracles:loottable" -> addRewardTextField(reward, left, top, "loot_table", "Loot table");
            case "heracles:command" -> addRewardTextField(reward, left, top, "command", "Command");
            case "heracles:selectable" -> {
                addRewardAmountField(reward, left + 14, top + 142, 92, "amount");
                addRenderableWidget(Widgets.button(widget -> {
                    widget.withPosition(left + 114, top + 139).withSize(172, 24);
                    widget.withRenderer(WidgetRenderers.text(Component.literal("Manage choices (" + nestedRewards(reward).size() + ")")));
                    widget.withCallback(() -> {
                        nestedRewardsOpen = true;
                        nestedRewardChooserOpen = false;
                        rewardEditorError = "";
                        rebuildWidgets();
                    });
                }));
            }
            default -> { }
        }
        addRenderableWidget(Widgets.button(widget -> {
            widget.withPosition(left + 14, top + 244).withSize(128, 22);
            widget.withRenderer(WidgetRenderers.text(Component.literal("Cancel")));
            widget.withCallback(() -> requestModalDiscard(() -> closeRewardEditor(nested)));
        }));
        addRenderableWidget(Widgets.button(widget -> {
            widget.withPosition(left + 158, top + 244).withSize(128, 22);
            widget.withRenderer(WidgetRenderers.text(Component.literal("Save reward")));
            widget.withCallback(() -> saveRewardEditor(nested));
        }));
    }

    private void addRewardTextField(DraftReward reward, int left, int top, String key, String label) {
        EditBox field = new EditBox(font, left + 14, top + 142, 272, 18, Component.literal(label));
        field.setValue(jsonString(reward.source, key, ""));
        field.setResponder(value -> reward.source.addProperty(key, value));
        addRenderableWidget(field);
    }

    private void addRewardAmountField(DraftReward reward, int x, int y, int width, String path) {
        int current = path.equals("item.count") ? rewardItemCount(reward.source) : jsonInt(reward.source, path, 1);
        EditBox amount = new EditBox(font, x, y, width, 18, Component.literal("Amount"));
        amount.setValue(Integer.toString(current));
        amount.setResponder(value -> {
            int parsed;
            try { parsed = Integer.parseInt(value); } catch (NumberFormatException ignored) { parsed = 0; }
            if (path.equals("item.count")) setRewardItem(reward.source, rewardItemId(reward.source), parsed);
            else reward.source.addProperty(path, parsed);
        });
        addRenderableWidget(amount);
    }

    private void addRewardCycleButton(DraftReward reward, int x, int y, int width, String key, String fallback, List<String> values) {
        String current = jsonString(reward.source, key, fallback).toLowerCase(java.util.Locale.ROOT);
        addRenderableWidget(Widgets.button(widget -> {
            widget.withPosition(x, y).withSize(width, 24);
            widget.withRenderer(WidgetRenderers.text(Component.literal(friendly(current))));
            widget.withCallback(() -> {
                int index = Math.max(0, values.indexOf(current));
                reward.source.addProperty(key, values.get((index + 1) % values.size()));
                rebuildWidgets();
            });
        }));
    }

    private void closeRewardEditor(boolean nested) {
        rewardEditorError = "";
        picker = Picker.NONE;
        if (nested) {
            editingNestedReward = null;
            editingNestedRewardIndex = -1;
        } else {
            editingReward = null;
            editingRewardIndex = -1;
            nestedRewardsOpen = false;
        }
        rebuildWidgets();
    }

    private void saveRewardEditor(boolean nested) {
        DraftReward reward = nested ? editingNestedReward : editingReward;
        String error = validateRewardDraft(reward, nested);
        if (!error.isEmpty()) {
            rewardEditorError = error;
            return;
        }
        if (nested) {
            List<DraftReward> rewards = nestedRewards(editingReward);
            rewards.set(editingNestedRewardIndex, reward.copy());
            setNestedRewards(editingReward, rewards);
        } else {
            createQuestRewards.set(editingRewardIndex, reward.copy());
        }
        closeRewardEditor(nested);
    }

    private void addNestedRewardWidgets() {
        int left = rewardEditorLeft();
        int top = rewardEditorTop();
        List<DraftReward> rewards = nestedRewards(editingReward);
        int y = top + 40;
        int end = Math.min(rewards.size(), nestedRewardScroll + 4);
        for (int index = nestedRewardScroll; index < end; index++) {
            int nestedIndex = index;
            int rowY = y + (index - nestedRewardScroll) * 42;
            addRenderableWidget(Widgets.button(widget -> {
                widget.withPosition(left + 14, rowY).withSize(218, 34);
                widget.withRenderer(WidgetRenderers.text(Component.literal(rewards.get(nestedIndex).id + "  ·  " + rewards.get(nestedIndex).choice().label)));
                widget.withCallback(() -> {
                    editingNestedRewardIndex = nestedIndex;
                    editingNestedReward = rewards.get(nestedIndex).copy();
                    rebuildWidgets();
                });
            }));
            addRenderableWidget(Widgets.button(widget -> {
                widget.withPosition(left + 240, rowY + 5).withSize(46, 24);
                widget.withRenderer(WidgetRenderers.text(Component.literal("\u00d7")));
                widget.withCallback(() -> {
                    List<DraftReward> updated = nestedRewards(editingReward);
                    updated.remove(nestedIndex);
                    setNestedRewards(editingReward, updated);
                    nestedRewardScroll = Math.min(nestedRewardScroll, Math.max(0, updated.size() - 4));
                    rebuildWidgets();
                });
                widget.withTooltip(Component.literal("Delete choice"));
            }));
        }
        addRenderableWidget(Widgets.button(widget -> {
            widget.withPosition(left + 14, top + 211).withSize(272, 24);
            widget.withRenderer(WidgetRenderers.text(Component.literal("+  Add choice")));
            widget.withCallback(() -> nestedRewardChooserOpen = !nestedRewardChooserOpen);
        }));
        addRenderableWidget(Widgets.button(widget -> {
            widget.withPosition(left + 86, top + 244).withSize(128, 22);
            widget.withRenderer(WidgetRenderers.text(Component.literal("Done")));
            widget.withCallback(() -> {
                nestedRewardsOpen = false;
                nestedRewardChooserOpen = false;
                rebuildWidgets();
            });
        }));
    }

    private int taskListCapacity() {
        return Math.max(1, (height - 79) / 48);
    }

    private int maxCreateTaskScroll() {
        return Math.max(0, createQuestTasks.size() + 1 - taskListCapacity());
    }

    private void openPicker(Picker value) {
        openPicker(value, PickerTarget.QUEST_ICON);
    }

    private void openPicker(Picker value, PickerTarget target) {
        picker = value;
        pickerTarget = target;
        pickerScroll = pickerScrollByTarget.getOrDefault(target, 0);
        pickerSearch = null;
        rebuildWidgets();
    }

    private DraftReward activeRewardDraft() {
        return editingNestedReward != null ? editingNestedReward : editingReward;
    }

    private boolean validCreateQuestDraft() {
        return draftValidationError().isEmpty() && !mutations.isPending();
    }

    private String draftValidationError() {
        if (!createQuestId.matches("[a-z0-9_.-]+")) return "Quest ID may only contain lowercase letters, numbers, ., _, and -.";
        if (createQuestTitle.trim().isEmpty()) return "Quest title is required.";
        if (quests.stream().anyMatch(quest -> quest.definition.id().equals(createQuestId) &&
            (!editingExistingQuest || !quest.definition.id().equals(originalQuestId)))) {
            return "Another quest already uses this ID.";
        }
        Set<String> taskIds = new java.util.HashSet<>();
        for (int index = 0; index < createQuestTasks.size(); index++) {
            DraftTask task = createQuestTasks.get(index);
            if (task.id == null || !task.id.matches("[a-z0-9_.-]+")) return "Task IDs may only contain lowercase letters, numbers, ., _, and -.";
            if (!taskIds.add(task.id)) return "Duplicate task ID: " + task.id;
            if (task.isSupported()) {
                String error = validateTaskDraft(task.copy(), index);
                if (!error.isEmpty()) return "Task '" + task.id + "': " + error;
            }
        }
        Set<String> rewardIds = new java.util.HashSet<>();
        for (int index = 0; index < createQuestRewards.size(); index++) {
            DraftReward reward = createQuestRewards.get(index);
            if (reward.id == null || !reward.id.matches("[a-z0-9_.-]+")) return "Reward IDs may only contain lowercase letters, numbers, ., _, and -.";
            if (!rewardIds.add(reward.id)) return "Duplicate reward ID: " + reward.id;
        }
        JsonObject root = new JsonObject();
        JsonObject display = new JsonObject();
        display.addProperty("title", createQuestTitle);
        root.add("display", display);
        JsonObject rewards = new JsonObject();
        createQuestRewards.forEach(reward -> rewards.add(reward.id, reward.source.deepCopy()));
        root.add("rewards", rewards);
        JsonObject tasks = new JsonObject();
        createQuestTasks.forEach(task -> tasks.add(task.id, task.source.deepCopy()));
        root.add("tasks", tasks);
        return QuestDiagnostics.validate("editor", root).stream()
            .filter(QuestDiagnostics.Diagnostic::blocksSave)
            .map(diagnostic -> diagnostic.path() + ": " + diagnostic.message())
            .collect(java.util.stream.Collectors.joining("\n"));
    }

    private void updateCreateConfirmButton() {
        if (createConfirmButton != null) createConfirmButton.active = validCreateQuestDraft();
    }

    private void confirmCreateQuest() {
        String validationError = draftValidationError();
        if (!validationError.isEmpty()) {
            editorMessage = validationError;
            editorMessageSuccess = false;
            rebuildWidgets();
            return;
        }
        updateDraftGroupPosition();
        JsonObject current = draftSnapshot();
        JsonObject draft = new JsonObject();
        draft.addProperty("id", createQuestId);
        draft.addProperty("title", createQuestTitle);
        draft.addProperty("subtitle", createQuestSubtitle);
        draft.addProperty("body", createQuestBody);
        draft.addProperty("icon", createQuestIcon);
        draft.addProperty("background", createQuestBackground);
        draft.addProperty("group", group);
        draft.addProperty("x", createQuestX);
        draft.addProperty("y", createQuestY);
        draft.add("groups", createQuestGroups.deepCopy());
        JsonObject tasks = new JsonObject();
        createQuestTasks.forEach(task -> tasks.add(task.id, task.source.deepCopy()));
        draft.add("tasks", tasks);
        JsonObject rewards = new JsonObject();
        createQuestRewards.forEach(reward -> rewards.add(reward.id, reward.source.deepCopy()));
        draft.add("rewards", rewards);
        if (editingExistingQuest) {
            draft.addProperty("original_id", originalQuestId);
            JsonObject changed = new JsonObject();
            for (String field : List.of("title", "subtitle", "body", "icon", "background", "groups")) {
                if (draftBaseline == null || !java.util.Objects.equals(draftBaseline.get(field), current.get(field))) {
                    changed.addProperty(field, true);
                }
            }
            draft.add("changed_fields", changed);
        }
        String json = GSON.toJson(draft);
        if (json.length() > QuestNetwork.EditorMutationPayload.MAX_JSON_LENGTH) {
            editorMessage = "This quest is too large to save (maximum 1 MiB).";
            editorMessageSuccess = false;
            rebuildWidgets();
            return;
        }
        editorMessage = "Saving…";
        editorMessageSuccess = false;
        sendEditorMutation(editingExistingQuest ? "update_quest" : "create_quest", draft);
        rebuildWidgets();
    }

    private JsonObject draftSnapshot() {
        JsonObject draft = new JsonObject();
        draft.addProperty("id", createQuestId);
        draft.addProperty("title", createQuestTitle);
        draft.addProperty("subtitle", createQuestSubtitle);
        draft.addProperty("body", createQuestBody);
        draft.addProperty("icon", createQuestIcon);
        draft.addProperty("background", createQuestBackground);
        draft.add("groups", createQuestGroups.deepCopy());
        JsonObject tasks = new JsonObject();
        createQuestTasks.forEach(task -> tasks.add(task.id, task.source.deepCopy()));
        draft.add("tasks", tasks);
        JsonObject rewards = new JsonObject();
        createQuestRewards.forEach(reward -> rewards.add(reward.id, reward.source.deepCopy()));
        draft.add("rewards", rewards);
        return draft;
    }

    private boolean hasUnsavedDraft() {
        if (hasUnsavedModal()) return true;
        return createQuestDockOpen && draftBaseline != null && !draftBaseline.equals(draftSnapshot());
    }

    private boolean hasUnsavedModal() {
        if (hasUnsavedChapterEditor()) return true;
        if (editingTask != null && editingTaskIndex >= 0 && editingTaskIndex < createQuestTasks.size() &&
            !editingTask.sameAs(createQuestTasks.get(editingTaskIndex))) return true;
        if (editingReward != null && editingRewardIndex >= 0 && editingRewardIndex < createQuestRewards.size() &&
            !editingReward.sameAs(createQuestRewards.get(editingRewardIndex))) return true;
        if (editingNestedReward != null) {
            List<DraftReward> nested = nestedRewards(editingReward);
            if (editingNestedRewardIndex >= 0 && editingNestedRewardIndex < nested.size() &&
                !editingNestedReward.sameAs(nested.get(editingNestedRewardIndex))) return true;
        }
        return false;
    }

    private void requestDiscard(Runnable action) {
        if (mutations.isPending()) return;
        if (!hasUnsavedDraft()) {
            action.run();
            return;
        }
        discardAction = action;
        discardConfirmation = true;
        rebuildWidgets();
    }

    private void requestModalDiscard(Runnable action) {
        if (!hasUnsavedModal()) {
            action.run();
            return;
        }
        discardAction = action;
        discardConfirmation = true;
        rebuildWidgets();
    }

    private void closeDraft() {
        createQuestDockOpen = false;
        editingExistingQuest = false;
        originalQuestId = null;
        draftBaseline = null;
        mutations.cancel();
    }

    public void handleEditorResult(QuestNetwork.EditorResultPayload result) {
        QuestMutationCoordinator.Completion completion = mutations.complete(result.requestId(), result.success(), result.message());
        if (completion == null) return;
        String operation = completion.pending().operation();
        diagnostics = QuestDiagnostics.decode(result.diagnostics());
        diagnosticsScroll = 0;
        editorMessage = result.message();
        editorMessageSuccess = result.success();
        diagnosticsFromImport = false;
        if (result.success()) {
            if (clipboardMutationPending) CLIPBOARD.clear();
            clipboardMutationPending = false;
            importController.clear();
            if (List.of("create_quest", "update_quest", "delete_quest", "paste_quest", "import_quests").contains(operation)) {
                createQuestDockOpen = false;
                editingExistingQuest = false;
                originalQuestId = null;
                draftBaseline = null;
                editorTool = EditorTool.SELECT;
            }
        }
        if (!result.success()) clipboardMutationPending = false;
        if (!result.success() && "import_quests".equals(operation)) {
            importController.applyServerDiagnostics(diagnostics);
            diagnostics = List.of();
            modalHost.open(QuestModalHost.Modal.FILE_IMPORT);
            importScroll = 0;
        }
        if (!result.success() && "chapter_action".equals(operation)) chapterEditorOpen = true;
        if (!result.success() && "remove_quest_group".equals(operation)) createQuestDockOpen = true;
        if (modalHost.is(QuestModalHost.Modal.DIAGNOSTICS)) closeDiagnosticsModal();
        rebuildWidgets();
    }

    private static boolean canEdit() {
        return Minecraft.getInstance().player != null &&
            Commands.LEVEL_GAMEMASTERS.check(Minecraft.getInstance().player.permissions());
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
        ChapterDisplay chapterDisplay = chapterDisplays.get(group);
        if (chapterDisplay != null && !chapterDisplay.background.isBlank()) {
            try {
                Identifier texture = Identifier.parse(chapterDisplay.background);
                int backgroundX = sidebarWidth();
                int backgroundWidth = Math.max(1, canvasRight() - backgroundX);
                int backgroundY = canvasTop();
                int backgroundHeight = Math.max(1, height - backgroundY);
                graphics.blit(
                    RenderPipelines.GUI_TEXTURED,
                    texture,
                    backgroundX,
                    backgroundY,
                    0.0f,
                    0.0f,
                    backgroundWidth,
                    backgroundHeight,
                    backgroundWidth,
                    backgroundHeight,
                    0x99FFFFFF
                );
            } catch (RuntimeException ignored) { }
        }
        graphics.enableScissor(0, canvasTop(), width, height);
        graphics.pose().pushMatrix();
        graphics.pose().translate(treeCenterX(), treeCenterY());
        graphics.pose().scale((float) graph.zoom());
        graphics.pose().translate(-treeCenterX(), -treeCenterY());
        drawDependencyPaths(graphics);
        drawLinkPreview(graphics, toTreeX(mouseX), toTreeY(mouseY));
        drawQuestNodes(graphics, toTreeX(mouseX), toTreeY(mouseY));
        drawCreateQuestPreview(graphics);
        graphics.pose().popMatrix();
        graphics.disableScissor();
        drawPanelScrims(graphics);
        boolean rewardModal = editingReward != null || nestedRewardsOpen || editingNestedReward != null;
        boolean diagnosticsModal = modalHost.is(QuestModalHost.Modal.DIAGNOSTICS);
        boolean importModal = modalHost.is(QuestModalHost.Modal.FILE_IMPORT);
        boolean modalVisible = diagnosticsModal || importModal || editingTask != null || rewardModal || picker != Picker.NONE || deleteQuestConfirmation || discardConfirmation || taskDeleteConfirmation >= 0 || chapterEditorOpen || pasteIdPrompt;
        if (modalVisible) {
            drawBaseForeground(graphics, mouseX, mouseY);
            if (diagnosticsModal) {
                drawDiagnosticsModal(graphics);
            } else if (importModal) {
                drawImportModal(graphics);
            } else if (editingTask != null) {
                drawTaskEditorPanel(graphics);
                if (picker != Picker.NONE) drawTaskEditorForeground(graphics);
            }
            if (rewardModal) {
                drawRewardEditorPanel(graphics);
                if (picker != Picker.NONE) drawRewardModalForeground(graphics, mouseX, mouseY);
            }
            if (deleteQuestConfirmation) drawDeleteQuestConfirmation(graphics);
            if (discardConfirmation) drawDiscardConfirmation(graphics);
            if (taskDeleteConfirmation >= 0) drawDeleteTaskConfirmation(graphics);
            if (chapterEditorOpen) drawChapterEditor(graphics);
            if (pasteIdPrompt) drawPasteIdPrompt(graphics);
            if (picker != Picker.NONE) drawPickerPanel(graphics);
            super.extractRenderState(graphics, mouseX, mouseY, partialTick);
            if (picker == Picker.NONE) {
                if (editingTask != null) drawTaskEditorForeground(graphics);
                if (rewardModal) drawRewardModalForeground(graphics, mouseX, mouseY);
            } else {
                drawPickerContents(graphics, mouseX, mouseY);
            }
            return;
        }
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        drawBaseForeground(graphics, mouseX, mouseY);
    }

    private void drawBaseForeground(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (!editorMessage.isEmpty() && !createQuestDockOpen) {
            HeaderLayout header = headerLayout();
            int x = sidebarWidth() + 8;
            int right = Math.max(x + 1, canvasRight() - 4);
            graphics.fill(x - 4, header.statusY() - 3, right, header.statusY() + HEADER_ROW_HEIGHT, 0xAA20242B);
            graphics.enableScissor(x, header.statusY() - 2, right, header.statusY() + HEADER_ROW_HEIGHT);
            drawClippedText(
                graphics,
                editorMessage,
                x,
                header.statusY(),
                Math.max(1, right - x - 4),
                editorMessageSuccess ? 0xFF77DD99 : 0xFFFF9999
            );
            graphics.disableScissor();
        }
        if (sidebarOpen) graphics.text(
            font,
            Component.literal("Heracles"),
            8,
            4,
            0xFFFFFFFF,
            true
        );
        if (!editMode) {
            graphics.text(
                font,
                Component.literal(group),
                sidebarWidth() + 10,
                10,
                0xFFB8C0CC,
                false
            );
        }
        if (editMode && editorTool == EditorTool.LINK) {
            graphics.text(
                font,
                Component.literal(graph.linkSourceId() == null
                    ? "Link: select prerequisite"
                    : "Link: select dependent"),
                sidebarWidth() + 112,
                10,
                0xFF9FDFFF,
                false
            );
        }
        if (createQuestDockOpen) drawCreateQuestDock(graphics, mouseX, mouseY);
        else if (detailsOpen) drawDetails(graphics);
    }

    private void addDiagnosticsModalWidgets() {
        int left = (width - 440) / 2;
        int top = (height - 300) / 2;
        addRenderableWidget(Widgets.button(widget -> {
            widget.withPosition(left + 330, top + 264).withSize(96, 22);
            widget.withRenderer(WidgetRenderers.text(Component.literal("Close")));
            widget.withCallback(() -> {
                closeDiagnosticsModal();
                rebuildWidgets();
            });
        }));
    }

    private void closeDiagnosticsModal() {
        if (diagnosticsFromImport) {
            diagnosticsFromImport = false;
            modalHost.open(QuestModalHost.Modal.FILE_IMPORT);
        } else {
            modalHost.close();
        }
    }

    private void openImportDiagnostics(String key) {
        QuestImportController.Entry entry = importController.entries().stream()
            .filter(candidate -> candidate.key().equals(key))
            .findFirst()
            .orElse(null);
        if (entry == null || entry.diagnostics().isEmpty()) return;
        diagnostics = entry.diagnostics();
        diagnosticsScroll = 0;
        diagnosticsFromImport = true;
        modalHost.open(QuestModalHost.Modal.DIAGNOSTICS);
        rebuildWidgets();
    }

    private void openBatchDiagnostics() {
        if (importController.batchDiagnostics().isEmpty()) return;
        diagnostics = importController.batchDiagnostics();
        diagnosticsScroll = 0;
        diagnosticsFromImport = true;
        modalHost.open(QuestModalHost.Modal.DIAGNOSTICS);
        rebuildWidgets();
    }

    private List<String> diagnosticLines(int maxWidth) {
        List<String> lines = new ArrayList<>();
        for (QuestDiagnostics.Diagnostic diagnostic : diagnostics) {
            String prefix = "[" + diagnostic.severity() + "] "
                + (diagnostic.questId() == null || diagnostic.questId().isBlank() ? "" : diagnostic.questId() + " ")
                + diagnostic.path();
            addWrappedDiagnosticLine(lines, prefix, maxWidth);
            addWrappedDiagnosticLine(lines, diagnostic.message(), maxWidth);
            if (diagnostic.suggestedFix() != null && !diagnostic.suggestedFix().isBlank()) {
                addWrappedDiagnosticLine(lines, "Fix: " + diagnostic.suggestedFix(), maxWidth);
            }
        }
        return List.copyOf(lines);
    }

    private int importVisibleRows() {
        return importController.batchDiagnostics().isEmpty() ? 8 : 7;
    }

    private void addWrappedDiagnosticLine(List<String> lines, String value, int maxWidth) {
        String remaining = value == null ? "" : value;
        if (remaining.isEmpty()) {
            lines.add("");
            return;
        }
        while (!remaining.isEmpty()) {
            String line = font.plainSubstrByWidth(remaining, maxWidth);
            if (line.isEmpty()) line = remaining.substring(0, 1);
            lines.add(line);
            remaining = remaining.substring(line.length()).stripLeading();
        }
    }

    private void drawDiagnosticsModal(GuiGraphicsExtractor graphics) {
        int left = (width - 440) / 2;
        int top = (height - 300) / 2;
        graphics.fill(0, 0, width, height, 0x99000000);
        graphics.fill(left, top, left + 440, top + 300, 0xFF20242B);
        graphics.fill(left + 1, top + 1, left + 439, top + 28, 0xFF303640);
        graphics.text(font, Component.literal("Validation diagnostics"), left + 12, top + 9, 0xFFFFFFFF, true);
        int visibleRows = 13;
        List<String> lines = diagnosticLines(416);
        int maxScroll = Math.max(0, lines.size() - visibleRows);
        diagnosticsScroll = Math.max(0, Math.min(maxScroll, diagnosticsScroll));
        graphics.enableScissor(left + 8, top + 34, left + 432, top + 254);
        for (int index = diagnosticsScroll; index < lines.size() && index < diagnosticsScroll + visibleRows; index++) {
            int y = top + 38 + (index - diagnosticsScroll) * 16;
            String line = lines.get(index);
            int color = line.startsWith("[ERROR]") ? 0xFFFF9999
                : line.startsWith("[WARNING]") ? 0xFFFFD27D
                : line.startsWith("Fix:") ? 0xFF9FDFFF : 0xFFB8C0CC;
            graphics.text(font, Component.literal(line), left + 12, y, color, false);
        }
        graphics.disableScissor();
        if (diagnostics.isEmpty()) graphics.text(font, Component.literal("No diagnostics reported."), left + 12, top + 42, 0xFFB8C0CC, false);
        else if (maxScroll > 0) graphics.text(font, Component.literal("Scroll for more"), left + 12, top + 270, 0xFF8893A3, false);
    }

    private void addImportModalWidgets() {
        int left = (width - 500) / 2;
        int top = (height - 340) / 2;
        importIdFields.clear();
        List<QuestImportController.Entry> entries = importController.entries();
        int visibleRows = importVisibleRows();
        int first = Math.max(0, Math.min(importScroll, Math.max(0, entries.size() - visibleRows)));
        int listTop = top + (importController.batchDiagnostics().isEmpty() ? 52 : 64);
        for (int index = first; index < entries.size() && index < first + visibleRows; index++) {
            QuestImportController.Entry entry = entries.get(index);
            int y = listTop + (index - first) * 32;
            EditBox id = new EditBox(font, left + 250, y, 100, 18, Component.literal("Quest ID"));
            id.setValue(entry.id() == null ? "" : entry.id());
            id.setResponder(value -> {
                importController.changeId(entry.key(), value);
                updateImportMessage();
            });
            importIdFields.put(entry.key(), id);
            addRenderableWidget(id);
            addRenderableWidget(Widgets.button(widget -> {
                widget.withPosition(left + 354, y).withSize(62, 20);
                widget.withRenderer(WidgetRenderers.text(Component.literal("Details")));
                widget.active = !entry.diagnostics().isEmpty();
                widget.withCallback(() -> openImportDiagnostics(entry.key()));
                widget.withTooltip(Component.literal("View every diagnostic for this file"));
            }));
            addRenderableWidget(Widgets.button(widget -> {
                widget.withPosition(left + 420, y).withSize(62, 20);
                widget.withRenderer(WidgetRenderers.text(Component.literal("Remove")));
                widget.withCallback(() -> removeImportFile(entry.key()));
            }));
        }
        addRenderableWidget(Widgets.button(widget -> {
            widget.withPosition(left + 12, top + 304).withSize(100, 22);
            widget.withRenderer(WidgetRenderers.text(Component.literal("Cancel")));
            widget.withCallback(this::cancelImport);
        }));
        if (!importController.batchDiagnostics().isEmpty()) addRenderableWidget(Widgets.button(widget -> {
            widget.withPosition(left + 120, top + 304).withSize(120, 22);
            widget.withRenderer(WidgetRenderers.text(Component.literal("Batch details")));
            widget.withCallback(this::openBatchDiagnostics);
            widget.withTooltip(Component.literal("View batch-level server diagnostics"));
        }));
        addRenderableWidget(Widgets.button(widget -> {
            widget.withPosition(left + 388, top + 304).withSize(100, 22);
            widget.withRenderer(WidgetRenderers.text(Component.literal("Import")));
            widget.active = importController.canSubmit() && !mutations.isPending();
            widget.withCallback(this::sendImport);
        }));
    }

    private void drawImportModal(GuiGraphicsExtractor graphics) {
        int left = (width - 500) / 2;
        int top = (height - 340) / 2;
        graphics.fill(0, 0, width, height, 0x99000000);
        graphics.fill(left, top, left + 500, top + 340, 0xFF20242B);
        graphics.fill(left + 1, top + 1, left + 499, top + 28, 0xFF303640);
        graphics.text(font, Component.literal("Import quests"), left + 12, top + 9, 0xFFFFFFFF, true);
        graphics.text(font, Component.literal("Each file is checked independently; Import is all-or-nothing."), left + 12, top + 30, 0xFFB8C0CC, false);
        if (!importController.batchDiagnostics().isEmpty()) {
            long errors = importController.batchDiagnostics().stream().filter(QuestDiagnostics.Diagnostic::blocksSave).count();
            graphics.text(font, Component.literal("Batch rejected: " + errors + " error(s) — Batch details"), left + 12, top + 42, 0xFFFF9999, false);
        }
        List<QuestImportController.Entry> entries = importController.entries();
        int listTop = top + (importController.batchDiagnostics().isEmpty() ? 52 : 64);
        int visibleRows = importVisibleRows();
        int first = Math.max(0, Math.min(importScroll, Math.max(0, entries.size() - visibleRows)));
        graphics.enableScissor(left + 8, listTop - 4, left + 492, top + 292);
        for (int index = first; index < entries.size() && index < first + visibleRows; index++) {
            QuestImportController.Entry entry = entries.get(index);
            int y = listTop + 4 + (index - first) * 32;
            int color = entry.valid() ? 0xFF77DD99 : 0xFFFF9999;
            String label = entry.key() + " (" + entry.source().getBytes(java.nio.charset.StandardCharsets.UTF_8).length + " bytes)";
            if (label.length() > 42) label = label.substring(0, 41) + "…";
            graphics.text(font, Component.literal(label), left + 12, y, 0xFFFFFFFF, false);
            long errors = entry.diagnostics().stream().filter(QuestDiagnostics.Diagnostic::blocksSave).count();
            long warnings = entry.diagnostics().stream().filter(diagnostic -> diagnostic.severity() == QuestDiagnostics.Severity.WARNING).count();
            String detail = entry.diagnostics().isEmpty() ? "ready" : errors + " error(s), " + warnings + " warning(s) — Details";
            if (detail.length() > 42) detail = detail.substring(0, 41) + "…";
            graphics.text(font, Component.literal(detail), left + 12, y + 14, color, false);
        }
        graphics.disableScissor();
    }

    private void cancelImport() {
        importController.clear();
        modalHost.close();
        importIdFields.clear();
        rebuildWidgets();
    }

    private void drawPanelScrims(GuiGraphicsExtractor graphics) {
        int sidebarWidth = sidebarWidth();
        graphics.fill(0, 0, sidebarWidth, height, 0xF020242B);
        graphics.verticalLine(sidebarWidth, 0, height, 0xFF49515E);
        if (detailsOpen || createQuestDockOpen) {
            int detailsLeft = width - detailsWidth();
            graphics.fill(detailsLeft, 0, width, height, 0xF020242B);
            graphics.verticalLine(detailsLeft, 0, height, 0xFF49515E);
        }
    }

    private void drawCreateQuestDock(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int x = width - detailsWidth() + 12;
        if (createQuestTab == DetailTab.OVERVIEW) {
        } else if (createQuestTab == DetailTab.TASKS) {
            drawDraftTasks(graphics);
        } else if (createQuestTab == DetailTab.REWARDS) {
            drawDraftRewards(graphics);
        } else {
            graphics.textWithWordWrap(
                font,
                Component.literal(createQuestTab.label + " will be implemented in a later editor iteration."),
                x,
                42,
                detailsWidth() - 24,
                0xFFB8C0CC,
                false
            );
        }
        if (createQuestTab == DetailTab.TASKS && taskChooserOpen) {
            drawTaskChooser(graphics, mouseX, mouseY);
        }
        if (createQuestTab == DetailTab.REWARDS && rewardChooserOpen) {
            drawRewardChooser(graphics, mouseX, mouseY, false);
        }
        if (!editorMessage.isEmpty()) {
            graphics.textWithWordWrap(
                font,
                Component.literal(editorMessage),
                x,
                height - 48,
                detailsWidth() - 24,
                editorMessageSuccess ? 0xFF77DD99 : 0xFFFF9999,
                false
            );
        }
    }

    private void drawDraftRewards(GuiGraphicsExtractor graphics) {
        int x = width - detailsWidth() + 12;
        int cardWidth = detailsWidth() - 24;
        int y = 43;
        int end = Math.min(createQuestRewards.size(), createRewardScroll + rewardListCapacity());
        for (int index = createRewardScroll; index < end; index++) {
            int cardY = y + (index - createRewardScroll) * 48;
            DraftReward reward = createQuestRewards.get(index);
            graphics.fill(x, cardY, x + cardWidth - 63, cardY + 42, 0xFF303640);
            graphics.outline(x, cardY, cardWidth, 42, 0xFF59616E);
            graphics.item(new ItemStack(reward.displayIcon()), x + 7, cardY + 13);
            drawClippedText(graphics, reward.displayLabel(), x + 29, cardY + 9, cardWidth - 108, reward.isSupported() ? 0xFFFFFFFF : 0xFFFFAA77);
            drawClippedText(graphics, reward.id, x + 29, cardY + 23, cardWidth - 108, 0xFF8E98A6);
        }
        if (createQuestRewards.isEmpty()) graphics.text(font, Component.literal("No rewards yet"), x, 34, 0xFF8E98A6, false);
    }

    private void drawRewardChooser(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean nested) {
        int left = nested ? rewardEditorLeft() + 24 : width - detailsWidth() + 16;
        int top = nested ? rewardEditorTop() + 70 : 47;
        int chooserWidth = nested ? 252 : detailsWidth() - 32;
        List<RewardChoice> choices = nested
            ? REWARD_CHOICES.stream().filter(choice -> !choice.type.equals("heracles:selectable")).toList()
            : REWARD_CHOICES;
        int chooserHeight = choices.size() * TASK_CHOOSER_ROW_HEIGHT + 4;
        graphics.fill(left, top, left + chooserWidth, top + chooserHeight, 0xFF20242B);
        graphics.outline(left, top, chooserWidth, chooserHeight, 0xFF8A929F);
        for (int index = 0; index < choices.size(); index++) {
            RewardChoice choice = choices.get(index);
            int rowY = top + 2 + index * TASK_CHOOSER_ROW_HEIGHT;
            boolean hovered = mouseX >= left + 2 && mouseX < left + chooserWidth - 2 && mouseY >= rowY && mouseY < rowY + TASK_CHOOSER_ROW_HEIGHT - 1;
            if (hovered) graphics.fill(left + 2, rowY, left + chooserWidth - 2, rowY + TASK_CHOOSER_ROW_HEIGHT - 1, 0xFF454C58);
            graphics.item(new ItemStack(choice.icon), left + 4, rowY + 5);
            graphics.text(font, Component.literal(choice.label), left + 24, rowY + 9, 0xFFFFFFFF, false);
        }
    }

    private void drawDraftTasks(GuiGraphicsExtractor graphics) {
        int x = width - detailsWidth() + 12;
        int cardWidth = detailsWidth() - 24;
        int y = 43;
        int end = Math.min(createQuestTasks.size(), createTaskScroll + taskListCapacity());
        for (int index = createTaskScroll; index < end; index++) {
            int cardY = y + (index - createTaskScroll) * 48;
            DraftTask task = createQuestTasks.get(index);
            graphics.fill(x, cardY, x + cardWidth - 63, cardY + 42, 0xFF303640);
            graphics.outline(x, cardY, cardWidth, 42, 0xFF59616E);
            graphics.item(new ItemStack(task.displayIcon()), x + 7, cardY + 13);
            drawClippedText(graphics, task.displayLabel(), x + 29, cardY + 9, cardWidth - 108, task.isSupported() ? 0xFFFFFFFF : 0xFFFFAA77);
            drawClippedText(graphics, task.id, x + 29, cardY + 23, cardWidth - 108, 0xFF8E98A6);
        }
        if (createQuestTasks.isEmpty()) {
            graphics.text(
                font,
                Component.literal("No tasks yet"),
                x,
                34,
                0xFF8E98A6,
                false
            );
        }
    }

    private void drawTaskChooser(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int left = width - detailsWidth() + 16;
        int top = 47;
        int chooserWidth = detailsWidth() - 32;
        int visibleCount = Math.min(TASK_CHOOSER_VISIBLE, TASK_CHOICES.size() - taskChooserScroll);
        int chooserHeight = visibleCount * TASK_CHOOSER_ROW_HEIGHT + 4;
        graphics.fill(left, top, left + chooserWidth, top + chooserHeight, 0xFF20242B);
        graphics.outline(left, top, chooserWidth, chooserHeight, 0xFF8A929F);
        for (int visible = 0; visible < visibleCount; visible++) {
            TaskChoice choice = TASK_CHOICES.get(taskChooserScroll + visible);
            int rowY = top + 2 + visible * TASK_CHOOSER_ROW_HEIGHT;
            boolean hovered = mouseX >= left + 2 && mouseX < left + chooserWidth - 2 &&
                mouseY >= rowY && mouseY < rowY + TASK_CHOOSER_ROW_HEIGHT - 1;
            if (hovered) graphics.fill(left + 2, rowY, left + chooserWidth - 2, rowY + TASK_CHOOSER_ROW_HEIGHT - 1, 0xFF454C58);
            graphics.item(new ItemStack(choice.icon), left + 4, rowY + 5);
            graphics.text(
                font,
                Component.literal(choice.label),
                left + 24,
                rowY + (choice.implemented ? 9 : 3),
                choice.implemented ? 0xFFFFFFFF : 0xFF9AA2AE,
                false
            );
            if (!choice.implemented) graphics.text(
                font,
                Component.literal("Not yet implemented"),
                left + 24,
                rowY + 14,
                0xFF707987,
                false
            );
        }
    }

    private void drawCreateQuestPreview(GuiGraphicsExtractor graphics) {
        if (!editMode || !createQuestDockOpen) return;
        int x = treeCenterX() + graph.panX() + createQuestX - NODE_WIDTH / 2;
        int y = treeCenterY() + graph.panY() + createQuestY - NODE_HEIGHT / 2;
        graphics.blit(
            RenderPipelines.GUI_TEXTURED,
            DEFAULT_QUEST_FRAME,
            x,
            y,
            NODE_WIDTH,
            0.0f,
            NODE_WIDTH,
            NODE_HEIGHT,
            NODE_WIDTH * 5,
            NODE_HEIGHT,
            0xCCFFFFFF
        );
        Item previewItem = BuiltInRegistries.ITEM.getValue(Identifier.parse(createQuestIcon));
        graphics.item(new ItemStack(previewItem == null ? Items.MAP : previewItem), x + 4, y + 4);
        graphics.outline(x - 2, y - 2, NODE_WIDTH + 4, NODE_HEIGHT + 4, 0x99FFD966);
    }

    private void drawPickerPanel(GuiGraphicsExtractor graphics) {
        graphics.fill(0, 0, width, height, 0x88000000);
        int left = pickerLeft();
        int top = pickerTop();
        graphics.fill(left, top, left + 200, top + 176, 0xFF20242B);
        graphics.outline(left, top, 200, 176, 0xFF8A929F);
        graphics.text(
            font,
            Component.literal(switch (picker) {
                case ICON -> "Choose item";
                case ENTITY -> "Choose entity";
                default -> "Choose background";
            }),
            left + 12,
            top + 10,
            0xFFFFFFFF,
            true
        );
    }

    private void drawPickerContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (picker == Picker.ICON) drawItemPicker(graphics, mouseX, mouseY);
        else if (picker == Picker.ENTITY) drawEntityPicker(graphics, mouseX, mouseY);
        else drawBackgroundPicker(graphics, mouseX, mouseY);
    }

    private void drawTaskEditorPanel(GuiGraphicsExtractor graphics) {
        graphics.fill(0, 0, width, height, 0x88000000);
        int left = taskEditorLeft();
        int top = taskEditorTop();
        graphics.fill(left, top, left + 260, top + 300, 0xFF20242B);
        graphics.outline(left, top, 260, 300, 0xFF8A929F);
    }

    private void drawRewardEditorPanel(GuiGraphicsExtractor graphics) {
        graphics.fill(0, 0, width, height, 0x88000000);
        int left = rewardEditorLeft();
        int top = rewardEditorTop();
        graphics.fill(left, top, left + 300, top + 280, 0xFF20242B);
        graphics.outline(left, top, 300, 280, 0xFF8A929F);
    }

    private void drawDeleteQuestConfirmation(GuiGraphicsExtractor graphics) {
        graphics.fill(0, 0, width, height, 0x88000000);
        int left = (width - 240) / 2;
        int top = (height - 110) / 2;
        graphics.fill(left, top, left + 240, top + 110, 0xFF20242B);
        graphics.outline(left, top, 240, 110, 0xFF8A929F);
        graphics.text(font, Component.literal("Delete quest?"), left + 12, top + 12, 0xFFFFFFFF, true);
        graphics.textWithWordWrap(font, Component.literal("This deletes the quest file and resets its player progress."), left + 12, top + 32, 216, 0xFFFFAAAA, false);
    }

    private void drawDeleteTaskConfirmation(GuiGraphicsExtractor graphics) {
        graphics.fill(0, 0, width, height, 0x88000000);
        int left = (width - 240) / 2;
        int top = (height - 110) / 2;
        graphics.fill(left, top, left + 240, top + 110, 0xFF20242B);
        graphics.outline(left, top, 240, 110, 0xFF8A929F);
        graphics.text(font, Component.literal("Delete task?"), left + 12, top + 12, 0xFFFFFFFF, true);
        String id = taskDeleteConfirmation >= 0 && taskDeleteConfirmation < createQuestTasks.size()
            ? createQuestTasks.get(taskDeleteConfirmation).id : "this task";
        graphics.textWithWordWrap(font, Component.literal("Delete '" + id + "' and its configuration?"), left + 12, top + 34, 216, 0xFFFFAAAA, false);
    }

    private void drawDiscardConfirmation(GuiGraphicsExtractor graphics) {
        graphics.fill(0, 0, width, height, 0x88000000);
        int left = (width - 260) / 2;
        int top = (height - 116) / 2;
        graphics.fill(left, top, left + 260, top + 116, 0xFF20242B);
        graphics.outline(left, top, 260, 116, 0xFF8A929F);
        graphics.text(font, Component.literal("Discard unsaved changes?"), left + 12, top + 12, 0xFFFFFFFF, true);
        graphics.textWithWordWrap(font, Component.literal("The quest draft has changes that have not been saved."), left + 12, top + 34, 236, 0xFFFFCC88, false);
    }

    private void drawChapterEditor(GuiGraphicsExtractor graphics) {
        graphics.fill(0, 0, width, height, 0x88000000);
        int left = (width - 280) / 2;
        int top = (height - 210) / 2;
        graphics.fill(left, top, left + 280, top + 210, 0xFF20242B);
        graphics.outline(left, top, 280, 210, 0xFF8A929F);
        graphics.text(font, Component.literal(chapterEditorOriginal == null ? "Create chapter" : "Edit chapter"), left + 14, top + 14, 0xFFFFFFFF, true);
        graphics.text(font, Component.literal("Name"), left + 14, top + 36, 0xFFB8C0CC, false);
        graphics.text(font, Component.literal("Chapter icon"), left + 56, top + 88, 0xFFB8C0CC, false);
        graphics.text(font, Component.literal("Background"), left + 14, top + 114, 0xFFB8C0CC, false);
        if (!chapterEditorError.isEmpty()) graphics.text(font, Component.literal(chapterEditorError), left + 14, top + 151, 0xFFFF7777, false);
    }

    private void drawPasteIdPrompt(GuiGraphicsExtractor graphics) {
        int left = (width - 280) / 2;
        int top = (height - 130) / 2;
        graphics.fill(0, 0, width, height, 0x88000000);
        graphics.fill(left, top, left + 280, top + 130, 0xFF20242B);
        graphics.outline(left, top, 280, 130, 0xFF8A929F);
        graphics.text(font, Component.literal("Paste quest"), left + 14, top + 14, 0xFFFFFFFF, true);
        graphics.text(font, Component.literal("Choose the ID for the cloned quest."), left + 14, top + 34, 0xFFB8C0CC, false);
    }

    private void drawRewardModalForeground(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (editingNestedReward != null) drawRewardEditorForeground(graphics, editingNestedReward, true);
        else if (nestedRewardsOpen) drawNestedRewardsForeground(graphics, mouseX, mouseY);
        else drawRewardEditorForeground(graphics, editingReward, false);
    }

    private void drawRewardEditorForeground(GuiGraphicsExtractor graphics, DraftReward reward, boolean nested) {
        int left = rewardEditorLeft();
        int top = rewardEditorTop();
        RewardChoice choice = reward.choice();
        graphics.item(new ItemStack(choice.icon), left + 14, top + 10);
        graphics.text(font, Component.literal((nested ? "Edit choice: " : "Edit ") + choice.label), left + 36, top + 14, 0xFFFFFFFF, true);
        graphics.text(font, Component.literal("ID"), left + 14, top + 27, 0xFFB8C0CC, false);
        graphics.text(font, Component.literal("Title override"), left + 14, top + 59, 0xFFB8C0CC, false);
        graphics.text(font, Component.literal("Icon override"), left + 84, top + 108, 0xFFB8C0CC, false);
        ItemStack icon = rewardEditorIcon(reward);
        if (!icon.isEmpty()) graphics.item(icon, left + 23, top + 105);
        switch (reward.type) {
            case "heracles:xp" -> {
                graphics.text(font, Component.literal("Amount"), left + 14, top + 131, 0xFFB8C0CC, false);
                graphics.text(font, Component.literal("Unit"), left + 114, top + 131, 0xFFB8C0CC, false);
            }
            case "heracles:item" -> {
                graphics.text(font, Component.literal("Item"), left + 14, top + 131, 0xFFB8C0CC, false);
                graphics.text(font, Component.literal("Amount"), left + 14, top + 170, 0xFFB8C0CC, false);
            }
            case "heracles:loottable" -> graphics.text(font, Component.literal("Loot table"), left + 14, top + 131, 0xFFB8C0CC, false);
            case "heracles:command" -> graphics.text(font, Component.literal("Command"), left + 14, top + 131, 0xFFB8C0CC, false);
            case "heracles:selectable" -> {
                graphics.text(font, Component.literal("Selection amount"), left + 14, top + 131, 0xFFB8C0CC, false);
                graphics.text(font, Component.literal("Nested rewards"), left + 114, top + 131, 0xFFB8C0CC, false);
            }
            default -> { }
        }
        if (!rewardEditorError.isEmpty()) graphics.textWithWordWrap(font, Component.literal(rewardEditorError), left + 14, top + 210, 272, 0xFFFF7777, false);
    }

    private void drawNestedRewardsForeground(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int left = rewardEditorLeft();
        int top = rewardEditorTop();
        graphics.item(new ItemStack(Items.BUNDLE), left + 14, top + 10);
        graphics.text(font, Component.literal("Selectable reward choices"), left + 36, top + 14, 0xFFFFFFFF, true);
        if (nestedRewards(editingReward).isEmpty()) graphics.text(font, Component.literal("No choices yet"), left + 14, top + 48, 0xFF8E98A6, false);
        if (nestedRewardChooserOpen) drawRewardChooser(graphics, mouseX, mouseY, true);
    }

    private void drawTaskEditorForeground(GuiGraphicsExtractor graphics) {
        int left = taskEditorLeft();
        int top = taskEditorTop();
        TaskChoice choice = editingTask.choice();
        graphics.item(new ItemStack(choice.icon), left + 14, top + 10);
        graphics.text(font, Component.literal("Edit " + choice.label), left + 36, top + 14, 0xFFFFFFFF, true);
        graphics.text(font, Component.literal("ID"), left + 14, top + 27, 0xFFB8C0CC, false);
        graphics.text(font, Component.literal("Title override"), left + 14, top + 59, 0xFFB8C0CC, false);
        graphics.text(font, Component.literal("Icon override"), left + 84, top + 108, 0xFFB8C0CC, false);
        ItemStack icon = taskEditorIcon();
        if (!icon.isEmpty()) graphics.item(icon, left + 23, top + 105);
        switch (editingTask.type) {
            case "heracles:dummy" -> {
                graphics.text(font, Component.literal("Trigger value"), left + 14, top + 131, 0xFFB8C0CC, false);
                graphics.text(font, Component.literal("Description"), left + 14, top + 170, 0xFFB8C0CC, false);
            }
            case "heracles:item" -> {
                graphics.text(font, Component.literal("Item or #tag"), left + 14, top + 131, 0xFFB8C0CC, false);
                graphics.text(font, Component.literal("Amount"), left + 14, top + 170, 0xFFB8C0CC, false);
                graphics.text(font, Component.literal("Collection"), left + 104, top + 170, 0xFFB8C0CC, false);
            }
            case "heracles:xp" -> {
                graphics.text(font, Component.literal("Amount"), left + 14, top + 131, 0xFFB8C0CC, false);
                graphics.text(font, Component.literal("Unit"), left + 104, top + 131, 0xFFB8C0CC, false);
                graphics.text(font, Component.literal("Collection"), left + 14, top + 170, 0xFFB8C0CC, false);
            }
            case "heracles:kill_entity" -> {
                graphics.text(font, Component.literal("Entity"), left + 14, top + 131, 0xFFB8C0CC, false);
                graphics.text(font, Component.literal("Amount"), left + 14, top + 170, 0xFFB8C0CC, false);
            }
            case "heracles:advancement" -> taskFieldLabel(graphics, left, top, "Advancement IDs (comma separated)", null);
            case "heracles:biome" -> taskFieldLabel(graphics, left, top, "Biome or #tag", null);
            case "heracles:block_interaction" -> {
                taskFieldLabel(graphics, left, top, "Block or #tag", "Component/data predicate (JSON)");
                graphics.text(font, Component.literal("Block state predicate (JSON)"), left + 14, top + 209, 0xFFB8C0CC, false);
            }
            case "heracles:changed_dimension" -> {
                graphics.text(font, Component.literal("From dimension (optional)"), left + 14, top + 131, 0xFFB8C0CC, false);
                graphics.text(font, Component.literal("To dimension (optional)"), left + 136, top + 131, 0xFFB8C0CC, false);
            }
            case "heracles:check" -> taskFieldLabel(graphics, left, top, "Player data predicate (JSON)", null);
            case "heracles:composite" -> taskFieldLabel(graphics, left, top, "Required tasks", "Nested task map (JSON)");
            case "heracles:entity_interaction" -> taskFieldLabel(graphics, left, top, "Entity or #tag", "Component/data predicate (JSON)");
            case "heracles:item_interaction", "heracles:item_use" -> taskFieldLabel(graphics, left, top, "Item or #tag", "Component/data predicate (JSON)");
            case "heracles:location" -> taskFieldLabel(graphics, left, top, "Location predicate (JSON)", "Description");
            case "heracles:recipe" -> taskFieldLabel(graphics, left, top, "Recipe IDs (comma separated)", null);
            case "heracles:stat" -> {
                graphics.text(font, Component.literal("Statistic ID"), left + 14, top + 131, 0xFFB8C0CC, false);
                graphics.text(font, Component.literal("Target"), left + 170, top + 131, 0xFFB8C0CC, false);
            }
            case "heracles:structure" -> taskFieldLabel(graphics, left, top, "Structure or #tag", null);
            default -> {
            }
        }
        if (!taskEditorError.isEmpty()) graphics.textWithWordWrap(
            font,
            Component.literal(taskEditorError),
            left + 14,
            top + 245,
            232,
            0xFFFF7777,
            false
        );
    }

    private void taskFieldLabel(GuiGraphicsExtractor graphics, int left, int top, String first, String second) {
        graphics.text(font, Component.literal(first), left + 14, top + 131, 0xFFB8C0CC, false);
        if (second != null) graphics.text(font, Component.literal(second), left + 14, top + 170, 0xFFB8C0CC, false);
    }

    private void drawEntityPicker(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int left = pickerLeft() + 12;
        int top = pickerTop() + 56;
        List<EntityType<?>> entities = filteredPickerEntities();
        int end = Math.min(entities.size(), pickerScroll + 40);
        for (int index = pickerScroll; index < end; index++) {
            int visible = index - pickerScroll;
            int x = left + visible % 8 * 22;
            int y = top + visible / 8 * 22;
            EntityType<?> entity = entities.get(index);
            boolean hovered = mouseX >= x && mouseX < x + 20 && mouseY >= y && mouseY < y + 20;
            graphics.fill(x, y, x + 20, y + 20, hovered ? 0xFF59616E : 0xFF343A44);
            graphics.outline(x, y, 20, 20, 0xFF707987);
            graphics.item(entityIcon(entity), x + 2, y + 2);
            if (hovered) {
                Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(entity);
                String label = entity.getDescription().getString() + (id == null ? "" : " (" + id + ")");
                drawClippedText(graphics, label, pickerLeft() + 12, pickerTop() + 164, 176, 0xFFFFFFFF);
            }
        }
    }

    private void drawItemPicker(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int left = pickerLeft() + 12;
        int top = pickerTop() + 56;
        List<Item> items = filteredPickerItems();
        int end = Math.min(items.size(), pickerScroll + 40);
        for (int index = pickerScroll; index < end; index++) {
            int visible = index - pickerScroll;
            int x = left + visible % 8 * 22;
            int y = top + visible / 8 * 22;
            boolean hovered = mouseX >= x && mouseX < x + 20 && mouseY >= y && mouseY < y + 20;
            graphics.fill(x, y, x + 20, y + 20, hovered ? 0xFF59616E : 0xFF343A44);
            graphics.outline(x, y, 20, 20, 0xFF707987);
            ItemStack stack = new ItemStack(items.get(index));
            graphics.item(stack, x + 2, y + 2);
            if (hovered) {
                String label = items.get(index).getName(stack).getString() + " (" + BuiltInRegistries.ITEM.getKey(items.get(index)) + ")";
                drawClippedText(graphics, label, pickerLeft() + 12, pickerTop() + 164, 176, 0xFFFFFFFF);
            }
        }
    }

    private void drawBackgroundPicker(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int left = pickerLeft() + 12;
        int top = pickerTop() + 34;
        for (int index = 0; index < QUEST_BACKGROUNDS.size(); index++) {
            Identifier texture = QUEST_BACKGROUNDS.get(index);
            int x = left + index % 4 * 44;
            int y = top + index / 4 * 42;
            boolean hovered = mouseX >= x && mouseX < x + 40 && mouseY >= y && mouseY < y + 38;
            graphics.fill(x, y, x + 40, y + 38, hovered ? 0xFF59616E : 0xFF343A44);
            QuestBackground background = questBackground(texture.toString());
            graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                texture,
                x + (40 - background.width) / 2,
                y + (34 - background.height) / 2,
                0.0f,
                0.0f,
                background.width,
                background.height,
                background.width * 5,
                background.height,
                0xFFFFFFFF
            );
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

    private void drawLinkPreview(
        GuiGraphicsExtractor graphics,
        double mouseX,
        double mouseY
    ) {
        if (!editMode || editorTool != EditorTool.LINK || graph.linkSourceId() == null) return;
        NodeBounds source = nodeBounds.get(graph.linkSourceId());
        if (source == null) return;
        drawTexturedPath(
            graphics,
            new PathPoint(
                source.x + source.width / 2.0,
                source.y + source.height / 2.0
            ),
            new PathPoint(mouseX, mouseY),
            true
        );
    }

    private void drawQuestNodes(
        GuiGraphicsExtractor graphics,
        double mouseX,
        double mouseY
    ) {
        for (ClientQuest quest : visibleQuests()) {
            if (editingExistingQuest && createQuestDockOpen && quest.definition.id().equals(originalQuestId)) continue;
            NodeBounds bounds = nodeBounds.get(quest.definition.id());
            if (bounds == null) continue;
            QuestBackground background = questBackground(quest.definition);
            int frame = quest.claimed ? 3 : quest.complete ? 2 : quest.unlocked ? 1 : 0;
            graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                background.texture,
                bounds.x + background.xOffset,
                bounds.y + background.yOffset,
                frame * background.width,
                0.0f,
                background.width,
                background.height,
                background.width * 5,
                background.height,
                0xFFFFFFFF
            );
            if (bounds.contains(mouseX, mouseY)) {
                graphics.blit(
                    RenderPipelines.GUI_TEXTURED,
                    background.texture,
                    bounds.x + background.xOffset,
                    bounds.y + background.yOffset,
                    4 * background.width,
                    0.0f,
                    background.width,
                    background.height,
                    background.width * 5,
                    background.height,
                    0xFFFFFFFF
                );
            }
            if (quest.definition.id().equals(graph.selectedId())) {
                graphics.outline(
                    bounds.x - 2,
                    bounds.y - 2,
                    bounds.width + 4,
                    bounds.height + 4,
                    0xFFFFD966
                );
            }
            if (quest.definition.id().equals(graph.linkSourceId())) {
                graphics.outline(
                    bounds.x - 4,
                    bounds.y - 4,
                    bounds.width + 8,
                    bounds.height + 8,
                    0xFF6CCBFF
                );
            }
            graphics.item(
                QuestPresentation.questIcon(quest.definition),
                bounds.x + 4,
                bounds.y + 4
            );
        }
    }

    private static QuestBackground questBackground(QuestDefinition definition) {
        return questBackground(definition.display().iconBackground());
    }

    private static QuestBackground questBackground(String value) {
        try {
            Identifier texture = Identifier.parse(value);
            String path = texture.getPath();
            if (path.endsWith("/diamonds.png")) return new QuestBackground(texture, -4, -4, 32, 32);
            if (path.endsWith("/hearts.png")) return new QuestBackground(texture, -4, -2, 32, 32);
            if (path.endsWith("/pentagons.png")) return new QuestBackground(texture, 0, -2, 24, 24);
            return new QuestBackground(texture, 0, 0, NODE_WIDTH, NODE_HEIGHT);
        } catch (RuntimeException exception) {
            return new QuestBackground(DEFAULT_QUEST_FRAME, 0, 0, NODE_WIDTH, NODE_HEIGHT);
        }
    }

    private int pickerLeft() {
        return (width - 200) / 2;
    }

    private int pickerTop() {
        return (height - 176) / 2;
    }

    private int taskEditorLeft() {
        return (width - 260) / 2;
    }

    private int taskEditorTop() {
        return (height - 300) / 2;
    }

    private int rewardEditorLeft() {
        return (width - 300) / 2;
    }

    private int rewardEditorTop() {
        return (height - 280) / 2;
    }

    private List<Item> filteredPickerItems() {
        String query = pickerSearch == null
            ? ""
            : pickerSearch.getValue().trim().toLowerCase(java.util.Locale.ROOT);
        return BuiltInRegistries.ITEM.stream()
            .filter(item -> item != Items.AIR)
            .filter(item -> {
                Identifier id = BuiltInRegistries.ITEM.getKey(item);
                return query.isEmpty() ||
                    id.toString().contains(query) ||
                    item.getName(new ItemStack(item)).getString().toLowerCase(java.util.Locale.ROOT).contains(query);
            })
            .toList();
    }

    private List<EntityType<?>> filteredPickerEntities() {
        String query = pickerSearch == null
            ? ""
            : pickerSearch.getValue().trim().toLowerCase(java.util.Locale.ROOT);
        return BuiltInRegistries.ENTITY_TYPE.stream()
            .filter(entity -> {
                Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(entity);
                return query.isEmpty() || id.toString().contains(query) ||
                    entity.getDescription().getString().toLowerCase(java.util.Locale.ROOT).contains(query);
            })
            .sorted(Comparator.comparing(entity -> BuiltInRegistries.ENTITY_TYPE.getKey(entity).toString()))
            .toList();
    }

    private static ItemStack entityIcon(EntityType<?> entity) {
        return SpawnEggItem.byId(entity)
            .map(holder -> new ItemStack(holder.value()))
            .orElseGet(() -> new ItemStack(Items.ARMOR_STAND));
    }

    private ItemStack taskEditorIcon() {
        JsonObject icon = editingTask.source.has("icon") && editingTask.source.get("icon").isJsonObject()
            ? editingTask.source.getAsJsonObject("icon")
            : null;
        if (icon != null && icon.has("item")) {
            try {
                Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(icon.get("item").getAsString()));
                if (item != null && item != Items.AIR) return new ItemStack(item);
            } catch (RuntimeException ignored) { }
        }
        QuestDefinition.Task parsed = QuestDefinition.parse("editor", taskRoot(editingTask)).tasks().get(editingTask.id);
        return parsed == null ? new ItemStack(editingTask.choice().icon) : QuestPresentation.taskIcon(parsed);
    }

    private ItemStack rewardEditorIcon(DraftReward reward) {
        JsonObject icon = reward.source.has("icon") && reward.source.get("icon").isJsonObject()
            ? reward.source.getAsJsonObject("icon") : null;
        if (icon != null && icon.has("item")) {
            try {
                Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(icon.get("item").getAsString()));
                if (item != null && item != Items.AIR) return new ItemStack(item);
            } catch (RuntimeException ignored) { }
        }
        QuestDefinition.Reward parsed = QuestDefinition.parse("editor", rewardRoot(reward)).rewards().get(reward.id);
        return parsed == null ? new ItemStack(reward.choice().icon) : QuestPresentation.rewardIcon(parsed);
    }

    private static JsonObject taskRoot(DraftTask task) {
        JsonObject root = new JsonObject();
        root.addProperty("title", "Editor preview");
        JsonObject tasks = new JsonObject();
        tasks.add(task.id, task.source.deepCopy());
        root.add("tasks", tasks);
        return root;
    }

    private String validateTaskDraft(DraftTask task, int editedIndex) {
        if (task.id == null || !task.id.matches("[a-z0-9_.-]+")) return "ID may only contain lowercase letters, numbers, ., _, and -.";
        for (int index = 0; index < createQuestTasks.size(); index++) {
            if (index != editedIndex && createQuestTasks.get(index).id.equals(task.id)) return "Another task already uses this ID.";
        }
        String structuredError = normalizeStructuredTaskFields(task);
        if (!structuredError.isEmpty()) return structuredError;
        String registryError = RegistryValidation.validate("editor", taskRoot(task), QuestScreen::clientContainsRegistryTarget).stream()
            .filter(QuestDiagnostics.Diagnostic::blocksSave)
            .map(QuestDiagnostics.Diagnostic::message)
            .findFirst().orElse("");
        if (!registryError.isEmpty()) return registryError;
        if (task.type.equals("heracles:dummy") && jsonString(task.source, "value", "").isBlank()) return "Trigger value is required.";
        if (List.of("heracles:item", "heracles:xp", "heracles:kill_entity", "heracles:composite").contains(task.type)
            && jsonInt(task.source, "amount", 0) < 1) return "Amount must be at least 1.";
        if (task.type.equals("heracles:stat") && jsonInt(task.source, "target", 0) < 1) return "Target must be at least 1.";
        if (List.of("heracles:item", "heracles:item_interaction", "heracles:item_use").contains(task.type)) {
            String item = registryValueString(task.source, "item", "");
            if (!validIdentifier(item.startsWith("#") ? item.substring(1) : item)) return "Enter a valid item or #tag identifier.";
            if (!item.startsWith("#") && !registryContains(BuiltInRegistries.ITEM, item)) return "That item does not exist.";
        }
        if (List.of("heracles:kill_entity", "heracles:entity_interaction").contains(task.type)) {
            String entity = registryValueString(task.source, "entity", "");
            String id = entity.startsWith("#") ? entity.substring(1) : entity;
            if (!validIdentifier(id) || (!entity.startsWith("#") && !registryContains(BuiltInRegistries.ENTITY_TYPE, entity))) return "That entity does not exist.";
        }
        String identifierError = validateTaskIdentifiers(task);
        if (!identifierError.isEmpty()) return identifierError;
        return QuestDefinition.parse("editor", taskRoot(task)).issues().stream()
            .filter(issue -> issue.severity() == QuestDefinition.Severity.ERROR)
            .map(QuestDefinition.ValidationIssue::message)
            .findFirst().orElse("");
    }

    private static String normalizeStructuredTaskFields(DraftTask task) {
        List<String> keys = switch (task.type) {
            case "heracles:check", "heracles:entity_interaction",
                 "heracles:item_interaction", "heracles:item_use" -> List.of("components");
            case "heracles:block_interaction" -> List.of("components", "state");
            case "heracles:location" -> List.of("predicate");
            case "heracles:composite" -> List.of("tasks");
            default -> List.of();
        };
        for (String key : keys) {
            JsonElement value = task.source.get(key);
            if (value == null) continue;
            if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
                try {
                    value = JsonParser.parseString(value.getAsString());
                    task.source.add(key, value);
                } catch (RuntimeException exception) {
                    return friendly(key) + " must be valid JSON.";
                }
            }
            if (!value.isJsonObject()) return friendly(key) + " must be a JSON object.";
        }
        return "";
    }

    private static String validateTaskIdentifiers(DraftTask task) {
        List<String> scalarKeys = switch (task.type) {
            case "heracles:biome" -> List.of("biomes");
            case "heracles:block_interaction" -> List.of("block");
            case "heracles:changed_dimension" -> List.of("from", "to");
            case "heracles:stat" -> List.of("stat");
            case "heracles:structure" -> List.of("structures");
            default -> List.of();
        };
        for (String key : scalarKeys) {
            String value = registryValueString(task.source, key, "");
            if (task.type.equals("heracles:changed_dimension") && value.isBlank()) continue;
            String id = value.startsWith("#") ? value.substring(1) : value;
            if (!validIdentifier(id)) return friendly(key) + " must be a valid identifier" + (key.equals("from") || key.equals("to") ? " or blank." : ".");
        }
        for (String key : List.of("advancements", "recipes")) {
            if (!task.source.has(key)) continue;
            JsonElement values = task.source.get(key);
            if (!values.isJsonArray() || values.getAsJsonArray().isEmpty()) return friendly(key) + " must contain at least one identifier.";
            for (JsonElement value : values.getAsJsonArray()) {
                if (!value.isJsonPrimitive() || !validIdentifier(value.getAsString())) return friendly(key) + " contains an invalid identifier.";
            }
        }
        return "";
    }

    private String validateRewardDraft(DraftReward reward, boolean nested) {
        if (reward.id == null || !reward.id.matches("[a-z0-9_.-]+")) return "ID may only contain lowercase letters, numbers, ., _, and -.";
        List<DraftReward> peers = nested ? nestedRewards(editingReward) : createQuestRewards;
        int editedIndex = nested ? editingNestedRewardIndex : editingRewardIndex;
        for (int index = 0; index < peers.size(); index++) {
            if (index != editedIndex && peers.get(index).id.equals(reward.id)) return "Another reward already uses this ID.";
        }
        if (nested && reward.type.equals("heracles:selectable")) return "Selectable rewards cannot contain selectable rewards.";
        if (reward.type.equals("heracles:item")) {
            if (!registryContains(BuiltInRegistries.ITEM, rewardItemId(reward.source))) return "That item does not exist.";
            if (rewardItemCount(reward.source) < 1) return "Amount must be at least 1.";
        }
        if (reward.type.equals("heracles:xp") && jsonInt(reward.source, "amount", 0) < 1) return "Amount must be at least 1.";
        if (reward.type.equals("heracles:loottable") && !validIdentifier(jsonString(reward.source, "loot_table", ""))) return "Enter a valid loot table identifier.";
        if (reward.type.equals("heracles:command") && jsonString(reward.source, "command", "").isBlank()) return "Command is required.";
        if (reward.type.equals("heracles:selectable")) {
            List<DraftReward> choices = nestedRewards(reward);
            int amount = jsonInt(reward.source, "amount", 0);
            if (choices.isEmpty()) return "Add at least one selectable reward choice.";
            if (amount < 1 || amount > choices.size()) return "Selection amount must be between 1 and the number of choices.";
            if (choices.stream().anyMatch(choice -> choice.type.equals("heracles:selectable"))) return "Selectable rewards cannot contain selectable rewards.";
        }
        QuestDefinition parsed = QuestDefinition.parse("editor", rewardRoot(reward));
        return parsed.issues().stream()
            .filter(issue -> issue.severity() == QuestDefinition.Severity.ERROR)
            .map(QuestDefinition.ValidationIssue::message)
            .findFirst().orElse("");
    }

    private static JsonObject rewardRoot(DraftReward reward) {
        JsonObject root = new JsonObject();
        root.addProperty("title", "Editor preview");
        JsonObject rewards = new JsonObject();
        rewards.add(reward.id, reward.source.deepCopy());
        root.add("rewards", rewards);
        return root;
    }

    private static String rewardItemId(JsonObject source) {
        if (!source.has("item")) return "minecraft:stone";
        if (source.get("item").isJsonObject()) return jsonString(source.getAsJsonObject("item"), "id", "minecraft:stone");
        return source.get("item").getAsString();
    }

    private static int rewardItemCount(JsonObject source) {
        return source.has("item") && source.get("item").isJsonObject()
            ? jsonInt(source.getAsJsonObject("item"), "count", 1) : 1;
    }

    private static void setRewardItem(JsonObject source, String id, int count) {
        JsonObject item = new JsonObject();
        item.addProperty("id", id);
        item.addProperty("count", count);
        source.add("item", item);
    }

    private static List<DraftReward> nestedRewards(DraftReward parent) {
        List<DraftReward> rewards = new ArrayList<>();
        if (parent == null || !parent.source.has("rewards") || !parent.source.get("rewards").isJsonObject()) return rewards;
        parent.source.getAsJsonObject("rewards").entrySet().forEach(entry -> {
            if (entry.getValue().isJsonObject()) {
                JsonObject source = entry.getValue().getAsJsonObject();
                rewards.add(new DraftReward(entry.getKey(), jsonString(source, "type", "heracles:item"), source.deepCopy()));
            }
        });
        return rewards;
    }

    private static void setNestedRewards(DraftReward parent, List<DraftReward> rewards) {
        JsonObject object = new JsonObject();
        rewards.forEach(reward -> object.add(reward.id, reward.source.deepCopy()));
        parent.source.add("rewards", object);
    }

    private static boolean registryContains(net.minecraft.core.Registry<?> registry, String value) {
        try {
            return registry.containsKey(Identifier.parse(value));
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private static boolean clientContainsRegistryTarget(RegistryValidation.Target target, String value) {
        if (value == null || value.isBlank()) return true;
        boolean tag = value.startsWith("#");
        Identifier id = Identifier.tryParse(tag ? value.substring(1) : value);
        if (id == null) return false;
        try {
            return switch (target) {
                case ITEM -> tag ? registryTag(BuiltInRegistries.ITEM, Registries.ITEM, id) : BuiltInRegistries.ITEM.containsKey(id);
                case BLOCK -> tag ? registryTag(BuiltInRegistries.BLOCK, Registries.BLOCK, id) : BuiltInRegistries.BLOCK.containsKey(id);
                case ENTITY -> tag ? registryTag(BuiltInRegistries.ENTITY_TYPE, Registries.ENTITY_TYPE, id) : BuiltInRegistries.ENTITY_TYPE.containsKey(id);
                case BIOME -> Minecraft.getInstance().level == null || (tag ? registryTag(Minecraft.getInstance().level.registryAccess().lookupOrThrow(Registries.BIOME), Registries.BIOME, id) : Minecraft.getInstance().level.registryAccess().lookupOrThrow(Registries.BIOME).containsKey(id));
                case STRUCTURE -> Minecraft.getInstance().level == null || (tag ? registryTag(Minecraft.getInstance().level.registryAccess().lookupOrThrow(Registries.STRUCTURE), Registries.STRUCTURE, id) : Minecraft.getInstance().level.registryAccess().lookupOrThrow(Registries.STRUCTURE).containsKey(id));
                case DIMENSION -> Minecraft.getInstance().level == null
                    || Minecraft.getInstance().level.registryAccess().lookupOrThrow(Registries.DIMENSION).containsKey(id)
                    || Minecraft.getInstance().getConnection().levels().stream().anyMatch(key -> key.identifier().equals(id));
                default -> true;
            };
        } catch (RuntimeException exception) {
            return true;
        }
    }

    private static <T> boolean registryTag(Registry<T> registry, net.minecraft.resources.ResourceKey<? extends Registry<T>> key, Identifier id) {
        return registry.getTagOrEmpty(net.minecraft.tags.TagKey.create(key, id)).iterator().hasNext();
    }

    private static boolean validIdentifier(String value) {
        return value != null && value.matches("(?:[a-z0-9_.-]+:)?[a-z0-9/._-]+");
    }

    private static String jsonString(JsonObject object, String key, String fallback) {
        try {
            return object.has(key) ? object.get(key).getAsString() : fallback;
        } catch (RuntimeException exception) {
            return fallback;
        }
    }

    private static String registryValueString(JsonObject object, String key, String fallback) {
        if (!object.has(key)) return fallback;
        JsonElement value = object.get(key);
        if (value.isJsonPrimitive()) return value.getAsString();
        if (!value.isJsonObject()) return fallback;
        JsonObject registryValue = value.getAsJsonObject();
        if (registryValue.has("tag")) return "#" + jsonString(registryValue, "tag", "");
        return jsonString(registryValue, "id", fallback);
    }

    private static int jsonInt(JsonObject object, String key, int fallback) {
        try {
            return object.has(key) ? object.get(key).getAsInt() : fallback;
        } catch (RuntimeException exception) {
            return fallback;
        }
    }

    private static int parseInteger(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private static JsonArray stringArray(String values) {
        JsonArray array = new JsonArray();
        for (String value : values.split(",")) {
            String trimmed = value.trim();
            if (!trimmed.isEmpty()) array.add(trimmed);
        }
        return array;
    }

    private static String jsonStringList(JsonObject object, String key, String fallback) {
        if (!object.has(key)) return fallback;
        JsonElement value = object.get(key);
        if (value.isJsonArray()) {
            return value.getAsJsonArray().asList().stream()
                .filter(JsonElement::isJsonPrimitive)
                .map(JsonElement::getAsString)
                .collect(java.util.stream.Collectors.joining(", "));
        }
        return value.isJsonPrimitive() ? value.getAsString() : fallback;
    }

    private static JsonObject defaultLocationPredicate() {
        JsonObject predicate = new JsonObject();
        predicate.addProperty("dimension", "minecraft:overworld");
        return predicate;
    }

    private static void setOptionalString(JsonObject object, String key, String value) {
        if (value == null || value.isBlank()) object.remove(key);
        else object.addProperty(key, value);
    }

    private static String friendly(String value) {
        String text = value.replace('_', ' ');
        return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    private static String unsupportedReason(EditorTypeRegistry.Kind kind, String type) {
        EditorTypeRegistry.Descriptor descriptor = EDITOR_TYPES.resolve(kind, type);
        return descriptor.availabilityReason().isBlank()
            ? "Unsupported " + kind.name().toLowerCase(java.util.Locale.ROOT) + " type is preserved read-only"
            : descriptor.availabilityReason() + ": " + type;
    }

    private void drawClippedText(GuiGraphicsExtractor graphics, String value, int x, int y, int maxWidth, int color) {
        String text = value == null ? "" : value;
        if (maxWidth <= 0) return;
        if (font.width(text) > maxWidth) {
            text = font.plainSubstrByWidth(text, Math.max(0, maxWidth - font.width("…"))) + "…";
        }
        graphics.text(font, Component.literal(text), x, y, color, false);
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
        result.addAll(chapters);
        quests.forEach(quest ->
            result.addAll(quest.definition.display().groups().keySet())
        );
        return result;
    }

    private ClientQuest selected() {
        return quests
            .stream()
            .filter(quest -> quest.definition.id().equals(graph.selectedId()))
            .findFirst()
            .orElse(null);
    }

    private int canvasRight() {
        return detailsOpen || createQuestDockOpen
            ? width - detailsWidth()
            : width;
    }

    private int canvasTop() {
        return headerLayout().canvasTop();
    }

    private int treeCenterX() {
        return (sidebarWidth() + width - detailsWidth()) / 2;
    }

    private int treeCenterY() {
        return height / 2;
    }

    private double toTreeX(double screenX) {
        return (screenX - treeCenterX()) / graph.zoom() + treeCenterX();
    }

    private double toTreeY(double screenY) {
        return (screenY - treeCenterY()) / graph.zoom() + treeCenterY();
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
    public boolean keyPressed(KeyEvent event) {
        if (modalHost.is(QuestModalHost.Modal.DIAGNOSTICS)) {
            if (event.isEscape() || event.key() == InputConstants.KEY_RETURN) {
                closeDiagnosticsModal();
                rebuildWidgets();
            }
            return true;
        }
        if (modalHost.is(QuestModalHost.Modal.FILE_IMPORT)) {
            if (event.isEscape()) {
                cancelImport();
                return true;
            }
            if (event.hasControlDown() && event.key() == InputConstants.KEY_RETURN && importController.canSubmit()) {
                sendImport();
                return true;
            }
            return super.keyPressed(event);
        }
        if (pasteIdPrompt && event.key() == InputConstants.KEY_RETURN) {
            confirmPasteIdPrompt();
            return true;
        }
        if ((taskChooserOpen || rewardChooserOpen || nestedRewardChooserOpen) && !event.isEscape()) {
            return true;
        }
        if (!isTextEditing() && event.hasControlDown() && event.key() == InputConstants.KEY_RETURN && importController.canSubmit()) {
            sendImport();
            return true;
        }
        if (!isTextEditing() && event.hasControlDown() && !event.hasAltDown()) {
            if (event.key() == InputConstants.KEY_C && editMode && selected() != null) {
                ClientQuest quest = selected();
                CLIPBOARD.copy(quest.definition.id(), quest.raw());
                editorMessage = "Copied quest '" + quest.definition.id() + "'.";
                editorMessageSuccess = true;
                rebuildWidgets();
                return true;
            }
            if (event.key() == InputConstants.KEY_X && editMode && selected() != null) {
                ClientQuest quest = selected();
                CLIPBOARD.cut(quest.definition.id(), quest.raw());
                editorMessage = "Cut quest '" + quest.definition.id() + "' (paste to complete the move).";
                editorMessageSuccess = true;
                rebuildWidgets();
                return true;
            }
            if (event.key() == InputConstants.KEY_V && editMode && CLIPBOARD.hasContent()) {
                if (event.hasShiftDown() || CLIPBOARD.isMove()) sendClipboardPaste(event.hasShiftDown(), null);
                else openPasteIdPrompt();
                return true;
            }
        }
        if (event.key() == InputConstants.KEY_RETURN && !isTextEditing()) {
            if (discardConfirmation && discardAction != null) {
                Runnable action = discardAction;
                discardConfirmation = false;
                discardAction = null;
                action.run();
                return true;
            }
            if (deleteQuestConfirmation) {
                deleteQuestConfirmation = false;
                confirmDeleteQuest();
                return true;
            }
            if (taskDeleteConfirmation >= 0) {
                confirmDeleteTask();
                return true;
            }
            if (chapterEditorOpen) {
                saveChapter();
                return true;
            }
            if (pasteIdPrompt) {
                confirmPasteIdPrompt();
                return true;
            }
            if (createQuestDockOpen && validCreateQuestDraft()) {
                confirmCreateQuest();
                return true;
            }
        }
        if (editMode && picker == Picker.NONE && !taskChooserOpen && !rewardChooserOpen && !nestedRewardChooserOpen
            && !isTextEditing() && !event.hasControlDown() && !event.hasAltDown()) {
            EditorTool shortcut = switch (event.key()) {
                case InputConstants.KEY_S -> EditorTool.SELECT;
                case InputConstants.KEY_H -> EditorTool.HAND;
                case InputConstants.KEY_A -> EditorTool.ADD;
                case InputConstants.KEY_L -> EditorTool.LINK;
                default -> null;
            };
            if (shortcut != null) {
                editorTool = shortcut;
                rebuildWidgets();
                return true;
            }
        }
        if (event.hasControlDown() && event.key() == InputConstants.KEY_S && createQuestDockOpen) {
            confirmCreateQuest();
            return true;
        }
        if (!event.isEscape()) return super.keyPressed(event);
        if (discardConfirmation) {
            discardConfirmation = false;
            discardAction = null;
            rebuildWidgets();
            return true;
        }
        if (picker != Picker.NONE) {
            picker = Picker.NONE;
            rebuildWidgets();
            return true;
        }
        if (taskChooserOpen) {
            taskChooserOpen = false;
            rebuildWidgets();
            return true;
        }
        if (rewardChooserOpen) {
            rewardChooserOpen = false;
            rebuildWidgets();
            return true;
        }
        if (nestedRewardChooserOpen) {
            nestedRewardChooserOpen = false;
            rebuildWidgets();
            return true;
        }
        if (deleteQuestConfirmation) {
            deleteQuestConfirmation = false;
            rebuildWidgets();
            return true;
        }
        if (taskDeleteConfirmation >= 0) {
            taskDeleteConfirmation = -1;
            rebuildWidgets();
            return true;
        }
        if (editingTask != null) {
            requestModalDiscard(this::closeTaskEditor);
            return true;
        }
        if (editingNestedReward != null) {
            requestModalDiscard(() -> closeRewardEditor(true));
            return true;
        }
        if (editingReward != null || nestedRewardsOpen) {
            requestModalDiscard(() -> closeRewardEditor(false));
            return true;
        }
        if (chapterEditorOpen) {
            requestModalDiscard(() -> {
                chapterEditorOpen = false;
                chapterEditorBaseline = null;
                rebuildWidgets();
            });
            return true;
        }
        if (pasteIdPrompt) {
            pasteIdPrompt = false;
            pasteIdField = null;
            rebuildWidgets();
            return true;
        }
        if (createQuestDockOpen) {
            requestDiscard(() -> {
                closeDraft();
                rebuildWidgets();
            });
            return true;
        }
        onClose();
        return true;
    }

    private boolean isTextEditing() {
        return getFocused() instanceof EditBox || getFocused() instanceof MultiLineEditBox;
    }

    private void sendClipboardPaste(boolean chapterOnly, String requestedId) {
        String sourceId = CLIPBOARD.sourceId();
        JsonObject request = new JsonObject();
        request.addProperty("source_id", sourceId);
        request.addProperty("chapter", group);
        request.addProperty("chapter_only", chapterOnly);
        ClientQuest source = quests.stream().filter(quest -> quest.definition.id().equals(sourceId)).findFirst().orElse(null);
        if (source == null && !chapterOnly) {
            editorMessage = "The copied quest is no longer available.";
            editorMessageSuccess = false;
            return;
        }
        if (!chapterOnly) {
            String id = CLIPBOARD.isMove() ? sourceId : requestedId;
            if (id == null || !id.matches("[a-z0-9_.-]+") || questById(id) != null) {
                editorMessage = "Choose a new, unused lowercase quest ID.";
                editorMessageSuccess = false;
                return;
            }
            request.addProperty("id", id);
            JsonObject quest = CLIPBOARD.transferSnapshot();
            request.add("quest", quest);
            request.addProperty("move", CLIPBOARD.isMove());
            QuestDefinition.GroupDisplay position = source == null ? new QuestDefinition.GroupDisplay(0, 0) : source.definition.position(group);
            request.addProperty("x", position.x());
            request.addProperty("y", position.y());
        } else if (source != null) {
            request.addProperty("x", source.definition.position(group).x());
            request.addProperty("y", source.definition.position(group).y());
        }
        clipboardMutationPending = true;
        sendEditorMutation("paste_quest", request);
    }

    private void openPasteIdPrompt() {
        pasteIdPrompt = true;
        pasteIdField = null;
        rebuildWidgets();
    }

    private void addPasteIdPromptWidgets() {
        int left = (width - 280) / 2;
        int top = (height - 130) / 2;
        pasteIdField = new EditBox(font, left + 14, top + 52, 252, 18, Component.literal("New quest ID"));
        pasteIdField.setValue(CLIPBOARD.sourceId() + "_copy");
        addRenderableWidget(pasteIdField);
        setInitialFocus(pasteIdField);
        addRenderableWidget(Widgets.button(widget -> {
            widget.withPosition(left + 14, top + 88).withSize(100, 22);
            widget.withRenderer(WidgetRenderers.text(Component.literal("Cancel")));
            widget.withCallback(() -> { pasteIdPrompt = false; pasteIdField = null; rebuildWidgets(); });
        }));
        addRenderableWidget(Widgets.button(widget -> {
            widget.withPosition(left + 166, top + 88).withSize(100, 22);
            widget.withRenderer(WidgetRenderers.text(Component.literal("Paste")));
            widget.withCallback(this::confirmPasteIdPrompt);
        }));
    }

    private void confirmPasteIdPrompt() {
        if (pasteIdField == null) return;
        String id = pasteIdField.getValue().trim();
        pasteIdPrompt = false;
        pasteIdField = null;
        sendClipboardPaste(false, id);
        rebuildWidgets();
    }

    /** Parsing is independent per file and failed files remain removable. */
    @Override
    public void onFilesDrop(List<java.nio.file.Path> paths) {
        importController.addFiles(paths);
        updateImportMessage();
        if (!paths.isEmpty()) modalHost.open(QuestModalHost.Modal.FILE_IMPORT);
        rebuildWidgets();
    }

    private void updateImportMessage() {
        editorMessage = importController.summary() + (importController.canSubmit() ? "\nImport ready (Ctrl-Enter to submit)." : "\nImport contains invalid files; remove or correct them.");
        editorMessageSuccess = importController.canSubmit();
    }

    private void openNativeFilePicker() {
        NativeFilePicker.open(
            paths -> Minecraft.getInstance().execute(() -> onFilesDrop(paths)),
            error -> Minecraft.getInstance().execute(() -> {
                editorMessage = error;
                editorMessageSuccess = false;
                rebuildWidgets();
            })
        );
    }

    public void removeImportFile(String key) {
        importController.remove(key);
        updateImportMessage();
        rebuildWidgets();
    }

    public boolean changeImportId(String key, String id) {
        boolean changed = importController.changeId(key, id);
        if (changed) {
            updateImportMessage();
            rebuildWidgets();
        }
        return changed;
    }

    private void sendImport() {
        editorMessage = "Importing…";
        editorMessageSuccess = false;
        JsonObject request = importController.request();
        request.addProperty("chapter", group);
        sendEditorMutation("import_quests", request);
    }

    private void sendEditorMutation(String operation, JsonObject request) {
        QuestMutationCoordinator.Pending pending;
        try {
            pending = mutations.begin(operation, request);
        } catch (IllegalStateException exception) {
            editorMessage = "Another editor operation is still pending.";
            editorMessageSuccess = false;
            return;
        }
        diagnostics = List.of();
        modalHost.close();
        ClientPacketDistributor.sendToServer(new QuestNetwork.EditorMutationPayload(pending.requestId(), operation, GSON.toJson(request)));
    }

    @Override
    public void onClose() {
        requestDiscard(() -> QuestScreen.super.onClose());
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (modalHost.is(QuestModalHost.Modal.DIAGNOSTICS)) {
            super.mouseClicked(event, doubleClick);
            return true;
        }
        if (modalHost.is(QuestModalHost.Modal.FILE_IMPORT)) {
            super.mouseClicked(event, doubleClick);
            return true;
        }
        if (picker != Picker.NONE) return pickerClicked(event);
        if (nestedRewardChooserOpen) return rewardChooserClicked(event, true);
        if (rewardChooserOpen) return rewardChooserClicked(event, false);
        if (taskChooserOpen) return taskChooserClicked(event);
        if (super.mouseClicked(event, doubleClick)) return true;
        if (editingTask != null || editingReward != null || nestedRewardsOpen || editingNestedReward != null || deleteQuestConfirmation || discardConfirmation || taskDeleteConfirmation >= 0 || chapterEditorOpen) return true;
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
            event.x() < canvasRight() &&
            event.y() >= canvasTop()
        ) {
            double treeX = toTreeX(event.x());
            double treeY = toTreeY(event.y());
            if (editMode && editorTool == EditorTool.HAND) {
                graph.beginPan();
                return true;
            }
            if (editMode && editorTool == EditorTool.ADD) {
                boolean occupied = visibleQuests().stream().anyMatch(quest -> {
                    NodeBounds bounds = nodeBounds.get(quest.definition.id());
                    return bounds != null && bounds.contains(treeX, treeY);
                });
                if (!occupied) {
                    requestDiscard(() -> beginCreateQuest(treeX, treeY));
                    return true;
                }
                return true;
            }
            if (editMode && editorTool == EditorTool.SELECT && editingExistingQuest && createQuestDockOpen) {
                NodeBounds draftBounds = new NodeBounds(
                    treeCenterX() + graph.panX() + createQuestX - NODE_WIDTH / 2,
                    treeCenterY() + graph.panY() + createQuestY - NODE_HEIGHT / 2,
                    NODE_WIDTH,
                    NODE_HEIGHT
                );
                if (draftBounds.contains(treeX, treeY)) {
                    graph.dragQuest(originalQuestId);
                    return true;
                }
            }
            for (ClientQuest quest : visibleQuests()) {
                NodeBounds bounds = nodeBounds.get(quest.definition.id());
                if (bounds != null && bounds.contains(treeX, treeY)) {
                    if (editMode && editorTool == EditorTool.LINK) {
                        linkQuest(quest.definition.id(), event.hasShiftDown());
                        return true;
                    }
                    if (editMode && editorTool == EditorTool.SELECT) {
                        requestDiscard(() -> {
                            beginEditQuest(quest);
                            graph.dragQuest(quest.definition.id());
                        });
                        return true;
                    }
                    graph.select(quest.definition.id());
                    detailScroll = 0;
                    createQuestDockOpen = false;
                    detailsOpen = true;
                    rebuildWidgets();
                    return true;
                }
            }
            if (editMode && editorTool == EditorTool.SELECT) {
                return true;
            }
            if (editMode && editorTool == EditorTool.LINK) {
                graph.clearLink();
                return true;
            }
            if (!editMode && detailsOpen) {
                detailsOpen = false;
                rebuildWidgets();
            }
            graph.setPanning(!editMode || editorTool == EditorTool.HAND);
            return true;
        }
        return false;
    }

    private void linkQuest(String questId, boolean remove) {
        if (graph.linkSourceId() == null) {
            graph.linkFrom(questId);
            return;
        }
        if (graph.linkSourceId().equals(questId)) {
            graph.clearLink();
            return;
        }
        JsonObject change = new JsonObject();
        change.addProperty("prerequisite", graph.linkSourceId());
        change.addProperty("dependent", questId);
        change.addProperty("remove", remove);
        sendEditorMutation("set_dependency", change);
    }

    private boolean taskChooserClicked(MouseButtonEvent event) {
        if (event.input() != 0) return true;
        int left = width - detailsWidth() + 16;
        int top = 47;
        int chooserWidth = detailsWidth() - 32;
        int visibleCount = Math.min(TASK_CHOOSER_VISIBLE, TASK_CHOICES.size() - taskChooserScroll);
        int chooserHeight = visibleCount * TASK_CHOOSER_ROW_HEIGHT + 4;
        if (event.x() < left || event.x() >= left + chooserWidth ||
            event.y() < top || event.y() >= top + chooserHeight) {
            taskChooserOpen = false;
            return true;
        }
        int row = (int) (event.y() - top - 2) / TASK_CHOOSER_ROW_HEIGHT;
        if (row >= 0 && row < visibleCount) {
            TaskChoice choice = TASK_CHOICES.get(taskChooserScroll + row);
            if (choice.implemented) {
                addDraftTask(choice);
                taskChooserOpen = false;
                rebuildWidgets();
            }
        }
        return true;
    }

    private boolean rewardChooserClicked(MouseButtonEvent event, boolean nested) {
        if (event.input() != 0) return true;
        int left = nested ? rewardEditorLeft() + 24 : width - detailsWidth() + 16;
        int top = nested ? rewardEditorTop() + 70 : 47;
        int chooserWidth = nested ? 252 : detailsWidth() - 32;
        List<RewardChoice> choices = nested
            ? REWARD_CHOICES.stream().filter(choice -> !choice.type.equals("heracles:selectable")).toList()
            : REWARD_CHOICES;
        int chooserHeight = choices.size() * TASK_CHOOSER_ROW_HEIGHT + 4;
        if (event.x() < left || event.x() >= left + chooserWidth || event.y() < top || event.y() >= top + chooserHeight) {
            if (nested) nestedRewardChooserOpen = false;
            else rewardChooserOpen = false;
            rebuildWidgets();
            return true;
        }
        int row = (int) (event.y() - top - 2) / TASK_CHOOSER_ROW_HEIGHT;
        if (row >= 0 && row < choices.size()) {
            addDraftReward(choices.get(row), nested);
            rebuildWidgets();
        }
        return true;
    }

    private void addDraftTask(TaskChoice choice) {
        EditorTypeRegistry.Descriptor descriptor = EDITOR_TYPES.resolve(EditorTypeRegistry.Kind.TASK, choice.type);
        if (!descriptor.editable()) {
            editorMessage = descriptor.availabilityReason();
            editorMessageSuccess = false;
            return;
        }
        String base = choice.type.substring(choice.type.indexOf(':') + 1);
        int suffix = 1;
        String id = base;
        while (draftTaskIdExists(id)) id = base + "_" + ++suffix;
        JsonObject source = new JsonObject();
        source.addProperty("type", choice.type);
        source.addProperty("title", choice.label);
        switch (choice.type) {
            case "heracles:dummy" -> source.addProperty("value", id);
            case "heracles:item" -> {
                source.addProperty("item", "minecraft:stone");
                source.addProperty("amount", 1);
                source.addProperty("collection", "automatic");
            }
            case "heracles:xp" -> {
                source.addProperty("amount", 1);
                source.addProperty("xpType", "level");
                source.addProperty("collectionType", "automatic");
            }
            case "heracles:kill_entity" -> {
                source.addProperty("entity", "minecraft:pig");
                source.addProperty("amount", 1);
            }
            case "heracles:advancement" -> source.add("advancements", stringArray("minecraft:story/mine_stone"));
            case "heracles:biome" -> source.addProperty("biomes", "minecraft:plains");
            case "heracles:block_interaction" -> source.addProperty("block", "minecraft:stone");
            case "heracles:changed_dimension" -> source.addProperty("to", "minecraft:the_nether");
            case "heracles:check" -> source.add("components", new JsonObject());
            case "heracles:composite" -> {
                source.addProperty("amount", 1);
                JsonObject tasks = new JsonObject();
                JsonObject child = new JsonObject();
                child.addProperty("type", "heracles:check");
                child.add("components", new JsonObject());
                tasks.add("check", child);
                source.add("tasks", tasks);
            }
            case "heracles:entity_interaction" -> source.addProperty("entity", "minecraft:pig");
            case "heracles:item_interaction", "heracles:item_use" -> source.addProperty("item", "minecraft:stick");
            case "heracles:location" -> {
                source.addProperty("description", "Reach the configured location");
                source.add("predicate", defaultLocationPredicate());
            }
            case "heracles:recipe" -> source.add("recipes", stringArray("minecraft:crafting_table"));
            case "heracles:stat" -> {
                source.addProperty("stat", "minecraft:jump");
                source.addProperty("target", 1);
            }
            case "heracles:structure" -> source.addProperty("structures", "#minecraft:village");
            default -> throw new IllegalArgumentException("Task type is not implemented: " + choice.type);
        }
        createQuestTasks.add(new DraftTask(id, choice.type, source));
        editingTaskIndex = createQuestTasks.size() - 1;
        editingTask = createQuestTasks.get(editingTaskIndex).copy();
        taskEditorError = "";
        createTaskScroll = maxCreateTaskScroll();
    }

    private void addDraftReward(RewardChoice choice, boolean nested) {
        EditorTypeRegistry.Descriptor descriptor = EDITOR_TYPES.resolve(EditorTypeRegistry.Kind.REWARD, choice.type);
        if (!descriptor.editable()) {
            editorMessage = descriptor.availabilityReason();
            editorMessageSuccess = false;
            return;
        }
        List<DraftReward> rewards = nested ? nestedRewards(editingReward) : createQuestRewards;
        String base = choice.type.substring(choice.type.indexOf(':') + 1);
        int suffix = 1;
        String id = base;
        while (rewardIdExists(rewards, id)) id = base + "_" + ++suffix;
        JsonObject source = new JsonObject();
        source.addProperty("type", choice.type);
        source.addProperty("title", choice.label);
        switch (choice.type) {
            case "heracles:xp" -> {
                source.addProperty("xptype", "level");
                source.addProperty("amount", 1);
            }
            case "heracles:item" -> setRewardItem(source, "minecraft:stone", 1);
            case "heracles:loottable" -> source.addProperty("loot_table", "minecraft:chests/simple_dungeon");
            case "heracles:command" -> source.addProperty("command", "say Quest complete");
            case "heracles:selectable" -> {
                source.addProperty("amount", 1);
                source.add("rewards", new JsonObject());
            }
            default -> throw new IllegalArgumentException("Unknown reward type " + choice.type);
        }
        DraftReward reward = new DraftReward(id, choice.type, source);
        rewards.add(reward);
        if (nested) {
            setNestedRewards(editingReward, rewards);
            editingNestedRewardIndex = rewards.size() - 1;
            editingNestedReward = reward.copy();
            nestedRewardChooserOpen = false;
        } else {
            editingRewardIndex = rewards.size() - 1;
            editingReward = reward.copy();
            createRewardScroll = maxCreateRewardScroll();
            rewardChooserOpen = false;
        }
    }

    private static boolean rewardIdExists(List<DraftReward> rewards, String id) {
        return rewards.stream().anyMatch(reward -> reward.id.equals(id));
    }

    private boolean draftTaskIdExists(String id) {
        return createQuestTasks.stream().anyMatch(task -> task.id.equals(id));
    }

    private boolean pickerClicked(MouseButtonEvent event) {
        if (event.input() != 0) return true;
        if (pickerSearch != null && pickerSearch.mouseClicked(event, false)) return true;
        int left = pickerLeft();
        int top = pickerTop();
        if (event.x() < left || event.x() >= left + 200 || event.y() < top || event.y() >= top + 176) {
            picker = Picker.NONE;
            rebuildWidgets();
            return true;
        }
        if (picker == Picker.ICON) {
            List<Item> items = filteredPickerItems();
            int gridX = left + 12;
            int gridY = top + 56;
            if (event.x() < gridX || event.x() >= gridX + 176 || event.y() < gridY || event.y() >= gridY + 110) {
                return true;
            }
            int column = (int) (event.x() - gridX) / 22;
            int row = (int) (event.y() - gridY) / 22;
            if (column >= 0 && column < 8 && row >= 0 && row < 5) {
                int index = pickerScroll + row * 8 + column;
                if (index < items.size()) {
                    String id = BuiltInRegistries.ITEM.getKey(items.get(index)).toString();
                    switch (pickerTarget) {
                        case QUEST_ICON -> createQuestIcon = id;
                        case TASK_ICON -> {
                            JsonObject icon = new JsonObject();
                            icon.addProperty("item", id);
                            editingTask.source.add("icon", icon);
                        }
                        case TASK_ITEM -> editingTask.source.addProperty("item", id);
                        case TASK_BLOCK -> editingTask.source.addProperty("block", id);
                        case TASK_ENTITY -> { }
                        case REWARD_ICON -> {
                            JsonObject icon = new JsonObject();
                            icon.addProperty("item", id);
                            activeRewardDraft().source.add("icon", icon);
                        }
                        case REWARD_ITEM -> setRewardItem(activeRewardDraft().source, id, rewardItemCount(activeRewardDraft().source));
                        case CHAPTER_ICON -> chapterEditorIcon = id;
                    }
                    picker = Picker.NONE;
                    rebuildWidgets();
                }
            }
        } else if (picker == Picker.ENTITY) {
            List<EntityType<?>> entities = filteredPickerEntities();
            int gridX = left + 12;
            int gridY = top + 56;
            if (event.x() < gridX || event.x() >= gridX + 176 || event.y() < gridY || event.y() >= gridY + 110) return true;
            int column = (int) (event.x() - gridX) / 22;
            int row = (int) (event.y() - gridY) / 22;
            int index = pickerScroll + row * 8 + column;
            if (column >= 0 && column < 8 && row >= 0 && row < 5 && index < entities.size()) {
                editingTask.source.addProperty("entity", BuiltInRegistries.ENTITY_TYPE.getKey(entities.get(index)).toString());
                picker = Picker.NONE;
                rebuildWidgets();
            }
        } else {
            int gridX = left + 12;
            int gridY = top + 34;
            if (event.x() < gridX || event.x() >= gridX + 176 || event.y() < gridY || event.y() >= gridY + 126) {
                return true;
            }
            int column = (int) (event.x() - gridX) / 44;
            int row = (int) (event.y() - gridY) / 42;
            int index = row * 4 + column;
            if (column >= 0 && column < 4 && row >= 0 && index < QUEST_BACKGROUNDS.size()) {
                createQuestBackground = QUEST_BACKGROUNDS.get(index).toString();
                picker = Picker.NONE;
                rebuildWidgets();
            }
        }
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        graph.endPointerAction();
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(
        MouseButtonEvent event,
        double dragX,
        double dragY
    ) {
        if (graph.panning()) {
            graph.panBy(dragX, dragY);
            rebuildWidgets();
            return true;
        }
        if (graph.draggingQuestId() != null && editingExistingQuest && editorTool == EditorTool.SELECT) {
            createQuestX += (int) Math.round(dragX / graph.zoom());
            createQuestY += (int) Math.round(dragY / graph.zoom());
            updateDraftGroupPosition();
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
        if (modalHost.is(QuestModalHost.Modal.DIAGNOSTICS)) {
            int max = Math.max(0, diagnosticLines(416).size() - 13);
            diagnosticsScroll = Math.max(0, Math.min(max, diagnosticsScroll - (int) Math.signum(scrollY)));
            return true;
        }
        if (modalHost.is(QuestModalHost.Modal.FILE_IMPORT)) {
            int max = Math.max(0, importController.entries().size() - importVisibleRows());
            importScroll = Math.max(0, Math.min(max, importScroll - (int) Math.signum(scrollY)));
            rebuildWidgets();
            return true;
        }
        if (picker == Picker.ICON) {
            int itemCount = filteredPickerItems().size();
            int maxRow = Math.max(0, (itemCount + 7) / 8 - 5);
            int row = pickerScroll / 8 - (int) Math.signum(scrollY);
            pickerScroll = Math.max(0, Math.min(maxRow, row)) * 8;
            pickerScrollByTarget.put(pickerTarget, pickerScroll);
            return true;
        }
        if (picker == Picker.ENTITY) {
            int entityCount = filteredPickerEntities().size();
            int maxRow = Math.max(0, (entityCount + 7) / 8 - 5);
            int row = pickerScroll / 8 - (int) Math.signum(scrollY);
            pickerScroll = Math.max(0, Math.min(maxRow, row)) * 8;
            pickerScrollByTarget.put(pickerTarget, pickerScroll);
            return true;
        }
        if (picker == Picker.BACKGROUND) return true;
        if (nestedRewardsOpen && !nestedRewardChooserOpen && editingNestedReward == null) {
            int max = Math.max(0, nestedRewards(editingReward).size() - 4);
            nestedRewardScroll = Math.max(0, Math.min(max, nestedRewardScroll - (int) Math.signum(scrollY)));
            rebuildWidgets();
            return true;
        }
        if (taskChooserOpen) {
            int max = Math.max(0, TASK_CHOICES.size() - TASK_CHOOSER_VISIBLE);
            taskChooserScroll = Math.max(
                0,
                Math.min(max, taskChooserScroll - (int) Math.signum(scrollY))
            );
            return true;
        }
        if (createQuestDockOpen && createQuestTab == DetailTab.TASKS &&
            mouseX >= width - detailsWidth()) {
            createTaskScroll = Math.max(
                0,
                Math.min(maxCreateTaskScroll(), createTaskScroll - (int) Math.signum(scrollY))
            );
            rebuildWidgets();
            return true;
        }
        if (createQuestDockOpen && createQuestTab == DetailTab.REWARDS && mouseX >= width - detailsWidth()) {
            createRewardScroll = Math.max(0, Math.min(maxCreateRewardScroll(), createRewardScroll - (int) Math.signum(scrollY)));
            rebuildWidgets();
            return true;
        }
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
        if (mouseX > sidebarWidth() && mouseX < canvasRight() && mouseY >= canvasTop()) {
            graph.moveZoom(scrollY * 0.1);
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

    private enum EditorTool {
        SELECT("move", "Move or select quest"),
        HAND("drag", "Pan quest tree"),
        ADD("add", "Add quest"),
        LINK("link", "Link dependency; Shift-click the dependent to remove");

        private final String icon;
        private final String tooltip;

        EditorTool(String icon, String tooltip) {
            this.icon = icon;
            this.tooltip = tooltip;
        }
    }

    private enum Picker {
        NONE,
        ICON,
        ENTITY,
        BACKGROUND
    }

    private enum PickerTarget {
        QUEST_ICON,
        TASK_ICON,
        TASK_ITEM,
        TASK_BLOCK,
        TASK_ENTITY,
        REWARD_ICON,
        REWARD_ITEM,
        CHAPTER_ICON
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

    private record TaskChoice(
        String type,
        String label,
        Item icon,
        boolean implemented
    ) {}

    private record RewardChoice(String type, String label, Item icon) {}

    private static final class DraftTask {
        private String id;
        private final String type;
        private final JsonObject source;

        private DraftTask(String id, String type, JsonObject source) {
            this.id = id;
            this.type = type;
            this.source = source;
        }

        private DraftTask copy() {
            return new DraftTask(id, type, source.deepCopy());
        }

        private boolean sameAs(DraftTask other) {
            return other != null && java.util.Objects.equals(id, other.id) && type.equals(other.type) && source.equals(other.source);
        }

        private boolean isSupported() {
            return EDITOR_TYPES.resolve(EditorTypeRegistry.Kind.TASK, type).editable();
        }

        private TaskChoice choice() {
            return TASK_CHOICES.stream()
                .filter(choice -> choice.type.equals(type))
                .findFirst()
                .orElse(TASK_CHOICES.getFirst());
        }

        private String displayLabel() {
            return isSupported() ? choice().label : "Unsupported: " + type;
        }

        private Item displayIcon() {
            return isSupported() ? choice().icon : Items.BARRIER;
        }
    }

    private static final class DraftReward {
        private String id;
        private final String type;
        private final JsonObject source;

        private DraftReward(String id, String type, JsonObject source) {
            this.id = id;
            this.type = type;
            this.source = source;
        }

        private DraftReward copy() {
            return new DraftReward(id, type, source.deepCopy());
        }

        private boolean sameAs(DraftReward other) {
            return other != null && java.util.Objects.equals(id, other.id) && type.equals(other.type) && source.equals(other.source);
        }

        private boolean isSupported() {
            return EDITOR_TYPES.resolve(EditorTypeRegistry.Kind.REWARD, type).editable();
        }

        private RewardChoice choice() {
            return REWARD_CHOICES.stream()
                .filter(choice -> choice.type.equals(type))
                .findFirst().orElse(REWARD_CHOICES.getFirst());
        }

        private String displayLabel() {
            return isSupported() ? choice().label : "Unsupported: " + type;
        }

        private Item displayIcon() {
            return isSupported() ? choice().icon : Items.BARRIER;
        }
    }

    private record QuestBackground(
        Identifier texture,
        int xOffset,
        int yOffset,
        int width,
        int height
    ) {}

    private record RewardChoiceBounds(
        String selectionKey,
        String choiceId,
        NodeBounds bounds
    ) {}

    private record HeaderLayout(
        int editX,
        int importX,
        int diagnosticsX,
        int actionY,
        int statusY,
        int canvasTop
    ) {}

    private record ChapterDisplay(String icon, String background) {}

    private record ClientQuest(
        QuestDefinition definition,
        Map<String, Integer> progress,
        boolean unlocked,
        boolean complete,
        boolean claimed,
        boolean pinned,
        JsonObject raw
    ) {}
}
