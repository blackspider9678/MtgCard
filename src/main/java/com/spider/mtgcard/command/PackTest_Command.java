// com/spider/mtgcard/command/PackTest_Command.java  (server/common)
package com.spider.mtgcard.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.spider.mtgcard.content.pack.PackGenerator;
import com.spider.mtgcard.content.pack.cache.ScryfallCache;
import com.spider.mtgcard.net.WorldState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public final class PackTest_Command {

    /** Attach as a child to /mtg root: root.then(PackTest_Command.node()); */
    public static LiteralArgumentBuilder<ServerCommandSource> node() {
        return literal("packtest")
                .then(argument("set", StringArgumentType.word())
                        .executes(ctx -> runPackTest(ctx, StringArgumentType.getString(ctx, "set"))));
    }

    // ---------- Command body ----------
    public static int runPackTest(CommandContext<ServerCommandSource> ctx, String rawSet) {
        ServerCommandSource src = ctx.getSource();
        ServerWorld world = src.getServer().getOverworld();
        ServerPlayerEntity player = null;
        try { player = src.getPlayer(); } catch (Exception ignored) {}

        String set = sanitize(rawSet);
        if (set == null) {
            msg(src, "§cInvalid set code. Use 2–6 letters/digits, e.g. [mh3].");
            return 0;
        }

        msg(src, "§7Running pack test for set §e[" + set + "]§7 …");

        // Slot plan (15)
        List<PackGenerator.RaritySlot> slots = new ArrayList<>(15);
        for (int i = 0; i < 6; i++) slots.add(PackGenerator.RaritySlot.COMMON);   // 1–6
        slots.add(PackGenerator.RaritySlot.UNCOMMON);                              // 7 (variety)
        for (int i = 0; i < 3; i++) slots.add(PackGenerator.RaritySlot.UNCOMMON);  // 8–10
        slots.add(PackGenerator.RaritySlot.FOIL_RANDOM);                           // 11
        slots.add(PackGenerator.RaritySlot.RARE_OR_MYTHIC);                        // 12
        slots.add(PackGenerator.RaritySlot.BASIC_LAND);                            // 13
        slots.add(PackGenerator.RaritySlot.RANDOM);                                // 14
        slots.add(PackGenerator.RaritySlot.TOKEN_OR_ART);                          // 15

        // Scryfall (official) checks
        RunSummary scryfallSummary = new RunSummary(set);
        int TRIALS_PER_SLOT = 20; // adjust if you want more/less sampling

        List<CompletableFuture<Void>> jobs = new ArrayList<>();
        for (PackGenerator.RaritySlot slot : slots) {
            for (int t = 0; t < TRIALS_PER_SLOT; t++) {
                ScryfallCache.Query q = queryFor(slot);

                // For token slot, we allow set OR t<set> (handled by buildQuery when onlySet=set)
                ScryfallCache.Context ctxFetch = ScryfallCache.Context.builder()
                        .gamePaper(true)
                        .excludeSets(List.of("4bb","fbb","rin","ren","ps11","psal"))
                        .blacklist(Set.of())
                        .onlySet(set) // strict lock; TokenExtra path ORs t<set> inside buildQuery
                        .build();

                CompletableFuture<Void> fut = ScryfallCache.pickRandomAsync(world, ctxFetch, q, false, true)
                        .thenAccept(card -> {
                            scryfallSummary.total.incrementAndGet();
                            if (card == null || card.set == null) {
                                scryfallSummary.nulls.incrementAndGet();
                                return;
                            }
                            String got = card.set.toLowerCase(Locale.ROOT);
                            boolean ok = (slot == PackGenerator.RaritySlot.TOKEN_OR_ART)
                                    ? (got.equals(set) || got.equals("t" + set))
                                    : got.equals(set);

                            if (!ok) {
                                scryfallSummary.wrongSet.incrementAndGet();
                                synchronized (scryfallSummary.mismatches) {
                                    if (scryfallSummary.mismatches.size() < 10) {
                                        scryfallSummary.mismatches.add(slot + " -> " + card.name + " (" + card.set + ")");
                                    }
                                }
                            } else {
                                scryfallSummary.ok.incrementAndGet();
                            }
                        })
                        .exceptionally(ex -> {
                            scryfallSummary.errors.incrementAndGet();
                            return null;
                        });
                jobs.add(fut);
            }
        }

        // Custom set availability check
        CustomSummary customSummary = customCheck(src.getServer(), set);

        CompletableFuture.allOf(jobs.toArray(CompletableFuture[]::new)).whenComplete((v, ex) -> {
            StringBuilder report = new StringBuilder();
            report.append("§aScryfall set-lock report for [").append(set).append("]:\n");
            report.append("  §7Total: §f").append(scryfallSummary.total.get())
                    .append("§7, OK: §a").append(scryfallSummary.ok.get())
                    .append("§7, Wrong-set: §c").append(scryfallSummary.wrongSet.get())
                    .append("§7, Nulls: §e").append(scryfallSummary.nulls.get())
                    .append("§7, Errors: §6").append(scryfallSummary.errors.get()).append("\n");

            if (!scryfallSummary.mismatches.isEmpty()) {
                report.append("  §7Examples of mismatches (slot → name (set)):\n");
                for (String line : scryfallSummary.mismatches) {
                    report.append("    §c").append(line).append("\n");
                }
            }

            if (customSummary.hasAny) {
                report.append("§dCustom pool for [").append(set).append("]:\n")
                        .append("  §7Counts by rarity — c: ").append(customSummary.c)
                        .append(", u: ").append(customSummary.u)
                        .append(", r: ").append(customSummary.r)
                        .append(", m: ").append(customSummary.m).append("\n");
                if (!customSummary.notes.isEmpty()) {
                    report.append("  §7Notes:\n");
                    for (String n : customSummary.notes) report.append("    §7- ").append(n).append("\n");
                }
            } else {
                report.append("§dCustom pool: §7No custom cards tagged with set=[").append(set).append("].\n");
            }

            // Small toast + full report split across chat lines
            msg(src, "§aPacktest complete for §e[" + set + "]§a. See chat/log for details.");
            for (String chunk : splitForChat(report.toString(), 240)) {
                msg(src, chunk);
            }
        });

        return 1;
    }

    // ---------- Helpers ----------
    private static ScryfallCache.Query queryFor(PackGenerator.RaritySlot slot) {
        return switch (slot) {
            case COMMON -> ScryfallCache.Query.common();
            case WILDCARD_C_OR_U -> (Math.random() < 0.125)
                    ? ScryfallCache.Query.common()
                    : ScryfallCache.Query.uncommon();
            case UNCOMMON -> ScryfallCache.Query.uncommon();
            case RARE_OR_MYTHIC -> (Math.random() < 0.125)
                    ? ScryfallCache.Query.mythic()
                    : ScryfallCache.Query.rare();
            case BASIC_LAND -> ScryfallCache.Query.basicLand();
            case RANDOM, FOIL_RANDOM -> ScryfallCache.Query.randomNonBasic();
            case TOKEN_OR_ART -> ScryfallCache.Query.tokenOrExtra();
        };
    }

    private static String sanitize(String s) {
        if (s == null) return null;
        s = s.trim().toLowerCase(Locale.ROOT);
        if (!s.matches("^[a-z0-9]{2,6}$")) return null;
        return s;
    }

    private static void msg(ServerCommandSource src, String text) {
        src.sendFeedback(() -> Text.literal(text), false);
    }

    private static List<String> splitForChat(String s, int max) {
        ArrayList<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String line : s.split("\n")) {
            if (cur.length() + line.length() + 1 > max) {
                out.add(cur.toString());
                cur.setLength(0);
            }
            if (cur.length() > 0) cur.append("\n");
            cur.append(line);
        }
        if (cur.length() > 0) out.add(cur.toString());
        return out;
    }

    private record RunSummary(String set,
                              AtomicInteger total,
                              AtomicInteger ok,
                              AtomicInteger wrongSet,
                              AtomicInteger nulls,
                              AtomicInteger errors,
                              List<String> mismatches) {
        RunSummary(String set) {
            this(set, new AtomicInteger(), new AtomicInteger(), new AtomicInteger(),
                    new AtomicInteger(), new AtomicInteger(),
                    Collections.synchronizedList(new ArrayList<>()));
        }
    }

    private record CustomSummary(boolean hasAny, int c, int u, int r, int m, List<String> notes) {}

    private static CustomSummary customCheck(MinecraftServer server, String set) {
        var store = WorldState.get(server).customCards();
        if (store == null) return new CustomSummary(false, 0, 0, 0, 0, List.of("CustomCardStore = null"));
        var all = store.all();
        if (all == null || all.isEmpty()) return new CustomSummary(false, 0, 0, 0, 0, List.of("CustomCardStore is empty"));

        int c=0,u=0,r=0,m=0;
        for (var meta : all) {
            if (meta == null || meta.set == null || !meta.set.equalsIgnoreCase(set)) continue;
            String rar = (meta.rarity == null) ? "" : meta.rarity.trim().toLowerCase(Locale.ROOT);
            switch (rar) {
                case "c", "common" -> c++;
                case "u", "uncommon" -> u++;
                case "r", "rare" -> r++;
                case "m", "mythic", "mythic rare" -> m++;
            }
        }
        boolean any = (c+u+r+m) > 0;
        ArrayList<String> notes = new ArrayList<>();
        if (!any) notes.add("No custom cards labeled with set=[" + set + "].");
        else {
            if (c < 6) notes.add("Common pool < 6; may stall variety in pack.");
            if (u < 4) notes.add("Uncommon pool < 4; may cause repeats.");
            if (r + m < 1) notes.add("No rare/mythic candidates.");
        }
        return new CustomSummary(any, c,u,r,m, notes);
    }

    private PackTest_Command() {}
}
