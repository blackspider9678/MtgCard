// com/spider/mtgcard/command/Import_Command.java
package com.spider.mtgcard.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.spider.mtgcard.config.ImportPerms;
import com.spider.mtgcard.net.CustomImportPackets;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

import static net.minecraft.commands.Commands.literal;

public final class Import_Command {
    public static LiteralArgumentBuilder<CommandSourceStack> node() {
        return literal("import")
                .requires(src -> {
                    var p = src.getPlayer();
                    return p != null && ImportPerms.canImport(p);
                })
                .executes(ctx -> {
                    var p = ctx.getSource().getPlayerOrException();
                    if (!CustomImportPackets.openImportGui(p)) {
                        ctx.getSource().sendFailure(Component.literal("You do not have permission to import custom cards."));
                        return 0;
                    }

                    ctx.getSource().sendSuccess(() -> Component.literal("\u00A7aOpening Custom Import..."), false);
                    return 1;
                });
    }
}
