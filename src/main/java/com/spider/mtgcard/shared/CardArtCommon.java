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
import java.util.Optional;

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

        return computeLegacyArtKey(meta, faceIndex);
    }

    public static String computeOfficialArtKey(CompoundTag meta, int faceIndex) {
        if (meta == null) return "";

        String name = meta.getString("name").orElse("");
        String set = meta.getString("set").orElse("");
        String collector = meta.getString("collector_number").orElse("");

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

    public static List<String> legacyFallbackArtKeys(CompoundTag meta, int faceIndex) {
        if (meta == null) return List.of();

        String primary = computeArtKey(meta, faceIndex);
        String legacy = computeLegacyArtKey(meta, faceIndex);
        if (legacy.isBlank() || legacy.equals(primary)) return List.of();

        ArrayList<String> out = new ArrayList<>(1);
        out.add(legacy);
        return List.copyOf(out);
    }

    public static String encodeFallbackKeys(List<String> keys) {
        if (keys == null || keys.isEmpty()) return "";

        ArrayList<String> safe = new ArrayList<>(keys.size());
        for (String key : keys) {
            String sanitized = sanitizeArtKey(key);
            if (!sanitized.isBlank() && !safe.contains(sanitized)) {
                safe.add(sanitized);
            }
        }
        return String.join(",", safe);
    }

    public static List<String> decodeFallbackKeys(String encoded) {
        if (encoded == null || encoded.isBlank()) return List.of();

        ArrayList<String> out = new ArrayList<>();
        for (String raw : encoded.split(",")) {
            String key = sanitizeArtKey(raw);
            if (!key.isBlank() && !out.contains(key)) {
                out.add(key);
            }
        }
        return List.copyOf(out);
    }

    public static String computeLegacyArtKey(CompoundTag meta, int faceIndex) {
        if (meta == null) return "missingmeta_f" + Math.max(0, faceIndex);

        String scryId = meta.getString("scryfall_id").orElse("");
        if (scryId.isEmpty()) scryId = meta.getString("id").orElse("");
        if (!scryId.isEmpty()) return sanitizeArtKey(scryId + "_f" + Math.max(0, faceIndex));

        String url = extractImageUrl(meta, faceIndex);
        if (url == null || url.isEmpty()) return "missingmeta_f" + Math.max(0, faceIndex);

        return sha1(stripQuery(url)) + "_f" + Math.max(0, faceIndex);
    }

    public static String extractImageUrl(CompoundTag meta, int faceIndex) {
        if (meta == null) return "";

        String faceUrl = extractImageUrlFromFaces(meta, "card_faces", faceIndex);
        if (!faceUrl.isEmpty()) return faceUrl;
        faceUrl = extractImageUrlFromFaces(meta, "faces", faceIndex);
        if (!faceUrl.isEmpty()) return faceUrl;

        String directRoot = meta.getString("image_png").orElse("");
        if (!directRoot.isEmpty()) return directRoot;
        Optional<CompoundTag> urisRoot = meta.getCompound("image_uris");
        if (urisRoot.isPresent()) return choose(urisRoot.get());
        return "";
    }

    private static String extractImageUrlFromFaces(CompoundTag meta, String facesKey, int faceIndex) {
        Optional<ListTag> facesOpt = meta.getList(facesKey);
        if (facesOpt.isPresent() && !facesOpt.get().isEmpty()) {
            int idx = Math.max(0, Math.min(faceIndex, facesOpt.get().size() - 1));
            Optional<CompoundTag> face0 = facesOpt.get().getCompound(idx);
            if (face0.isPresent()) {
                String direct = face0.get().getString("image_png").orElse("");
                if (!direct.isEmpty()) return direct;
                Optional<CompoundTag> uris = face0.get().getCompound("image_uris");
                if (uris.isPresent()) {
                    String u = choose(uris.get());
                    if (!u.isEmpty()) return u;
                }
            }
        }
        return "";
    }

    public static int faceCount(CompoundTag meta) {
        if (meta == null) return 1;
        ListTag faces = meta.getList("card_faces").orElse(null);
        if (faces != null && faces.size() > 1) return faces.size();
        faces = meta.getList("faces").orElse(null);
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
        String png   = uris.getString("png").orElse("");
        if (!png.isEmpty()) return png;
        String large = uris.getString("large").orElse("");
        if (!large.isEmpty()) return large;
        String normal = uris.getString("normal").orElse("");
        if (!normal.isEmpty()) return normal;
        String art = uris.getString("art_crop").orElse("");
        if (!art.isEmpty()) return art;
        String border = uris.getString("border_crop").orElse("");
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
