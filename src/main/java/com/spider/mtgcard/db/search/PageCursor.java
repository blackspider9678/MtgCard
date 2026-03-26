package com.spider.mtgcard.db.search;


import net.minecraft.network.FriendlyByteBuf;


public record PageCursor(int offset, int pageSize){
    public static PageCursor fromBuf(FriendlyByteBuf buf){
        int off = buf.readVarInt(); int size = buf.readVarInt(); return new PageCursor(off, size);
    }
    public void write(FriendlyByteBuf buf){
        buf.writeVarInt(offset); buf.writeVarInt(pageSize);
    }
}