package com.spider.mtgcard.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.spider.mtgcard.net.payload.DeckPayloads;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public final class DeckServer_Command {

    public static LiteralArgumentBuilder<CommandSourceStack> node() {
        return literal("deck")
                .then(literal("export")
                        .then(argument("name", StringArgumentType.greedyString())
                                .executes(ctx -> {
                                    ServerPlayer p;
                                    try { p = ctx.getSource().getPlayer(); } catch (Exception e) { p = null; }
                                    if (p == null) {
                                        ctx.getSource().sendSuccess(() -> Component.literal("§cNo player context."), false);
                                        return 0;
                                    }
                                    String name = StringArgumentType.getString(ctx, "name");
                                    ServerPlayNetworking.send(p, new DeckPayloads.DeckExportRequestS2C(name));
                                    ctx.getSource().sendSuccess(() -> Component.literal("§aExporting deck on client…"), false);
                                    return 1;
                                })))
                .then(literal("list")
                        .executes(ctx -> {
                            ServerPlayer p;
                            try { p = ctx.getSource().getPlayer(); } catch (Exception e) { p = null; }
                            if (p == null) {
                                ctx.getSource().sendSuccess(() -> Component.literal("§cNo player context."), false);
                                return 0;
                            }
                            ServerPlayNetworking.send(p, new DeckPayloads.DeckListRequestS2C());
                            return 1;
                        }));
    }

    private DeckServer_Command() {}
}
