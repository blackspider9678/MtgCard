// DeckControlScreen.java
package com.spider.mtgcard.client.gui;

import com.spider.mtgcard.client.java.CardArtManager;
import com.spider.mtgcard.db.search.CardMeta;
import com.spider.mtgcard.deckbox.DeckboxBlockEntity;
import com.spider.mtgcard.deckcontrol.DeckControlBlockEntity;
import com.spider.mtgcard.deckcontrol.DeckControlPackets;
import com.spider.mtgcard.deckcontrol.DeckControlScreenHandler;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix3x2f;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

import static com.spider.mtgcard.deckcontrol.DeckControlBlockEntity.LIBRARY_SLOTS;
import static com.spider.mtgcard.deckcontrol.DeckControlScreenHandler.GUI_H;
import static com.spider.mtgcard.deckcontrol.DeckControlScreenHandler.GUI_W;

/**
 * Put in:
 *   src/client/java/com/spider/mtgcard/client/gui/DeckControlScreen.java
 */
public class DeckControlScreen extends HandledScreen<DeckControlScreenHandler> {

    // --- layout constants ---
    private static final int PAD = 8;

    private static final int BTN_H = 20;

    // Main screen: left selected-card panel
    private static final int SEL_PANEL_W = 104;
    private static final int SEL_CARD_W = 92;
    private static final int SEL_CARD_H = 128;

    // Main screen: right controls
    private static final int GAP = 6;
    private static final int STEP_W = 18;          // [-] [+]
    private static final int COUNT_PAD = 6;        // spacing before "x#"
    private static final int COUNT_TEXT_Y_OFF = 6; // baseline-ish in button row

    // Overlay render boxes (Reveal/Scry/Surveil)
    private static final int OVER_CARD_W = 28;
    private static final int OVER_CARD_H = 40;
    private static final int OVER_GAP = 6;

    // Overlay
    private OverlayMode overlay = OverlayMode.NONE;

    // Hover-selected (from player inventory)
    private ItemStack hoveredPlayerCard = ItemStack.EMPTY;
    private int hoveredPlayerInvIndex = -1; // 0..35 (PLAYER INVENTORY INDEX)

    private String toast = "";
    private int toastTicks = 0;

    // Place overlay state
    private boolean placeBottom = false;
    private int placeFromTop = 3;

    // Amount steppers
    private int revealN = 1;
    private int scryN = 1;
    private int surveilN = 1;
    private int millN = 1;

    // Cascade (kept as UI-only for now)
    private int cascadeSourceMv = 4;

    // Buttons
    private ButtonWidget btnDraw;
    private ButtonWidget btnShuffle;

    private ButtonWidget btnStartCascade;
    private ButtonWidget btnPlaceHovered;

    private ButtonWidget btnOverlayPlaceConfirm;
    private ButtonWidget btnOverlayConfirm; // Scry/Surveil Confirm
    private ButtonWidget btnOverlayDone;    // Reveal Done
    private ButtonWidget overlayCancel;

    // Stepper buttons (main screen)
    private ButtonWidget btnScryMinus, btnScryPlus;
    private ButtonWidget btnSurveilMinus, btnSurveilPlus;
    private ButtonWidget btnRevealMinus, btnRevealPlus;
    private ButtonWidget btnMillMinus, btnMillPlus;

    // Place overlay controls (extra buttons)
    private ButtonWidget btnPlaceTop;
    private ButtonWidget btnPlaceBottomMode;
    private ButtonWidget btnPlaceMinus;
    private ButtonWidget btnPlacePlus;

    private int cascadeHitIndex = -1;
    private int cascadeSourceMvNet = 0;
    private List<ItemStack> cascadeRevealed = List.of();

    private ButtonWidget btnCascadeCast;
    private ButtonWidget btnCascadeExile;

    // Server-driven overlay payload state
    private DeckControlPackets.OverlayKind overlayKind = null;
    private int overlayN = 0;
    private List<ItemStack> overlayCards = List.of();

    // For scry/surveil selection
    // SCRY: bit=1 means KEEP ON TOP
    // SURVEIL: bit=1 means MILL
    private int bitmask = 0;

    // "Locked" selection so you can move mouse away and still use buttons
    private ItemStack selectedPlayerCard = ItemStack.EMPTY;
    private int selectedPlayerInvIndex = -1;

    // fallback "card back" texture (your existing item texture)
    private static final Identifier CARD_BACK_TEX =
            Identifier.of("mtgcard", "textures/item/card.png");

    private boolean cascadePendingClient = false;

    // ---- Drag/drop (SCRY/SURVEIL) ----
    private final List<Integer> topOrder = new ArrayList<>();
    private final List<Integer> bottomOrder = new ArrayList<>();

    private boolean dragging = false;
    private int dragCardIdx = -1;          // index into overlayCards
    private boolean dragFromTop = true;
    private int dragFromPos = -1;          // position within list
    private int dragMouseOffX = 0;
    private int dragMouseOffY = 0;

    // cached layout for hit-testing (computed each frame)
    private int scry_ox, scry_oy, scry_ow, scry_btnY;
    private int scry_areaX, scry_areaW;
    private int scry_topRowY, scry_botRowY;
    private int scry_cols;

    // --- Hover regions for overlay windows (screen-space coords) ---
    private int hoverTopX, hoverTopY, hoverTopCols;
    private int hoverBotX, hoverBotY, hoverBotCols;

    // true = topOrder/bottomOrder are indices into overlayCards (scry/surveil)
// false = direct list order (reveal/cascade/etc)
    private boolean hoverUsesIndexOrder = false;

    // --- Hover-preview state (same as DeckboxScreen) ---
    private long hoverSinceMs = 0L;
    private static final long HOVER_DEBOUNCE_MS = 80;

    private ItemStack lastHoverStack = ItemStack.EMPTY;
    private int lastHoverFace = 0;
    private CardArtManager.TextureRef lastTexRef = null;

    // animation
    private long animStartMs = 0L;
    private static final int ANIM_MS = 180;
    private static final float ANIM_SCALE_FROM = 0.92f;

    // cached “special overlay boxes” for hover (set during overlay draw)
    private int placeCardBoxX, placeCardBoxY, placeCardBoxW, placeCardBoxH; // PLACE preview box
    private int cascadeThumbX, cascadeThumbY, cascadeThumbCols;             // CASCADE thumbnails grid

    private int[] buildOrderArray() {
        int n = overlayN;
        int[] out = new int[n];
        int k = 0;
        for (int idx : topOrder) if (k < n) out[k++] = idx;
        for (int idx : bottomOrder) if (k < n) out[k++] = idx;
        return out;
    }

    public DeckControlScreen(DeckControlScreenHandler handler, PlayerInventory inv, Text title) {
        super(handler, inv, title);

        this.backgroundWidth = GUI_W; // whatever yours is now
        this.backgroundHeight = GUI_H; // <- increase (example)
        this.playerInventoryTitleY = this.backgroundHeight - 94; // keep player inv label aligned
    }

    // Base (non-overlay) buttons we want to hide during modal overlays
    private final List<ButtonWidget> baseButtons = new ArrayList<>();

    private ButtonWidget addBase(ButtonWidget w) {
        baseButtons.add(w);
        return addDrawableChild(w);
    }

    private ButtonWidget addOverlay(ButtonWidget w) {
        return addDrawableChild(w);
    }

    @Override
    protected void init() {
        super.init();

        // Re-center + clamp so the header never renders off-screen on shorter windows
        this.x = (this.width - this.backgroundWidth) / 2;
        this.y = Math.max(0, (this.height - this.backgroundHeight) / 2);

        int left = this.x;
        int top  = this.y;

        // --- Main layout anchors ---
        int headerH = 20;

        int selX = left + PAD;
        int selY = top + headerH + PAD;

        int controlsX = selX + SEL_PANEL_W + GAP;
        int controlsY = selY;

        int rightEdge = left + backgroundWidth - PAD;
        int controlsW = rightEdge - controlsX;

        // Top row: [Draw] [Shuffle] [Reveal Top]
        int topBtnW = (controlsW - GAP * 2) / 3;


        btnDraw = addBase(ButtonWidget.builder(Text.literal("Draw"), b ->
                sendDeckAction(DeckControlPackets.Action.DRAW, 0, 0)
        ).dimensions(controlsX, controlsY, topBtnW, BTN_H).build());

        btnShuffle = addBase(ButtonWidget.builder(Text.literal("Shuffle"), b -> {
            sendDeckAction(DeckControlPackets.Action.SHUFFLE, 0, 0);
            toast = "Shuffling…";
            toastTicks = 40;
        }).dimensions(controlsX + topBtnW + GAP, controlsY, topBtnW, BTN_H).build());

        // Action rows:
        // [ Action ] [-] [+]   x#
        int rowY = controlsY + BTN_H + 14;

        int actionW = Math.min(118, controlsW - (STEP_W * 2 + GAP * 2 + 44)); // keep sane on narrow widths
        int minusX = controlsX + actionW + GAP;
        int plusX  = minusX + STEP_W + GAP;
        int countX = plusX + STEP_W + COUNT_PAD; // text only

        // Scry row
        btnScryMinus = addBase(stepperButton(minusX, rowY, "-", () -> scryN = clamp(scryN - 1, 1, 31)));
        btnScryPlus  = addBase(stepperButton(plusX,  rowY, "+", () -> scryN = clamp(scryN + 1, 1, 31)));
        addBase(ButtonWidget.builder(Text.literal("Scry"), b ->
                sendDeckAction(DeckControlPackets.Action.START_SCRY, scryN, 0)
        ).dimensions(controlsX, rowY, actionW, BTN_H).build());
        rowY += BTN_H + 8;

        // Surveil row
        btnSurveilMinus = addBase(stepperButton(minusX, rowY, "-", () -> surveilN = clamp(surveilN - 1, 1, 31)));
        btnSurveilPlus  = addBase(stepperButton(plusX,  rowY, "+", () -> surveilN = clamp(surveilN + 1, 1, 31)));
        addBase(ButtonWidget.builder(Text.literal("Surveil"), b ->
                sendDeckAction(DeckControlPackets.Action.START_SURVEIL, surveilN, 0)
        ).dimensions(controlsX, rowY, actionW, BTN_H).build());
        rowY += BTN_H + 8;

        // Reveal N row
        btnRevealMinus = addBase(stepperButton(minusX, rowY, "-", () -> revealN = clamp(revealN - 1, 1, 31)));
        btnRevealPlus  = addBase(stepperButton(plusX,  rowY, "+", () -> revealN = clamp(revealN + 1, 1, 31)));
        addBase(ButtonWidget.builder(Text.literal("Peek N"), b ->
                sendDeckAction(DeckControlPackets.Action.REVEAL_N, revealN, 0)
        ).dimensions(controlsX, rowY, actionW, BTN_H).build());
        rowY += BTN_H + 8;

        // Mill row
        btnMillMinus = addBase(stepperButton(minusX, rowY, "-", () -> millN = clamp(millN - 1, 1, 99)));
        btnMillPlus  = addBase(stepperButton(plusX,  rowY, "+", () -> millN = clamp(millN + 1, 1, 99)));
        addBase(ButtonWidget.builder(Text.literal("Mill"), b ->
                sendDeckAction(DeckControlPackets.Action.MILL_N, millN, 0)
        ).dimensions(controlsX, rowY, actionW, BTN_H).build());
        rowY += BTN_H + 18;

        // Bottom row: [Start Cascade] [Place Selected...]
        int bottomBtnW = (controlsW - GAP) / 2;

        btnStartCascade = addBase(ButtonWidget.builder(Text.literal("Start Cascade"), b -> {
            int mv = getCascadeSourceMv();
            ClientPlayNetworking.send(new DeckControlPackets.CascadeStartC2S(handler.getPos(), mv));
            toast = "Cascading…";
            toastTicks = 30;
        }).dimensions(controlsX, rowY, bottomBtnW, BTN_H).build());

        btnPlaceHovered = addBase(ButtonWidget.builder(Text.literal("Place Selected…"), b -> {
            overlay = OverlayMode.PLACE_CARD;
            placeBottom = false;
            placeFromTop = 3;
        }).dimensions(controlsX + bottomBtnW + GAP, rowY, bottomBtnW, BTN_H).build());

        // New row under cascade/place
        int utilY = rowY + BTN_H + 6;
        int utilW = (controlsW - GAP) / 2;

        addBase(ButtonWidget.builder(Text.literal("Shuffle GY → Library"), b -> {
            sendDeckAction(DeckControlPackets.Action.SHUFFLE_GRAVEYARD_TO_LIBRARY, 0, 0);
            toast = "Shuffling graveyard into library…";
            toastTicks = 40;
        }).dimensions(controlsX, utilY, utilW, BTN_H).build());

        addBase(ButtonWidget.builder(Text.literal("Reset Deck"), b -> {
            sendDeckAction(DeckControlPackets.Action.RESET_DECK, 0, 0);
            toast = "Resetting deck…";
            toastTicks = 40;
        }).dimensions(controlsX + utilW + GAP, utilY, utilW, BTN_H).build());


        // NOTE: positions for these are re-anchored inside drawOverlay() when CASCADE is open.
        btnCascadeCast = addOverlay(ButtonWidget.builder(Text.literal("Cast"), b -> {
            ClientPlayNetworking.send(new DeckControlPackets.CascadeResolveC2S(handler.getPos(), true));
            cascadePendingClient = false;
            overlay = OverlayMode.NONE;
            clearOverlayHoverCache();
        }).dimensions(left + PAD + 148, top + PAD + 118, 54, BTN_H).build());
        btnCascadeCast.visible = false;

        btnCascadeExile = addOverlay(ButtonWidget.builder(Text.literal("Exile"), b -> {
            ClientPlayNetworking.send(new DeckControlPackets.CascadeResolveC2S(handler.getPos(), false));
            cascadePendingClient = false;
            overlay = OverlayMode.NONE;
            clearOverlayHoverCache();
        }).dimensions(left + PAD + 188, top + PAD + 118, 54, BTN_H).build());
        btnCascadeExile.visible = false;

        // --- Overlay buttons ---
        btnOverlayPlaceConfirm = addOverlay(ButtonWidget.builder(Text.literal("Place"), b -> {
            performPlaceSelected();
        }).dimensions(left + PAD + 168, top + PAD + 80, 56, BTN_H).build());
        btnOverlayPlaceConfirm.visible = false;

        btnOverlayConfirm = addOverlay(
                ButtonWidget.builder(Text.literal("Confirm"), b -> {

                    if (overlay == OverlayMode.SCRY) {
                        int keepMask = 0;
                        for (int idx : topOrder) keepMask |= (1 << idx);
                        sendDeckAction(DeckControlPackets.Action.RESOLVE_SCRY, overlayN, keepMask);
                    } else if (overlay == OverlayMode.SURVEIL) {
                        int millMask = 0;
                        for (int idx : bottomOrder) millMask |= (1 << idx);
                        sendDeckAction(DeckControlPackets.Action.RESOLVE_SURVEIL, overlayN, millMask);
                    }

                    overlay = OverlayMode.NONE;
                    clearOverlayHoverCache();

                }).dimensions(left + PAD + 168, top + PAD + 118, 76, BTN_H).build()
        );
        btnOverlayConfirm.visible = false;

        btnOverlayDone = addOverlay(ButtonWidget.builder(Text.literal("Done"), b -> overlay = OverlayMode.NONE)
                .dimensions(left + PAD + 168, top + PAD + 118, 56, BTN_H).build());
        btnOverlayDone.visible = false;

        overlayCancel = addOverlay(ButtonWidget.builder(Text.literal("Cancel"), b -> {
            if (overlay == OverlayMode.CASCADE && cascadePendingClient) {
                ClientPlayNetworking.send(new DeckControlPackets.CascadeResolveC2S(handler.getPos(), false));
                cascadePendingClient = false;
            }
            overlay = OverlayMode.NONE;
            clearOverlayHoverCache();

        }).dimensions(left + PAD + 168, top + PAD + 104, 56, BTN_H).build());
        overlayCancel.visible = false;

        // --- Place overlay extra controls (anchored later in drawPlaceOverlay) ---
        btnPlaceTop = addOverlay(ButtonWidget.builder(Text.literal("Top"), b -> {
            placeBottom = false;
            placeFromTop = 1;
        }).dimensions(left + PAD + 120, top + PAD + 60, 44, BTN_H).build());
        btnPlaceTop.visible = false;

        btnPlaceBottomMode = addOverlay(ButtonWidget.builder(Text.literal("Bottom"), b -> {
            placeBottom = true;
        }).dimensions(left + PAD + 170, top + PAD + 60, 60, BTN_H).build());
        btnPlaceBottomMode.visible = false;

        btnPlaceMinus = addOverlay(ButtonWidget.builder(Text.literal("-"), b -> {
            placeBottom = false;
            placeFromTop = clamp(placeFromTop - 1, 1, 99);
        }).dimensions(left + PAD + 120, top + PAD + 86, 20, BTN_H).build());
        btnPlaceMinus.visible = false;

        btnPlacePlus = addOverlay(ButtonWidget.builder(Text.literal("+"), b -> {
            placeBottom = false;
            placeFromTop = clamp(placeFromTop + 1, 1, 99);
        }).dimensions(left + PAD + 146, top + PAD + 86, 20, BTN_H).build());
        btnPlacePlus.visible = false;
    }

    private ButtonWidget stepperButton(int x, int y, String label, Runnable onClick) {
        return ButtonWidget.builder(Text.literal(label), b -> onClick.run())
                .dimensions(x, y, STEP_W, BTN_H).build();
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        this.renderBackground(ctx, mouseX, mouseY, delta);

        // keep hover/selection updated even while overlay is open
        updateHoveredPlayerCard(mouseX, mouseY);

        boolean modal = overlay != OverlayMode.NONE;

        // Hide/disable normal UI while modal overlay is open
        for (ButtonWidget w : baseButtons) {
            w.visible = !modal;
            w.active  = !modal;
        }

        // set overlay vis/active BEFORE super.render
        boolean ok = canUseSelectedCard();
        if (btnStartCascade != null) btnStartCascade.active = ok;
        if (btnPlaceHovered != null) btnPlaceHovered.active = ok;

        if (btnCascadeCast != null)  btnCascadeCast.visible  = (overlay == OverlayMode.CASCADE && cascadeHitIndex >= 0);
        if (btnCascadeExile != null) btnCascadeExile.visible = (overlay == OverlayMode.CASCADE);

        if (btnOverlayPlaceConfirm != null) btnOverlayPlaceConfirm.visible = (overlay == OverlayMode.PLACE_CARD);
        if (btnPlaceTop != null)         btnPlaceTop.visible         = (overlay == OverlayMode.PLACE_CARD);
        if (btnPlaceBottomMode != null)  btnPlaceBottomMode.visible  = (overlay == OverlayMode.PLACE_CARD);
        if (btnPlaceMinus != null)       btnPlaceMinus.visible       = (overlay == OverlayMode.PLACE_CARD);
        if (btnPlacePlus != null)        btnPlacePlus.visible        = (overlay == OverlayMode.PLACE_CARD);

        // active states (only meaningful in place overlay)
        if (overlay == OverlayMode.PLACE_CARD) {
            boolean can = canUseSelectedCard();
            if (btnOverlayPlaceConfirm != null) btnOverlayPlaceConfirm.active = can;
            if (btnPlaceTop != null)        btnPlaceTop.active = true;
            if (btnPlaceBottomMode != null) btnPlaceBottomMode.active = true;
            if (btnPlaceMinus != null)      btnPlaceMinus.active = !placeBottom && placeFromTop > 1;
            if (btnPlacePlus != null)       btnPlacePlus.active = !placeBottom && placeFromTop < 99;
        }
        if (btnOverlayConfirm != null)      btnOverlayConfirm.visible      = (overlay == OverlayMode.SCRY || overlay == OverlayMode.SURVEIL);
        if (btnOverlayDone != null)         btnOverlayDone.visible         = (overlay == OverlayMode.REVEAL_N);

        if (overlayCancel != null)          overlayCancel.visible = modal;

        // let minecraft draw the base screen + widgets
        super.render(ctx, mouseX, mouseY, delta);

        // draw overlay AFTER everything (dims slots/items/etc correctly)
        if (modal) {
            drawOverlay(ctx, mouseX, mouseY);

            // redraw overlay buttons on TOP of overlay so they are visible
            if (overlayCancel != null && overlayCancel.visible)
                overlayCancel.render(ctx, mouseX, mouseY, delta);

            if (overlay == OverlayMode.CASCADE) {
                if (btnCascadeCast != null && btnCascadeCast.visible)
                    btnCascadeCast.render(ctx, mouseX, mouseY, delta);
                if (btnCascadeExile != null && btnCascadeExile.visible)
                    btnCascadeExile.render(ctx, mouseX, mouseY, delta);
            }

            if (overlay == OverlayMode.PLACE_CARD) {
                if (btnPlaceTop != null && btnPlaceTop.visible)
                    btnPlaceTop.render(ctx, mouseX, mouseY, delta);
                if (btnPlaceBottomMode != null && btnPlaceBottomMode.visible)
                    btnPlaceBottomMode.render(ctx, mouseX, mouseY, delta);
                if (btnPlaceMinus != null && btnPlaceMinus.visible)
                    btnPlaceMinus.render(ctx, mouseX, mouseY, delta);
                if (btnPlacePlus != null && btnPlacePlus.visible)
                    btnPlacePlus.render(ctx, mouseX, mouseY, delta);
            }

            if (btnOverlayPlaceConfirm != null && btnOverlayPlaceConfirm.visible)
                btnOverlayPlaceConfirm.render(ctx, mouseX, mouseY, delta);
            if (btnOverlayConfirm != null && btnOverlayConfirm.visible)
                btnOverlayConfirm.render(ctx, mouseX, mouseY, delta);
            if (btnOverlayDone != null && btnOverlayDone.visible)
                btnOverlayDone.render(ctx, mouseX, mouseY, delta);

        } else {
            // Draw the "x#" counts on the main screen (text only)
            drawMainCounts(ctx);
            // Draw "Click card to select" hint under the bottom row
            drawMainHint(ctx);
        }

        renderHoverPreview(ctx, mouseX, mouseY, delta);


        this.drawMouseoverTooltip(ctx, mouseX, mouseY);

        if (toastTicks > 0) {
            toastTicks--;
            ctx.drawText(textRenderer, Text.literal(toast), this.x + PAD, this.y + backgroundHeight - 26, 0xFFFFFFFF, false);
        }
    }

    private void drawMainCounts(DrawContext ctx) {
        int left = this.x;
        int top = this.y;

        int headerH = 20;

        int selX = left + PAD;
        int selY = top + headerH + PAD;

        int controlsX = selX + SEL_PANEL_W + GAP;
        int controlsY = selY;

        int rightEdge = left + backgroundWidth - PAD;
        int controlsW = rightEdge - controlsX;

        int rowY = controlsY + BTN_H + 14;

        int actionW = Math.min(118, controlsW - (STEP_W * 2 + GAP * 2 + 44));
        int minusX = controlsX + actionW + GAP;
        int plusX  = minusX + STEP_W + GAP;
        int countX = plusX + STEP_W + COUNT_PAD;

        // Scry
        ctx.drawText(textRenderer, Text.literal("x" + scryN), countX, rowY + COUNT_TEXT_Y_OFF, 0xFFD0D0D0, false);
        rowY += BTN_H + 8;

        // Surveil
        ctx.drawText(textRenderer, Text.literal("x" + surveilN), countX, rowY + COUNT_TEXT_Y_OFF, 0xFFD0D0D0, false);
        rowY += BTN_H + 8;

        // Reveal N
        ctx.drawText(textRenderer, Text.literal("x" + revealN), countX, rowY + COUNT_TEXT_Y_OFF, 0xFFD0D0D0, false);
        rowY += BTN_H + 8;

        // Mill
        ctx.drawText(textRenderer, Text.literal("x" + millN), countX, rowY + COUNT_TEXT_Y_OFF, 0xFFD0D0D0, false);
    }

    private void drawMainHint(DrawContext ctx) {
        int left = this.x;
        int top = this.y;

        int headerH = 20;
        int selX = left + PAD;
        int selY = top + headerH + PAD;

        int controlsX = selX + SEL_PANEL_W + GAP;
        int controlsY = selY;

        int rightEdge = left + backgroundWidth - PAD;
        int controlsW = rightEdge - controlsX;

        int rowY = controlsY + BTN_H + 14;
        rowY += (BTN_H + 8) * 4;  // 4 action rows
        rowY += 18;               // spacing before bottom buttons
        rowY += BTN_H + 6;        // below Start Cascade / Place Selected
        rowY += BTN_H + 6;        // below Shuffle GY / Reset Deck

        Text hint = Text.literal("Click a card in your inventory to select");
        int w = textRenderer.getWidth(hint);
        int x = controlsX + (controlsW - w) / 2;
        ctx.drawText(textRenderer, hint, x, rowY, 0xFFB0B0B0, false);
    }

    @Override
    protected void drawBackground(DrawContext ctx, float delta, int mouseX, int mouseY) {
        int left = this.x;
        int top = this.y;

        // Frame
        ctx.fill(left, top, left + backgroundWidth, top + backgroundHeight, 0xCC101010);
        ctx.fill(left + 1, top + 1, left + backgroundWidth - 1, top + backgroundHeight - 1, 0xCC1A1A1A);

        // Header
        ctx.fill(left, top, left + backgroundWidth, top + 20, 0xCC0E0E0E);
        ctx.drawText(textRenderer, Text.literal("Deck Control"), left + PAD, top + 6, 0xFFF2F2F2, false);

        String linked = handler.isLinked() ? "Linked: ✓" : "Linked: ✕";
        int linkedColor = handler.isLinked() ? 0xFF55FF55 : 0xFFFF5555;
        int lw = textRenderer.getWidth(linked);
        ctx.drawText(textRenderer, Text.literal(linked), left + backgroundWidth - PAD - lw, top + 6, linkedColor, false);

        // --- Left Selected Card panel ---
        int selX = left + PAD;
        int selY = top + 20 + PAD;

        int panelW = SEL_PANEL_W;
        int panelH = SEL_CARD_H + 34; // card + label + 2 lines
        ctx.fill(selX, selY, selX + panelW, selY + panelH, 0xAA000000);
        ctx.fill(selX + 1, selY + 1, selX + panelW - 1, selY + panelH - 1, 0xAA111111);

        ctx.drawText(textRenderer, Text.literal("Card Selected"), selX + 6, selY + 6, 0xFFE0E0E0, false);

        int cardX = selX + (panelW - SEL_CARD_W) / 2;
        int cardY = selY + 18;

        ItemStack preview = selectedPlayerCard;
        if (overlay == OverlayMode.CASCADE && cascadeHitIndex >= 0 && cascadeHitIndex < cascadeRevealed.size()) {
            preview = cascadeRevealed.get(cascadeHitIndex);
        }

        // Card box
        ctx.fill(cardX, cardY, cardX + SEL_CARD_W, cardY + SEL_CARD_H, 0xFF111111);
        ctx.fill(cardX + 1, cardY + 1, cardX + SEL_CARD_W - 1, cardY + SEL_CARD_H - 1, 0xFF2A2A2A);

        if (!preview.isEmpty()) {
            drawCardArtFit(ctx, preview, cardX + 2, cardY + 2, SEL_CARD_W - 4, SEL_CARD_H - 4);
        } else {
            drawCardBackFit(ctx, cardX + 2, cardY + 2, SEL_CARD_W - 4, SEL_CARD_H - 4);
        }

        // Selected card info lines
        int infoY = cardY + SEL_CARD_H + 6;
        if (!selectedPlayerCard.isEmpty() && handler.isCardItem(selectedPlayerCard)) {
            CardMeta.Info info = CardMeta.read(selectedPlayerCard);
            String nm = info.name().isEmpty() ? "(card)" : info.name();
            ctx.drawText(textRenderer, Text.literal(nm), selX + 6, infoY, 0xFFFFFFFF, false);
            ctx.drawText(textRenderer, Text.literal("MV: " + info.mv()), selX + 6, infoY + 12, 0xFFCFCFCF, false);
        } else {
            ctx.drawText(textRenderer, Text.literal("(none)"), selX + 6, infoY, 0xFFCFCFCF, false);
            ctx.drawText(textRenderer, Text.literal("Click card to select"), selX + 6, infoY + 12, 0xFFB0B0B0, false);
        }

        // Footer separator above inventory (keep your existing bar if you like)
        int invTop = top + (this.backgroundHeight - 94); // matches playerInventoryTitleY logic
        ctx.fill(left + PAD, invTop - 6, left + backgroundWidth - PAD, invTop - 5, 0x80383838);
    }

    @Override
    protected void drawForeground(DrawContext ctx, int mouseX, int mouseY) {
        // Keep empty — we draw our own header in drawBackground.
    }

    private void drawOverlay(DrawContext ctx, int mouseX, int mouseY) {
        int left = this.x;
        int top = this.y;

        // dim
        ctx.fill(left, top, left + backgroundWidth, top + backgroundHeight, 0xAA000000);

        hoverTopCols = 0;
        hoverBotCols = 0;
        hoverUsesIndexOrder = false;

        if (overlay == OverlayMode.PLACE_CARD) {
            drawPlaceOverlay(ctx);
            return;
        }

        if (overlay == OverlayMode.CASCADE) {
            int ox = left + PAD;
            int oy = top + PAD + 10;
            int ow = backgroundWidth - PAD * 2;
            int oh = 180;

            ctx.fill(ox, oy, ox + ow, oy + oh, 0xDD151515);
            ctx.fill(ox + 1, oy + 1, ox + ow - 1, oy + oh - 1, 0xDD202020);

            ctx.drawText(textRenderer, Text.literal("CASCADE (MV " + cascadeSourceMvNet + ")"),
                    ox + 8, oy + 8, 0xFFFFFFFF, false);

            // --- Bigger chosen card preview ---
            int bx = ox + 10;
            int by = oy + 26;
            int bw = 88;
            int bh = 124;

            ctx.fill(bx, by, bx + bw, by + bh, 0xFF111111);
            ctx.fill(bx + 1, by + 1, bx + bw - 1, by + bh - 1, 0xFF2A2A2A);

            // --- Buttons anchored inside the cascade panel ---
            int btnPad = 10;
            int btnY = oy + oh - btnPad - BTN_H;
            int btnW = 54;
            int btnGap = 6;
            int pairX = ox + ow - btnPad - (btnW * 2 + btnGap);

            if (overlayCancel != null) {
                overlayCancel.setX(pairX);
                overlayCancel.setY(btnY - (BTN_H + 4));
                overlayCancel.setWidth(btnW * 2 + btnGap);
                overlayCancel.visible = true;
            }
            if (btnCascadeCast != null) {
                btnCascadeCast.setX(pairX);
                btnCascadeCast.setY(btnY);
                btnCascadeCast.setWidth(btnW);
                btnCascadeCast.visible = (cascadeHitIndex >= 0);
            }
            if (btnCascadeExile != null) {
                btnCascadeExile.setX(pairX + btnW + btnGap);
                btnCascadeExile.setY(btnY);
                btnCascadeExile.setWidth(btnW);
                btnCascadeExile.visible = true;
            }

            // Chosen hit info
            if (cascadeHitIndex >= 0 && cascadeHitIndex < cascadeRevealed.size()) {
                ItemStack hit = cascadeRevealed.get(cascadeHitIndex);
                drawCardArtFit(ctx, hit, bx + 2, by + 2, bw - 4, bh - 4);
                var info = CardMeta.read(hit);
                ctx.drawText(textRenderer, Text.literal(info.name() + " | MV " + info.mv()),
                        bx + bw + 10, by + 4, 0xFFFFFF, false);
            } else {
                ctx.drawText(textRenderer, Text.literal("No valid hit"),
                        bx + 10, by + 52, 0xFFCFCFCF, false);
                ctx.drawText(textRenderer, Text.literal("Exile will bottom all revealed"),
                        bx + bw + 10, by + 4, 0xFFCFCFCF, false);
            }

            // --- Revealed thumbnails that never go off-window ---
            int thumbX = bx + bw + 10;
            int thumbY = by + 18;

            int thumbW = (ox + ow - 10) - thumbX;
            int thumbH = (btnY - 8) - thumbY;

            int cellW = OVER_CARD_W + OVER_GAP;
            int cols = Math.max(1, thumbW / cellW);

            cascadeThumbX = thumbX;
            cascadeThumbY = thumbY;
            cascadeThumbCols = cols;

            ctx.enableScissor(ox, oy, ox + ow, oy + oh);

            int shown = 0;
            for (int i = 0; i < cascadeRevealed.size(); i++) {
                int col = i % cols;
                int row = i / cols;

                int cx = thumbX + col * cellW;
                int cy = thumbY + row * (OVER_CARD_H + 6);

                if (cy + OVER_CARD_H > thumbY + thumbH) break;

                boolean isHit = (i == cascadeHitIndex);
                int bg = isHit ? 0xFF2A3A2A : 0xFF2A2A2A;
                ctx.fill(cx, cy, cx + OVER_CARD_W, cy + OVER_CARD_H, bg);
                ctx.fill(cx + 1, cy + 1, cx + OVER_CARD_W - 1, cy + OVER_CARD_H - 1, 0xFF111111);

                ItemStack st = cascadeRevealed.get(i);
                drawCardArtFit(ctx, st, cx + 1, cy + 1, OVER_CARD_W - 2, OVER_CARD_H - 2);
                shown++;
            }

            int remaining = cascadeRevealed.size() - shown;
            if (remaining > 0) {
                ctx.drawText(textRenderer, Text.literal("+" + remaining + " more"),
                        thumbX, thumbY + thumbH - 10, 0xFFCFCFCF, false);
            }

            ctx.disableScissor();
            return;
        }

        if (overlay == OverlayMode.REVEAL_N) {
            drawRevealOverlay(ctx, mouseX, mouseY);
            return;
        }

        if (overlay == OverlayMode.SCRY || overlay == OverlayMode.SURVEIL) {
            drawScrySurveilOverlay(ctx, mouseX, mouseY);
            return;
        }
    }

    private ItemStack getHoveredCardForPreview(int mouseX, int mouseY) {

        // 0) PLACE overlay: hovering the big preview card box
        if (overlay == OverlayMode.PLACE_CARD) {
            if (isMouseIn(mouseX, mouseY, placeCardBoxX, placeCardBoxY, placeCardBoxW, placeCardBoxH)) {
                if (!selectedPlayerCard.isEmpty() && handler.isCardItem(selectedPlayerCard)) return selectedPlayerCard;
            }
        }

        // 1) slot hover
        Slot slot = this.focusedSlot;
        if (slot != null && slot.hasStack() && isMouseOverSlotArea(slot, mouseX, mouseY)) {
            ItemStack st = slot.getStack();
            if (st.isOf(com.spider.mtgcard.item.ModItems.CARD)) return st;
        }

        // 2) CASCADE overlay: hover thumbnails grid (multi-row)
        if (overlay == OverlayMode.CASCADE) {
            ItemStack st = hoverGridStacks(mouseX, mouseY, cascadeThumbX, cascadeThumbY, cascadeThumbCols, cascadeRevealed);
            if (!st.isEmpty()) return st;

            // also allow hover over the big chosen hit preview (uses same box size as you draw)
            // (optional – if you want it, say so and I’ll wire exact coords too)
        }
        // 3) SCRY/SURVEIL overlay: your cached rows (ONLY when overlay is open)
        if (overlay == OverlayMode.SCRY || overlay == OverlayMode.SURVEIL) {

            if (hoverTopCols > 0) {
                ItemStack top = hoverUsesIndexOrder
                        ? hoverRowByOrder(mouseX, mouseY, hoverTopX, hoverTopY, hoverTopCols, topOrder)
                        : hoverRowDirect(mouseX, mouseY, hoverTopX, hoverTopY, hoverTopCols, overlayCards);
                if (!top.isEmpty()) return top;
            }

            if (hoverBotCols > 0) {
                ItemStack bot = hoverUsesIndexOrder
                        ? hoverRowByOrder(mouseX, mouseY, hoverBotX, hoverBotY, hoverBotCols, bottomOrder)
                        : hoverRowDirect(mouseX, mouseY, hoverBotX, hoverBotY, hoverBotCols, overlayCards);
                if (!bot.isEmpty()) return bot;
            }
        }


        return ItemStack.EMPTY;
    }

    private ItemStack hoverGridStacks(int mouseX, int mouseY, int startX, int startY, int cols, List<ItemStack> stacks) {
        if (cols <= 0 || stacks == null || stacks.isEmpty()) return ItemStack.EMPTY;

        int cellW = OVER_CARD_W + OVER_GAP;
        int cellH = OVER_CARD_H + 6; // matches your cascade row spacing

        int relX = mouseX - startX;
        int relY = mouseY - startY;
        if (relX < 0 || relY < 0) return ItemStack.EMPTY;

        int col = relX / cellW;
        int row = relY / cellH;
        if (col < 0 || col >= cols) return ItemStack.EMPTY;

        int cx = startX + col * cellW;
        int cy = startY + row * cellH;

        // must be inside the actual card rect, not the gap
        if (mouseX < cx || mouseX >= cx + OVER_CARD_W) return ItemStack.EMPTY;
        if (mouseY < cy || mouseY >= cy + OVER_CARD_H) return ItemStack.EMPTY;

        int idx = row * cols + col;
        if (idx < 0 || idx >= stacks.size()) return ItemStack.EMPTY;

        ItemStack st = stacks.get(idx);
        return (st != null && !st.isEmpty() && st.isOf(com.spider.mtgcard.item.ModItems.CARD)) ? st : ItemStack.EMPTY;
    }

    private void drawScrySurveilOverlay(DrawContext ctx, int mouseX, int mouseY) {
        int left = this.x;
        int top  = this.y;

        // dim background
        ctx.fill(left, top, left + backgroundWidth, top + backgroundHeight, 0xAA000000);

        // panel geometry
        int ox = left + PAD;
        int oy = top + PAD + 10;
        int ow = backgroundWidth - PAD * 2;

        scry_ox = ox;
        scry_oy = oy;
        scry_ow = ow;

        int lineY1 = oy + 36;

        scry_areaX = ox + 8;
        scry_areaW = ow - 16;

        int labelX = ox + 8; // <-- you were missing this

        int topLabelY = lineY1 + 6;
        scry_topRowY = topLabelY + 12;

        int rowH = OVER_CARD_H + 4;
        int lineY2 = scry_topRowY + rowH + 6;
        int botLabelY = lineY2 + 6;
        scry_botRowY = botLabelY + 12;

        int lineY3 = scry_botRowY + rowH + 6;

        int cellW = OVER_CARD_W + OVER_GAP;
        scry_cols = Math.max(1, scry_areaW / cellW);

        // buttons below bottom divider
        scry_btnY = lineY3 + 6;
        int btnY = scry_btnY;
        int btnW = 90;

        // Panel height includes buttons + padding
        int oh = (btnY + BTN_H + 10) - oy;

        // panel draw
        ctx.fill(ox, oy, ox + ow, oy + oh, 0xDD151515);
        ctx.fill(ox + 1, oy + 1, ox + ow - 1, oy + oh - 1, 0xDD202020);

        String title = (overlay == OverlayMode.SCRY) ? "Scry" : "Surveil";
        ctx.drawText(textRenderer, Text.literal("Resolving: " + title + " (x" + overlayN + ")"),
                ox + 8, oy + 8, 0xFFFFFFFF, false);
        ctx.drawText(textRenderer, Text.literal("Click and drag cards."),
                ox + 8, oy + 22, 0xFFCFCFCF, false);

        // dividers + labels
        ctx.fill(ox + 6, lineY1, ox + ow - 6, lineY1 + 1, 0x80383838);

        ctx.drawText(textRenderer, Text.literal("Top of Library:"), labelX, topLabelY, 0xFFD0D0D0, false);
        ctx.fill(ox + 6, lineY2, ox + ow - 6, lineY2 + 1, 0x80383838);

        Text bottomLabel = (overlay == OverlayMode.SURVEIL)
                ? Text.literal("Graveyard:")
                : Text.literal("Bottom of Library:");

        ctx.drawText(textRenderer, bottomLabel, labelX, botLabelY, 0xFFD0D0D0, false);
        ctx.fill(ox + 6, lineY3, ox + ow - 6, lineY3 + 1, 0x80383838);

        hoverTopX = scry_areaX;
        hoverTopY = scry_topRowY;
        hoverTopCols = scry_cols;

        hoverBotX = scry_areaX;
        hoverBotY = scry_botRowY;
        hoverBotCols = scry_cols;

        hoverUsesIndexOrder = true;

        // draw rows (draggable)
        drawOrderRow(ctx, topOrder, scry_areaX, scry_topRowY, scry_cols, mouseX, mouseY, true);
        drawBottomSlotsRow(ctx, bottomOrder, scry_areaX, scry_botRowY, scry_cols, overlayN, mouseX, mouseY);

        drawDragGhost(ctx, mouseX, mouseY);

        // buttons
        if (btnOverlayConfirm != null) {
            btnOverlayConfirm.setX(ox + 10);
            btnOverlayConfirm.setY(btnY);
            btnOverlayConfirm.setWidth(btnW);
            btnOverlayConfirm.visible = true;
            btnOverlayConfirm.active = true;
        }

        if (overlayCancel != null) {
            overlayCancel.setX(ox + ow - 10 - btnW);
            overlayCancel.setY(btnY);
            overlayCancel.setWidth(btnW);
            overlayCancel.visible = true;
            overlayCancel.active = true;
        }
    }

    // --- Your cleaned Place overlay stays as-is (already matches what you pasted) ---
    private void drawPlaceOverlay(DrawContext ctx) {
        int left = this.x;
        int top  = this.y;

        int ox = left + PAD;
        int oy = top + PAD + 10;
        int ow = backgroundWidth - PAD * 2;
        int oh = 140;

        ctx.fill(ox, oy, ox + ow, oy + oh, 0xDD151515);
        ctx.fill(ox + 1, oy + 1, ox + ow - 1, oy + oh - 1, 0xDD202020);

        int bx = ox + 12;
        int by = oy + 16;
        int bw = 56;
        int bh = 78;

        placeCardBoxX = bx;
        placeCardBoxY = by;
        placeCardBoxW = bw;
        placeCardBoxH = bh;

        ctx.fill(bx, by, bx + bw, by + bh, 0xFF111111);
        ctx.fill(bx + 1, by + 1, bx + bw - 1, by + bh - 1, 0xFF2A2A2A);

        if (!selectedPlayerCard.isEmpty()) {
            drawCardArtFit(ctx, selectedPlayerCard, bx + 2, by + 2, bw - 4, bh - 4);
        }

        int cx = bx + bw + 14;
        int rightPad = 12;
        int cw = (ox + ow - rightPad) - cx;

        int gap = 8;
        int halfW = (cw - gap) / 2;

        int stepW = 40;
        int stepGap = 8;

        int row1Y = by + 2;
        int row2Y = row1Y + BTN_H + 10;
        int row3Y = row2Y + BTN_H + 10;
        int row4Y = row3Y + 16;
        int rowBtnY = oy + oh - 10 - BTN_H;

        if (btnPlaceTop != null) {
            btnPlaceTop.setX(cx);
            btnPlaceTop.setY(row1Y);
            btnPlaceTop.setWidth(halfW);
            btnPlaceTop.visible = true;
            btnPlaceTop.active = true;
        }

        if (btnPlaceBottomMode != null) {
            btnPlaceBottomMode.setX(cx + halfW + gap);
            btnPlaceBottomMode.setY(row1Y);
            btnPlaceBottomMode.setWidth(halfW);
            btnPlaceBottomMode.visible = true;
            btnPlaceBottomMode.active = true;
        }

        int minusX = cx;
        int plusX  = cx + stepW + stepGap;

        if (btnPlaceMinus != null) {
            btnPlaceMinus.setX(minusX);
            btnPlaceMinus.setY(row2Y);
            btnPlaceMinus.setWidth(stepW);
            btnPlaceMinus.visible = true;
            btnPlaceMinus.active = !placeBottom && placeFromTop > 1;
        }

        if (btnPlacePlus != null) {
            btnPlacePlus.setX(plusX);
            btnPlacePlus.setY(row2Y);
            btnPlacePlus.setWidth(stepW);
            btnPlacePlus.visible = true;
            btnPlacePlus.active = !placeBottom && placeFromTop < 99;
        }

        Text placedText = placeBottom
                ? Text.literal("Placed: Bottom")
                : Text.literal("Placed: From top " + placeFromTop);

        int placedW = textRenderer.getWidth(placedText);
        int placedX = cx + (cw - placedW) / 2;
        placedX = Math.max(cx, Math.min(placedX, (cx + cw) - placedW));

        ctx.drawText(textRenderer, placedText, placedX, row3Y + 2, 0xFFD0D0D0, false);

        Text hint = Text.literal("Enter = Place   Esc = Cancel");
        int hintW = textRenderer.getWidth(hint);
        int hintX = cx + (cw - hintW) / 2;
        hintX = Math.max(cx, Math.min(hintX, (cx + cw) - hintW));

        ctx.drawText(textRenderer, hint, hintX, row4Y + 2, 0xFFB0B0B0, false);

        if (overlayCancel != null) {
            overlayCancel.setX(cx);
            overlayCancel.setY(rowBtnY);
            overlayCancel.setWidth(halfW);
            overlayCancel.visible = true;
            overlayCancel.active = true;
        }

        if (btnOverlayPlaceConfirm != null) {
            btnOverlayPlaceConfirm.setX(cx + halfW + gap);
            btnOverlayPlaceConfirm.setY(rowBtnY);
            btnOverlayPlaceConfirm.setWidth(halfW);
            btnOverlayPlaceConfirm.visible = true;
            btnOverlayPlaceConfirm.active = canUseSelectedCard();
        }
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        int mx = (int) click.x();
        int my = (int) click.y();

        Slot slot = this.focusedSlot;
        if (slot != null && slot.hasStack()
                && handler.isPlayerInventorySlot(slot)
                && isMouseOverSlotArea(slot, mx, my)
                && handler.isCardItem(slot.getStack())) {

            selectedPlayerCard = slot.getStack();
            selectedPlayerInvIndex = slot.getIndex();
            cascadeSourceMv = CardMeta.read(selectedPlayerCard).mv();
            return true;
        }

        if (overlay == OverlayMode.SCRY || overlay == OverlayMode.SURVEIL) {

            // Try pick from TOP row
            int pickedTopPos = pickRowPos(mx, my, scry_areaX, scry_topRowY, scry_cols);
            if (pickedTopPos >= 0 && pickedTopPos < topOrder.size()) {
                startDrag(mx, my, topOrder.get(pickedTopPos), true, pickedTopPos);
                return true;
            }

            // Try pick from BOTTOM row
            int pickedBotPos = pickRowPos(mx, my, scry_areaX, scry_botRowY, scry_cols);
            if (pickedBotPos >= 0 && pickedBotPos < bottomOrder.size()) {
                startDrag(mx, my, bottomOrder.get(pickedBotPos), false, pickedBotPos);
                return true;
            }
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int keyCode = input.key();

        // PLACE overlay keys
        if (overlay == OverlayMode.PLACE_CARD) {

            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                overlay = OverlayMode.NONE;
                clearOverlayHoverCache();
                return true;
            }

            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                performPlaceSelected();
                return true;
            }

            if (keyCode == GLFW.GLFW_KEY_T) {
                placeBottom = false;
                placeFromTop = 1;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_B) {
                placeBottom = true;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_UP) {
                placeBottom = false;
                placeFromTop = clamp(placeFromTop + 1, 1, 99);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_DOWN) {
                placeBottom = false;
                placeFromTop = clamp(placeFromTop - 1, 1, 99);
                return true;
            }
        }

        // Other overlays: Esc closes
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && overlay != OverlayMode.NONE) {
            if (overlay == OverlayMode.CASCADE && cascadePendingClient) {
                ClientPlayNetworking.send(new DeckControlPackets.CascadeResolveC2S(handler.getPos(), false));
                cascadePendingClient = false;
            }
            overlay = OverlayMode.NONE;
            clearOverlayHoverCache();
            return true;
        }

        return super.keyPressed(input);
    }

    @Override
    public void removed() {
        super.removed();
        if (cascadePendingClient) {
            ClientPlayNetworking.send(new DeckControlPackets.CascadeResolveC2S(handler.getPos(), false));
            cascadePendingClient = false;
        }
    }

    private void updateHoveredPlayerCard(int mouseX, int mouseY) {
        hoveredPlayerCard = ItemStack.EMPTY;
        hoveredPlayerInvIndex = -1;

        Slot slot = this.focusedSlot;
        if (slot == null || !slot.hasStack()) return;

        if (!handler.isPlayerInventorySlot(slot)) return;
        if (!isMouseOverSlotArea(slot, mouseX, mouseY)) return;

        hoveredPlayerCard = slot.getStack();
        hoveredPlayerInvIndex = slot.getIndex();
    }

    private boolean isMouseOverSlotArea(Slot slot, int mouseX, int mouseY) {
        int sx = this.x + slot.x;
        int sy = this.y + slot.y;
        return mouseX >= sx && mouseX < sx + 16 && mouseY >= sy && mouseY < sy + 16;
    }

    private boolean canUseSelectedCard() {
        return handler.isLinked()
                && selectedPlayerInvIndex >= 0
                && !selectedPlayerCard.isEmpty()
                && handler.isCardItem(selectedPlayerCard);
    }

    private int getCascadeSourceMv() {
        if (canUseSelectedCard()) return CardMeta.read(selectedPlayerCard).mv();
        return cascadeSourceMv;
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    private static boolean isMouseIn(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    /* ---------------- Networking ---------------- */

    private void sendDeckAction(DeckControlPackets.Action action, int a, int b) {
        ClientPlayNetworking.send(new DeckControlPackets.ActionC2S(
                handler.getPos(),
                action.ordinal(),
                a,
                b
        ));
    }

    private void performPlaceSelected() {
        if (!canUseSelectedCard()) return;

        if (placeBottom) {
            sendDeckAction(DeckControlPackets.Action.PLACE_BOTTOM, selectedPlayerInvIndex, 0);
        } else {
            int nFromTop = clamp(placeFromTop, 1, 99);
            sendDeckAction(DeckControlPackets.Action.PLACE_N_FROM_TOP, selectedPlayerInvIndex, nFromTop);
        }

        overlay = OverlayMode.NONE;
        clearOverlayHoverCache();
    }

    /* ---------------- Overlay payload hook ---------------- */

    public void onOverlayPayload(DeckControlPackets.OverlayS2C payload) {
        if (!payload.pos().equals(this.handler.getPos())) return;

        this.overlayKind = DeckControlPackets.OverlayKind.values()[payload.kindOrdinal()];
        this.overlayN = payload.n();
        this.overlayCards = payload.cards();

        // reset selection system
        this.bitmask = 0;

        // init drag lists for SCRY/SURVEIL
        topOrder.clear();
        bottomOrder.clear();
        for (int i = 0; i < Math.min(overlayN, overlayCards.size()); i++) topOrder.add(i);

        dragging = false;
        dragCardIdx = -1;
        dragFromPos = -1;

        switch (overlayKind) {
            case REVEAL -> this.overlay = OverlayMode.REVEAL_N;
            case SCRY -> this.overlay = OverlayMode.SCRY;
            case SURVEIL -> this.overlay = OverlayMode.SURVEIL;
        }
    }


    /* ---------------- Types ---------------- */

    private enum OverlayMode {
        NONE,
        PLACE_CARD,
        REVEAL_N,
        SCRY,
        SURVEIL,
        CASCADE
    }

    private static int readFaceIndex(ItemStack st) {
        var comp = st.get(DataComponentTypes.CUSTOM_DATA);
        NbtCompound root = (comp == null) ? new NbtCompound() : comp.copyNbt();
        NbtCompound meta = root.getCompound("mtg_meta").orElseGet(NbtCompound::new);
        return meta.getInt("mtg_face").orElse(0);
    }

    private void drawCardArtFit(DrawContext ctx, ItemStack st, int x, int y, int w, int h) {
        if (st == null || st.isEmpty()) return;
        if (!handler.isCardItem(st)) return;

        int face = readFaceIndex(st);

        CardArtManager.TextureRef ref = CardArtManager.getOrRequestFace(st, face);

        if (ref == null || ref.id() == null) {
            drawCardBackFit(ctx, x, y, w, h);
            return;
        }

        int texW = ref.texW();
        int texH = ref.texH();
        if (texW <= 0 || texH <= 0) return;

        float aspect = (float) texW / (float) texH;

        int drawW = w;
        int drawH = (int) (drawW / aspect);
        if (drawH > h) {
            drawH = h;
            drawW = (int) (drawH * aspect);
        }

        int dx = x + (w - drawW) / 2;
        int dy = y + (h - drawH) / 2;

        ctx.drawTexture(
                RenderPipelines.GUI_TEXTURED,
                ref.id(),
                dx, dy,
                0f, 0f,
                drawW, drawH,
                texW, texH,
                texW, texH
        );
    }

    private void drawCardBackFit(DrawContext ctx, int x, int y, int w, int h) {
        final int texW = 16;
        final int texH = 16;

        float aspect = 1.0f;

        int drawW = w;
        int drawH = (int) (drawW / aspect);
        if (drawH > h) {
            drawH = h;
            drawW = (int) (drawH * aspect);
        }

        int dx = x + (w - drawW) / 2;
        int dy = y + (h - drawH) / 2;

        ctx.drawTexture(
                RenderPipelines.GUI_TEXTURED,
                CARD_BACK_TEX,
                dx, dy,
                0f, 0f,
                drawW, drawH,
                texW, texH,
                texW, texH
        );
    }

    public void onCascadePayload(DeckControlPackets.CascadeS2C payload) {
        if (!payload.pos().equals(this.handler.getPos())) return;

        this.cascadeSourceMvNet = payload.sourceMv();
        this.cascadeHitIndex = payload.hitIndex();
        this.cascadeRevealed = payload.revealed();

        this.overlay = OverlayMode.CASCADE;
        this.cascadePendingClient = true;
    }

    private void drawOverlayRow(DrawContext ctx, List<ItemStack> list, int startX, int startY, int cols, int mouseX, int mouseY) {
        for (int i = 0; i < list.size(); i++) {
            int col = i % cols;
            int row = i / cols;

            // only 1 row for now (your wireframe shows a single row)
            if (row > 0) break;

            int cx = startX + col * (OVER_CARD_W + OVER_GAP);
            int cy = startY;

            ctx.fill(cx, cy, cx + OVER_CARD_W, cy + OVER_CARD_H, 0xFF2A2A2A);
            ctx.fill(cx + 1, cy + 1, cx + OVER_CARD_W - 1, cy + OVER_CARD_H - 1, 0xFF111111);

            ItemStack st = list.get(i);
            if (st.isEmpty()) {
                drawCardBackFit(ctx, cx + 1, cy + 1, OVER_CARD_W - 2, OVER_CARD_H - 2);
            } else {
                drawCardArtFit(ctx, st, cx + 1, cy + 1, OVER_CARD_W - 2, OVER_CARD_H - 2);

                if (isMouseIn(mouseX, mouseY, cx, cy, OVER_CARD_W, OVER_CARD_H)) {
                    var info = CardMeta.read(st);
                    String nm = info.name().isEmpty() ? "(card)" : info.name();
                    ctx.drawTooltip(textRenderer, Text.literal(nm + " | MV " + info.mv()), mouseX, mouseY);
                }
            }
        }
    }

    private void drawOverlayCardsTopRow(DrawContext ctx, int startX, int startY, int cols, int mouseX, int mouseY) {
        int shown = Math.min(overlayN, overlayCards.size());

        for (int i = 0; i < shown; i++) {
            int col = i % cols;
            int row = i / cols;
            if (row > 0) break; // one row only per your wireframe

            int cx = startX + col * (OVER_CARD_W + OVER_GAP);
            int cy = startY;

            boolean selected = ((bitmask >> i) & 1) != 0;

            // highlight selected
            int bg = selected ? 0xFF3A2A2A : 0xFF2A2A2A;
            ctx.fill(cx, cy, cx + OVER_CARD_W, cy + OVER_CARD_H, bg);
            ctx.fill(cx + 1, cy + 1, cx + OVER_CARD_W - 1, cy + OVER_CARD_H - 1, 0xFF111111);

            ItemStack st = overlayCards.get(i);
            if (st.isEmpty()) {
                drawCardBackFit(ctx, cx + 1, cy + 1, OVER_CARD_W - 2, OVER_CARD_H - 2);
            } else {
                drawCardArtFit(ctx, st, cx + 1, cy + 1, OVER_CARD_W - 2, OVER_CARD_H - 2);

                if (isMouseIn(mouseX, mouseY, cx, cy, OVER_CARD_W, OVER_CARD_H)) {
                    var info = CardMeta.read(st);
                    String nm = info.name().isEmpty() ? "(card)" : info.name();
                    ctx.drawTooltip(textRenderer, Text.literal(nm + " | MV " + info.mv()), mouseX, mouseY);
                }
            }
        }
    }
    private void drawOverlayEmptySlotsRow(DrawContext ctx, int startX, int startY, int cols, int count) {
        int shown = Math.min(count, cols); // one row

        for (int i = 0; i < shown; i++) {
            int cx = startX + i * (OVER_CARD_W + OVER_GAP);
            int cy = startY;

            ctx.fill(cx, cy, cx + OVER_CARD_W, cy + OVER_CARD_H, 0xFF1F1F1F);
            ctx.fill(cx + 1, cy + 1, cx + OVER_CARD_W - 1, cy + OVER_CARD_H - 1, 0xFF101010);
        }
    }

    private void drawOrderRow(DrawContext ctx, List<Integer> order, int startX, int startY, int cols,
                              int mouseX, int mouseY, boolean isTopRow) {
        for (int pos = 0; pos < order.size(); pos++) {
            int col = pos % cols;
            int row = pos / cols;
            if (row > 0) break; // wireframe = single row

            int cx = startX + col * (OVER_CARD_W + OVER_GAP);
            int cy = startY;

            int cardIdx = order.get(pos);

            // if this card is being dragged, skip drawing it in the row (we’ll draw it as a ghost later)
            if (dragging && cardIdx == dragCardIdx) {
                ctx.fill(cx, cy, cx + OVER_CARD_W, cy + OVER_CARD_H, 0xFF1F1F1F);
                ctx.fill(cx + 1, cy + 1, cx + OVER_CARD_W - 1, cy + OVER_CARD_H - 1, 0xFF101010);
                continue;
            }

            ctx.fill(cx, cy, cx + OVER_CARD_W, cy + OVER_CARD_H, 0xFF2A2A2A);
            ctx.fill(cx + 1, cy + 1, cx + OVER_CARD_W - 1, cy + OVER_CARD_H - 1, 0xFF111111);

            ItemStack st = overlayCards.get(cardIdx);
            drawCardArtFit(ctx, st, cx + 1, cy + 1, OVER_CARD_W - 2, OVER_CARD_H - 2);

            if (isMouseIn(mouseX, mouseY, cx, cy, OVER_CARD_W, OVER_CARD_H)) {
                var info = CardMeta.read(st);
                String nm = info.name().isEmpty() ? "(card)" : info.name();
                ctx.drawTooltip(textRenderer, Text.literal(nm + " | MV " + info.mv()), mouseX, mouseY);
            }
        }

        // ghost render for the dragged card
    }

    private void drawDragGhost(DrawContext ctx, int mouseX, int mouseY) {
        if (!dragging || dragCardIdx < 0 || dragCardIdx >= overlayCards.size()) return;

        ItemStack st = overlayCards.get(dragCardIdx);
        int dx = mouseX - dragMouseOffX;
        int dy = mouseY - dragMouseOffY;

        ctx.fill(dx, dy, dx + OVER_CARD_W, dy + OVER_CARD_H, 0xAA2A2A2A);
        ctx.fill(dx + 1, dy + 1, dx + OVER_CARD_W - 1, dy + OVER_CARD_H - 1, 0xAA111111);
        drawCardArtFit(ctx, st, dx + 1, dy + 1, OVER_CARD_W - 2, OVER_CARD_H - 2);
    }

    private void drawBottomSlotsRow(DrawContext ctx, List<Integer> bottom, int startX, int startY, int cols, int slotCount,
                                    int mouseX, int mouseY) {
        int shownSlots = Math.min(slotCount, cols); // one row
        for (int i = 0; i < shownSlots; i++) {
            int cx = startX + i * (OVER_CARD_W + OVER_GAP);
            int cy = startY;

            boolean hasCard = (i < bottom.size());
            if (!hasCard) {
                ctx.fill(cx, cy, cx + OVER_CARD_W, cy + OVER_CARD_H, 0xFF1F1F1F);
                ctx.fill(cx + 1, cy + 1, cx + OVER_CARD_W - 1, cy + OVER_CARD_H - 1, 0xFF101010);
                continue;
            }

            int cardIdx = bottom.get(i);

            if (dragging && cardIdx == dragCardIdx) {
                ctx.fill(cx, cy, cx + OVER_CARD_W, cy + OVER_CARD_H, 0xFF1F1F1F);
                ctx.fill(cx + 1, cy + 1, cx + OVER_CARD_W - 1, cy + OVER_CARD_H - 1, 0xFF101010);
                continue;
            }

            ctx.fill(cx, cy, cx + OVER_CARD_W, cy + OVER_CARD_H, 0xFF2A2A2A);
            ctx.fill(cx + 1, cy + 1, cx + OVER_CARD_W - 1, cy + OVER_CARD_H - 1, 0xFF111111);

            ItemStack st = overlayCards.get(cardIdx);
            drawCardArtFit(ctx, st, cx + 1, cy + 1, OVER_CARD_W - 2, OVER_CARD_H - 2);
        }
    }
    private int pickRowPos(int mx, int my, int startX, int startY, int cols) {
        // only one row
        if (!(my >= startY && my < startY + OVER_CARD_H)) return -1;
        if (mx < startX) return -1;

        int cellW = OVER_CARD_W + OVER_GAP;
        int rel = mx - startX;
        int col = rel / cellW;

        if (col < 0 || col >= cols) return -1;

        int cx = startX + col * cellW;
        // must be inside the actual card rect, not the gap
        if (mx < cx || mx >= cx + OVER_CARD_W) return -1;

        return col;
    }

    private void startDrag(int mx, int my, int cardIdx, boolean fromTop, int fromPos) {
        dragging = true;
        dragCardIdx = cardIdx;
        dragFromTop = fromTop;
        dragFromPos = fromPos;

        // anchor ghost to where the card was drawn
        int cellW = OVER_CARD_W + OVER_GAP;
        int startX = scry_areaX;
        int startY = fromTop ? scry_topRowY : scry_botRowY;

        int cx = startX + fromPos * cellW;
        int cy = startY;

        dragMouseOffX = mx - cx;
        dragMouseOffY = my - cy;
    }
    @Override
    public boolean mouseReleased(Click click) {
        if ((overlay == OverlayMode.SCRY || overlay == OverlayMode.SURVEIL) && dragging) {
            int mx = (int) click.x();
            int my = (int) click.y();

            boolean overTop = (my >= scry_topRowY && my < scry_topRowY + OVER_CARD_H);
            boolean overBottom = (my >= scry_botRowY && my < scry_botRowY + OVER_CARD_H);

            if (overTop) {
                int insertPos = clampInsertPos(mx, scry_areaX, scry_cols);
                moveCardTo(topOrder, bottomOrder, insertPos, true);
            } else if (overBottom) {
                int insertPos = clampInsertPos(mx, scry_areaX, scry_cols);
                moveCardTo(bottomOrder, topOrder, insertPos, false);
            }
            // else drop outside -> snap back (no change)

            dragging = false;
            dragCardIdx = -1;
            dragFromPos = -1;
            return true;
        }

        return super.mouseReleased(click);
    }

    private int clampInsertPos(int mx, int startX, int cols) {
        int cellW = OVER_CARD_W + OVER_GAP;
        int rel = Math.max(0, mx - startX);
        int col = rel / cellW;
        return Math.max(0, Math.min(col, cols)); // allow “after last”
    }

    private void moveCardTo(List<Integer> target, List<Integer> other, int insertPos, boolean toTop) {
        // Remove from wherever it currently is
        target.remove((Integer) dragCardIdx);
        other.remove((Integer) dragCardIdx);

        // Clamp insert within current size
        insertPos = Math.max(0, Math.min(insertPos, target.size()));

        // Insert at new location
        target.add(insertPos, dragCardIdx);

        // (Optional) Keep within overlayN for bottom slots if you want:
        // while (bottomOrder.size() > overlayN) bottomOrder.remove(bottomOrder.size() - 1);
    }
    private int[] buildScryOrder() {
        int total = topOrder.size() + bottomOrder.size();
        int[] out = new int[total];
        int k = 0;
        for (int idx : topOrder) out[k++] = idx;
        for (int idx : bottomOrder) out[k++] = idx;
        return out;
    }

    private int getTopCount() {
        return topOrder.size();
    }

    private int buildLegacyBitmaskFromOrders() {
        int m = 0;
        if (overlay == OverlayMode.SCRY) {
            // old meaning: bit=1 => keep on top
            for (int idx : topOrder) m |= (1 << idx);
        } else {
            // old meaning: bit=1 => mill
            for (int idx : bottomOrder) m |= (1 << idx);
        }
        return m;
    }

    private ItemStack hoverRowDirect(int mouseX, int mouseY, int startX, int startY, int cols, java.util.List<ItemStack> stacks) {
        if (cols <= 0 || stacks == null || stacks.isEmpty()) return ItemStack.EMPTY;
        if (mouseY < startY || mouseY >= startY + OVER_CARD_H) return ItemStack.EMPTY;

        int cellW = OVER_CARD_W + OVER_GAP;
        int rel = mouseX - startX;
        if (rel < 0) return ItemStack.EMPTY;

        int col = rel / cellW;
        if (col < 0 || col >= cols) return ItemStack.EMPTY;
        if (col >= stacks.size()) return ItemStack.EMPTY;

        int cx = startX + col * cellW;
        if (mouseX < cx || mouseX >= cx + OVER_CARD_W) return ItemStack.EMPTY;

        ItemStack st = stacks.get(col);
        return (st != null && !st.isEmpty() && st.isOf(com.spider.mtgcard.item.ModItems.CARD)) ? st : ItemStack.EMPTY;
    }

    private ItemStack hoverRowByOrder(int mouseX, int mouseY, int startX, int startY, int cols, java.util.List<Integer> order) {
        if (cols <= 0 || order == null || order.isEmpty()) return ItemStack.EMPTY;
        if (mouseY < startY || mouseY >= startY + OVER_CARD_H) return ItemStack.EMPTY;

        int cellW = OVER_CARD_W + OVER_GAP;
        int rel = mouseX - startX;
        if (rel < 0) return ItemStack.EMPTY;

        int col = rel / cellW;
        if (col < 0 || col >= cols) return ItemStack.EMPTY;
        if (col >= order.size()) return ItemStack.EMPTY;

        int cx = startX + col * cellW;
        if (mouseX < cx || mouseX >= cx + OVER_CARD_W) return ItemStack.EMPTY;

        int idx = order.get(col);

        // don't hover the dragged card
        if (dragging && idx == dragCardIdx) return ItemStack.EMPTY;

        if (idx < 0 || idx >= overlayCards.size()) return ItemStack.EMPTY;

        ItemStack st = overlayCards.get(idx);
        return (st != null && !st.isEmpty() && st.isOf(com.spider.mtgcard.item.ModItems.CARD)) ? st : ItemStack.EMPTY;
    }

    private boolean isFoil(ItemStack st) {
        if (st == null || st.isEmpty()) return false;
        Boolean glint = st.get(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE);
        boolean hasGlint = glint != null && glint;
        var comp = st.get(DataComponentTypes.CUSTOM_DATA);
        var root = (comp == null) ? new NbtCompound() : comp.copyNbt();
        boolean foilNbt = root.getBoolean("mtg_foil").orElse(false);
        return hasGlint || foilNbt;
    }

    private void renderHoverPreview(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ItemStack st = getHoveredCardForPreview(mouseX, mouseY);
        if (st == null || st.isEmpty() || !st.isOf(com.spider.mtgcard.item.ModItems.CARD)) {
            lastHoverStack = ItemStack.EMPTY;
            lastTexRef = null;
            return;
        }

        long now = System.currentTimeMillis();

        // Track hover changes
        if (!ItemStack.areEqual(st, lastHoverStack)) {
            hoverSinceMs = now;
            lastHoverStack = st.copy();
            lastTexRef = null;
            animStartMs = now;
            lastHoverFace = readFaceIndex(st);
        }

        // Debounce so it doesn't flash when you sweep across slots
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

        // --- Layout (same idea as DeckboxScreen) ---
        final int panelMaxH = Math.min(this.backgroundHeight - 8, 220);
        final int panelMaxW = 180;

        int panelW = panelMaxW;
        int panelH = panelMaxH;

        int panelX = this.x - (panelW + 12);
        int panelY = this.y + 4;

        // if left side would go offscreen, put it to the right
        if (panelX < 8) panelX = this.x + this.backgroundWidth + 12;

        final int texW = ref.texW();
        final int texH = ref.texH();
        if (texW <= 0 || texH <= 0) return;

        float aspect = (float) texW / (float) texH;

        int drawW = panelW;
        int drawH = (int) (drawW / aspect);
        if (drawH > panelH) {
            drawH = panelH;
            drawW = (int) (drawH * aspect);
        }

        int x = panelX + (panelW - drawW) / 2;
        int y = panelY + (panelH - drawH) / 2;

        // --- Animation ---
        float t = Math.max(0f, Math.min(1f, (now - animStartMs) / (float) ANIM_MS));
        float ease = t * t * (3f - 2f * t);

        float scaleAnim = ANIM_SCALE_FROM + (1f - ANIM_SCALE_FROM) * ease;
        float angleDeg = (1f - ease) * 2.5f;

        // Optional: fade-in tint. If your drawTexture overload with color exists, keep using it.
        int alphaMain = (int) (255f * (0.40f + 0.60f * ease));
        int colorMain = (alphaMain << 24) | 0x00FFFFFF;

        // shadow tint
        int colorShadow = 0x55000000;

        // We render at native texture size then scale via matrices.
        float sx = (float) drawW / (float) texW;
        float sy = (float) drawH / (float) texH;

        var m = ctx.getMatrices();
        Matrix3x2f rot = new Matrix3x2f().rotate((float) Math.toRadians(angleDeg));

// ---------- Shadow pass ----------
        m.pushMatrix();
        m.translate((float) x, (float) y);
        m.scale(sx * 1.02f * scaleAnim, sy * 1.02f * scaleAnim);
        m.translate(texW / 2f, texH / 2f);
        m.mul(rot);
        m.translate(-texW / 2f, -texH / 2f);
        m.translate(2f, 3f);

        ctx.drawTexture(
                RenderPipelines.GUI_TEXTURED,
                ref.id(),
                0, 0,
                0f, 0f,
                texW, texH,
                texW, texH,
                colorShadow
        );
        m.popMatrix();

// ---------- Main pass ----------
        m.pushMatrix();
        m.translate((float) x, (float) y);
        m.scale(sx * scaleAnim, sy * scaleAnim);
        m.translate(texW / 2f, texH / 2f);
        m.mul(rot);
        m.translate(-texW / 2f, -texH / 2f);

        ctx.drawTexture(
                RenderPipelines.GUI_TEXTURED,
                ref.id(),
                0, 0,
                0f, 0f,
                texW, texH,
                texW, texH,
                colorMain
        );

// ---------- Foil shimmer ----------
        if (isFoil(st)) {
            int stripePx = Math.max(6, (int) (texW * 0.22f));
            float travel = texW + stripePx * 2f;
            float speed = 0.22f;
            float pos = ((now / 16f) * speed) % travel - stripePx;

            int u = Math.round(pos);
            if (u < texW && u + stripePx > 0) {
                int drawU = Math.max(0, Math.min(texW - stripePx, u));
                int clipW = Math.min(stripePx, texW - drawU);

                int shimmerAlpha = (int) (0x88 + 0x2A * ease);
                int colorShimmer = (shimmerAlpha << 24) | 0x00FFFFFF;

                ctx.drawTexture(
                        RenderPipelines.GUI_TEXTURED,
                        ref.id(),
                        drawU, 0,
                        (float) drawU, 0f,
                        clipW, texH,
                        texW, texH,
                        colorShimmer
                );
            }
        }

        m.popMatrix();
    }

    private void clearOverlayHoverCache() {
        hoverTopCols = 0;
        hoverBotCols = 0;
        hoverUsesIndexOrder = false;

        placeCardBoxX = placeCardBoxY = placeCardBoxW = placeCardBoxH = 0;
        cascadeThumbX = cascadeThumbY = cascadeThumbCols = 0;
    }

    private void drawRevealOverlay(DrawContext ctx, int mouseX, int mouseY) {
        int left = this.x;
        int top  = this.y;

        int ox = left + PAD;
        int oy = top + PAD + 10;
        int ow = backgroundWidth - PAD * 2;

        // height: enough for 1–2 rows + buttons
        int btnW = 90;
        int btnGap = 6;
        int btnY = oy + 140;
        int oh = (btnY + BTN_H + 10) - oy;

        // panel
        ctx.fill(ox, oy, ox + ow, oy + oh, 0xDD151515);
        ctx.fill(ox + 1, oy + 1, ox + ow - 1, oy + oh - 1, 0xDD202020);

        ctx.drawText(textRenderer,
                Text.literal("Resolving: Peek (" + overlayN + ")"),
                ox + 8, oy + 8, 0xFFFFFFFF, false);
        ctx.drawText(textRenderer,
                Text.literal("Private view — only you can see these."),
                ox + 8, oy + 22, 0xFFCFCFCF, false);

        int startX = ox + 10;
        int startY = oy + 42;

        int availW = ow - 20;
        int cellW = OVER_CARD_W + OVER_GAP;
        int cols = Math.max(1, availW / cellW);

        // hover-preview cache for this overlay row
        hoverTopX = startX;
        hoverTopY = startY;
        hoverTopCols = cols;
        hoverBotCols = 0;
        hoverUsesIndexOrder = false;

        // clip so cards never render outside the panel
        ctx.enableScissor(ox, oy, ox + ow, oy + oh);

        int shown = Math.min(overlayN, overlayCards.size());
        for (int i = 0; i < shown; i++) {
            int col = i % cols;
            int row = i / cols;

            int cx = startX + col * cellW;
            int cy = startY + row * (OVER_CARD_H + 10);

            // stop if we'd run into the button row
            if (cy + OVER_CARD_H > btnY - 8) break;

            ctx.fill(cx, cy, cx + OVER_CARD_W, cy + OVER_CARD_H, 0xFF2A2A2A);
            ctx.fill(cx + 1, cy + 1, cx + OVER_CARD_W - 1, cy + OVER_CARD_H - 1, 0xFF111111);

            ItemStack st = overlayCards.get(i);
            if (st == null || st.isEmpty()) drawCardBackFit(ctx, cx + 1, cy + 1, OVER_CARD_W - 2, OVER_CARD_H - 2);
            else drawCardArtFit(ctx, st, cx + 1, cy + 1, OVER_CARD_W - 2, OVER_CARD_H - 2);

            // tooltip
            if (st != null && !st.isEmpty() && isMouseIn(mouseX, mouseY, cx, cy, OVER_CARD_W, OVER_CARD_H)) {
                var info = CardMeta.read(st);
                String nm = info.name().isEmpty() ? "(card)" : info.name();
                ctx.drawTooltip(textRenderer, Text.literal(nm + " | MV " + info.mv()), mouseX, mouseY);
            }
        }

        ctx.disableScissor();

        // buttons inside panel (bottom-right)
        int cancelX = ox + ow - 10 - btnW;
        int doneX   = cancelX - btnGap - btnW;

        if (btnOverlayDone != null) {
            btnOverlayDone.setX(doneX);
            btnOverlayDone.setY(btnY);
            btnOverlayDone.setWidth(btnW);
            btnOverlayDone.visible = true;
            btnOverlayDone.active = true;
        }
        if (overlayCancel != null) {
            overlayCancel.setX(cancelX);
            overlayCancel.setY(btnY);
            overlayCancel.setWidth(btnW);
            overlayCancel.visible = true;
            overlayCancel.active = true;
        }
    }
}
