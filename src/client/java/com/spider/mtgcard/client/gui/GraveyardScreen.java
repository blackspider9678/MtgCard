package com.spider.mtgcard.client.gui;

import com.spider.mtgcard.client.compat.LegacyContainerScreen;
import com.spider.mtgcard.client.java.CardArtManager;
import com.spider.mtgcard.graveyard.GraveyardScreenHandler;
import com.spider.mtgcard.net.payload.GraveyardActionPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.Slot;
import net.minecraft.network.chat.Component;

import java.util.List;

import static com.spider.mtgcard.graveyard.GraveyardScreenHandler.TEX;

public class GraveyardScreen extends LegacyContainerScreen<GraveyardScreenHandler> {

    private Button exileAllBtn;
    private Button returnAllBtn;

    // must match your handler: 0..99 graveyard, 100..199 exile
    private static final int GRID_SIZE = 100;
    private static final int GRAVE_START = 0;
    private static final int EXILE_START = 100;

    // --- Preview state (same as Deckbox) ---
    private static final int PREVIEW_W = 260;
    private static final int PREVIEW_MARGIN = 16;
    private long hoverSinceMs = 0L;
    private static final long HOVER_DEBOUNCE_MS = 80;

    private ItemStack lastHoverStack = ItemStack.EMPTY;
    private int lastHoverFace = 0;
    private CardArtManager.TextureRef lastTexRef = null;

    // Animation (same as Deckbox)
    private long animStartMs = 0L;
    private static final int ANIM_MS = 180;
    private static final float ANIM_SCALE_FROM = 0.92f;



    public GraveyardScreen(GraveyardScreenHandler handler, Inventory inv, Component title) {
        super(handler, inv, title);

        int baseW = 8 + (10 * 18) + 18 + (10 * 18) + 8;   // 394
        int baseH = 24 + (10 * 18) + 22 + (4 * 18) + 12;

        this.imageWidth = baseW;        // ✅ main UI only
        this.imageHeight = baseH;
    }

    @Override
    protected void init() {
        super.init();

        // ✅ Force center (HandledScreen usually does this, but we want it locked)
        this.leftPos = (this.width - this.imageWidth) / 2;
        this.topPos = (this.height - this.imageHeight) / 2;

        int colW = 10 * 18;

        int graveColX = this.leftPos + 28;
        int exileColX = this.leftPos + 220;


        // ✅ center buttons inside each 10-wide column
        int exileBtnX  = graveColX + (colW - 70) / 2;
        int returnBtnX = exileColX + (colW - 78) / 2;

        int btnY = this.topPos + 6;

        exileAllBtn = addRenderableWidget(Button.builder(Component.literal("Exile All"), b -> {
            ClientPlayNetworking.send(new GraveyardActionPayload(
                    this.menu.pos, this.menu.containerId, GraveyardActionPayload.Action.EXILE_ALL
            ));
        }).bounds(exileBtnX, btnY, 70, 16).build());

        returnAllBtn = addRenderableWidget(Button.builder(Component.literal("Return All"), b -> {
            ClientPlayNetworking.send(new GraveyardActionPayload(
                    this.menu.pos, this.menu.containerId, GraveyardActionPayload.Action.RETURN_ALL
            ));
        }).bounds(returnBtnX, btnY, 78, 16).build());

        updateButtonStates();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        updateButtonStates();
    }

    private void updateButtonStates() {
        boolean graveHasAny = hasAnyInBlockRange(GRAVE_START, GRID_SIZE);
        boolean exileHasAny = hasAnyInBlockRange(EXILE_START, GRID_SIZE);

        if (exileAllBtn != null) exileAllBtn.active = graveHasAny;
        if (returnAllBtn != null) returnAllBtn.active = exileHasAny;
    }

    /** Checks the first 200 handler slots (your block inventory slots) for any stack in a range. */
    private boolean hasAnyInBlockRange(int start, int count) {
        int end = Math.min(start + count, this.menu.slots.size());
        for (int i = start; i < end; i++) {
            if (this.menu.getSlot(i).hasItem()) return true;
        }
        return false;
    }

    @Override
    public void render(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        this.renderBackground(ctx, mouseX, mouseY, delta);
        super.render(ctx, mouseX, mouseY, delta);
        this.renderTooltip(ctx, mouseX, mouseY);

        renderHoverPreview(ctx, mouseX, mouseY, delta);
    }

    @Override
    protected void renderBg(GuiGraphics ctx, float delta, int mouseX, int mouseY) {
        // draw full background texture (assumes texture is 512x512)
        ctx.blit(
                RenderPipelines.GUI_TEXTURED,
                TEX,
                this.leftPos, this.topPos,          // screen position
                0, 0,                    // texture u,v
                this.imageWidth,    // draw width
                this.imageHeight,   // draw height
                512, 512                 // texture size
        );

        int colW = 10 * 18;

        int headerY = this.topPos + 6;
        ctx.drawString(font, Component.literal("Graveyard"), this.leftPos + 8, headerY, 0xFFFFFFFF);
        ctx.drawString(font, Component.literal("Exile"), this.leftPos + 8 + colW + 18, headerY, 0xFFFFFFFF);
    }


    private boolean isMouseOverSlotArea(Slot slot, int mouseX, int mouseY) {
        int sx = this.leftPos + slot.x;
        int sy = this.topPos + slot.y;
        return mouseX >= sx && mouseX < sx + 16 && mouseY >= sy && mouseY < sy + 16;
    }

    private static int readFaceIndex(ItemStack st) {
        var comp = st.get(DataComponents.CUSTOM_DATA);
        var root = (comp == null) ? new net.minecraft.nbt.CompoundTag() : comp.copyTag();
        var meta = root.getCompound("mtg_meta").orElseGet(net.minecraft.nbt.CompoundTag::new);
        return meta.getInt("mtg_face").orElse(0);
    }

    private boolean isFoil(ItemStack st) {
        Boolean glint = st.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE);
        boolean hasGlint = glint != null && glint;
        var comp = st.get(DataComponents.CUSTOM_DATA);
        var root = (comp == null) ? new net.minecraft.nbt.CompoundTag() : comp.copyTag();
        boolean foilNbt = root.getBoolean("mtg_foil").orElse(false);
        return hasGlint || foilNbt;
    }
    @Override
    protected void renderLabels(GuiGraphics ctx, int mouseX, int mouseY) {
        // intentionally empty; we draw headers in drawBackground
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

        final int panelMaxH = Math.min(this.imageHeight - 8, 220);
        final int panelMaxW = 180;

        int panelW = panelMaxW;
        int panelH = panelMaxH;

        // ✅ ONLY change from Deckbox: prefer right side first, fallback left
        int panelX = this.leftPos + this.imageWidth + 12;
        int panelY = this.topPos + 4;
        if (panelX + panelW > this.width - 8) panelX = this.leftPos - (panelW + 12);

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

}
