package com.spider.mtgcard.net.payload;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class CardDisplayPayloads {

    // ---------- S2C: open large view ----------
    public record OpenDisplayViewS2C(int entityId, UUID hostId, long version, UUID selectedCardId,
                                     ItemStack stack, int attachmentCount) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<OpenDisplayViewS2C> ID =
                new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("mtgcard", "card_display_open"));

        public static final StreamCodec<RegistryFriendlyByteBuf, OpenDisplayViewS2C> CODEC =
                StreamCodec.ofMember(
                        (payload, buf) -> {
                            buf.writeVarInt(payload.entityId());
                            buf.writeUUID(payload.hostId());
                            buf.writeLong(payload.version());
                            buf.writeUUID(payload.selectedCardId());
                            ItemStack.STREAM_CODEC.encode(buf, payload.stack());
                            buf.writeVarInt(payload.attachmentCount());
                        },
                        buf -> new OpenDisplayViewS2C(
                                buf.readVarInt(),
                                buf.readUUID(),
                                buf.readLong(),
                                buf.readUUID(),
                                ItemStack.STREAM_CODEC.decode(buf),
                                buf.readVarInt()
                        )
                );

        public OpenDisplayViewS2C {
            hostId = hostId == null ? new UUID(0L, 0L) : hostId;
            selectedCardId = selectedCardId == null ? hostId : selectedCardId;
            stack = stack == null ? ItemStack.EMPTY : stack.copy();
            attachmentCount = Math.max(0, attachmentCount);
        }

        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    public record AttachmentSyncEntry(UUID id, ItemStack stack, int rotStep) {
        public AttachmentSyncEntry {
            id = id == null ? UUID.randomUUID() : id;
            stack = stack == null ? ItemStack.EMPTY : stack.copy();
            rotStep &= 1;
        }
    }

    // ---------- S2C: open attachment manager ----------
    public record OpenAttachmentsS2C(int entityId, UUID hostId, long version, UUID selectedCardId,
                                     ItemStack hostStack, List<AttachmentSyncEntry> attachments) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<OpenAttachmentsS2C> ID =
                new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("mtgcard", "card_display_attachments_open"));

        public static final StreamCodec<RegistryFriendlyByteBuf, OpenAttachmentsS2C> CODEC =
                StreamCodec.ofMember(
                        (payload, buf) -> {
                            buf.writeVarInt(payload.entityId());
                            buf.writeUUID(payload.hostId());
                            buf.writeLong(payload.version());
                            buf.writeUUID(payload.selectedCardId());
                            ItemStack.STREAM_CODEC.encode(buf, payload.hostStack());
                            writeAttachmentEntries(buf, payload.attachments());
                        },
                        buf -> new OpenAttachmentsS2C(
                                buf.readVarInt(),
                                buf.readUUID(),
                                buf.readLong(),
                                buf.readUUID(),
                                ItemStack.STREAM_CODEC.decode(buf),
                                readAttachmentEntries(buf)
                        )
                );

        public OpenAttachmentsS2C {
            hostId = hostId == null ? new UUID(0L, 0L) : hostId;
            selectedCardId = selectedCardId == null ? hostId : selectedCardId;
            hostStack = hostStack == null ? ItemStack.EMPTY : hostStack.copy();
            attachments = attachments == null ? List.of() : List.copyOf(attachments);
        }

        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    // ---------- S2C: close/resync failure ----------
    public record CloseDisplayScreensS2C(String message) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<CloseDisplayScreensS2C> ID =
                new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("mtgcard", "card_display_close_screens"));

        public static final StreamCodec<RegistryFriendlyByteBuf, CloseDisplayScreensS2C> CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.STRING_UTF8, CloseDisplayScreensS2C::message,
                        CloseDisplayScreensS2C::new
                );

        public CloseDisplayScreensS2C {
            message = message == null ? "" : message;
        }

        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    // ---------- C2S: set face ----------
    public record DisplaySetFaceC2S(int entityId, UUID hostId, UUID cardId, int face) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<DisplaySetFaceC2S> ID =
                new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("mtgcard", "card_display_set_face"));

        public static final StreamCodec<RegistryFriendlyByteBuf, DisplaySetFaceC2S> CODEC =
                StreamCodec.ofMember(
                        (payload, buf) -> {
                            buf.writeVarInt(payload.entityId());
                            buf.writeUUID(payload.hostId());
                            buf.writeUUID(payload.cardId());
                            buf.writeVarInt(payload.face());
                        },
                        buf -> new DisplaySetFaceC2S(buf.readVarInt(), buf.readUUID(), buf.readUUID(), buf.readVarInt())
                );

        public DisplaySetFaceC2S {
            hostId = hostId == null ? new UUID(0L, 0L) : hostId;
            cardId = cardId == null ? hostId : cardId;
        }

        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    // ---------- C2S: set counter value ----------
    public record DisplaySetCounterValueC2S(int entityId, UUID hostId, UUID cardId, String key, int value) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<DisplaySetCounterValueC2S> ID =
                new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("mtgcard", "card_display_set_counter_value"));

        public static final StreamCodec<RegistryFriendlyByteBuf, DisplaySetCounterValueC2S> CODEC =
                StreamCodec.ofMember(
                        (payload, buf) -> {
                            buf.writeVarInt(payload.entityId());
                            buf.writeUUID(payload.hostId());
                            buf.writeUUID(payload.cardId());
                            buf.writeUtf(payload.key());
                            buf.writeVarInt(payload.value());
                        },
                        buf -> new DisplaySetCounterValueC2S(
                                buf.readVarInt(),
                                buf.readUUID(),
                                buf.readUUID(),
                                buf.readUtf(),
                                buf.readVarInt()
                        )
                );

        public DisplaySetCounterValueC2S {
            hostId = hostId == null ? new UUID(0L, 0L) : hostId;
            cardId = cardId == null ? hostId : cardId;
            key = key == null ? "" : key;
        }

        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    // ---------- C2S: delete counter key ----------
    public record DisplayDeleteCounterC2S(int entityId, UUID hostId, UUID cardId, String key) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<DisplayDeleteCounterC2S> ID =
                new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("mtgcard", "card_display_delete_counter"));

        public static final StreamCodec<RegistryFriendlyByteBuf, DisplayDeleteCounterC2S> CODEC =
                StreamCodec.ofMember(
                        (payload, buf) -> {
                            buf.writeVarInt(payload.entityId());
                            buf.writeUUID(payload.hostId());
                            buf.writeUUID(payload.cardId());
                            buf.writeUtf(payload.key());
                        },
                        buf -> new DisplayDeleteCounterC2S(buf.readVarInt(), buf.readUUID(), buf.readUUID(), buf.readUtf())
                );

        public DisplayDeleteCounterC2S {
            hostId = hostId == null ? new UUID(0L, 0L) : hostId;
            cardId = cardId == null ? hostId : cardId;
            key = key == null ? "" : key;
        }

        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    // ---------- C2S: set hidden ----------
    public record DisplaySetHiddenC2S(int entityId, UUID hostId, UUID cardId, boolean hidden) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<DisplaySetHiddenC2S> ID =
                new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("mtgcard", "card_display_set_hidden"));

        public static final StreamCodec<RegistryFriendlyByteBuf, DisplaySetHiddenC2S> CODEC =
                StreamCodec.ofMember(
                        (payload, buf) -> {
                            buf.writeVarInt(payload.entityId());
                            buf.writeUUID(payload.hostId());
                            buf.writeUUID(payload.cardId());
                            buf.writeBoolean(payload.hidden());
                        },
                        buf -> new DisplaySetHiddenC2S(buf.readVarInt(), buf.readUUID(), buf.readUUID(), buf.readBoolean())
                );

        public DisplaySetHiddenC2S {
            hostId = hostId == null ? new UUID(0L, 0L) : hostId;
            cardId = cardId == null ? hostId : cardId;
        }

        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }


    // ---------- C2S: set counter meta ----------
    public record DisplaySetCounterMetaC2S(int entityId, UUID hostId, UUID cardId, String key, String displayName, String iconKey) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<DisplaySetCounterMetaC2S> ID =
                new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("mtgcard", "card_display_set_counter_meta"));

        public static final StreamCodec<RegistryFriendlyByteBuf, DisplaySetCounterMetaC2S> CODEC =
                StreamCodec.ofMember(
                        (payload, buf) -> {
                            buf.writeVarInt(payload.entityId());
                            buf.writeUUID(payload.hostId());
                            buf.writeUUID(payload.cardId());
                            buf.writeUtf(payload.key());
                            buf.writeUtf(payload.displayName());
                            buf.writeUtf(payload.iconKey());
                        },
                        buf -> new DisplaySetCounterMetaC2S(
                                buf.readVarInt(),
                                buf.readUUID(),
                                buf.readUUID(),
                                buf.readUtf(),
                                buf.readUtf(),
                                buf.readUtf()
                        )
                );

        public DisplaySetCounterMetaC2S {
            hostId = hostId == null ? new UUID(0L, 0L) : hostId;
            cardId = cardId == null ? hostId : cardId;
            key = key == null ? "" : key;
            displayName = displayName == null ? "" : displayName;
            iconKey = iconKey == null ? "none" : iconKey;
        }

        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    // ---------- C2S: open attachment manager ----------
    public record OpenAttachmentsC2S(int entityId, UUID hostId, UUID selectedCardId) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<OpenAttachmentsC2S> ID =
                new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("mtgcard", "card_display_attachments_request"));

        public static final StreamCodec<RegistryFriendlyByteBuf, OpenAttachmentsC2S> CODEC =
                StreamCodec.ofMember(
                        (payload, buf) -> {
                            buf.writeVarInt(payload.entityId());
                            buf.writeUUID(payload.hostId());
                            buf.writeUUID(payload.selectedCardId());
                        },
                        buf -> new OpenAttachmentsC2S(buf.readVarInt(), buf.readUUID(), buf.readUUID())
                );

        public OpenAttachmentsC2S {
            hostId = hostId == null ? new UUID(0L, 0L) : hostId;
            selectedCardId = selectedCardId == null ? hostId : selectedCardId;
        }

        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    // ---------- C2S: detach attachment ----------
    public record AttachmentDetachC2S(int entityId, UUID hostId, long version, UUID attachmentId,
                                      List<UUID> currentOrder) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<AttachmentDetachC2S> ID =
                new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("mtgcard", "card_display_attachment_detach"));

        public static final StreamCodec<RegistryFriendlyByteBuf, AttachmentDetachC2S> CODEC =
                StreamCodec.ofMember(
                        (payload, buf) -> {
                            buf.writeVarInt(payload.entityId());
                            buf.writeUUID(payload.hostId());
                            buf.writeLong(payload.version());
                            buf.writeUUID(payload.attachmentId());
                            writeUuidList(buf, payload.currentOrder());
                        },
                        buf -> new AttachmentDetachC2S(
                                buf.readVarInt(),
                                buf.readUUID(),
                                buf.readLong(),
                                buf.readUUID(),
                                readUuidList(buf)
                        )
                );

        public AttachmentDetachC2S {
            hostId = hostId == null ? new UUID(0L, 0L) : hostId;
            attachmentId = attachmentId == null ? new UUID(0L, 0L) : attachmentId;
            currentOrder = currentOrder == null ? List.of() : List.copyOf(currentOrder);
        }

        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    // ---------- C2S: save/reorder attachments ----------
    public record AttachmentReorderC2S(int entityId, UUID hostId, long version, List<UUID> order,
                                       UUID selectedCardId, boolean returnToLargeView) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<AttachmentReorderC2S> ID =
                new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("mtgcard", "card_display_attachment_reorder"));

        public static final StreamCodec<RegistryFriendlyByteBuf, AttachmentReorderC2S> CODEC =
                StreamCodec.ofMember(
                        (payload, buf) -> {
                            buf.writeVarInt(payload.entityId());
                            buf.writeUUID(payload.hostId());
                            buf.writeLong(payload.version());
                            writeUuidList(buf, payload.order());
                            buf.writeUUID(payload.selectedCardId());
                            buf.writeBoolean(payload.returnToLargeView());
                        },
                        buf -> new AttachmentReorderC2S(
                                buf.readVarInt(),
                                buf.readUUID(),
                                buf.readLong(),
                                readUuidList(buf),
                                buf.readUUID(),
                                buf.readBoolean()
                        )
                );

        public AttachmentReorderC2S {
            hostId = hostId == null ? new UUID(0L, 0L) : hostId;
            order = order == null ? List.of() : List.copyOf(order);
            selectedCardId = selectedCardId == null ? hostId : selectedCardId;
        }

        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    private static void writeAttachmentEntries(RegistryFriendlyByteBuf buf, List<AttachmentSyncEntry> entries) {
        List<AttachmentSyncEntry> safe = entries == null ? List.of() : entries;
        buf.writeVarInt(safe.size());
        for (AttachmentSyncEntry entry : safe) {
            buf.writeUUID(entry.id());
            ItemStack.STREAM_CODEC.encode(buf, entry.stack());
            buf.writeVarInt(entry.rotStep());
        }
    }

    private static List<AttachmentSyncEntry> readAttachmentEntries(RegistryFriendlyByteBuf buf) {
        int rawCount = Math.max(0, buf.readVarInt());
        int count = Math.min(16, rawCount);
        ArrayList<AttachmentSyncEntry> entries = new ArrayList<>(count);
        for (int i = 0; i < rawCount; i++) {
            UUID id = buf.readUUID();
            ItemStack stack = ItemStack.STREAM_CODEC.decode(buf);
            int rotStep = buf.readVarInt();
            if (i < 16) {
                entries.add(new AttachmentSyncEntry(id, stack, rotStep));
            }
        }
        return List.copyOf(entries);
    }

    private static void writeUuidList(RegistryFriendlyByteBuf buf, List<UUID> ids) {
        List<UUID> safe = ids == null ? List.of() : ids;
        buf.writeVarInt(safe.size());
        for (UUID id : safe) {
            buf.writeUUID(id == null ? new UUID(0L, 0L) : id);
        }
    }

    private static List<UUID> readUuidList(RegistryFriendlyByteBuf buf) {
        int rawCount = Math.max(0, buf.readVarInt());
        int count = Math.min(16, rawCount);
        ArrayList<UUID> ids = new ArrayList<>(count);
        for (int i = 0; i < rawCount; i++) {
            UUID id = buf.readUUID();
            if (i < 16) {
                ids.add(id);
            }
        }
        return List.copyOf(ids);
    }

    private CardDisplayPayloads() {}
}
