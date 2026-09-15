package com.spider.mtgcard.net;

import com.spider.mtgcard.Mtgcard;
import com.spider.mtgcard.config.GuideConfigSnapshot;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.resources.Identifier;

public final class GuideBookPackets {

    public static final Identifier OPEN_ID = Identifier.fromNamespaceAndPath(Mtgcard.MOD_ID, "guide_open");
    public static final Identifier CONFIG_REQUEST_ID = Identifier.fromNamespaceAndPath(Mtgcard.MOD_ID, "guide_config_request");
    public static final Identifier CONFIG_SYNC_ID = Identifier.fromNamespaceAndPath(Mtgcard.MOD_ID, "guide_config_sync");
    public static final Identifier CONFIG_SAVE_ID = Identifier.fromNamespaceAndPath(Mtgcard.MOD_ID, "guide_config_save");

    public record OpenPayload() implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<OpenPayload> ID = new CustomPacketPayload.Type<>(OPEN_ID);
        public static final StreamCodec<RegistryFriendlyByteBuf, OpenPayload> CODEC =
                StreamCodec.composite(ByteBufCodecs.VAR_INT, ignored -> 0, v -> new OpenPayload()); // minimal no-data codec

        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    public record ConfigRequestPayload() implements CustomPacketPayload {
        public static final Type<ConfigRequestPayload> ID = new Type<>(CONFIG_REQUEST_ID);
        public static final StreamCodec<RegistryFriendlyByteBuf, ConfigRequestPayload> CODEC =
                StreamCodec.composite(ByteBufCodecs.VAR_INT, ignored -> 0, ignored -> new ConfigRequestPayload());
        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    public record ConfigSyncPayload(String json, boolean canEdit, String message) implements CustomPacketPayload {
        public static final Type<ConfigSyncPayload> ID = new Type<>(CONFIG_SYNC_ID);
        public static final StreamCodec<RegistryFriendlyByteBuf, ConfigSyncPayload> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, ConfigSyncPayload::json,
                ByteBufCodecs.BOOL, ConfigSyncPayload::canEdit,
                ByteBufCodecs.STRING_UTF8, ConfigSyncPayload::message,
                ConfigSyncPayload::new
        );
        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    public record ConfigSavePayload(String json) implements CustomPacketPayload {
        public static final Type<ConfigSavePayload> ID = new Type<>(CONFIG_SAVE_ID);
        public static final StreamCodec<RegistryFriendlyByteBuf, ConfigSavePayload> CODEC =
                StreamCodec.composite(ByteBufCodecs.STRING_UTF8, ConfigSavePayload::json, ConfigSavePayload::new);
        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    public static void init() {
        PayloadTypeRegistry.playS2C().register(OpenPayload.ID, OpenPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ConfigRequestPayload.ID, ConfigRequestPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ConfigSyncPayload.ID, ConfigSyncPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ConfigSavePayload.ID, ConfigSavePayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(ConfigRequestPayload.ID, (payload, ctx) ->
                ctx.server().execute(() -> sendConfig(ctx.player(), "")));
        ServerPlayNetworking.registerGlobalReceiver(ConfigSavePayload.ID, (payload, ctx) ->
                ctx.server().execute(() -> {
                    if (!canEditServerConfig(ctx.player())) {
                        sendConfig(ctx.player(), "Server settings require operator permission.");
                        return;
                    }
                    try {
                        Mtgcard.LOGGER.info("{} updated MTGCard settings from the guidebook", ctx.player().getGameProfile().name());
                        com.spider.mtgcard.config.MtgcardConfig.applyServerSnapshot(GuideConfigSnapshot.fromJson(payload.json()));
                        sendConfig(ctx.player(), "Server settings saved.");
                    } catch (RuntimeException error) {
                        Mtgcard.LOGGER.warn("Rejected guidebook config update from {}", ctx.player().getGameProfile().name(), error);
                        sendConfig(ctx.player(), "Could not save server settings.");
                    }
                }));
    }

    public static void sendOpen(ServerPlayer player) {
        ServerPlayNetworking.send(player, new OpenPayload());
    }

    private static void sendConfig(ServerPlayer player, String message) {
        ServerPlayNetworking.send(player, new ConfigSyncPayload(
                GuideConfigSnapshot.current().toJson(),
                canEditServerConfig(player),
                message == null ? "" : message
        ));
    }

    private static boolean canEditServerConfig(ServerPlayer player) {
        return player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
    }

    private GuideBookPackets() {}
}
