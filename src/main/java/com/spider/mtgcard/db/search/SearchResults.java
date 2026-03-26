package com.spider.mtgcard.db.search;


import net.minecraft.network.FriendlyByteBuf; import java.util.*;


public class SearchResults {
    public List<IndexRecord> items = new ArrayList<>(); public int total; public PageCursor next;
    public void write(FriendlyByteBuf buf){
        buf.writeVarInt(total); buf.writeVarInt(items.size());
        for (IndexRecord r: items){
            buf.writeUtf(r.id); buf.writeUtf(r.name); buf.writeUtf(r.set);
            buf.writeUtf(r.rarity); buf.writeVarInt(r.mv); buf.writeUtf(r.imageSmall==null?"":r.imageSmall);
            buf.writeVarInt(r.colors==null?0:r.colors.size()); if(r.colors!=null) for(String c: r.colors) buf.writeUtf(c);
        }
        if (next==null){ buf.writeBoolean(false); } else { buf.writeBoolean(true); next.write(buf); }
    }
}