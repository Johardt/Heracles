package earth.terrarium.heracles.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.InputConstants;
import earth.terrarium.heracles.Heracles;
import earth.terrarium.heracles.core.QuestNetwork;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = Heracles.MOD_ID, dist = Dist.CLIENT)
public final class HeraclesClient {
    private static final KeyMapping.Category CATEGORY = new KeyMapping.Category(Identifier.fromNamespaceAndPath(Heracles.MOD_ID, "quests"));
    private static final KeyMapping OPEN_QUESTS = new KeyMapping("key.heracles.open_quests", InputConstants.Type.KEYSYM, 72, CATEGORY);
    private static JsonObject snapshot = new JsonObject();

    public HeraclesClient(IEventBus modBus) {
        modBus.addListener(this::registerKeys);
        modBus.addListener(this::registerPayloadHandlers);
        NeoForge.EVENT_BUS.addListener(this::clientTick);
    }

    private void registerKeys(RegisterKeyMappingsEvent event) {
        event.registerCategory(CATEGORY);
        event.register(OPEN_QUESTS);
    }

    private void registerPayloadHandlers(RegisterClientPayloadHandlersEvent event) {
        event.register(QuestNetwork.SyncPayload.TYPE, (payload, context) -> {
            snapshot = JsonParser.parseString(payload.json()).getAsJsonObject();
            if (payload.open() || Minecraft.getInstance().gui.screen() instanceof QuestScreen) {
                Minecraft.getInstance().gui.setScreen(new QuestScreen(snapshot));
            }
        });
    }

    private void clientTick(ClientTickEvent.Post event) {
        while (OPEN_QUESTS.consumeClick()) {
            if (Minecraft.getInstance().player != null) {
                ClientPacketDistributor.sendToServer(new QuestNetwork.ActionPayload("open", ""));
            }
        }
    }
}
