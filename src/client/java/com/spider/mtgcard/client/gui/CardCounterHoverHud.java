package com.spider.mtgcard.client.gui;

import com.spider.mtgcard.Mtgcard;
import com.spider.mtgcard.display.CardDisplayEntity;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.*;

@Environment(EnvType.CLIENT)
public final class CardCounterHoverHud {

    private static final int ICON = 16;
    private static final int ROW_H = 18;
    private static final int PAD_X = 8;

    private static CardDisplayEntity hovered = null;
    private static int hoverHoldTicks = 0; // small hysteresis
    private static ItemStack lastHoverStack = ItemStack.EMPTY;
    private static List<Row> cachedRows = List.of();

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> tick(client));
        HudElementRegistry.addLast(
                Identifier.fromNamespaceAndPath(Mtgcard.MOD_ID, "card_counter_hover"),
                (graphics, tickDelta) -> renderHud(new GuiGraphics(graphics))
        );
    }

    private static void tick(Minecraft client) {
        CardDisplayEntity now = null;

        HitResult hr = client.hitResult;
        if (hr instanceof EntityHitResult ehr && ehr.getEntity() instanceof CardDisplayEntity cde) {
            now = cde;
        }

        if (now != null) {
            hovered = now;
            hoverHoldTicks = 6; // keep showing briefly to avoid flicker
        } else {
            if (hoverHoldTicks > 0) hoverHoldTicks--;
            else {
                hovered = null;
                lastHoverStack = ItemStack.EMPTY;
                cachedRows = List.of();
            }
        }
    }

    private static void renderHud(GuiGraphics ctx) {
        if (hovered == null) return;

        ItemStack st = hovered.getStack();
        if (st == null || st.isEmpty()) return;

        // build rows: icon + value + name (optional)
        List<Row> rows = getCachedRows(st);
        if (rows.isEmpty()) return;

        Minecraft client = Minecraft.getInstance();
        int screenH = client.getWindow().getGuiScaledHeight();
        int x = PAD_X;
        int y = (screenH - rows.size() * ROW_H) / 2;

        // optional background panel
        int w = 40;
        int h = rows.size() * ROW_H + 6;
        ctx.fill(x - 4, y - 4, x + w, y + h, 0x7A101010);
        ctx.fill(x - 5, y - 5, x + w + 1, y + h + 1, 0xAA303030);

        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            int ry = y + i * ROW_H;

            // icon
            if (r.iconTex != null) {
                // draw full texture into 16x16 (no stretching weirdness)
                ctx.blit(RenderPipelines.GUI_TEXTURED, r.iconTex,
                        x, ry, 0, 0,
                        ICON, ICON,
                        ICON, ICON,
                        ICON, ICON
                );
            } else {
                ctx.drawString(client.font, "—", x + 4, ry + 4, 0xFF777777, false);
            }

            // value
            ctx.drawString(client.font, r.valueText, x + ICON + 6, ry + 4, 0xFFFFFFFF);

            // optional label (uncomment if you want)
            // ctx.drawTextWithShadow(client.textRenderer, r.label, x + ICON + 34, ry + 4, 0xFFAAAAAA);
        }
    }

    private record Row(String key, String label, Identifier iconTex, String valueText) {}

    private static List<Row> getCachedRows(ItemStack st) {
        if (!ItemStack.matches(st, lastHoverStack)) {
            lastHoverStack = st.copy();
            cachedRows = buildCounterRows(st);
        }
        return cachedRows;
    }

    private static List<Row> buildCounterRows(ItemStack st) {
        CompoundTag meta = getMeta(st);
        CompoundTag counters = meta.getCompound("counters").orElse(null);
        if (counters == null) return List.of();

        CompoundTag icons = meta.getCompound("counter_icons").orElse(null);
        CompoundTag names = meta.getCompound("counter_names").orElse(null);

        List<Row> out = new ArrayList<>();

        for (String k : counters.keySet()) {
            int v = counters.getInt(k).orElse(0);
            if (v <= 0) continue;

            String iconKey = (icons == null) ? "none" : icons.getString(k).orElse("none");
            Identifier tex = (!"none".equals(iconKey))
                    ? Identifier.fromNamespaceAndPath("mtgcard", "textures/gui/counters/" + iconKey + ".png")
                    : null;

            String label = (names == null) ? "" : names.getString(k).orElse("");
            if (label == null || label.isBlank()) label = toTitle(k.replace('_', ' '));

            String valueText = String.valueOf(v);

            out.add(new Row(k, label, tex, valueText));
        }

        out.sort((a, b) -> {
            int pa = priority(a.key);
            int pb = priority(b.key);
            if (pa != pb) return Integer.compare(pa, pb);
            return a.label.compareToIgnoreCase(b.label);
        });

        return out;
    }

    private static int priority(String key) {
        return switch (key) {
            case "poison" -> 0;
            case "energy" -> 1;
            case "experience" -> 2;
            case "loyalty" -> 3;
            case "shield" -> 4;
            case "stun" -> 5;
            case "time" -> 6;
            default -> 100;
        };
    }

    private static String toTitle(String s) {
        if (s == null || s.isBlank()) return "—";
        String[] parts = s.trim().split("\\s+");
        StringBuilder out = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) continue;
            if (!out.isEmpty()) out.append(' ');
            out.append(Character.toUpperCase(p.charAt(0)));
            if (p.length() > 1) out.append(p.substring(1));
        }
        return out.toString();
    }

    private static CompoundTag getMeta(ItemStack st) {
        var comp = st.get(DataComponents.CUSTOM_DATA);
        CompoundTag root = (comp == null) ? new CompoundTag() : comp.copyTag();
        return root.getCompound("mtg_meta").orElseGet(CompoundTag::new);
    }

    private CardCounterHoverHud() {}
}
