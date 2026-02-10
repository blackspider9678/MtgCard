// com/spider/mtgcard/command/Import_Command.java
package com.spider.mtgcard.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.spider.mtgcard.net.CustomImportPackets;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

import static net.minecraft.server.command.CommandManager.literal;

public final class Import_Command {
    public static LiteralArgumentBuilder<ServerCommandSource> node() {
        return literal("import").executes(ctx -> {
            var p = ctx.getSource().getPlayerOrThrow();
            CustomImportPackets.openImportGui(p); // <-- send OpenImportGui
            ctx.getSource().sendFeedback(() -> Text.literal("§aOpening Custom Import…"), false);
            return 1;
        });
    }
}
