package com.spider.mtgcard.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.spider.mtgcard.shared.MtgCardPaths;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.stream.Stream;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public final class Art_Command {

    public static LiteralArgumentBuilder<CommandSourceStack> node() {
        return literal("art")
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

    private static int stats(CommandSourceStack src) {
        Path mainArt = MtgCardPaths.mainArtDir(src.getServer());
        Path customArt = MtgCardPaths.customArtRoot(src.getServer());
        Path legacyArt = MtgCardPaths.legacyArtDir(src.getServer());

        long main = countImages(mainArt, false);
        long custom = countImages(customArt, true);
        long legacy = countImages(legacyArt, false);

        src.sendSuccess(() -> Component.literal("Main Art: " + main + ", Custom Art: " + custom
                + ", Total New Art: " + (main + custom)), false);
        src.sendSuccess(() -> Component.literal("Legacy flat art fallback: " + legacy), false);
        src.sendSuccess(() -> Component.literal("Main folder: " + mainArt), false);
        src.sendSuccess(() -> Component.literal("Custom folder: " + customArt), false);
        return 1;
    }

    private static int find(CommandSourceStack src, String id) { /* unchanged */ return 1; }
    private static int rebuild(CommandSourceStack src) { /* unchanged */ return 1; }
    private static int purge(CommandSourceStack src) { /* unchanged */ return 1; }

    private static long countImages(Path dir, boolean recursive) {
        if (dir == null || !Files.exists(dir)) return 0;
        try (Stream<Path> s = recursive ? Files.walk(dir) : Files.list(dir)) {
            return s.filter(Files::isRegularFile)
                    .filter(Art_Command::isImageFile)
                    .count();
        } catch (IOException e) {
            return 0;
        }
    }

    private static boolean isImageFile(Path path) {
        String fn = path == null ? "" : path.getFileName().toString().toLowerCase(Locale.ROOT);
        return fn.endsWith(".webp") || fn.endsWith(".png") || fn.endsWith(".jpg") || fn.endsWith(".jpeg");
    }

    private static boolean isOp(CommandSourceStack src) {
        ServerPlayer p;
        try { p = src.getPlayer(); } catch (Exception e) { return false; }
        return p != null && com.spider.mtgcard.config.Perms.isOp(p);
    }

    private Art_Command() {}
}
