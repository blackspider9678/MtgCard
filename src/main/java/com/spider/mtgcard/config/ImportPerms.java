package com.spider.mtgcard.config;

import com.spider.mtgcard.config.MtgcardConfig;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Locale;

public final class ImportPerms {

    public static boolean canImport(ServerPlayerEntity player) {
        if (player == null) return false;

        MtgcardConfig cfg = MtgcardConfig.get();

        // If enabled, anyone can import
        if (cfg.Anyone_Can_Import) return true;

        // Whitelist overrides ops requirement (when Anyone_Can_Import is false)
        String name = player.getGameProfile().name();
        if (name != null) {
            String n = name.toLowerCase(Locale.ROOT);
            for (String w : cfg.Import_Whitelist) {
                if (w != null && n.equals(w.toLowerCase(Locale.ROOT))) return true;
            }
        }

        // Otherwise: ops only
        // NOTE: in newer mappings, this is still the simplest reliable check.
        return Perms.isOp(player);
    }

    private ImportPerms() {}
}
