package com.spider.mtgcard.net.payload;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

public record DeckboxTabNamesPayload(int syncId, List<String> tabNames) implements CustomPacketPayload {
    public static final Type<DeckboxTabNamesPayload> ID =
            new Type<>(Identifier.fromNamespaceAndPath("mtgcard", "deckbox_tab_names"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DeckboxTabNamesPayload> CODEC =
            StreamCodec.ofMember(
                    (value, buf) -> DeckboxTabNamesPayload.write(buf, value),
                    DeckboxTabNamesPayload::read
            );

    @Override public Type<? extends CustomPacketPayload> type() { return ID; }

    public DeckboxTabNamesPayload {
        tabNames = tabNames == null ? List.of() : List.copyOf(tabNames);
    }

    public int entryCount() {
        return tabNames.size() / 2;
    }

    public String commander(int index) {
        return at(index * 2);
    }

    public String partner(int index) {
        return at(index * 2 + 1);
    }

    private String at(int index) {
        return index >= 0 && index < tabNames.size() ? tabNames.get(index) : "";
    }

    private static DeckboxTabNamesPayload read(RegistryFriendlyByteBuf buf) {
        int syncId = buf.readVarInt();
        int n = buf.readVarInt();
        List<String> list = new ArrayList<>(n * 2);
        for (int i = 0; i < n; i++) {
            String c = buf.readUtf(32767);
            String p = buf.readUtf(32767);
            list.add(c);
            list.add(p);
        }
        return new DeckboxTabNamesPayload(syncId, list);
    }

    private static void write(RegistryFriendlyByteBuf buf, DeckboxTabNamesPayload p) {
        buf.writeVarInt(p.syncId());
        buf.writeVarInt(p.entryCount());
        for (int i = 0; i < p.entryCount(); i++) {
            buf.writeUtf(p.commander(i) == null ? "" : p.commander(i));
            buf.writeUtf(p.partner(i) == null ? "" : p.partner(i));
        }
    }
}
