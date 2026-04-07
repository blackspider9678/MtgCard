package com.spider.mtgcard.net.payload;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

public record DeckboxTabNamesPayload(int syncId, List<Entry> entries) implements CustomPacketPayload {
    public static final Type<DeckboxTabNamesPayload> ID =
            new Type<>(Identifier.fromNamespaceAndPath("mtgcard", "deckbox_tab_names"));

    public record Entry(String commander, String partner) {}

    public static final StreamCodec<RegistryFriendlyByteBuf, DeckboxTabNamesPayload> CODEC =
            StreamCodec.ofMember(
                    (value, buf) -> DeckboxTabNamesPayload.write(buf, value),
                    DeckboxTabNamesPayload::read
            );

    @Override public Type<? extends CustomPacketPayload> type() { return ID; }

    private static DeckboxTabNamesPayload read(RegistryFriendlyByteBuf buf) {
        int syncId = buf.readVarInt();
        int n = buf.readVarInt();
        List<Entry> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            String c = buf.readUtf(32767);
            String p = buf.readUtf(32767);
            list.add(new Entry(c, p));
        }
        return new DeckboxTabNamesPayload(syncId, list);
    }

    private static void write(RegistryFriendlyByteBuf buf, DeckboxTabNamesPayload p) {
        buf.writeVarInt(p.syncId());
        buf.writeVarInt(p.entries().size());
        for (Entry e : p.entries()) {
            buf.writeUtf(e.commander() == null ? "" : e.commander());
            buf.writeUtf(e.partner() == null ? "" : e.partner());
        }
    }
}
