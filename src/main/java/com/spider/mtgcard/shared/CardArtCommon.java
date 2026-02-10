// src/main/java/com/spider/mtgcard/shared/CardArtCommon.java
package com.spider.mtgcard.shared;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;

public final class CardArtCommon {

    public static String computeArtKey(NbtCompound meta, int faceIndex) {
        String scryId = meta.getString("scryfall_id").orElse("");
        if (scryId.isEmpty()) scryId = meta.getString("id").orElse("");
        if (!scryId.isEmpty()) return scryId + "_f" + faceIndex;

        String url = extractImageUrl(meta, faceIndex);
        if (url == null || url.isEmpty()) return "missingmeta_f" + faceIndex;

        return sha1(stripQuery(url)) + "_f" + faceIndex;
    }

    public static String extractImageUrl(NbtCompound meta, int faceIndex) {
        Optional<NbtList> facesOpt = meta.getList("card_faces");
        if (facesOpt.isPresent() && !facesOpt.get().isEmpty()) {
            int idx = Math.max(0, Math.min(faceIndex, facesOpt.get().size() - 1));
            Optional<NbtCompound> face0 = facesOpt.get().getCompound(idx);
            if (face0.isPresent()) {
                String direct = face0.get().getString("image_png").orElse("");
                if (!direct.isEmpty()) return direct;
                Optional<NbtCompound> uris = face0.get().getCompound("image_uris");
                if (uris.isPresent()) {
                    String u = choose(uris.get());
                    if (!u.isEmpty()) return u;
                }
            }
        }
        String directRoot = meta.getString("image_png").orElse("");
        if (!directRoot.isEmpty()) return directRoot;
        Optional<NbtCompound> urisRoot = meta.getCompound("image_uris");
        if (urisRoot.isPresent()) return choose(urisRoot.get());
        return "";
    }

    private static String choose(NbtCompound uris) {
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
