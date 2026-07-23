package com.spider.mtgcard.util;

import com.spider.mtgcard.content.pack.cache.ScryfallModels;
import com.spider.mtgcard.item.ModItems;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class CardStackBuilders {

    private CardStackBuilders() {}

    public static ItemStack buildCustomStackFromId(String customId, boolean foilVisual) {
        if (customId == null || customId.isBlank()) return ItemStack.EMPTY;

        ItemStack card = new ItemStack(ModItems.CARD);

        CompoundTag meta = new CompoundTag();
        meta.putString("id", customId);

        // Mark + identify
        meta.putBoolean("is_custom", true);
        meta.putString("custom_id", customId);

        String frontKey = customWorldKey(customId, 0);
        String backKey  = customWorldKey(customId, 1);

        meta.putString("world_art_front", frontKey);
        meta.putString("world_art",       frontKey);
        meta.putString("world_art_back",  backKey);

        // Optional: useful for UI/purchase identity
        meta.putString("set", "cstm");
        meta.putString("collector_number", customId);

        meta.putInt("mtg_face", 0);
        meta.putString("mtg_uid", UUID.randomUUID().toString());

        CompoundTag root = new CompoundTag();
        root.put("mtg_meta", meta);
        TcgCardMeta.mirrorMtgToTcg(root);
        card.set(DataComponents.CUSTOM_DATA, CustomData.of(root));

        // ✅ Give ID-only customs a visible debug name (since no meta.name here)
        applyColoredNameIfUnset(card, "Custom " + customId, "special");

        if (foilVisual) {
            card.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        }

        return card;
    }

    public static ItemStack buildCustomStackFromMeta(com.spider.mtgcard.content.pack.custom.CustomCardStore.CardMeta m, boolean foilVisual) {
        if (m == null || m.id == null || m.id.isBlank()) return ItemStack.EMPTY;

        ItemStack card = new ItemStack(ModItems.CARD);
        CompoundTag meta = new CompoundTag();

        meta.putString("id", m.id);

        meta.putBoolean("is_custom", true);
        meta.putString("custom_id", m.id);

        // World art keys:
        String frontKey = normalizeCustomKey(m.artKeyFront, m.id, 0);
        String backKey  = normalizeCustomKey(m.artKeyBack,  m.id, 1);

        meta.putString("world_art_front", frontKey);
        meta.putString("world_art",       frontKey);
        meta.putString("world_art_back",  backKey);

        String displayName = customDisplayName(m);

        // Mirror Scryfall-style fields so UI/search behaves consistently
        if (displayName != null) meta.putString("name", displayName);
        if (m.manaCost != null) meta.putString("mana_cost", m.manaCost);
        if (m.typeLine != null) meta.putString("type_line", m.typeLine);
        if (m.oracleText != null) meta.putString("oracle_text", m.oracleText);
        if (m.power != null) meta.putString("power", m.power);
        if (m.toughness != null) meta.putString("toughness", m.toughness);
        if (m.loyalty != null) meta.putString("loyalty", m.loyalty);
        if (m.rarity != null) meta.putString("rarity", m.rarity);

        if (m.doubleFaced) {
            putCustomFaces(meta, m, frontKey, backKey);
        }

        String setCode = (m.set == null || m.set.isBlank()) ? "cstm" : m.set.toLowerCase(java.util.Locale.ROOT);
        meta.putString("set", setCode);
        meta.putString("collector_number", m.id);

        meta.putInt("mtg_face", 0);
        meta.putString("mtg_uid", UUID.randomUUID().toString());

        CompoundTag root = new CompoundTag();
        root.put("mtg_meta", meta);
        TcgCardMeta.mirrorMtgToTcg(root);
        card.set(DataComponents.CUSTOM_DATA, CustomData.of(root));

        // ✅ IMPORTANT: give it a visible name so it’s not just "Card"
        applyColoredNameIfUnset(card, displayName, m.rarity);

        if (foilVisual) card.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        return card;
    }

    private static void putCustomFaces(
            CompoundTag meta,
            com.spider.mtgcard.content.pack.custom.CustomCardStore.CardMeta m,
            String frontKey,
            String backKey
    ) {
        ListTag faces = new ListTag();

        CompoundTag front = new CompoundTag();
        putIfNotBlank(front, "name", m.name);
        putIfNotBlank(front, "mana_cost", m.manaCost);
        putIfNotBlank(front, "type_line", m.typeLine);
        putIfNotBlank(front, "oracle_text", m.oracleText);
        putIfNotBlank(front, "power", m.power);
        putIfNotBlank(front, "toughness", m.toughness);
        putIfNotBlank(front, "loyalty", m.loyalty);
        putIfNotBlank(front, "world_art", frontKey);
        faces.add(front);

        CompoundTag back = new CompoundTag();
        putIfNotBlank(back, "name", m.backName);
        putIfNotBlank(back, "type_line", m.backTypeLine);
        putIfNotBlank(back, "oracle_text", m.backOracleText);
        putIfNotBlank(back, "power", m.backPower);
        putIfNotBlank(back, "toughness", m.backToughness);
        putIfNotBlank(back, "loyalty", m.backLoyalty);
        putIfNotBlank(back, "world_art", backKey);
        faces.add(back);

        meta.put("card_faces", faces);
    }

    private static void putIfNotBlank(CompoundTag tag, String key, String value) {
        if (tag == null || key == null || key.isBlank() || value == null || value.isBlank()) {
            return;
        }
        tag.putString(key, value);
    }

    private static String customDisplayName(com.spider.mtgcard.content.pack.custom.CustomCardStore.CardMeta m) {
        if (m == null) return "";
        String front = m.name == null ? "" : m.name.trim();
        String back = m.backName == null ? "" : m.backName.trim();
        if (m.doubleFaced && !front.isBlank() && !back.isBlank()) {
            return front + " // " + back;
        }
        return front;
    }

    private static String bestImageUrl(Map<String, String> imageUris) {
        if (imageUris == null || imageUris.isEmpty()) return null;
        String png = imageUris.get("png");
        return (png != null && !png.isEmpty()) ? png : null;
    }

    public static ItemStack buildScryfallStackFromModel(ScryfallModels.Card c, boolean foilVisual) {
        ItemStack card = new ItemStack(ModItems.CARD);
        if (c == null || c.id == null || c.id.isBlank()) return ItemStack.EMPTY;

        CompoundTag meta = new CompoundTag();
        if (c.id != null) meta.putString("id", c.id);
        if (c.oracleId != null) meta.putString("oracle_id", c.oracleId);
        if (c.name != null) meta.putString("name", c.name);
        if (c.set != null) meta.putString("set", c.set);
        if (c.collectorNumber != null) meta.putString("collector_number", c.collectorNumber);
        if (c.rarity != null) meta.putString("rarity", c.rarity);
        if (c.layout != null) meta.putString("layout", c.layout);

        if (c.colors != null && !c.colors.isEmpty()) {
            ListTag list = new ListTag();
            for (String col : c.colors) list.add(StringTag.valueOf(col));
            meta.put("colors", list);
        }
        if (c.colorIdentity != null && !c.colorIdentity.isEmpty()) {
            ListTag list = new ListTag();
            for (String col : c.colorIdentity) list.add(StringTag.valueOf(col));
            meta.put("color_identity", list);
        }

        if (c.manaCost != null) meta.putString("mana_cost", c.manaCost);
        if (c.typeLine != null) meta.putString("type_line", c.typeLine);
        if (c.oracleText != null) meta.putString("oracle_text", c.oracleText);
        if (c.power != null) meta.putString("power", c.power);
        if (c.toughness != null) meta.putString("toughness", c.toughness);
        if (c.loyalty != null) meta.putString("loyalty", c.loyalty);
        meta.putInt("mana_value", c.manaValue);

        if (c.legalities != null && !c.legalities.isEmpty()) {
            CompoundTag leg = new CompoundTag();
            for (var e : c.legalities.entrySet()) {
                if (e.getKey() != null && e.getValue() != null) leg.putString(e.getKey(), e.getValue());
            }
            meta.put("legalities", leg);
        }

        if (c.price != null) {
            CompoundTag p = new CompoundTag();
            if (c.price.usd != null)       p.putString("usd", c.price.usd);
            if (c.price.usdFoil != null)   p.putString("usdFoil", c.price.usdFoil);
            if (c.price.usdEtched != null) p.putString("usdEtched", c.price.usdEtched);
            if (c.price.eur != null)       p.putString("eur", c.price.eur);
            if (c.price.eurFoil != null)   p.putString("eurFoil", c.price.eurFoil);
            if (c.price.tix != null)       p.putString("tix", c.price.tix);
            meta.put("price", p);
        }

        if (c.scryfallUri != null) meta.putString("scryfall_uri", c.scryfallUri);
        meta.putBoolean("is_token_like", c.isTokenLike);

        if (c.imageUris != null) {
            String single = bestImageUrl(c.imageUris);
            if (single != null) meta.putString("image_png", single);
        }

        if (c.faces != null && !c.faces.isEmpty()) {
            ListTag faces = new ListTag();
            String firstFacePng = null;

            for (var fModel : c.faces) {
                CompoundTag f = new CompoundTag();
                if (fModel.name != null) f.putString("name", fModel.name);

                String fPng = bestImageUrl(fModel.imageUris);
                if (fPng != null) {
                    f.putString("image_png", fPng);
                    if (firstFacePng == null) firstFacePng = fPng;
                }

                if (fModel.manaCost != null)   f.putString("mana_cost",   fModel.manaCost);
                if (fModel.typeLine != null)   f.putString("type_line",   fModel.typeLine);
                if (fModel.oracleText != null) f.putString("oracle_text", fModel.oracleText);
                if (fModel.power != null)      f.putString("power",       fModel.power);
                if (fModel.toughness != null)  f.putString("toughness",   fModel.toughness);
                if (fModel.loyalty != null)    f.putString("loyalty",     fModel.loyalty);

                faces.add(f);
            }

            meta.put("card_faces", faces);

            if (firstFacePng != null && !meta.contains("image_png")) {
                meta.putString("image_png", firstFacePng);
            }
        }

        meta.putInt("mtg_face", 0);
        meta.putString("mtg_uid", UUID.randomUUID().toString());

        CompoundTag root = new CompoundTag();
        root.put("mtg_meta", meta);
        TcgCardMeta.mirrorMtgToTcg(root);
        card.set(DataComponents.CUSTOM_DATA, CustomData.of(root));

        applyColoredNameIfUnset(card, c.name, c.rarity);
        if (foilVisual) {
            card.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        }
        return card;
    }

    /**
     * ✅ Name rule (updated):
     * - Don't stomp anvil renames (CUSTOM_NAME)
     * - Ensure ITEM_NAME exists for consistent UI/getName()
     * - Use CUSTOM_NAME for colored rarity styling
     */
    /**
     * Name rule:
     * - Never stomp anvil renames (CUSTOM_NAME already present)
     * - Ensure CUSTOM_NAME exists for getName() / UI display
     * - Ensure ITEM_NAME exists as a stable fallback (tooltips/UI consistency)
     */
    public static void applyColoredNameIfUnset(ItemStack stack, String name, String rarity) {
        if (stack == null || stack.isEmpty()) return;
        if (name == null || name.isBlank()) return;

        // If player/anvil already named it, do not override.
        if (stack.has(DataComponents.CUSTOM_NAME)) return;

        TextColor c = colorForRarity(rarity);

        // Display name (what getName() tends to show)
        stack.set(
                DataComponents.CUSTOM_NAME,
                Component.literal(name).setStyle(Style.EMPTY.withColor(c).withItalic(false))
        );

        // Plain fallback name (only if missing)
        if (!stack.has(DataComponents.ITEM_NAME)) {
            stack.set(DataComponents.ITEM_NAME, Component.literal(name));
        }
    }

    private static TextColor colorForRarity(String rarity) {
        if (rarity == null) return TextColor.fromRgb(0xFFFFFF);

        return switch (rarity.trim().toLowerCase(Locale.ROOT)) {
            case "common", "c" -> TextColor.fromRgb(0xFFFFFF);
            case "uncommon", "u" -> TextColor.fromRgb(0x55FFFF);
            case "rare", "r" -> TextColor.fromRgb(0xFFD700);
            case "mythic", "m", "mythic rare" -> TextColor.fromRgb(0xE0A020);
            case "special", "s" -> TextColor.fromRgb(0xFF55FF);
            default -> TextColor.fromRgb(0xFFFFFF);
        };
    }

    private static String customWorldKey(String customId, int face) {
        String id = (customId == null) ? "" : customId.trim();
        return "custom_" + id + "_f" + face;
    }

    private static String normalizeCustomKey(String key, String customId, int face) {
        if (key == null || key.isBlank()) return customWorldKey(customId, face);

        return key.trim();
    }
}
