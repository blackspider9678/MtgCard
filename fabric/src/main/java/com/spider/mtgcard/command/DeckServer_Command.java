package com.spider.mtgcard.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.spider.mtgcard.net.payload.DeckPayloads;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public final class DeckServer_Command {
    public static LiteralArgumentBuilder<CommandSourceStack> node() {
        return literal("deck")
                .then(literal("export")
                        .then(argument("name", StringArgumentType.greedyString())
                                .executes(ctx -> {
                                    ServerPlayer player = player(ctx.getSource());
                                    if (player == null) {
                                        ctx.getSource().sendSuccess(() -> Component.literal("No player context."), false);
                                        return 0;
                                    }
                                    String name = StringArgumentType.getString(ctx, "name");
                                    ServerPlayNetworking.send(player, new DeckPayloads.DeckExportRequestS2C(name));
                                    ctx.getSource().sendSuccess(() -> Component.literal("Exporting deck on client..."), false);
                                    return 1;
                                })))
                .then(literal("list")
                        .executes(ctx -> {
                            ServerPlayer player = player(ctx.getSource());
                            if (player == null) {
                                ctx.getSource().sendSuccess(() -> Component.literal("No player context."), false);
                                return 0;
                            }
                            ServerPlayNetworking.send(player, new DeckPayloads.DeckListRequestS2C());
                            return 1;
                        }));
    }

    private static ServerPlayer player(CommandSourceStack source) {
        try {
            return source.getPlayer();
        } catch (Exception ignored) {
            return null;
        }
    }

    private DeckServer_Command() {
    }
}
