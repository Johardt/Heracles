package me.johardt.heracles.core;

import com.google.gson.JsonParser;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class QuestNetwork {
    private QuestNetwork() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToClient(SyncPayload.TYPE, SyncPayload.STREAM_CODEC);
        registrar.playToServer(ActionPayload.TYPE, ActionPayload.STREAM_CODEC, (payload, context) -> {
            ServerPlayer player = (ServerPlayer) context.player();
            QuestRuntime runtime = QuestRuntime.get();
            switch (payload.action()) {
                case "open" -> runtime.sync(player, true);
                case "claim" -> {
                    if (!payload.argument().startsWith("{")) {
                        runtime.claim(player, payload.argument());
                        break;
                    }
                    try {
                        var json = JsonParser.parseString(payload.argument()).getAsJsonObject();
                        java.util.Map<String, java.util.List<String>> selections = new java.util.HashMap<>();
                        if (json.has("selections") && json.get("selections").isJsonObject()) {
                            json.getAsJsonObject("selections").entrySet().forEach(entry -> selections.put(entry.getKey(),
                                entry.getValue().getAsJsonArray().asList().stream().map(value -> value.getAsString()).toList()));
                        }
                        runtime.claim(player, json.get("quest").getAsString(), selections);
                    } catch (RuntimeException ignored) {
                        player.sendSystemMessage(net.minecraft.network.chat.Component.literal("Invalid Heracles reward selection"));
                    }
                }
                case "submit" -> {
                    String[] parts = payload.argument().split("\\|", 2);
                    if (parts.length == 2) runtime.submit(player, parts[0], parts[1]);
                }
                default -> {}
            }
        });
    }

    public record SyncPayload(String json, boolean open) implements CustomPacketPayload {
        public static final Type<SyncPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("heracles", "sync"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SyncPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeUtf(payload.json(), 1_048_576);
                buffer.writeBoolean(payload.open());
            },
            buffer -> new SyncPayload(buffer.readUtf(1_048_576), buffer.readBoolean())
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record ActionPayload(String action, String argument) implements CustomPacketPayload {
        public static final Type<ActionPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("heracles", "action"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ActionPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeUtf(payload.action(), 32);
                buffer.writeUtf(payload.argument(), 8192);
            },
            buffer -> new ActionPayload(buffer.readUtf(32), buffer.readUtf(8192))
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
