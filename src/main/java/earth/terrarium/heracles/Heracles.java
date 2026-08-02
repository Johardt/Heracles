package earth.terrarium.heracles;

import com.mojang.logging.LogUtils;
import earth.terrarium.heracles.core.QuestCommands;
import earth.terrarium.heracles.core.QuestNetwork;
import earth.terrarium.heracles.core.QuestRuntime;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.slf4j.Logger;

/**
 * NeoForge 26.2 bootstrap entrypoint.
 *
 * <p>The quest implementation is intentionally reintroduced incrementally from the retained
 * 1.21 sources as its Minecraft and library APIs are ported.</p>
 */
@Mod(Heracles.MOD_ID)
public final class Heracles {
    public static final String MOD_ID = "heracles";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Heracles(IEventBus modBus) {
        modBus.addListener(QuestNetwork::register);
        NeoForge.EVENT_BUS.addListener(this::onServerAboutToStart);
        NeoForge.EVENT_BUS.addListener(this::onServerStopping);
        NeoForge.EVENT_BUS.addListener(this::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(this::onPlayerLogin);
        NeoForge.EVENT_BUS.addListener(this::onPlayerTick);
        LOGGER.info("Heracles core quest runtime loaded on NeoForge");
    }

    private void onServerAboutToStart(ServerAboutToStartEvent event) {
        QuestRuntime.start(event.getServer());
    }

    private void onServerStopping(ServerStoppingEvent event) {
        QuestRuntime.stop();
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        QuestCommands.register(event.getDispatcher());
    }

    private void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            QuestRuntime.get().sync(player, false);
        }
    }

    private void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && player.tickCount % 20 == 0) {
            QuestRuntime.get().updateInventoryTasks(player);
        }
    }
}
