package com.spider.mtgcard.util;

import com.spider.mtgcard.content.pack.cache.ScryfallModels;
import com.spider.mtgcard.item.ModItems;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class CardStackBuilders {

    private CardStackBuilders() {}

    public static ItemStack buildCustomStackFromId(String customId, boolean foilVisual) {
        if (customId == null || customId.isBlank()) return ItemStack.EMPTY;

        ItemStack card = new ItemStack(ModItems.CARD);

        NbtCompound meta = new NbtCompound();
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

        NbtCompound root = new NbtCompound();
        root.put("mtg_meta", meta);
        card.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(root));

        // ✅ Give ID-only customs a visible debug name (since no meta.name here)
        applyColoredNameIfUnset(card, "Custom " + customId, "special");

        if (foilVisual) {
            card.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
        }

        return card;
    }

    public static ItemStack buildCustomStackFromMeta(com.spider.mtgcard.content.pack.custom.CustomCardStore.CardMeta m, boolean foilVisual) {
        if (m == null || m.id == null || m.id.isBlank()) return ItemStack.EMPTY;

        ItemStack card = new ItemStack(ModItems.CARD);
        NbtCompound meta = new NbtCompound();

        meta.putString("id", m.id);

        meta.putBoolean("is_custom", true);
        meta.putString("custom_id", m.id);

        // World art keys:
        String frontKey = normalizeCustomKey(m.artKeyFront, m.id, 0);
        String backKey  = normalizeCustomKey(m.artKeyBack,  m.id, 1);

        meta.putString("world_art_front", frontKey);
        meta.putString("world_art",       frontKey);
        meta.putString("world_art_back",  backKey);

        // Mirror Scryfall-style fields so UI/search behaves consistently
        if (m.name != null) meta.putString("name", m.name);
        if (m.manaCost != null) meta.putString("mana_cost", m.manaCost);
        if (m.typeLine != null) meta.putString("type_line", m.typeLine);
        if (m.oracleText != null) meta.putString("oracle_text", m.oracleText);
        if (m.power != null) meta.putString("power", m.power);
        if (m.toughness != null) meta.putString("toughness", m.toughness);
        if (m.loyalty != null) meta.putString("loyalty", m.loyalty);
        if (m.rarity != null) meta.putString("rarity", m.rarity);

        String setCode = (m.set == null || m.set.isBlank()) ? "cstm" : m.set.toLowerCase(java.util.Locale.ROOT);
        meta.putString("set", setCode);
        meta.putString("collector_number", m.id);

        meta.putInt("mtg_face", 0);
        meta.putString("mtg_uid", UUID.randomUUID().toString());

        NbtCompound root = new NbtCompound();
        root.put("mtg_meta", meta);
        card.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(root));

        // ✅ IMPORTANT: give it a visible name so it’s not just "Card"
        applyColoredNameIfUnset(card, m.name, m.rarity);

        if (foilVisual) card.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
        return card;
    }

    private static String bestImageUrl(Map<String, String> imageUris) {
        if (imageUris == null || imageUris.isEmpty()) return null;
        String png = imageUris.get("png");
        return (png != null && !png.isEmpty()) ? png : null;
    }

    public static ItemStack buildScryfallStackFromModel(ScryfallModels.Card c, boolean foilVisual) {
        ItemStack card = new ItemStack(ModItems.CARD);
        if (c == null) return new ItemStack(ModItems.CARD);

        NbtCompound meta = new NbtCompound();
        if (c.id != null) meta.putString("id", c.id);
        if (c.oracleId != null) meta.putString("oracle_id", c.oracleId);
        if (c.name != null) meta.putString("name", c.name);
        if (c.set != null) meta.putString("set", c.set);
        if (c.collectorNumber != null) meta.putString("collector_number", c.collectorNumber);
        if (c.rarity != null) meta.putString("rarity", c.rarity);
        if (c.layout != null) meta.putString("layout", c.layout);

        if (c.colors != null && !c.colors.isEmpty()) {
            NbtList list = new NbtList();
            for (String col : c.colors) list.add(NbtString.of(col));
            meta.put("colors", list);
        }
        if (c.colorIdentity != null && !c.colorIdentity.isEmpty()) {
            NbtList list = new NbtList();
            for (String col : c.colorIdentity) list.add(NbtString.of(col));
            meta.put("color_identity", list);
        }

        if (c.typeLine != null) meta.putString("type_line", c.typeLine);
        if (c.power != null) meta.putString("power", c.power);
        if (c.toughness != null) meta.putString("toughness", c.toughness);
        meta.putInt("mana_value", c.manaValue);

        if (c.legalities != null && !c.legalities.isEmpty()) {
            NbtCompound leg = new NbtCompound();
            for (var e : c.legalities.entrySet()) {
                if (e.getKey() != null && e.getValue() != null) leg.putString(e.getKey(), e.getValue());
            }
            meta.put("legalities", leg);
        }

        if (c.price != null) {
            NbtCompound p = new NbtCompound();
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
            NbtList faces = new NbtList();
            String firstFacePng = null;

            for (var fModel : c.faces) {
                NbtCompound f = new NbtCompound();
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

                faces.add(f);
            }

            meta.put("card_faces", faces);

            if (firstFacePng != null && !meta.contains("image_png")) {
                meta.putString("image_png", firstFacePng);
            }
        }

        meta.putInt("mtg_face", 0);
        meta.putString("mtg_uid", UUID.randomUUID().toString());

        NbtCompound root = new NbtCompound();
        root.put("mtg_meta", meta);
        card.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(root));

        applyColoredNameIfUnset(card, c.name, c.rarity);
        if (foilVisual) {
            card.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
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
        if (stack.contains(DataComponentTypes.CUSTOM_NAME)) return;

        TextColor c = colorForRarity(rarity);

        // Display name (what getName() tends to show)
        stack.set(
                DataComponentTypes.CUSTOM_NAME,
                Text.literal(name).setStyle(Style.EMPTY.withColor(c).withItalic(false))
        );

        // Plain fallback name (only if missing)
        if (!stack.contains(DataComponentTypes.ITEM_NAME)) {
            stack.set(DataComponentTypes.ITEM_NAME, Text.literal(name));
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

        String k = key.trim();
        if (k.startsWith("custom_")) return k;

        return customWorldKey(customId, face);
    }
}
