package com.spider.mtgcard.db.search;


import net.minecraft.network.PacketByteBuf; import java.util.*;


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


    public static Filters fromBuf(PacketByteBuf buf){
        Filters f = new Filters();
        int colorBits = buf.readVarInt(); for (Color c: Color.values()) if(((colorBits>>c.bit)&1)==1) f.colorsAny.add(c);
        f.colorExact = buf.readBoolean(); f.useColorIdentity = buf.readBoolean();
        f.typesAny = new HashSet<>(buf.readCollection(ArrayList::new, PacketByteBuf::readString));
        f.typesAll = new HashSet<>(buf.readCollection(ArrayList::new, PacketByteBuf::readString));
        f.rarities = new HashSet<>(buf.readCollection(ArrayList::new, PacketByteBuf::readString));
        f.sets = new HashSet<>(buf.readCollection(ArrayList::new, PacketByteBuf::readString));
        f.mvMin = buf.readVarInt(); f.mvMax = buf.readVarInt();
        f.sortBy = SortBy.values()[buf.readVarInt()]; f.sortAsc = buf.readBoolean(); f.pageSize = buf.readVarInt();
        return f;
    }
    public void write(PacketByteBuf buf){
        int bits=0; for (Color c: colorsAny) bits |= (1<<c.bit); buf.writeVarInt(bits);
        buf.writeBoolean(colorExact); buf.writeBoolean(useColorIdentity);
        buf.writeCollection(typesAny, PacketByteBuf::writeString);
        buf.writeCollection(typesAll, PacketByteBuf::writeString);
        buf.writeCollection(rarities, PacketByteBuf::writeString);
        buf.writeCollection(sets, PacketByteBuf::writeString);
        buf.writeVarInt(mvMin); buf.writeVarInt(mvMax);
        buf.writeVarInt(sortBy.ordinal()); buf.writeBoolean(sortAsc); buf.writeVarInt(pageSize);
    }
    public enum Color { W(0),U(1),B(2),R(3),G(4); public final int bit; Color(int b){bit=b;} }
    public enum SortBy { NAME, SET, RARITY, MV, TIMESTAMP }
}