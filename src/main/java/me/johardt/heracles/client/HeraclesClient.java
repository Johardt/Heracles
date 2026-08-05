package me.johardt.heracles.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.InputConstants;
import me.johardt.heracles.Heracles;
import me.johardt.heracles.core.QuestNetwork;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = Heracles.MOD_ID, dist = Dist.CLIENT)
public final class HeraclesClient {

    private static final KeyMapping.Category CATEGORY = new KeyMapping.Category(
        Identifier.fromNamespaceAndPath(Heracles.MOD_ID, "quests")
    );
    private static final KeyMapping OPEN_QUESTS = new KeyMapping(
        "key.heracles.open_quests",
        InputConstants.Type.KEYSYM,
        72,
        CATEGORY
    );
    private static final KeyMapping TOGGLE_TRACKER = new KeyMapping(
        "key.heracles.toggle_tracker",
        InputConstants.Type.KEYSYM,
        74,
        CATEGORY
    );
    private static final SoundEvent QUEST_COMPLETE_SOUND = SoundEvent.createVariableRangeEvent(
        Identifier.fromNamespaceAndPath(Heracles.MOD_ID, "quest_complete")
    );
    private static JsonObject snapshot = new JsonObject();
    private static boolean trackerCollapsed;

    public HeraclesClient(IEventBus modBus) {
        modBus.addListener(this::registerKeys);
        modBus.addListener(this::registerPayloadHandlers);
        modBus.addListener(this::registerGuiLayers);
        NeoForge.EVENT_BUS.addListener(this::clientTick);
    }

    private void registerKeys(RegisterKeyMappingsEvent event) {
        event.registerCategory(CATEGORY);
        event.register(OPEN_QUESTS);
        event.register(TOGGLE_TRACKER);
    }

    private void registerPayloadHandlers(
        RegisterClientPayloadHandlersEvent event
    ) {
        event.register(QuestNetwork.SyncPayload.TYPE, (payload, context) -> {
            snapshot = JsonParser.parseString(payload.json()).getAsJsonObject();
            if (
                payload.open() ||
                Minecraft.getInstance().gui.screen() instanceof QuestScreen
            ) {
                QuestScreen previous =
                    Minecraft.getInstance().gui.screen() instanceof
                        QuestScreen screen
                        ? screen
                        : null;
                Minecraft.getInstance().gui.setScreen(
                    new QuestScreen(snapshot, previous)
                );
            }
        });
        event.register(
            QuestNetwork.NotificationPayload.TYPE,
            (payload, context) -> {
                if (payload.kind().equals("complete")) {
                    Minecraft.getInstance().getSoundManager().play(
                        SimpleSoundInstance.forUI(QUEST_COMPLETE_SOUND, 1.0F)
                    );
                }
                var id = switch (payload.kind()) {
                    case "unlock" -> QuestHud.UNLOCK_TOAST;
                    case "complete" -> QuestHud.COMPLETE_TOAST;
                    default -> QuestHud.REWARD_TOAST;
                };
                net.minecraft.client.gui.components.toasts.SystemToast.add(
                    Minecraft.getInstance().gui.toastManager(),
                    id,
                    Component.literal(payload.title()),
                    Component.literal(payload.detail())
                );
            }
        );
    }

    private void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(
            VanillaGuiLayers.CHAT,
            Identifier.fromNamespaceAndPath(Heracles.MOD_ID, "quest_tracker"),
            (graphics, delta) ->
                QuestHud.render(graphics, snapshot, trackerCollapsed)
        );
    }

    private void clientTick(ClientTickEvent.Post event) {
        while (OPEN_QUESTS.consumeClick()) {
            if (Minecraft.getInstance().player != null) {
                ClientPacketDistributor.sendToServer(
                    new QuestNetwork.ActionPayload("open", "")
                );
            }
        }
        while (TOGGLE_TRACKER.consumeClick())
            trackerCollapsed = !trackerCollapsed;
    }
}
