package com.spider.mtgcard.client.gui;

import com.spider.mtgcard.client.java.CardArtManager;
import com.spider.mtgcard.graveyard.GraveyardScreenHandler;
import com.spider.mtgcard.net.payload.GraveyardActionPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;

import java.util.List;

import static com.spider.mtgcard.graveyard.GraveyardScreenHandler.TEX;

public class GraveyardScreen extends HandledScreen<GraveyardScreenHandler> {

    private ButtonWidget exileAllBtn;
    private ButtonWidget returnAllBtn;

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



    public GraveyardScreen(GraveyardScreenHandler handler, PlayerInventory inv, Text title) {
        super(handler, inv, title);

        int baseW = 8 + (10 * 18) + 18 + (10 * 18) + 8;   // 394
        int baseH = 24 + (10 * 18) + 22 + (4 * 18) + 12;

        this.backgroundWidth = baseW;        // ✅ main UI only
        this.backgroundHeight = baseH;
    }

    @Override
    protected void init() {
        super.init();

        // ✅ Force center (HandledScreen usually does this, but we want it locked)
        this.x = (this.width - this.backgroundWidth) / 2;
        this.y = (this.height - this.backgroundHeight) / 2;

        int colW = 10 * 18;

        int graveColX = this.x + 28;
        int exileColX = this.x + 220;


        // ✅ center buttons inside each 10-wide column
        int exileBtnX  = graveColX + (colW - 70) / 2;
        int returnBtnX = exileColX + (colW - 78) / 2;

        int btnY = this.y + 6;

        exileAllBtn = addDrawableChild(ButtonWidget.builder(Text.literal("Exile All"), b -> {
            ClientPlayNetworking.send(new GraveyardActionPayload(
                    this.handler.pos, this.handler.syncId, GraveyardActionPayload.Action.EXILE_ALL
            ));
        }).dimensions(exileBtnX, btnY, 70, 16).build());

        returnAllBtn = addDrawableChild(ButtonWidget.builder(Text.literal("Return All"), b -> {
            ClientPlayNetworking.send(new GraveyardActionPayload(
                    this.handler.pos, this.handler.syncId, GraveyardActionPayload.Action.RETURN_ALL
            ));
        }).dimensions(returnBtnX, btnY, 78, 16).build());

        updateButtonStates();
    }

    @Override
    protected void handledScreenTick() {
        super.handledScreenTick();
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
        int end = Math.min(start + count, this.handler.slots.size());
        for (int i = start; i < end; i++) {
            if (this.handler.getSlot(i).hasStack()) return true;
        }
        return false;
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        this.renderBackground(ctx, mouseX, mouseY, delta);
        super.render(ctx, mouseX, mouseY, delta);
        this.drawMouseoverTooltip(ctx, mouseX, mouseY);

        renderHoverPreview(ctx, mouseX, mouseY, delta);
    }

    @Override
    protected void drawBackground(DrawContext ctx, float delta, int mouseX, int mouseY) {
        // draw full background texture (assumes texture is 512x512)
        ctx.drawTexture(
                RenderPipelines.GUI_TEXTURED,
                TEX,
                this.x, this.y,          // screen position
                0, 0,                    // texture u,v
                this.backgroundWidth,    // draw width
                this.backgroundHeight,   // draw height
                512, 512                 // texture size
        );

        int colW = 10 * 18;

        int headerY = this.y + 6;
        ctx.drawTextWithShadow(textRenderer, Text.literal("Graveyard"), this.x + 8, headerY, 0xFFFFFFFF);
        ctx.drawTextWithShadow(textRenderer, Text.literal("Exile"), this.x + 8 + colW + 18, headerY, 0xFFFFFFFF);
    }


    private boolean isMouseOverSlotArea(Slot slot, int mouseX, int mouseY) {
        int sx = this.x + slot.x;
        int sy = this.y + slot.y;
        return mouseX >= sx && mouseX < sx + 16 && mouseY >= sy && mouseY < sy + 16;
    }

    private static int readFaceIndex(ItemStack st) {
        var comp = st.get(DataComponentTypes.CUSTOM_DATA);
        var root = (comp == null) ? new net.minecraft.nbt.NbtCompound() : comp.copyNbt();
        var meta = root.getCompound("mtg_meta").orElseGet(net.minecraft.nbt.NbtCompound::new);
        return meta.getInt("mtg_face").orElse(0);
    }

    private boolean isFoil(ItemStack st) {
        Boolean glint = st.get(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE);
        boolean hasGlint = glint != null && glint;
        var comp = st.get(DataComponentTypes.CUSTOM_DATA);
        var root = (comp == null) ? new net.minecraft.nbt.NbtCompound() : comp.copyNbt();
        boolean foilNbt = root.getBoolean("mtg_foil").orElse(false);
        return hasGlint || foilNbt;
    }
    @Override
    protected void drawForeground(DrawContext ctx, int mouseX, int mouseY) {
        // intentionally empty; we draw headers in drawBackground
    }


    private void renderHoverPreview(DrawContext ctx, int mouseX, int mouseY, float delta) {
        var slot = this.focusedSlot;
        if (slot == null || !slot.hasStack() || !isMouseOverSlotArea(slot, mouseX, mouseY)) {
            lastHoverStack = ItemStack.EMPTY;
            lastTexRef = null;
            return;
        }

        ItemStack st = slot.getStack();
        if (!st.isOf(com.spider.mtgcard.item.ModItems.CARD)) {
            lastHoverStack = ItemStack.EMPTY;
            lastTexRef = null;
            return;
        }

        long now = System.currentTimeMillis();
        if (!ItemStack.areEqual(st, lastHoverStack)) {
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

        final int panelMaxH = Math.min(this.backgroundHeight - 8, 220);
        final int panelMaxW = 180;

        int panelW = panelMaxW;
        int panelH = panelMaxH;

        // ✅ ONLY change from Deckbox: prefer right side first, fallback left
        int panelX = this.x + this.backgroundWidth + 12;
        int panelY = this.y + 4;
        if (panelX + panelW > this.width - 8) panelX = this.x - (panelW + 12);

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

        var m = ctx.getMatrices();
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

        ctx.drawTexture(RenderPipelines.GUI_TEXTURED, ref.id(),
                0, 0, 0f, 0f, texW, texH, texW, texH, colorShadow);

        m.set(m00, m01, m10, m11, m20, m21);

        // main
        m.translate((float) x, (float) y);
        m.scale(sx * scaleAnim, sy * scaleAnim);
        m.translate(texW / 2f, texH / 2f);
        m.rotate((float) Math.toRadians(angleDeg));
        m.translate(-texW / 2f, -texH / 2f);

        ctx.drawTexture(RenderPipelines.GUI_TEXTURED, ref.id(),
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

                ctx.drawTexture(RenderPipelines.GUI_TEXTURED, ref.id(),
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
