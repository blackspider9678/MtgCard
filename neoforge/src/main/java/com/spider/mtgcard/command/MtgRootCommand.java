package com.spider.mtgcard.command;

import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import static net.minecraft.commands.Commands.literal;

public final class MtgRootCommand {
    public static void register(RegisterCommandsEvent event) {
        var root = literal("mtg")
                .requires(src -> true)
                .executes(ctx -> {
                    ctx.getSource().sendSuccess(() -> Component.literal(
                            "\u00A7a/mtg commands:\n" +
                                    "\u00A7e/mtg art <find|rebuild|purge>\n" +
                                    "\u00A7e/mtg custom <import|sets|card|remove>\n" +
                                    "\u00A77(Deck export/list are client-side: \u00A7e/mtg deck ...\u00A77)"
                    ), false);
                    return 1;
                });

        root.then(Art_Command.node());
        root.then(Custom_Command.node());
        root.then(ImportPerm_Command.node());
        root.then(DeckServer_Command.node());

        event.getDispatcher().register(root);
    }

    private MtgRootCommand() {}
}
