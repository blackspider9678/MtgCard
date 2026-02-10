package com.spider.mtgcard.db.search;


import net.minecraft.network.PacketByteBuf; import java.util.*;


public class SearchResults {
    public List<IndexRecord> items = new ArrayList<>(); public int total; public PageCursor next;
    public void write(PacketByteBuf buf){
        buf.writeVarInt(total); buf.writeVarInt(items.size());
        for (IndexRecord r: items){
            buf.writeString(r.id); buf.writeString(r.name); buf.writeString(r.set);
            buf.writeString(r.rarity); buf.writeVarInt(r.mv); buf.writeString(r.imageSmall==null?"":r.imageSmall);
            buf.writeVarInt(r.colors==null?0:r.colors.size()); if(r.colors!=null) for(String c: r.colors) buf.writeString(c);
        }
        if (next==null){ buf.writeBoolean(false); } else { buf.writeBoolean(true); next.write(buf); }
    }
}