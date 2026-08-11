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
        f.typesAny = readStringSet(buf);
        f.typesAll = readStringSet(buf);
        f.rarities = readStringSet(buf);
        f.sets = readStringSet(buf);
        f.mvMin = buf.readVarInt(); f.mvMax = buf.readVarInt();
        f.sortBy = SortBy.values()[buf.readVarInt()]; f.sortAsc = buf.readBoolean(); f.pageSize = buf.readVarInt();
        return f;
    }
    public void write(FriendlyByteBuf buf){
        int bits=0; for (Color c: colorsAny) bits |= (1<<c.bit); buf.writeVarInt(bits);
        buf.writeBoolean(colorExact); buf.writeBoolean(useColorIdentity);
        writeStringSet(buf, typesAny);
        writeStringSet(buf, typesAll);
        writeStringSet(buf, rarities);
        writeStringSet(buf, sets);
        buf.writeVarInt(mvMin); buf.writeVarInt(mvMax);
        buf.writeVarInt(sortBy.ordinal()); buf.writeBoolean(sortAsc); buf.writeVarInt(pageSize);
    }
    private static Set<String> readStringSet(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        Set<String> values = new HashSet<>(size);
        for (int i = 0; i < size; i++) {
            values.add(buf.readUtf());
        }
        return values;
    }
    private static void writeStringSet(FriendlyByteBuf buf, Set<String> values) {
        buf.writeVarInt(values.size());
        for (String value : values) {
            buf.writeUtf(value);
        }
    }
    public enum Color { W(0),U(1),B(2),R(3),G(4); public final int bit; Color(int b){bit=b;} }
    public enum SortBy { NAME, SET, RARITY, MV, TIMESTAMP }
}
