package com.spider.mtgcard.db.search;


import net.minecraft.network.FriendlyByteBuf; import java.util.*;


public class Filters {
    public EnumSet<Color> colorsAny = EnumSet.noneOf(Color.class);
    public boolean colorExact = false, useColorIdentity = false;
    public Set<String> typesAny = new HashSet<>();
    public Set<String> typesAll = new HashSet<>();
    public Set<String> rarities = new HashSet<>();
    public Set<String> sets = new HashSet<>();
    public int mvMin = 0, mvMax = 20;


    // Sorting & page size
    public SortBy sortBy = SortBy.NAME; public boolean sortAsc = true; public int pageSize = 50;


    public static Filters fromBuf(FriendlyByteBuf buf){
        Filters f = new Filters();
        int colorBits = buf.readVarInt(); for (Color c: Color.values()) if(((colorBits>>c.bit)&1)==1) f.colorsAny.add(c);
        f.colorExact = buf.readBoolean(); f.useColorIdentity = buf.readBoolean();
        f.typesAny = new HashSet<>(buf.readCollection(ArrayList::new, FriendlyByteBuf::readUtf));
        f.typesAll = new HashSet<>(buf.readCollection(ArrayList::new, FriendlyByteBuf::readUtf));
        f.rarities = new HashSet<>(buf.readCollection(ArrayList::new, FriendlyByteBuf::readUtf));
        f.sets = new HashSet<>(buf.readCollection(ArrayList::new, FriendlyByteBuf::readUtf));
        f.mvMin = buf.readVarInt(); f.mvMax = buf.readVarInt();
        f.sortBy = SortBy.values()[buf.readVarInt()]; f.sortAsc = buf.readBoolean(); f.pageSize = buf.readVarInt();
        return f;
    }
    public void write(FriendlyByteBuf buf){
        int bits=0; for (Color c: colorsAny) bits |= (1<<c.bit); buf.writeVarInt(bits);
        buf.writeBoolean(colorExact); buf.writeBoolean(useColorIdentity);
        buf.writeCollection(typesAny, FriendlyByteBuf::writeUtf);
        buf.writeCollection(typesAll, FriendlyByteBuf::writeUtf);
        buf.writeCollection(rarities, FriendlyByteBuf::writeUtf);
        buf.writeCollection(sets, FriendlyByteBuf::writeUtf);
        buf.writeVarInt(mvMin); buf.writeVarInt(mvMax);
        buf.writeVarInt(sortBy.ordinal()); buf.writeBoolean(sortAsc); buf.writeVarInt(pageSize);
    }
    public enum Color { W(0),U(1),B(2),R(3),G(4); public final int bit; Color(int b){bit=b;} }
    public enum SortBy { NAME, SET, RARITY, MV, TIMESTAMP }
}