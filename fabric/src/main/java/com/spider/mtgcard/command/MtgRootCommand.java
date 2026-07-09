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
                                "/mtg commands:\n" +
                                        "/mtg art <find|rebuild|purge>\n" +
                                        "/mtg custom <import|sets|card|remove>\n" +
                                        "(Deck export/list are client-side: /mtg deck ...)"
                        ), false);
                        return 1;
                    });

            root.then(Art_Command.node());
            root.then(Custom_Command.node());
            root.then(ImportPerm_Command.node());
            root.then(DeckServer_Command.node());

            dispatcher.register(root);
        });
    }

    private MtgRootCommand() {
    }
}
