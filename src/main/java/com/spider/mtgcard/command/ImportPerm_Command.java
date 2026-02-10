package com.spider.mtgcard.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.spider.mtgcard.config.MtgcardConfig;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

import java.util.Locale;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public final class ImportPerm_Command {

    public static LiteralArgumentBuilder<ServerCommandSource> node() {
        return literal("importperm")
                .requires(src -> true) // keep visible; we’ll guard mutations below
                .executes(ctx -> {
                    MtgcardConfig cfg = MtgcardConfig.get();
                    ctx.getSource().sendFeedback(() -> Text.literal(
                            "§aMTG Import Permissions\n" +
                                    "§7Anyone_Can_Import: §e" + cfg.Anyone_Can_Import + "\n" +
                                    "§7Whitelist size: §e" + cfg.Import_Whitelist.size()
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
                            ctx.getSource().sendFeedback(() -> Text.literal("§aReloaded mtgcard.json"), false);
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
                                    ctx.getSource().sendFeedback(() -> Text.literal("§aAnyone_Can_Import set to §e" + v), false);
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
                                        ctx.getSource().sendFeedback(() -> Text.literal("§7Whitelist is empty."), false);
                                    } else {
                                        String joined = String.join(", ", cfg.Import_Whitelist);
                                        ctx.getSource().sendFeedback(() -> Text.literal("§aWhitelist: §e" + joined), false);
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

                                            ctx.getSource().sendFeedback(() -> Text.literal("§aAdded to whitelist: §e" + norm), false);
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

                                            ctx.getSource().sendFeedback(() -> Text.literal(
                                                    removed ? "§aRemoved from whitelist: §e" + norm
                                                            : "§cNot found in whitelist: §e" + norm
                                            ), false);
                                            return 1;
                                        })))
                );
    }
    private static boolean canManage(ServerCommandSource src) {
        var p = src.getPlayer();
        return p != null && com.spider.mtgcard.config.Perms.isOp(p);
    }

    private ImportPerm_Command() {}
}
