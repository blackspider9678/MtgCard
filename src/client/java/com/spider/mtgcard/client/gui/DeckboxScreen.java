package com.spider.mtgcard.client.gui;

import com.spider.mtgcard.client.compat.LegacyContainerScreen;
import com.spider.mtgcard.client.java.CardArtManager;
import com.spider.mtgcard.deckbox.DeckboxScreenHandler;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.Slot;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public class DeckboxScreen extends LegacyContainerScreen<DeckboxScreenHandler> {

    // Your GUI texture
    private static final Identifier TEX = Identifier.fromNamespaceAndPath("mtgcard", "textures/gui/deckbox.png");

    // --- Preview state (keep yours) ---
    private static final int PREVIEW_W = 260;
    private static final int PREVIEW_MARGIN = 16;
    private long hoverSinceMs = 0L;
    private static final long HOVER_DEBOUNCE_MS = 80;

    private ItemStack lastHoverStack = ItemStack.EMPTY;
    private int lastHoverFace = 0;
    private CardArtManager.TextureRef lastTexRef = null;

    // Animation
    private long animStartMs = 0L;
    private static final int ANIM_MS = 180;
    private static final float ANIM_SCALE_FROM = 0.92f;

    // container indices for side slots
    private static final int SLOT_BUNDLE    = com.spider.mtgcard.deckbox.DeckboxBlockEntity.FIRST_SIDE_SLOT;  // 99
    private static final int SLOT_COMMANDER = com.spider.mtgcard.deckbox.DeckboxBlockEntity.SECOND_SIDE_SLOT; // 100
    private static final int SLOT_PARTNER   = com.spider.mtgcard.deckbox.DeckboxBlockEntity.THIRD_SIDE_SLOT;  // 101

    private static int argb(int a, int rgb) {
        return ((a & 0xFF) << 24) | (rgb & 0xFFFFFF);
    }

    public DeckboxScreen(DeckboxScreenHandler handler, Inventory inv, Component title) {
        super(handler, inv, title);
        this.imageWidth = 212;
        this.imageHeight = 310;
    }

    @Override
    protected void init() {
        if (applyDefaultGuiScale()) return;

        super.init();
        this.leftPos = (this.width - this.imageWidth) / 2;
        this.topPos = (this.height - this.imageHeight) / 2;
    }

    /**
     * deckbox.png layout (256x256)
     *
     * Suggested regions inside the PNG:
     *  - Outer panel frame:   (0,0)   size 212x310 DOES NOT FIT in 256, so we tile/9-slice style.
     *  - We’ll instead use small pieces:
     *
     *  (0,0)   16x16 = corner
     *  (16,0)  16x16 = top edge tile
     *  (0,16)  16x16 = left edge tile
     *  (16,16) 16x16 = center tile
     *
     *  Slot well tile: (64,0)  18x18
     *  Header strip:   (64,32)  1x18  (we stretch horizontally)
     *  Divider strip:  (64,52)  1x2   (we stretch horizontally)
     *
     * You can paint these however you want.
     */
    private static final int TEX_W = 512;
    private static final int TEX_H = 512;

    @Override
    protected void renderBg(GuiGraphics ctx, float delta, int mouseX, int mouseY) {
        // Draw the background panel from the top-left of the PNG
        ctx.blit(
                RenderPipelines.GUI_TEXTURED,
                TEX,
                this.leftPos, this.topPos,
                0, 0,                          // u, v
                this.imageWidth,          // drawW (212)
                this.imageHeight,         // drawH (310)
                TEX_W, TEX_H                   // texture size
        );

        // Keep your tint bar if you want (optional)
        int tint = this.menu.getRgbTint();
        int accent = argb(0x66, tint);
        ctx.fill(this.leftPos + 8, this.topPos + 6, this.leftPos + this.imageWidth - 8, this.topPos + 8, accent);
    }

    @Override
    public void render(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        this.renderBackground(ctx, mouseX, mouseY, delta);
        super.render(ctx, mouseX, mouseY, delta);
        this.renderTooltip(ctx, mouseX, mouseY);
        renderHoverPreview(ctx, mouseX, mouseY, delta);
        drawSideSlotHints(ctx, mouseX, mouseY);
    }

    private void drawSideSlotHints(GuiGraphics ctx, int mouseX, int mouseY) {
        Slot slot = this.hoveredSlot;
        if (slot == null) return;

        int idx;
        try {
            idx = slot.getContainerSlot();
        } catch (Throwable t) {
            return; // mappings edge case
        }

        if (idx == SLOT_COMMANDER && !slot.hasItem()) {
            ctx.setTooltipForNextFrame(this.font, Component.literal("Commander Slot"), mouseX, mouseY);
        } else if (idx == SLOT_PARTNER && !slot.hasItem()) {
            ctx.setTooltipForNextFrame(this.font, Component.literal("Partner Slot"), mouseX, mouseY);
        } else if (idx == SLOT_BUNDLE && !slot.hasItem()) {
            ctx.setTooltipForNextFrame(this.font, Component.literal("Bundle Slot"), mouseX, mouseY);
        }
    }

    public static int readFaceIndex(ItemStack st) {
        var comp = st.get(DataComponents.CUSTOM_DATA);
        net.minecraft.nbt.CompoundTag root = (comp == null) ? new net.minecraft.nbt.CompoundTag() : comp.copyTag();
        net.minecraft.nbt.CompoundTag meta = root.getCompound("mtg_meta").orElseGet(net.minecraft.nbt.CompoundTag::new);
        return meta.getInt("mtg_face").orElse(0);
    }

    private boolean isMouseOverSlotArea(Slot slot, int mouseX, int mouseY) {
        int sx = this.leftPos + slot.x;
        int sy = this.topPos + slot.y;
        return mouseX >= sx && mouseX < sx + 16 && mouseY >= sy && mouseY < sy + 16;
    }

    private boolean isFoil(ItemStack st) {
        Boolean glint = st.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE);
        boolean hasGlint = glint != null && glint;
        var comp = st.get(DataComponents.CUSTOM_DATA);
        var root = (comp == null) ? new net.minecraft.nbt.CompoundTag() : comp.copyTag();
        boolean foilNbt = root.getBoolean("mtg_foil").orElse(false);
        return hasGlint || foilNbt;
    }

    private void renderHoverPreview(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        var slot = this.hoveredSlot;
        if (slot == null || !slot.hasItem() || !isMouseOverSlotArea(slot, mouseX, mouseY)) {
            lastHoverStack = ItemStack.EMPTY;
            lastTexRef = null;
            return;
        }

        ItemStack st = slot.getItem();
        if (!st.is(com.spider.mtgcard.item.ModItems.CARD)) {
            lastHoverStack = ItemStack.EMPTY;
            lastTexRef = null;
            return;
        }

        long now = System.currentTimeMillis();
        if (!ItemStack.matches(st, lastHoverStack)) {
            hoverSinceMs = now;
            lastHoverStack = st.copy();
            lastTexRef = null;
            animStartMs = now;
        }
        if (now - hoverSinceMs < HOVER_DEBOUNCE_MS) return;

        int face = readFaceIndex(st);
        if (face != lastHoverFace) {
            lastHoverFace = face;
            lastTexRef = null;
            animStartMs = now;
        }

        CardArtManager.TextureRef ref = CardArtManager.getOrRequestFace(st, face);
        if (ref == null || ref.id() == null) return;
        lastTexRef = ref;

        // layout near GUI (same as your version)
        final int panelMaxH = Math.min(this.imageHeight - 8, 220);
        final int panelMaxW = 180;

        int panelW = panelMaxW;
        int panelH = panelMaxH;
        int panelX = this.leftPos - (panelW + 12);
        int panelY = this.topPos + 4;
        if (panelX < 8) panelX = this.leftPos + this.imageWidth + 12;

        final int texW = ref.texW();
        final int texH = ref.texH();
        float aspect = (float) texW / (float) texH;

        int drawW = panelW;
        int drawH = (int) (drawW / aspect);
        if (drawH > panelH) {
            drawH = panelH;
            drawW = (int) (drawH * aspect);
        }

        int x = panelX + (panelW - drawW) / 2;
        int y = panelY + (panelH - drawH) / 2;

        long nowMs = System.currentTimeMillis();
        float t = Math.max(0f, Math.min(1f, (nowMs - animStartMs) / (float) ANIM_MS));
        float ease = t * t * (3f - 2f * t);

        float scaleAnim = ANIM_SCALE_FROM + (1f - ANIM_SCALE_FROM) * ease;
        float angleDeg = (1f - ease) * 2.5f;

        int alphaMain = (int) (255f * (0.40f + 0.60f * ease));
        int colorMain = (alphaMain << 24) | 0x00FFFFFF;
        int colorShadow = 0x55000000;

        float sx = (float) drawW / (float) texW;
        float sy = (float) drawH / (float) texH;

        var m = ctx.pose();
        float m00 = m.m00(), m01 = m.m01();
        float m10 = m.m10(), m11 = m.m11();
        float m20 = m.m20(), m21 = m.m21();

        // shadow
        m.translate((float) x, (float) y);
        m.scale(sx * 1.02f * scaleAnim, sy * 1.02f * scaleAnim);
        m.translate(texW / 2f, texH / 2f);
        m.rotate((float) Math.toRadians(angleDeg));
        m.translate(-texW / 2f, -texH / 2f);
        m.translate(2f, 3f);

        ctx.blit(RenderPipelines.GUI_TEXTURED, ref.id(),
                0, 0, 0f, 0f, texW, texH, texW, texH, colorShadow);

        m.set(m00, m01, m10, m11, m20, m21);

        // main
        m.translate((float) x, (float) y);
        m.scale(sx * scaleAnim, sy * scaleAnim);
        m.translate(texW / 2f, texH / 2f);
        m.rotate((float) Math.toRadians(angleDeg));
        m.translate(-texW / 2f, -texH / 2f);

        ctx.blit(RenderPipelines.GUI_TEXTURED, ref.id(),
                0, 0, 0f, 0f, texW, texH, texW, texH, colorMain);

        // foil shimmer
        if (isFoil(st)) {
            m.set(m00, m01, m10, m11, m20, m21);
            m.translate((float) x, (float) y);
            m.scale(sx * scaleAnim, sy * scaleAnim);
            m.translate(texW / 2f, texH / 2f);
            m.rotate((float) Math.toRadians(angleDeg));
            m.translate(-texW / 2f, -texH / 2f);

            int stripePx = Math.max(6, (int) (texW * 0.22f));
            float travel = texW + stripePx * 2f;
            float speed = 0.22f;
            float pos = ((nowMs / 16f) * speed) % travel - stripePx;

            int u = Math.round(pos);
            if (u < texW && u + stripePx > 0) {
                int drawU = Math.max(0, Math.min(texW - stripePx, u));
                int clipW = Math.min(stripePx, texW - drawU);

                int shimmerAlpha = (int) (0x88 + 0x2A * ease);
                int colorShimmer = (shimmerAlpha << 24) | 0x00FFFFFF;

                ctx.blit(RenderPipelines.GUI_TEXTURED, ref.id(),
                        drawU, 0, (float) drawU, 0f,
                        clipW, texH,
                        texW, texH,
                        colorShimmer
                );
            }
        }

        m.set(m00, m01, m10, m11, m20, m21);
    }

    @Override
    protected void renderLabels(GuiGraphics ctx, int mouseX, int mouseY) {
        // Title
        ctx.drawString(this.font, this.title, 25, 7, 0xF2F2F2, false);

        // Player inventory label (just above player slots)
        ctx.drawString(this.font, this.playerInventoryTitle, 25, 225 - 12, 0xCFCFCF, false);
    }
}
