package com.spider.mtgcard.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.spider.mtgcard.net.payload.DeckPayloads;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public final class DeckServer_Command {

    public static LiteralArgumentBuilder<ServerCommandSource> node() {
        return literal("deck")
                .then(literal("export")
                        .then(argument("name", StringArgumentType.greedyString())
                                .executes(ctx -> {
                                    ServerPlayerEntity p;
                                    try { p = ctx.getSource().getPlayer(); } catch (Exception e) { p = null; }
                                    if (p == null) {
                                        ctx.getSource().sendFeedback(() -> Text.literal("§cNo player context."), false);
                                        return 0;
                                    }
                                    String name = StringArgumentType.getString(ctx, "name");
                                    ServerPlayNetworking.send(p, new DeckPayloads.DeckExportRequestS2C(name));
                                    ctx.getSource().sendFeedback(() -> Text.literal("§aExporting deck on client…"), false);
                                    return 1;
                                })))
                .then(literal("list")
                        .executes(ctx -> {
                            ServerPlayerEntity p;
                            try { p = ctx.getSource().getPlayer(); } catch (Exception e) { p = null; }
                            if (p == null) {
                                ctx.getSource().sendFeedback(() -> Text.literal("§cNo player context."), false);
                                return 0;
                            }
                            ServerPlayNetworking.send(p, new DeckPayloads.DeckListRequestS2C());
                            return 1;
                        }));
    }

    private DeckServer_Command() {}
}
