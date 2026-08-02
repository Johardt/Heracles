package earth.terrarium.heracles;

import com.mojang.logging.LogUtils;
import earth.terrarium.heracles.core.QuestCommands;
import earth.terrarium.heracles.core.QuestNetwork;
import earth.terrarium.heracles.core.QuestRuntime;
import earth.terrarium.heracles.core.TaskEngine;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.player.AdvancementEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
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
        NeoForge.EVENT_BUS.addListener(this::onLivingDeath);
        NeoForge.EVENT_BUS.addListener(this::onAdvancementEarned);
        NeoForge.EVENT_BUS.addListener(this::onChangedDimension);
        NeoForge.EVENT_BUS.addListener(this::onBlockInteraction);
        NeoForge.EVENT_BUS.addListener(this::onEntityInteraction);
        NeoForge.EVENT_BUS.addListener(this::onItemInteraction);
        NeoForge.EVENT_BUS.addListener(this::onItemUsed);
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
            QuestRuntime.get().initialize(player);
            QuestRuntime.get().sync(player, Boolean.getBoolean("heracles.openQuestScreen"));
        }
    }

    private void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && player.tickCount % 20 == 0) {
            QuestRuntime.get().updateInventoryTasks(player);
        }
    }

    private void onLivingDeath(LivingDeathEvent event) {
        if (event.getSource().getEntity() instanceof ServerPlayer player) {
            QuestRuntime.get().signal(player, new TaskEngine.Signal.EntityKilled(BuiltInRegistries.ENTITY_TYPE.getKey(event.getEntity().getType()).toString()));
        }
    }

    private void onAdvancementEarned(AdvancementEvent.AdvancementEarnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            QuestRuntime.get().signal(player, new TaskEngine.Signal.AdvancementGranted(event.getAdvancement().id().toString()));
        }
    }

    private void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            QuestRuntime.get().signal(player, new TaskEngine.Signal.DimensionChanged(event.getFrom().identifier().toString(), event.getTo().identifier().toString()));
        }
    }

    private void onBlockInteraction(PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            String block = BuiltInRegistries.BLOCK.getKey(event.getLevel().getBlockState(event.getPos()).getBlock()).toString();
            QuestRuntime.get().signal(player, new TaskEngine.Signal.BlockInteracted(block));
            QuestRuntime.get().signal(player, new TaskEngine.Signal.ItemInteracted(BuiltInRegistries.ITEM.getKey(event.getItemStack().getItem()).toString()));
        }
    }

    private void onEntityInteraction(PlayerInteractEvent.EntityInteract event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            String entity = BuiltInRegistries.ENTITY_TYPE.getKey(event.getTarget().getType()).toString();
            QuestRuntime.get().signal(player, new TaskEngine.Signal.EntityInteracted(entity));
            QuestRuntime.get().signal(player, new TaskEngine.Signal.ItemInteracted(BuiltInRegistries.ITEM.getKey(event.getItemStack().getItem()).toString()));
        }
    }

    private void onItemInteraction(PlayerInteractEvent.RightClickItem event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            String item = BuiltInRegistries.ITEM.getKey(event.getItemStack().getItem()).toString();
            QuestRuntime.get().signal(player, new TaskEngine.Signal.ItemInteracted(item));
        }
    }

    private void onItemUsed(LivingEntityUseItemEvent.Finish event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            String item = BuiltInRegistries.ITEM.getKey(event.getItem().getItem()).toString();
            QuestRuntime.get().signal(player, new TaskEngine.Signal.ItemUsed(item));
        }
    }
}
