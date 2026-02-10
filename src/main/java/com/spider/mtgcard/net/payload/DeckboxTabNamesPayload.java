package com.spider.mtgcard.net.payload;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

public record DeckboxTabNamesPayload(int syncId, List<Entry> entries) implements CustomPayload {
    public static final Id<DeckboxTabNamesPayload> ID =
            new Id<>(Identifier.of("mtgcard", "deckbox_tab_names"));

    public record Entry(String commander, String partner) {}

    public static final PacketCodec<RegistryByteBuf, DeckboxTabNamesPayload> CODEC =
            PacketCodec.of(
                    (value, buf) -> DeckboxTabNamesPayload.write(buf, value),
                    DeckboxTabNamesPayload::read
            );

    @Override public Id<? extends CustomPayload> getId() { return ID; }

    private static DeckboxTabNamesPayload read(RegistryByteBuf buf) {
        int syncId = buf.readVarInt();
        int n = buf.readVarInt();
        List<Entry> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            String c = buf.readString(32767);
            String p = buf.readString(32767);
            list.add(new Entry(c, p));
        }
        return new DeckboxTabNamesPayload(syncId, list);
    }

    private static void write(RegistryByteBuf buf, DeckboxTabNamesPayload p) {
        buf.writeVarInt(p.syncId());
        buf.writeVarInt(p.entries().size());
        for (Entry e : p.entries()) {
            buf.writeString(e.commander() == null ? "" : e.commander());
            buf.writeString(e.partner() == null ? "" : e.partner());
        }
    }
}
