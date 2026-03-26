// com/spider/mtgcard/command/MtgRootCommand.java
package com.spider.mtgcard.command;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.network.chat.Component;

import static net.minecraft.commands.Commands.literal;

public final class MtgRootCommand {
    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, env) -> {
            var root = literal("mtg")
                    .requires(src -> true)
                    .executes(ctx -> {
                        ctx.getSource().sendSuccess(() -> Component.literal(
                                "§a/mtg commands:\n" +
                                        "§e/mtg art <find|rebuild|purge>\n" +
                                        "§e/mtg custom <import|sets|card|remove>\n" +
                                        "§7(Deck export/list are client-side: §e/mtg deck ...§7)"
                        ), false);
                        return 1;
                    });

            // /mtg art ...
            root.then(Art_Command.node());

            // /mtg custom ...
            root.then(Custom_Command.node());

            // Keep your existing import permissions command if you still want it
            root.then(ImportPerm_Command.node());

            root.then(DeckServer_Command.node());


            dispatcher.register(root);
        });
    }

    private MtgRootCommand() {}
}
