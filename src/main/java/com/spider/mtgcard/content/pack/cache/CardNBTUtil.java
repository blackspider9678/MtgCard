package com.spider.mtgcard.content.pack.cache;

import com.spider.mtgcard.util.StackData;
import com.spider.mtgcard.util.TcgCardMeta;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

import java.util.UUID;

public final class CardNBTUtil {

    /**
     * Write all stable MTG data into CUSTOM_DATA:
     * - root (CUSTOM_DATA): mtg_uid (string), mtg_foil (boolean)
     * - meta subtag: "mtg_meta" (card identity, faces, prices, links, flags)
     */
    public static void populateCardNbt(ItemStack st, ScryfallModels.Card rec, UUID instanceUuid, boolean foil) {
        // Read current CUSTOM_DATA (mutable copy)
        CompoundTag root = StackData.readCustom(st);

        // Ensure/obtain mtg_meta subcompound (Optionals in your mappings)
        CompoundTag meta = root.getCompound("mtg_meta").orElseGet(CompoundTag::new);

        // ---------- Identity ----------
        putStr(meta, "id",          rec.id);
        putStr(meta, "scryfall_id", rec.id);
        putStr(meta, "oracle_id",    rec.oracleId);
        putStr(meta, "name",         rec.name);
        putStr(meta, "set",          rec.set);
        putStr(meta, "collector_number", rec.collectorNumber);
        putStr(meta, "rarity",       rec.rarity);
        putStr(meta, "layout",       rec.layout);
        putStr(meta, "type_line",    rec.typeLine);
        putStr(meta, "power",        nz(rec.power));
        putStr(meta, "toughness",    nz(rec.toughness));
        meta.putInt("cmc",           rec.manaValue);

        // ---------- Colors ----------
        ListTag colors = new ListTag();
        if (rec.colors != null) for (String c : rec.colors) colors.add(net.minecraft.nbt.StringTag.valueOf(c));
        meta.put("colors", colors);

        ListTag cids = new ListTag();
        if (rec.colorIdentity != null) for (String c : rec.colorIdentity) cids.add(net.minecraft.nbt.StringTag.valueOf(c));
        meta.put("color_identity", cids);

        // ---------- Legalities ----------
        CompoundTag leg = new CompoundTag();
        if (rec.legalities != null) {
            for (var e : rec.legalities.entrySet()) {
                putStr(leg, e.getKey(), e.getValue());
            }
        }
        meta.put("legalities", leg);

        // ---------- Images (faces or single) ----------
        if (rec.faces != null && !rec.faces.isEmpty()) {
            ListTag faceList = new ListTag();
            for (var f : rec.faces) {
                CompoundTag fn = new CompoundTag();
                putStr(fn, "name", nz(f.name));

                CompoundTag uris = new CompoundTag();
                if (f.imageUris != null) {
                    for (var e : f.imageUris.entrySet()) putStr(uris, e.getKey(), e.getValue());
                }
                fn.put("image_uris", uris);

                if (f.manaCost   != null) putStr(fn, "mana_cost",   f.manaCost);
                if (f.typeLine   != null) putStr(fn, "type_line",   f.typeLine);
                if (f.oracleText != null) putStr(fn, "oracle_text", f.oracleText);
                if (f.power      != null) putStr(fn, "power",       f.power);
                if (f.toughness  != null) putStr(fn, "toughness",   f.toughness);

                faceList.add(fn);
            }
            meta.put("card_faces", faceList);
        } else {
            CompoundTag uris = new CompoundTag();
            if (rec.imageUris != null) {
                for (var e : rec.imageUris.entrySet()) putStr(uris, e.getKey(), e.getValue());
            }
            meta.put("image_uris", uris);
        }

        // ---------- Prices ----------
        // ---------- Prices (strings like Scryfall, "—" means missing) ----------
        CompoundTag prices = new CompoundTag();
        if (rec.price != null) {
            putPriceStr(prices, "usd",        rec.price.usd);
            putPriceStr(prices, "usd_foil",   rec.price.usdFoil);
            putPriceStr(prices, "usd_etched", rec.price.usdEtched);
            putPriceStr(prices, "eur",        rec.price.eur);
            putPriceStr(prices, "eur_foil",   rec.price.eurFoil);
            putPriceStr(prices, "tix",        rec.price.tix);
        }
        meta.put("prices", prices);

        // ---------- Links ----------
        if (rec.scryfallUri != null) putStr(meta, "scryfall_uri", rec.scryfallUri);

        // ---------- Flags ----------
        meta.putBoolean("is_token_like", rec.isTokenLike);
        meta.putBoolean("is_legendary", rec.typeLine != null && rec.typeLine.toLowerCase().contains("legendary"));

        // Write meta back into root
        root.put("mtg_meta", meta);

        // ---------- Instance-level flags on root ----------
        putStr(root, "mtg_uid",  instanceUuid == null ? "" : instanceUuid.toString()); // store UUID as string (cross-mappings safe)
        root.putBoolean("mtg_foil", foil);
        TcgCardMeta.mirrorMtgToTcg(root);

        // Save back to CUSTOM_DATA
        StackData.writeCustom(st, root);
    }

    private static void putPriceStr(CompoundTag tag, String key, String value) {
        if (value == null) return;
        String v = value.trim();
        if (v.isEmpty() || v.equals("—") || v.equals("-")) return;
        tag.putString(key, v);
    }

    // ----------------- helpers -----------------
    private static void putStr(CompoundTag tag, String key, String value) {
        tag.putString(key, value == null ? "" : value);
    }
    private static String nz(String s) { return s == null ? "" : s; }

    private CardNBTUtil() {}
}
