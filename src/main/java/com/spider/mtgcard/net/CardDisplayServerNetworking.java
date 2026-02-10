package com.spider.mtgcard.net;

import com.spider.mtgcard.display.CardDisplayEntity;
import com.spider.mtgcard.net.payload.CardDisplayPayloads;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;

public final class CardDisplayServerNetworking {

    public static void registerReceivers() {

        ServerPlayNetworking.registerGlobalReceiver(CardDisplayPayloads.DisplaySetFaceC2S.ID, (payload, ctx) -> {
            ctx.server().execute(() -> {
                if (!(ctx.player().getEntityWorld() instanceof ServerWorld sw)) return;

                CardDisplayEntity e = getDisplayEntity(sw, payload.entityId(), ctx.player().getX(), ctx.player().getY(), ctx.player().getZ());
                if (e == null) return;

                mutateEntityStack(e, st -> setFace(st, payload.face()));
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(CardDisplayPayloads.DisplaySetCounterValueC2S.ID, (payload, ctx) -> {
            ctx.server().execute(() -> {
                if (!(ctx.player().getEntityWorld() instanceof ServerWorld sw)) return;

                CardDisplayEntity e = getDisplayEntity(sw, payload.entityId(), ctx.player().getX(), ctx.player().getY(), ctx.player().getZ());
                if (e == null) return;

                mutateEntityStack(e, st -> setCounterValue(st, payload.key(), payload.value()));
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(CardDisplayPayloads.DisplaySetCounterMetaC2S.ID, (payload, ctx) -> {
            ctx.server().execute(() -> {
                if (!(ctx.player().getEntityWorld() instanceof ServerWorld sw)) return;

                CardDisplayEntity e = getDisplayEntity(sw, payload.entityId(), ctx.player().getX(), ctx.player().getY(), ctx.player().getZ());
                if (e == null) return;

                mutateEntityStack(e, st -> setCounterMeta(st, payload.key(), payload.displayName(), payload.iconKey()));
            });
        });
    }

    private static CardDisplayEntity getDisplayEntity(ServerWorld sw, int entityId, double px, double py, double pz) {
        var ent = sw.getEntityById(entityId);
        if (!(ent instanceof CardDisplayEntity e)) return null;

        // Basic anti-spoof: must be nearby (tweak radius as desired)
        double dx = e.getX() - px;
        double dy = e.getY() - py;
        double dz = e.getZ() - pz;
        if (dx*dx + dy*dy + dz*dz > 64.0) return null; // 8 blocks

        return e;
    }

    private static void mutateEntityStack(CardDisplayEntity e, java.util.function.Consumer<ItemStack> edit) {
        ItemStack cur = e.getStack();
        if (cur.isEmpty()) return;

        ItemStack copy = cur.copy();
        edit.accept(copy);
        e.setStack(copy);
    }

    // ---- your CUSTOM_DATA editing helpers unchanged ----

    private static NbtCompound getOrCreateMeta(ItemStack st) {
        var comp = st.get(DataComponentTypes.CUSTOM_DATA);
        NbtCompound root = (comp == null) ? new NbtCompound() : comp.copyNbt();
        NbtCompound meta = root.getCompound("mtg_meta").orElseGet(NbtCompound::new);
        root.put("mtg_meta", meta);
        st.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(root));
        return meta;
    }

    private static void setFace(ItemStack st, int faceIdx) {
        NbtCompound meta = getOrCreateMeta(st);
        meta.putInt("mtg_face", Math.max(0, faceIdx));

        var comp = st.get(DataComponentTypes.CUSTOM_DATA);
        NbtCompound root = (comp == null) ? new NbtCompound() : comp.copyNbt();
        root.put("mtg_meta", meta);
        st.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(root));
    }

    private static void setCounterValue(ItemStack st, String key, int value) {
        if (key == null) return;
        String k = key.trim().toLowerCase(java.util.Locale.ROOT).replace(' ', '_');
        if (k.isBlank()) return;

        var comp = st.get(DataComponentTypes.CUSTOM_DATA);
        NbtCompound root = (comp == null) ? new NbtCompound() : comp.copyNbt();
        NbtCompound meta = root.getCompound("mtg_meta").orElseGet(NbtCompound::new);
        NbtCompound counters = meta.getCompound("counters").orElseGet(NbtCompound::new);

        counters.putInt(k, Math.max(0, value));
        meta.put("counters", counters);
        root.put("mtg_meta", meta);

        st.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(root));
    }

    private static void setCounterMeta(ItemStack st, String key, String displayName, String iconKey) {
        if (key == null) return;
        String k = key.trim().toLowerCase(java.util.Locale.ROOT).replace(' ', '_');
        if (k.isBlank()) return;

        String name = (displayName == null) ? "" : displayName.trim();
        String icon = (iconKey == null) ? "none" : iconKey.trim().toLowerCase(java.util.Locale.ROOT).replace(' ', '_');
        if (icon.isBlank()) icon = "none";

        var comp = st.get(DataComponentTypes.CUSTOM_DATA);
        NbtCompound root = (comp == null) ? new NbtCompound() : comp.copyNbt();
        NbtCompound meta = root.getCompound("mtg_meta").orElseGet(NbtCompound::new);

        NbtCompound names = meta.getCompound("counter_names").orElseGet(NbtCompound::new);
        if (name.isBlank()) names.remove(k);
        else names.putString(k, name);
        meta.put("counter_names", names);

        NbtCompound icons = meta.getCompound("counter_icons").orElseGet(NbtCompound::new);
        icons.putString(k, icon);
        meta.put("counter_icons", icons);

        root.put("mtg_meta", meta);
        st.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(root));
    }

    private CardDisplayServerNetworking() {}
}
