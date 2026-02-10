package com.spider.mtgcard.db.search;


import net.minecraft.network.PacketByteBuf;


public record PageCursor(int offset, int pageSize){
    public static PageCursor fromBuf(PacketByteBuf buf){
        int off = buf.readVarInt(); int size = buf.readVarInt(); return new PageCursor(off, size);
    }
    public void write(PacketByteBuf buf){
        buf.writeVarInt(offset); buf.writeVarInt(pageSize);
    }
}