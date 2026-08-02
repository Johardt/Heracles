package earth.terrarium.heracles.client;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import earth.terrarium.heracles.core.QuestDefinition;
import earth.terrarium.heracles.core.QuestNetwork;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class QuestScreen extends Screen {
    private static final Gson GSON = new Gson();
    private final List<ClientQuest> quests;
    private int selected;

    public QuestScreen(JsonObject snapshot) {
        super(Component.literal("Heracles Quests"));
        this.quests = new ArrayList<>();
        snapshot.entrySet().forEach(entry -> {
            JsonObject json = entry.getValue().getAsJsonObject();
            QuestDefinition definition = GSON.fromJson(json, QuestDefinition.class);
            Map<String, Integer> progress = new HashMap<>();
            json.getAsJsonObject("progress").entrySet().forEach(task -> progress.put(task.getKey(), task.getValue().getAsInt()));
            quests.add(new ClientQuest(definition, progress, json.get("unlocked").getAsBoolean(), json.get("complete").getAsBoolean(), json.get("claimed").getAsBoolean()));
        });
        quests.sort(Comparator.comparingInt((ClientQuest quest) -> quest.definition().x()).thenComparing(quest -> quest.definition().id()));
    }

    @Override
    protected void init() {
        int left = Math.max(16, width / 2 - 190);
        for (int index = 0; index < quests.size(); index++) {
            ClientQuest quest = quests.get(index);
            int questIndex = index;
            addRenderableWidget(Button.builder(Component.literal(status(quest) + " " + quest.definition().title()), button -> selected = questIndex)
                .bounds(left, 48 + index * 24, 170, 20).build());
        }
        addRenderableWidget(Button.builder(Component.literal("Claim rewards"), button -> claimSelected())
            .bounds(width / 2 + 20, height - 44, 120, 20).build());
    }

    private void claimSelected() {
        if (quests.isEmpty()) return;
        ClientPacketDistributor.sendToServer(new QuestNetwork.ActionPayload("claim", quests.get(selected).definition().id()));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        int left = Math.max(16, width / 2 - 190);
        int detailLeft = width / 2 + 20;
        graphics.text(font, title, left, 20, 0xFFFFFFFF, true);
        graphics.text(font, Component.literal("Core port preview — press H to reopen"), left, 32, 0xFFAAAAAA, false);
        if (quests.isEmpty()) {
            graphics.text(font, Component.literal("No quests loaded."), detailLeft, 52, 0xFFFF5555, false);
            return;
        }
        ClientQuest quest = quests.get(Math.min(selected, quests.size() - 1));
        QuestDefinition definition = quest.definition();
        graphics.text(font, Component.literal(definition.title()), detailLeft, 52, 0xFFFFFFFF, true);
        graphics.text(font, Component.literal(definition.subtitle()), detailLeft, 66, 0xFFAAAAAA, false);
        int y = 86;
        for (String paragraph : definition.description()) {
            graphics.textWithWordWrap(font, Component.literal(stripMarkdown(paragraph)), detailLeft, y, Math.max(120, width / 2 - 50), 0xFFE0E0E0);
            y += font.wordWrapHeight(Component.literal(paragraph), Math.max(120, width / 2 - 50)) + 6;
        }
        y += 4;
        graphics.text(font, Component.literal("Tasks"), detailLeft, y, 0xFFFFD966, true);
        y += 14;
        for (QuestDefinition.Task task : definition.tasks()) {
            int progress = quest.progress().getOrDefault(task.id(), 0);
            graphics.text(font, Component.literal("• " + task.title() + " — " + progress + "/" + task.target()), detailLeft, y, progress >= task.target() ? 0xFF55FF55 : 0xFFFFFFFF, false);
            y += 12;
        }
        y += 4;
        graphics.text(font, Component.literal("Rewards"), detailLeft, y, 0xFFFFD966, true);
        y += 14;
        for (QuestDefinition.Reward reward : definition.rewards()) {
            graphics.text(font, Component.literal("• " + reward.title() + " × " + reward.amount()), detailLeft, y, 0xFFFFFFFF, false);
            y += 12;
        }
        graphics.text(font, Component.literal("Status: " + status(quest).trim()), detailLeft, y + 8, quest.complete() ? 0xFF55FF55 : 0xFFFFFFFF, false);
    }

    private static String status(ClientQuest quest) {
        if (!quest.unlocked()) return "[Locked]";
        if (quest.claimed()) return "[Claimed]";
        if (quest.complete()) return "[Complete]";
        return "[Active]";
    }

    private static String stripMarkdown(String text) {
        return text.replace("**", "").replace("__", "").replace("`", "");
    }

    private record ClientQuest(QuestDefinition definition, Map<String, Integer> progress, boolean unlocked, boolean complete, boolean claimed) {}
}
