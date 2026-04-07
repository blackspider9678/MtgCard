// com/spider/mtgcard/command/Import_Command.java
package com.spider.mtgcard.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.spider.mtgcard.net.CustomImportPackets;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

import static net.minecraft.commands.Commands.literal;

public final class Import_Command {
    public static LiteralArgumentBuilder<CommandSourceStack> node() {
        return literal("import").executes(ctx -> {
            var p = ctx.getSource().getPlayerOrException();
            CustomImportPackets.openImportGui(p); // <-- send OpenImportGui
            ctx.getSource().sendSuccess(() -> Component.literal("§aOpening Custom Import…"), false);
            return 1;
        });
    }
}
