package me.johardt.heracles;

import com.mojang.logging.LogUtils;
import me.johardt.heracles.core.QuestCommands;
import me.johardt.heracles.core.QuestNetwork;
import me.johardt.heracles.core.QuestRuntime;
import me.johardt.heracles.core.TaskEngine;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.StatAwardEvent;
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
        NeoForge.EVENT_BUS.addListener(this::onStatAwarded);
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
            QuestRuntime.get().signal(player, new TaskEngine.Signal.EntityKilled(
                QuestRuntime.registryEntry(event.getEntity().getType().builtInRegistryHolder(), new com.google.gson.JsonObject(), 1)));
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
            var block = event.getLevel().getBlockState(event.getPos()).getBlock().builtInRegistryHolder();
            QuestRuntime.get().signal(player, new TaskEngine.Signal.BlockInteracted(QuestRuntime.registryEntry(block, new com.google.gson.JsonObject(), 1)));
            QuestRuntime.get().signal(player, new TaskEngine.Signal.ItemInteracted(QuestRuntime.itemEntry(player, event.getItemStack())));
        }
    }

    private void onEntityInteraction(PlayerInteractEvent.EntityInteract event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            var entity = event.getTarget().getType().builtInRegistryHolder();
            QuestRuntime.get().signal(player, new TaskEngine.Signal.EntityInteracted(QuestRuntime.registryEntry(entity, new com.google.gson.JsonObject(), 1)));
            QuestRuntime.get().signal(player, new TaskEngine.Signal.ItemInteracted(QuestRuntime.itemEntry(player, event.getItemStack())));
        }
    }

    private void onItemInteraction(PlayerInteractEvent.RightClickItem event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            QuestRuntime.get().signal(player, new TaskEngine.Signal.ItemInteracted(QuestRuntime.itemEntry(player, event.getItemStack())));
        }
    }

    private void onItemUsed(LivingEntityUseItemEvent.Finish event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            QuestRuntime.get().signal(player, new TaskEngine.Signal.ItemUsed(QuestRuntime.itemEntry(player, event.getItem())));
        }
    }

    private void onStatAwarded(StatAwardEvent event) {
        if (QuestRuntime.isStarted() && event.getEntity() instanceof ServerPlayer player
            && event.getStat().getType() == net.minecraft.stats.Stats.CUSTOM
            && event.getStat().getValue() instanceof net.minecraft.resources.Identifier id) {
            QuestRuntime.get().signal(player, new TaskEngine.Signal.Statistic(id.toString(), event.getValue()));
        }
    }
}
