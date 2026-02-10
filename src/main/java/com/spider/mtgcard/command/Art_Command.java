// com/spider/mtgcard/command/Art_Command.java
package com.spider.mtgcard.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.WorldSavePath;

import java.io.IOException;
import java.nio.file.*;
import java.util.Locale;
import java.util.stream.Stream;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public final class Art_Command {

    // Change if needed
    private static final String ART_DIR_NAME = "art"; // <world>/mtgcard/art/

    public static LiteralArgumentBuilder<ServerCommandSource> node() {
        return literal("art")
                // ✅ NEW: /mtg art -> stats
                .executes(ctx -> stats(ctx.getSource()))

                .then(literal("find")
                        .then(argument("id", StringArgumentType.greedyString())
                                .executes(ctx -> {
                                    String id = StringArgumentType.getString(ctx, "id").trim();
                                    return find(ctx.getSource(), id);
                                })))
                .then(literal("rebuild")
                        .executes(ctx -> rebuild(ctx.getSource())))
                .then(literal("purge")
                        .requires(Art_Command::isOp)
                        .executes(ctx -> purge(ctx.getSource())));
    }

    private static int stats(ServerCommandSource src) {
        Path artDir = worldArtDir(src.getServer());

        if (!Files.exists(artDir)) {
            src.sendFeedback(() -> Text.literal("§7No art directory found at: §e" + artDir), false);
            src.sendFeedback(() -> Text.literal("§aCustom Art: §e0§a, Total Art: §e0"), false);
            return 1;
        }

        long total = 0;
        long custom = 0;

        try (Stream<Path> s = Files.list(artDir)) {
            for (Path p : s.toList()) {
                if (!Files.isRegularFile(p)) continue;
                total++;
                String fn = p.getFileName().toString().toLowerCase(Locale.ROOT);
                if (fn.startsWith("custom_")) custom++;
            }
        } catch (IOException e) {
            src.sendFeedback(() -> Text.literal("§cFailed to read art dir: §7" + e.getMessage()), false);
            return 0;
        }

        long finalTotal = total;
        long finalCustom = custom;
        src.sendFeedback(() -> Text.literal("§aCustom Art: §e" + finalCustom + "§a, Total Art: §e" + finalTotal), false);
        src.sendFeedback(() -> Text.literal("§7Folder: §e" + artDir), false);
        return 1;
    }

    // existing find/rebuild/purge/isOp/worldArtDir/sanitizeKeyForSearch below...

    private static int find(ServerCommandSource src, String id) { /* unchanged */ return 1; }
    private static int rebuild(ServerCommandSource src) { /* unchanged */ return 1; }
    private static int purge(ServerCommandSource src) { /* unchanged */ return 1; }

    private static Path worldArtDir(net.minecraft.server.MinecraftServer server) {
        return server.getSavePath(WorldSavePath.ROOT).resolve("mtgcard").resolve(ART_DIR_NAME);
    }

    private static boolean isOp(ServerCommandSource src) {
        ServerPlayerEntity p;
        try { p = src.getPlayer(); } catch (Exception e) { return false; }
        return p != null && com.spider.mtgcard.config.Perms.isOp(p);
    }

    private Art_Command() {}
}
