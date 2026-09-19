package me.johardt.heracles.core;

import net.minecraft.server.level.ServerPlayer;

/** Outbound client synchronization port used by the quest runtime. */
public interface QuestSync {

    void snapshot(ServerPlayer player, String json, boolean open);

    void notification(
        ServerPlayer player,
        String kind,
        String title,
        String detail
    );
}
