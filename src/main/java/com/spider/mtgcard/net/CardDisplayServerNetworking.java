package com.spider.mtgcard.net;

import com.spider.mtgcard.display.CardDisplayEntity;
import com.spider.mtgcard.net.payload.CardDisplayPayloads;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;

public final class CardDisplayServerNetworking {

    public static void registerReceivers() {

        ServerPlayNetworking.registerGlobalReceiver(CardDisplayPayloads.DisplaySetFaceC2S.ID, (payload, ctx) -> {
            ctx.server().execute(() -> {
                if (!(ctx.player().level() instanceof ServerLevel sw)) return;

                CardDisplayEntity e = getDisplayEntity(sw, payload.entityId(), ctx.player().getX(), ctx.player().getY(), ctx.player().getZ());
                if (e == null) return;

                mutateEntityStack(e, st -> setFace(st, payload.face()));
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(CardDisplayPayloads.DisplaySetCounterValueC2S.ID, (payload, ctx) -> {
            ctx.server().execute(() -> {
                if (!(ctx.player().level() instanceof ServerLevel sw)) return;

                CardDisplayEntity e = getDisplayEntity(sw, payload.entityId(), ctx.player().getX(), ctx.player().getY(), ctx.player().getZ());
                if (e == null) return;

                mutateEntityStack(e, st -> setCounterValue(st, payload.key(), payload.value()));
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(CardDisplayPayloads.DisplaySetCounterMetaC2S.ID, (payload, ctx) -> {
            ctx.server().execute(() -> {
                if (!(ctx.player().level() instanceof ServerLevel sw)) return;

                CardDisplayEntity e = getDisplayEntity(sw, payload.entityId(), ctx.player().getX(), ctx.player().getY(), ctx.player().getZ());
                if (e == null) return;

                mutateEntityStack(e, st -> setCounterMeta(st, payload.key(), payload.displayName(), payload.iconKey()));
            });
        });
    }

    private static CardDisplayEntity getDisplayEntity(ServerLevel sw, int entityId, double px, double py, double pz) {
        var ent = sw.getEntity(entityId);
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

    private static CompoundTag getOrCreateMeta(ItemStack st) {
        var comp = st.get(DataComponents.CUSTOM_DATA);
        CompoundTag root = (comp == null) ? new CompoundTag() : comp.copyTag();
        CompoundTag meta = root.getCompound("mtg_meta").orElseGet(CompoundTag::new);
        root.put("mtg_meta", meta);
        st.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
        return meta;
    }

    private static void setFace(ItemStack st, int faceIdx) {
        CompoundTag meta = getOrCreateMeta(st);
        meta.putInt("mtg_face", Math.max(0, faceIdx));

        var comp = st.get(DataComponents.CUSTOM_DATA);
        CompoundTag root = (comp == null) ? new CompoundTag() : comp.copyTag();
        root.put("mtg_meta", meta);
        st.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
    }

    private static void setCounterValue(ItemStack st, String key, int value) {
        if (key == null) return;
        String k = key.trim().toLowerCase(java.util.Locale.ROOT).replace(' ', '_');
        if (k.isBlank()) return;

        var comp = st.get(DataComponents.CUSTOM_DATA);
        CompoundTag root = (comp == null) ? new CompoundTag() : comp.copyTag();
        CompoundTag meta = root.getCompound("mtg_meta").orElseGet(CompoundTag::new);
        CompoundTag counters = meta.getCompound("counters").orElseGet(CompoundTag::new);

        counters.putInt(k, Math.max(0, value));
        meta.put("counters", counters);
        root.put("mtg_meta", meta);

        st.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
    }

    private static void setCounterMeta(ItemStack st, String key, String displayName, String iconKey) {
        if (key == null) return;
        String k = key.trim().toLowerCase(java.util.Locale.ROOT).replace(' ', '_');
        if (k.isBlank()) return;

        String name = (displayName == null) ? "" : displayName.trim();
        String icon = (iconKey == null) ? "none" : iconKey.trim().toLowerCase(java.util.Locale.ROOT).replace(' ', '_');
        if (icon.isBlank()) icon = "none";

        var comp = st.get(DataComponents.CUSTOM_DATA);
        CompoundTag root = (comp == null) ? new CompoundTag() : comp.copyTag();
        CompoundTag meta = root.getCompound("mtg_meta").orElseGet(CompoundTag::new);

        CompoundTag names = meta.getCompound("counter_names").orElseGet(CompoundTag::new);
        if (name.isBlank()) names.remove(k);
        else names.putString(k, name);
        meta.put("counter_names", names);

        CompoundTag icons = meta.getCompound("counter_icons").orElseGet(CompoundTag::new);
        icons.putString(k, icon);
        meta.put("counter_icons", icons);

        root.put("mtg_meta", meta);
        st.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
    }

    private CardDisplayServerNetworking() {}
}
