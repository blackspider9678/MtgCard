// src/main/java/com/spider/mtgcard/shared/CardArtCommon.java
package com.spider.mtgcard.shared;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

import java.text.Normalizer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import net.minecraft.nbt.Tag;

public final class CardArtCommon {

    private static final int MAX_COMPONENT_LENGTH = 48;

    /**
     * Primary official-card key:
     * name_set_collector.webp for single-faced cards, and
     * name_set_collector_f0.webp / name_set_collector_f1.webp for multi-faced cards.
     */
    public static String computeArtKey(CompoundTag meta, int faceIndex) {
        String official = computeOfficialArtKey(meta, faceIndex);
        if (!official.isBlank()) return official;

        return computeSourceArtKey(meta, faceIndex);
    }

    public static String computeOfficialArtKey(CompoundTag meta, int faceIndex) {
        if (meta == null) return "";

        String name = meta.getString("name");
        String set = meta.getString("set");
        String collector = meta.getString("collector_number");

        boolean includeFace = faceCount(meta) > 1 || faceIndex > 0;
        return officialArtKey(name, set, collector, includeFace ? Math.max(0, faceIndex) : -1);
    }

    public static String officialArtKey(String name, String set, String collectorNumber, int faceIndex) {
        String safeName = sanitizeComponent(name);
        String safeSet = sanitizeComponent(set);
        String safeCollector = sanitizeComponent(collectorNumber);

        if (safeName.isBlank() || safeSet.isBlank() || safeCollector.isBlank()) {
            return "";
        }

        String key = safeName + "_" + safeSet + "_" + safeCollector;
        if (faceIndex >= 0) {
            key += "_f" + faceIndex;
        }
        return key;
    }

    public static String computeSourceArtKey(CompoundTag meta, int faceIndex) {
        if (meta == null) return "missingmeta_f" + Math.max(0, faceIndex);

        String scryId = meta.getString("scryfall_id");
        if (scryId.isEmpty()) scryId = meta.getString("id");
        if (!scryId.isEmpty()) return sanitizeArtKey(scryId + "_f" + Math.max(0, faceIndex));

        String url = extractImageUrl(meta, faceIndex);
        if (url == null || url.isEmpty()) return "missingmeta_f" + Math.max(0, faceIndex);

        return sha1(stripQuery(url)) + "_f" + Math.max(0, faceIndex);
    }

    public static String extractImageUrl(CompoundTag meta, int faceIndex) {
        if (meta == null) return "";
        ListTag faces = meta.getList("card_faces", Tag.TAG_COMPOUND);
        if (faces.isEmpty()) faces = meta.getList("faces", Tag.TAG_COMPOUND);
        if (!faces.isEmpty()) {
            int idx = Math.max(0, Math.min(faceIndex, faces.size() - 1));
            CompoundTag face0 = faces.getCompound(idx);
            if (!face0.isEmpty()) {
                String direct = face0.getString("image_png");
                if (!direct.isEmpty()) return direct;
                CompoundTag uris = face0.getCompound("image_uris");
                if (!uris.isEmpty()) {
                    String u = choose(uris);
                    if (!u.isEmpty()) return u;
                }
            }
        }
        String directRoot = meta.getString("image_png");
        if (!directRoot.isEmpty()) return directRoot;
        CompoundTag urisRoot = meta.getCompound("image_uris");
        if (!urisRoot.isEmpty()) return choose(urisRoot);
        return "";
    }

    public static int faceCount(CompoundTag meta) {
        if (meta == null) return 1;
        ListTag faces = meta.getList("card_faces", Tag.TAG_COMPOUND);
        if (faces != null && faces.size() > 1) return faces.size();
        faces = meta.getList("faces", Tag.TAG_COMPOUND);
        if (faces != null && faces.size() > 1) return faces.size();
        return 1;
    }

    public static String sanitizeArtKey(String key) {
        if (key == null) return "";

        String s = key.trim().toLowerCase(Locale.ROOT);
        if (s.length() > 160) s = s.substring(0, 160);
        s = s.replaceAll("[^a-z0-9_\\-]", "_");
        s = s.replaceAll("_+", "_");
        while (s.startsWith("_")) s = s.substring(1);
        while (s.endsWith("_")) s = s.substring(0, s.length() - 1);
        return s;
    }

    public static String sanitizeComponent(String raw) {
        if (raw == null) return "";

        String normalized = Normalizer.normalize(raw.trim().toLowerCase(Locale.ROOT), Normalizer.Form.NFKD);
        StringBuilder out = new StringBuilder(normalized.length());
        boolean lastWasSeparator = false;

        for (int i = 0; i < normalized.length();) {
            int cp = normalized.codePointAt(i);
            i += Character.charCount(cp);

            int type = Character.getType(cp);
            if (type == Character.NON_SPACING_MARK
                    || type == Character.COMBINING_SPACING_MARK
                    || type == Character.ENCLOSING_MARK) {
                continue;
            }

            if ((cp >= 'a' && cp <= 'z') || (cp >= '0' && cp <= '9') || cp == '-') {
                out.appendCodePoint(cp);
                lastWasSeparator = false;
                continue;
            }

            if (cp > 0x7F) {
                appendSeparator(out);
                out.append('u').append(Integer.toHexString(cp));
                lastWasSeparator = false;
                continue;
            }

            if (!lastWasSeparator && out.length() > 0) {
                out.append('_');
                lastWasSeparator = true;
            }
        }

        String s = out.toString();
        while (s.startsWith("_")) s = s.substring(1);
        while (s.endsWith("_")) s = s.substring(0, s.length() - 1);
        s = s.replaceAll("_+", "_");

        if (s.length() > MAX_COMPONENT_LENGTH) {
            s = s.substring(0, MAX_COMPONENT_LENGTH);
            while (s.endsWith("_")) s = s.substring(0, s.length() - 1);
        }
        return s;
    }

    private static String choose(CompoundTag uris) {
        String png   = uris.getString("png");
        if (!png.isEmpty()) return png;
        String large = uris.getString("large");
        if (!large.isEmpty()) return large;
        String normal = uris.getString("normal");
        if (!normal.isEmpty()) return normal;
        String art = uris.getString("art_crop");
        if (!art.isEmpty()) return art;
        String border = uris.getString("border_crop");
        return border == null ? "" : border;
    }

    private static void appendSeparator(StringBuilder out) {
        if (out.length() > 0 && out.charAt(out.length() - 1) != '_') {
            out.append('_');
        }
    }

    public static String stripQuery(String url) {
        int q = url.indexOf('?');
        return (q >= 0) ? url.substring(0, q) : url;
    }

    private static String sha1(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] dig = md.digest(s.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(dig);
        } catch (Exception e) {
            return Integer.toHexString(s.hashCode());
        }
    }

    private CardArtCommon() {}
}
