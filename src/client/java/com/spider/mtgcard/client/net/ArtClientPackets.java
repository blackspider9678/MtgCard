package com.spider.mtgcard.client.net;

import com.spider.mtgcard.client.java.CardArtManager;
import com.spider.mtgcard.net.ArtPackets;
import com.spider.mtgcard.net.CustomCardPackets;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ArtClientPackets {

    private static final class IncomingArt {
        final int total;
        final byte[][] parts;
        int received = 0;

        IncomingArt(int total) {
            this.total = Math.max(1, total);
            this.parts = new byte[this.total][];
        }

        boolean add(int idx, byte[] data) {
            if (idx < 0 || idx >= total) return false;
            if (data == null) data = new byte[0];

            if (parts[idx] == null) {
                parts[idx] = data;
                received++;
            }
            return received >= total;
        }

        byte[] join() {
            int sum = 0;
            for (byte[] p : parts) sum += (p == null ? 0 : p.length);

            byte[] out = new byte[sum];
            int o = 0;
            for (byte[] p : parts) {
                if (p == null) continue;
                System.arraycopy(p, 0, out, o, p.length);
                o += p.length;
            }
            return out;
        }
    }

    // artKey -> accumulator
    private static final Map<String, IncomingArt> INCOMING = new ConcurrentHashMap<>();

    public static void registerClientReceivers() {
        ClientPlayNetworking.registerGlobalReceiver(
                ArtPackets.ArtChunk.ID,
                (payload, ctx) -> {
                    final String key = payload.artKey();
                    final String game = payload.game();
                    if (key == null || key.isBlank()) return;

                    ctx.client().execute(() -> {
                        String incomingKey = (game == null || game.isBlank() ? "mtg" : game.trim().toLowerCase(java.util.Locale.ROOT)) + ":" + key;
                        // get/create accumulator for this key
                        IncomingArt acc = INCOMING.get(incomingKey);
                        if (acc == null || acc.total != payload.total()) {
                            acc = new IncomingArt(payload.total());
                            INCOMING.put(incomingKey, acc);
                        }

                        boolean done = acc.add(payload.index(), payload.data());
                        if (done) {
                            INCOMING.remove(incomingKey);

                            byte[] bytes = acc.join();

                            // Reuse your old path exactly
                            CardArtManager.onArtResponse(game, key, bytes);
                        }
                    });
                }
        );
        ClientPlayNetworking.registerGlobalReceiver(CustomCardPackets.CustomArtReady.ID, (payload, ctx) -> {
            String key = payload.artKey();
            String setCode = payload.setCode();
            ctx.client().execute(() -> {
                com.spider.mtgcard.client.java.CardArtManager.refreshWorldArt(key, setCode);
            });
        });
        ClientPlayNetworking.registerGlobalReceiver(CustomCardPackets.CustomArtInvalidate.ID, (payload, ctx) -> {
            var keys = payload.artKeys();
            ctx.client().execute(() -> {
                if (keys == null) return;
                for (String key : keys) {
                    com.spider.mtgcard.client.java.CardArtManager.forgetWorldArt(key);
                }
            });
        });
    }

    private ArtClientPackets() {}
}
