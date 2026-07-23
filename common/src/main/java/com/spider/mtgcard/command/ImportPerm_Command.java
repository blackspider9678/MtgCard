package com.spider.mtgcard.command;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.spider.mtgcard.config.MtgcardConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

import java.util.Locale;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public final class ImportPerm_Command {

    public static LiteralArgumentBuilder<CommandSourceStack> node() {
        return literal("importperm")
                .requires(src -> true) // keep visible; we'll guard mutations below
                .executes(ctx -> {
                    MtgcardConfig cfg = MtgcardConfig.get();
                    ctx.getSource().sendSuccess(() -> Component.literal(
                            "\u00A7aMTG Import Permissions\n" +
                                    "\u00A77Anyone_Can_Import: \u00A7e" + cfg.Anyone_Can_Import + "\n" +
                                    "\u00A77Whitelist size: \u00A7e" + cfg.Import_Whitelist.size()
                    ), false);
                    return 1;
                })

                // /mtg importperm reload
                .then(literal("reload")
                        .requires(src -> {
                            var p = src.getPlayer();
                            return p != null && com.spider.mtgcard.config.Perms.isOp(p);
                        })
                        .executes(ctx -> {
                            MtgcardConfig.reload();
                            ctx.getSource().sendSuccess(() -> Component.literal("\u00A7aReloaded mtgcard.toml"), false);
                            return 1;
                        }))

                // /mtg importperm anyone <true|false>
                .then(literal("anyone")
                        .requires(src -> {
                            var p = src.getPlayer();
                            return p != null && com.spider.mtgcard.config.Perms.isOp(p);
                        })
                        .then(argument("value", BoolArgumentType.bool())
                                .executes(ctx -> {
                                    boolean v = BoolArgumentType.getBool(ctx, "value");
                                    MtgcardConfig cfg = MtgcardConfig.get();
                                    cfg.Anyone_Can_Import = v;
                                    MtgcardConfig.save();
                                    ctx.getSource().sendSuccess(() -> Component.literal("\u00A7aAnyone_Can_Import set to \u00A7e" + v), false);
                                    return 1;
                                })))

                // /mtg importperm whitelist ...
                .then(literal("whitelist")
                        .then(literal("list")
                                .requires(src -> {
                                    var p = src.getPlayer();
                                    return p != null && com.spider.mtgcard.config.Perms.isOp(p);
                                })
                                .executes(ctx -> {
                                    MtgcardConfig cfg = MtgcardConfig.get();
                                    if (cfg.Import_Whitelist.isEmpty()) {
                                        ctx.getSource().sendSuccess(() -> Component.literal("\u00A77Whitelist is empty."), false);
                                    } else {
                                        String joined = String.join(", ", cfg.Import_Whitelist);
                                        ctx.getSource().sendSuccess(() -> Component.literal("\u00A7aWhitelist: \u00A7e" + joined), false);
                                    }
                                    return 1;
                                }))
                        .then(literal("add")
                                .requires(src -> {
                                    var p = src.getPlayer();
                                    return p != null && com.spider.mtgcard.config.Perms.isOp(p);
                                })
                                .then(argument("name", StringArgumentType.word())
                                        .executes(ctx -> {
                                            String name = StringArgumentType.getString(ctx, "name");
                                            String norm = name.toLowerCase(Locale.ROOT);

                                            MtgcardConfig cfg = MtgcardConfig.get();
                                            cfg.Import_Whitelist.add(norm);
                                            MtgcardConfig.save();

                                            ctx.getSource().sendSuccess(() -> Component.literal("\u00A7aAdded to whitelist: \u00A7e" + norm), false);
                                            return 1;
                                        })))
                        .then(literal("remove")
                                .requires(src -> {
                                    var p = src.getPlayer();
                                    return p != null && com.spider.mtgcard.config.Perms.isOp(p);
                                })
                                .then(argument("name", StringArgumentType.word())
                                        .executes(ctx -> {
                                            String name = StringArgumentType.getString(ctx, "name");
                                            String norm = name.toLowerCase(Locale.ROOT);

                                            MtgcardConfig cfg = MtgcardConfig.get();
                                            boolean removed = cfg.Import_Whitelist.removeIf(s -> s != null && s.equalsIgnoreCase(norm));
                                            MtgcardConfig.save();

                                            ctx.getSource().sendSuccess(() -> Component.literal(
                                                    removed ? "\u00A7aRemoved from whitelist: \u00A7e" + norm
                                                            : "\u00A7cNot found in whitelist: \u00A7e" + norm
                                            ), false);
                                            return 1;
                                        })))
                );
    }

    private static boolean canManage(CommandSourceStack src) {
        var p = src.getPlayer();
        return p != null && com.spider.mtgcard.config.Perms.isOp(p);
    }

    private ImportPerm_Command() {}
}
