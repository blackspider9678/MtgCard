// src/main/java/com/spider/mtgcard/content/pack/custom/CustomCardItems.java
package com.spider.mtgcard.content.pack.custom;

import com.spider.mtgcard.util.StackData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;

public final class CustomCardItems {

    /** Give a custom card item to a player, using server-side metadata. */
    public static void give(ServerPlayer to, CustomCardStore.CardMeta m, boolean foil) {
        ItemStack stack = new ItemStack(com.spider.mtgcard.item.ModItems.CARD);

        // Build mtg_meta
        CompoundTag meta = new CompoundTag();
        meta.putString("id", m.id); // critical: makes artKey = id + "_fX"
        meta.putString("name", nz(m.name));
        meta.putString("mana_cost", nz(m.manaCost));
        meta.putString("type_line", nz(m.typeLine));
        meta.putString("rarity", nz(m.rarity));
        meta.putString("set", nz(m.set));
        meta.putString("oracle_text", nz(m.oracleText));
        meta.putString("power", nz(m.power));
        meta.putString("toughness", nz(m.toughness));
        meta.putString("loyalty", nz(m.loyalty));

        if (m.doubleFaced) {
            ListTag faces = new ListTag();

            CompoundTag f0 = new CompoundTag();
            f0.putString("name", nz(m.name));
            f0.putString("type_line", nz(m.typeLine));
            f0.putString("oracle_text", nz(m.oracleText));
            f0.putString("power", nz(m.power));
            f0.putString("toughness", nz(m.toughness));
            f0.putString("loyalty", nz(m.loyalty));
            // ✅ per-face art key (front)
            if (m.artKeyFront != null && !m.artKeyFront.isBlank()) {
                f0.putString("world_art", m.artKeyFront);
            }
            faces.add(f0);

            CompoundTag f1 = new CompoundTag();
            f1.putString("name", nz(m.backName));
            f1.putString("type_line", nz(m.backTypeLine));
            f1.putString("oracle_text", nz(m.backOracleText));
            f1.putString("power", nz(m.backPower));
            f1.putString("toughness", nz(m.backToughness));
            f1.putString("loyalty", nz(m.backLoyalty));
            // ✅ per-face art key (back)
            if (m.artKeyBack != null && !m.artKeyBack.isBlank()) {
                f1.putString("world_art", m.artKeyBack);
            }
            faces.add(f1);

            meta.put("card_faces", faces);
        }
        if (m.artKeyFront != null && !m.artKeyFront.isBlank()) {
            meta.putString("world_art_front", m.artKeyFront);
            meta.putString("world_art", m.artKeyFront); // fallback
        }
        if (m.artKeyBack != null && !m.artKeyBack.isBlank()) {
            meta.putString("world_art_back", m.artKeyBack);
        }


        // Root CUSTOM_DATA
        CompoundTag root = new CompoundTag();
        root.put("mtg_meta", meta);
        root.putBoolean("mtg_foil", foil);
        StackData.writeCustom(stack, root);

        to.getInventory().add(stack);
    }

    private static String nz(String s) { return s == null ? "" : s; }

    private CustomCardItems() {}
}
