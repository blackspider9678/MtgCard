package com.spider.mtgcard.client;

import com.spider.mtgcard.client.compat.LegacyScreen;
import com.spider.mtgcard.client.input.GuiCardFaceFlipHandler;
import com.spider.mtgcard.client.java.CardArtManager;
import com.spider.mtgcard.config.MtgcardConfig;
import com.spider.mtgcard.net.payload.SetCounterMetaPayload;
import com.spider.mtgcard.net.payload.SetCounterValuePayload;
import com.spider.mtgcard.net.payload.SetFacePayload;
import com.spider.mtgcard.util.TcgCardMeta;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.ListTag;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.*;

@Environment(EnvType.CLIENT)
public class CardLargeViewScreen extends LegacyScreen implements GuiCardFaceFlipHandler {

    private final ItemStack stack; // copy so we don't mutate held item client-side
    private int faceIndex;
    private int faceCount;

    // rotated card rect for hit-testing + button placement
    private int cardX, cardY, cardW, cardH;

    // flip button rect
    private int flipX, flipY, flipW, flipH;
    private static final int FLIP_SIZE = 18;
    private static final int BTN_PAD   = 8;
    private static final int BTN_GAP   = 6;

    // rotate button rect
    private int rotX, rotY, rotW, rotH;

    // Info panel toggle
    private boolean infoOpen = false;

    // top-left info button
    private static final int INFO_SIZE = 18;
    private int infoX, infoY, infoW, infoH;

    // currently displayed face's scryfall id (for price/legalities)
    private String currentScryfallId;

    // refresh throttling while panel is open
    private int refreshTicker = 0;
    private static final int REFRESH_CHECK_EVERY_TICKS = 20; // ~1s

    @SuppressWarnings("unused")
    private static final Identifier FLIP_ICON = Identifier.withDefaultNamespace("widget/turn_back");
    private static final String EM_DASH = "\u2014";
    private static final String BULLET = "\u2022";
    private static final String EURO = "\u20AC";
    private static final String FLIP_GLYPH_FRONT = "\u21BB";
    private static final String FLIP_GLYPH_BACK = "\u21BA";
    private static final String ROTATE_GLYPH = "\u27F3";

    private static String sanitizeUiText(String s) {
        if (s == null) return EM_DASH;

        return s
                .replace("Ã¢â‚¬â€", EM_DASH)
                .replace("â€”", EM_DASH)
                .replace("Ã¢â‚¬Â¢", BULLET)
                .replace("â€¢", BULLET)
                .replace("Ã¢â€šÂ¬", EURO)
                .replace("â‚¬", EURO)
                .replace("Ã‚Â°", "\u00B0")
                .replace("Â°", "\u00B0")
                .replace("Ã¢â€ Â»", FLIP_GLYPH_FRONT)
                .replace("â†»", FLIP_GLYPH_FRONT)
                .replace("Ã¢â€ Âº", FLIP_GLYPH_BACK)
                .replace("â†º", FLIP_GLYPH_BACK)
                .replace("Ã¢Å¸Â³", ROTATE_GLYPH)
                .replace("âŸ³", ROTATE_GLYPH);
    }

    // ------------------------------------------------------------
    // Flip animation state
    // ------------------------------------------------------------
    private static final int FLIP_TICKS = 8;
    private boolean flipping = false;
    private float flipProgress = 0f; // 0..1
    private boolean flipSwapped = false;

    private int flipFromFace = 0;
    private int flipToFace = 0;

    // ------------------------------------------------------------
    // Rotation state (0,90,180,270)
    // ------------------------------------------------------------
    private int rotSteps;

    // ------------------------------------------------------------
    // Legality scroll state (inside info panel)
    // ------------------------------------------------------------
    private float legalityScroll = 0f;
    private boolean draggingLegalityBar = false;
    private int legalityBarDragOffset = 0;

    // cached scroll area for mouse checks
    private int legAreaX, legAreaY, legAreaW, legAreaH;
    private int legBarX, legBarY, legBarW, legBarH;

    private static final int LEG_ROW_H = 12;
    private static final int SCROLLBAR_W = 6;
    private int lastLegalityRowCount = 0;

    private static final int PRICE_NORMAL = 0xFFFFFFFF;
    private static final int PRICE_FOIL   = 0xFFFF55FF; // Magenta
    private static final int PRICE_ETCHED = 0xFF55FFFF; // Cyan

    private enum Tab { INFO, COUNTERS }
    private Tab tab = Tab.INFO;

    // tabs
    private int tabX, tabY;
    private static final int TAB_H = 18;
    private static final int TAB_GAP = 8;

    private enum PriceTier {
        NORMAL,
        FOIL,
        ETCHED
    }

    // Counters tab state
    private int countersScroll = 0;
    private String selectedCounterKey = null; // <--- add this (replace selectedCounter int)
    private static final int CTR_ROW_H = 14;

    // ------------------------------------------------------------
    // Counters editor (widgets/state)
    // ------------------------------------------------------------
    private EditBox counterNameField;
    private Button createCounterBtn;
    private Button saveCounterBtn;
    private Button deleteCounterBtn;
    private Button iconCycleBtn;

    private boolean counterEditorOpen = false;
    private String editingCounterKey = null; // null = creating new
    private String editingIconKey = "none";

    private record PriceResult(String value, PriceTier tier) {}

    private final int handSlot;

    private static final int ICON_TEX = 16; // actual PNG size (most MC-style icons)

    // ------------------------------------------------------------
// Icon picker (grid + scroll)
// ------------------------------------------------------------
    private int iconScroll = 0;
    private boolean draggingIconBar = false;
    private int iconBarDragOffset = 0;

    // cached rects for hit-testing
    private int iconAreaX, iconAreaY, iconAreaW, iconAreaH;
    private int iconBarX, iconBarY, iconBarW, iconBarH;

    private static final int ICON_CELL = 18;   // cell size
    private static final int ICON_PAD  = 2;    // inner padding
    private static final int ICON_COLS = 8;    // columns in grid
    private static final int ICON_SCROLLBAR_W = 6;

    // ---- Counters HUD (always visible on right) ----
    private static final int HUD_PAD = 8;
    private static final int HUD_ICON = 18;          // was 12
    private static final int HUD_ROW_H = HUD_ICON + 6; // auto height
    private static final int HUD_BG = 0x7A101010;
    private static final int HUD_BORDER = 0xAA303030;
    private static final int HUD_HOVER = 0xAA70E0FF;

    private String hudHoverCounterKey = null;

    // optional: hover detection rect per row
    private int hudX, hudY, hudW, hudH;

    // ---- +/- hitboxes (HUD + Counters tab list) ----
    private record Rect(int x, int y, int w, int h) {
        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    private final Map<String, Rect> hudMinusRects = new HashMap<>();
    private final Map<String, Rect> hudPlusRects  = new HashMap<>();

    private final Map<String, Rect> listMinusRects = new HashMap<>();
    private final Map<String, Rect> listPlusRects  = new HashMap<>();

    private static final int ADJ_BTN = 12;   // size of +/- buttons
    private static final int ADJ_GAP = 2;    // gap between +/- buttons and text

    // ---- icon texture size cache ----
    private record TexSize(int w, int h) {}

    private static final Map<Identifier, TexSize> ICON_SIZE_CACHE = new HashMap<>();

    private final int displayEntityId; // -1 = hand mode, otherwise entity id


    public CardLargeViewScreen(ItemStack original, int handSlot, int displayEntityId) {
        super(Component.literal("Card"));
        this.stack = original.copy();
        this.handSlot = handSlot;
        this.displayEntityId = displayEntityId; // âœ… IMPORTANT
        this.hidden = readHidden(this.stack);

        this.faceIndex = readFaceIndex(this.stack);
        this.faceCount = readFaceCount(this.stack);
        this.rotSteps = readRotation(this.stack);

        if (this.faceCount < 1) this.faceCount = 1;
        if (this.faceIndex < 0) this.faceIndex = 0;
        if (this.faceIndex > this.faceCount - 1) this.faceIndex = this.faceCount - 1;
    }

    private static final Identifier CARD_BACK_TEX =
            Identifier.fromNamespaceAndPath("mtgcard", "textures/gui/card.png");

    private boolean hidden;

    private static boolean readHidden(ItemStack st) {
        CompoundTag meta = getMeta(st);
        return meta.getBoolean("mtg_hidden").orElse(false);
    }

    private static void writeHidden(ItemStack st, boolean hidden) {
        editMeta(st, meta -> meta.putBoolean("mtg_hidden", hidden));
    }

    private static TexSize getTextureSize(Identifier texId) {
        TexSize cached = ICON_SIZE_CACHE.get(texId);
        if (cached != null) return cached;

        var client = Minecraft.getInstance();
        if (client == null) return new TexSize(16, 16);

        try {
            // Resource path is already "textures/..../file.png" in your Identifier
            var opt = client.getResourceManager().getResource(texId);
            if (opt.isPresent()) {
                try (var in = opt.get().open()) {
                    NativeImage img = NativeImage.read(in);
                    TexSize sz = new TexSize(img.getWidth(), img.getHeight());
                    img.close();
                    ICON_SIZE_CACHE.put(texId, sz);
                    return sz;
                }
            }
        } catch (Throwable ignored) {}

        // fallback if missing/bad
        TexSize fallback = new TexSize(16, 16);
        ICON_SIZE_CACHE.put(texId, fallback);
        return fallback;
    }

    /** Draw icon scaled to DEST size, with correct UVs based on actual PNG dimensions. */
    /** Draw icon "contain" (fit inside box, preserve aspect ratio, no cropping). */
    private void drawIconFit(GuiGraphics ctx, Identifier tex, int x, int y, int w, int h) {
        TexSize sz = getTextureSize(tex);
        int tw = Math.max(1, sz.w());
        int th = Math.max(1, sz.h());

        // scale to fit INSIDE the box (contain)
        float s = Math.min(w / (float) tw, h / (float) th);
        int dw = Math.max(1, Math.round(tw * s));
        int dh = Math.max(1, Math.round(th * s));

        int dx = x + (w - dw) / 2;
        int dy = y + (h - dh) / 2;

        // IMPORTANT: use the full overload:
        // destW/destH = dw/dh
        // regionW/regionH = tw/th (sample the whole image)
        // texW/texH = tw/th (actual texture size)
        ctx.blit(
                RenderPipelines.GUI_TEXTURED,
                tex,
                dx, dy,
                0, 0,
                dw, dh,
                tw, th,
                tw, th
        );
    }

    @Override
    protected void init() {
        super.init();
        this.clearWidgets();

        if (!infoOpen) return;

        int panelX = 8;
        int panelY = 8 + INFO_SIZE + 6;

        tabX = panelX + 10;
        tabY = panelY + 10;

        addRenderableWidget(Button.builder(Component.literal("Info"), b -> { tab = Tab.INFO; init(); })
                .bounds(tabX, tabY, 70, TAB_H).build());

        addRenderableWidget(Button.builder(Component.literal("Counters"), b -> { tab = Tab.COUNTERS; init(); })
                .bounds(tabX + 70 + TAB_GAP, tabY, 90, TAB_H).build());

        if (tab == Tab.COUNTERS) {
            initCountersEditorWidgets(panelX, panelY);
        }

        legalityScroll = 0f;
        draggingLegalityBar = false;
        legalityBarDragOffset = 0;
    }




    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private boolean isMouseOverInfo(double mx, double my) {
        return mx >= infoX && mx <= (infoX + infoW) && my >= infoY && my <= (infoY + infoH);
    }

    private int getShownFaceIndex() {
        if (!flipping) return faceIndex;
        return (flipProgress < 0.5f) ? flipFromFace : flipToFace;
    }

    private void toggleInfoPanel() {
        infoOpen = !infoOpen;
        refreshTicker = 0;

        if (infoOpen) {
            int shownFace = getShownFaceIndex();
            currentScryfallId = readScryfallIdForFace(stack, shownFace);
            if (shouldUseRemoteInfo() && currentScryfallId != null && !currentScryfallId.isBlank()) {
                ScryfallInfoManager.forceRefresh(currentScryfallId);
            }
        } else {
            draggingLegalityBar = false;
        }
        // Rebuild widgets so tabs appear/disappear
        init();
    }


    /** Try to get the scryfall id for the currently shown face. */
    private String readScryfallIdForFace(ItemStack st, int faceIdx) {
        CompoundTag meta = getMeta(st);

        // 1) per-face in card_faces list
        Tag el = meta.get("card_faces");
        if (el instanceof ListTag list && faceIdx >= 0 && faceIdx < list.size()) {
            Tag faceEl = list.get(faceIdx);
            if (faceEl instanceof CompoundTag fc) {
                String s = fc.getString("id").orElse("");
                if (!s.isBlank()) return s;

                s = fc.getString("scryfall_id").orElse("");
                if (!s.isBlank()) return s;

                s = fc.getString("oracle_id").orElse("");
                if (!s.isBlank()) return s;
            }
        }

        // 2) root-level
        String s = meta.getString("id").orElse("");
        if (!s.isBlank()) return s;

        s = meta.getString("scryfall_id").orElse("");
        if (!s.isBlank()) return s;

        s = meta.getString("oracle_id").orElse("");
        if (!s.isBlank()) return s;

        return null;
    }

    private boolean shouldUseRemoteInfo() {
        return TcgCardMeta.read(stack).isMtg();
    }

    private static int legalityColor(String status) {
        if (status == null) return 0xFFAAAAAA;
        String normalized = status.trim().toLowerCase(Locale.ROOT);

        return switch (normalized) {
            case "legal" -> 0xFF3BE36A;
            case "restricted" -> 0xFFE6D24A;
            case "banned" -> 0xFFE04A4A;
            case "not_legal" -> 0xFF777777;
            default -> 0xFFAAAAAA;
        };
    }

    private static String prettyLegality(String status) {
        if (status == null) return "â€”";
        String normalized = status.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "legal" -> "Legal";
            case "restricted" -> "Restricted";
            case "banned" -> "Banned";
            case "not_legal" -> "Not legal";
            default -> status;
        };
    }

    @Override
    public void tick() {
        super.tick();

        // flip animation
        if (flipping) {
            flipProgress += 1f / FLIP_TICKS;

            if (!flipSwapped && flipProgress >= 0.5f) {
                flipSwapped = true;

                faceIndex = flipToFace;
                writeFaceIndex(stack, faceIndex);

                sendFaceUpdate(faceIndex);

                // keep panel synced to shown face
                if (infoOpen) {
                    int shownFace = getShownFaceIndex();
                    currentScryfallId = readScryfallIdForFace(stack, shownFace);
                    if (shouldUseRemoteInfo() && currentScryfallId != null && !currentScryfallId.isBlank()) {
                        ScryfallInfoManager.forceRefresh(currentScryfallId);
                    }
                }
            }

            if (flipProgress >= 1f) {
                flipping = false;
                flipProgress = 0f;
                flipSwapped = false;
            }
        }

        // while info panel open, keep data fresh-ish but throttled
        if (infoOpen) {
            refreshTicker++;
            if (refreshTicker >= REFRESH_CHECK_EVERY_TICKS) {
                refreshTicker = 0;
                if (shouldUseRemoteInfo() && currentScryfallId != null && !currentScryfallId.isBlank()) {
                    ScryfallInfoManager.ensureFresh(currentScryfallId);
                }
            }
        }
    }

    private static CompoundTag getOrCreateCounterIcons(ItemStack st) {
        var comp = st.get(DataComponents.CUSTOM_DATA);
        CompoundTag root = (comp == null) ? new CompoundTag() : comp.copyTag();
        CompoundTag meta = root.getCompound("mtg_meta").orElseGet(CompoundTag::new);

        CompoundTag icons = meta.getCompound("counter_icons").orElseGet(CompoundTag::new);
        meta.put("counter_icons", icons);
        root.put("mtg_meta", meta);
        st.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
        return icons;
    }

    private static CompoundTag getOrCreateCounterNames(ItemStack st) {
        var comp = st.get(DataComponents.CUSTOM_DATA);
        CompoundTag root = (comp == null) ? new CompoundTag() : comp.copyTag();
        CompoundTag meta = root.getCompound("mtg_meta").orElseGet(CompoundTag::new);

        CompoundTag names = meta.getCompound("counter_names").orElseGet(CompoundTag::new);
        meta.put("counter_names", names);
        root.put("mtg_meta", meta);
        st.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
        return names;
    }

    private void initCountersEditorWidgets(int panelX, int panelY) {
        int panelW = 190;
        int panelH = this.height - (panelY) - 8;

        int x = panelX + 10;
        int editorW = panelW - 20;

        // Put editor near bottom of panel
        int editorY = panelY + panelH - 58;

        counterNameField = new EditBox(this.font, x, editorY, editorW, 18, Component.literal("Counter name"));
        counterNameField.setMaxLength(32);
        counterNameField.setHint(Component.literal("Counter name..."));
        addRenderableWidget(counterNameField);

        int btnY = editorY + 22;

        createCounterBtn = addRenderableWidget(
                Button.builder(Component.literal("Create"), b -> createCounterNow())
                        .bounds(x, btnY, 54, 18).build()
        );

        saveCounterBtn = addRenderableWidget(
                Button.builder(Component.literal("Save"), b -> saveEditedCounter())
                        .bounds(x + 58, btnY, 54, 18).build()
        );

        deleteCounterBtn = addRenderableWidget(
                Button.builder(Component.literal("Del"), b -> deleteEditedCounter())
                        .bounds(x + 116, btnY, 54, 18).build()
        );
        if (!counterEditorOpen) beginCreateCounter();

        // Disable Save/Del until editor is open (or editing existing)
        updateEditorButtons();
    }

    private static void deleteCounterKey(ItemStack st, String key) {
        final String k = normalizeCounterKey(key);

        editMeta(st, meta -> {
            // counters
            CompoundTag counters = meta.getCompound("counters").orElseGet(CompoundTag::new);
            counters.remove(k);
            meta.put("counters", counters);

            // icons
            CompoundTag icons = meta.getCompound("counter_icons").orElseGet(CompoundTag::new);
            icons.remove(k);
            meta.put("counter_icons", icons);

            // display names
            CompoundTag names = meta.getCompound("counter_names").orElseGet(CompoundTag::new);
            names.remove(k);
            meta.put("counter_names", names);
        });
    }

    private static void setCounterIcon(ItemStack st, String key, String icon) {
        final String k = normalizeCounterKey(key);
        final String ic = normalizeIconKey(icon);

        editMeta(st, meta -> {
            CompoundTag icons = meta.getCompound("counter_icons").orElseGet(CompoundTag::new);
            icons.putString(k, ic);
            meta.put("counter_icons", icons);
        });
    }

    private static String getCounterIcon(ItemStack st, String key) {
        key = normalizeCounterKey(key);

        CompoundTag meta = getMeta(st);
        CompoundTag icons = meta.getCompound("counter_icons").orElse(null);
        if (icons == null) return "none";
        return icons.getString(key).orElse("none");
    }

    private static void removeCounterIcon(ItemStack st, String key) {
        final String k = normalizeCounterKey(key);

        editMeta(st, meta -> {
            CompoundTag icons = meta.getCompound("counter_icons").orElseGet(CompoundTag::new);
            icons.remove(k);
            meta.put("counter_icons", icons);
        });
    }

    private static void setCounterDisplayName(ItemStack st, String key, String name) {
        final String k = normalizeCounterKey(key);
        final String cleaned = (name == null) ? "" : name.trim();

        editMeta(st, meta -> {
            CompoundTag names = meta.getCompound("counter_names").orElseGet(CompoundTag::new);
            if (cleaned.isBlank()) names.remove(k);
            else names.putString(k, cleaned);
            meta.put("counter_names", names);
        });
    }

    private static String getCounterDisplayName(ItemStack st, String key) {
        key = normalizeCounterKey(key);

        CompoundTag meta = getMeta(st);
        CompoundTag names = meta.getCompound("counter_names").orElse(null);
        if (names == null) return "";
        return names.getString(key).orElse("");
    }

    private static void removeCounterDisplayName(ItemStack st, String key) {
        final String k = normalizeCounterKey(key);

        editMeta(st, meta -> {
            CompoundTag names = meta.getCompound("counter_names").orElseGet(CompoundTag::new);
            names.remove(k);
            meta.put("counter_names", names);
        });
    }

    private void beginCreateCounter() {
        counterEditorOpen = true;
        editingCounterKey = null;
        editingIconKey = "none";
        iconScroll = 0;
        draggingIconBar = false;
        iconBarDragOffset = 0;

        if (counterNameField != null) {
            counterNameField.setValue("");
            this.setFocused(counterNameField);
            counterNameField.setFocused(true);
        }
        updateEditorButtons();
        refreshIconButton();
    }

    private void beginEditCounter(String key) {
        counterEditorOpen = true;
        editingCounterKey = normalizeCounterKey(key);
        editingIconKey = getCounterIcon(stack, editingCounterKey);
        iconScroll = 0;
        draggingIconBar = false;
        iconBarDragOffset = 0;

        String display = getCounterDisplayName(stack, editingCounterKey);
        if (display.isBlank()) display = toTitle(editingCounterKey.replace('_', ' '));
        if (counterNameField != null) {
            counterNameField.setValue(display);
            this.setFocused(counterNameField);
            counterNameField.setFocused(true);
        }

        updateEditorButtons();
        refreshIconButton();
    }

    private void saveEditedCounter() {
        if (!counterEditorOpen || counterNameField == null) return;
        if (editingCounterKey == null) return; // <-- only edit existing now

        String name = counterNameField.getValue().trim();
        if (name.isBlank()) return;

        String key = editingCounterKey;
        setCounterDisplayName(stack, key, name);
        setCounterIcon(stack, key, editingIconKey);

        sendCounterMetaUpdate(key, name, editingIconKey);

        updateEditorButtons();
    }

    private void deleteEditedCounter() {
        if (!counterEditorOpen || editingCounterKey == null) return;

        String key = editingCounterKey;

        // local immediate delete so UI updates instantly
        com.spider.mtgcard.util.StackData.deleteCounterKey(stack, key);

        // server delete (mode-aware)
        if (isDisplayMode()) {
            ClientPlayNetworking.send(new com.spider.mtgcard.net.payload.CardDisplayPayloads.DisplayDeleteCounterC2S(displayEntityId, key));
        } else {
            ClientPlayNetworking.send(new com.spider.mtgcard.net.payload.DeleteCounterPayload(handSlot, key));
        }

        counterEditorOpen = false;
        editingCounterKey = null;
        editingIconKey = "none";
        selectedCounterKey = null;
        if (counterNameField != null) counterNameField.setValue("");

        updateEditorButtons();
        refreshIconButton();
    }

    private void refreshIconButton() {
        if (iconCycleBtn != null) {
            iconCycleBtn.setMessage(Component.literal("Icon: " + editingIconKey));
        }
    }

    private void updateEditorButtons() {
        // Create should be usable whenever editor is open (and name not blank is handled in createCounterNow)
        boolean canCreate = counterEditorOpen;

        // Save/Del only when editing an existing counter
        boolean canSave = counterEditorOpen && editingCounterKey != null;
        boolean canDelete = canSave;

        if (createCounterBtn != null) createCounterBtn.active = canCreate;
        if (saveCounterBtn != null) saveCounterBtn.active = canSave;
        if (deleteCounterBtn != null) deleteCounterBtn.active = canDelete;
    }

    private void drawCountersHud(GuiGraphics ctx, int mouseX, int mouseY) {
        CompoundTag counters = getOrCreateCounters(stack);
        if (counters.keySet().isEmpty()) return;

        // sorted stable display
        List<String> keys = new ArrayList<>(counters.keySet());
        keys.sort(String::compareToIgnoreCase);

        hudMinusRects.clear();
        hudPlusRects.clear();

        // Layout on right side
        int valueMaxW = 0;
        for (String k : keys) {
            int v = counters.getInt(k).orElse(0);
            valueMaxW = Math.max(valueMaxW, this.font.width(String.valueOf(v)));
        }

        int innerW = HUD_ICON + 4 + ADJ_BTN + ADJ_GAP + valueMaxW + ADJ_GAP + ADJ_BTN; // icon + gap + number
        int boxW = innerW + 10;               // padding
        int boxH = keys.size() * HUD_ROW_H + 8;

        int x = this.width - HUD_PAD - boxW;
        int y = (this.height - boxH) / 2;

        hudX = x; hudY = y; hudW = boxW; hudH = boxH;

        // Panel background + border
        ctx.fill(x - 1, y - 1, x + boxW + 1, y + boxH + 1, HUD_BORDER);
        ctx.fill(x, y, x + boxW, y + boxH, HUD_BG);

        int rowX = x + 5;
        int rowY = y + 4;

        for (int i = 0; i < keys.size(); i++) {
            String k = keys.get(i);
            int v = counters.getInt(k).orElse(0);

            int ry = rowY + i * HUD_ROW_H;

            boolean hover = mouseX >= x && mouseX <= x + boxW
                    && mouseY >= ry && mouseY <= ry + HUD_ROW_H;

            if (hover) {
                hudHoverCounterKey = k;
                ctx.fill(x + 1, ry, x + boxW - 1, ry + HUD_ROW_H, 0x33202020);
            }

            // icon
            String iconKey = getCounterIcon(stack, k);
            int ix = rowX;
            int iy = ry + (HUD_ROW_H - HUD_ICON) / 2;

            if (iconKey == null || iconKey.equals("none")) {
                ctx.drawString(this.font, "â€”", ix + 2, ry + 3, 0xFF777777, false);
            } else {
                Identifier tex = Identifier.fromNamespaceAndPath("mtgcard", "textures/gui/counters/" + iconKey + ".png");
                drawIconFit(ctx, tex, ix, iy, HUD_ICON, HUD_ICON);
            }

            // number only
            // Layout: [icon] [ - ] [ value ] [ + ]
            String val = String.valueOf(v);
            int valW = this.font.width(val);

            int byBtn   = ry + (HUD_ROW_H - ADJ_BTN) / 2;

            // minus button goes right after icon
            int bxMinus = ix + HUD_ICON + 4;

            // value starts after minus button
            int txVal   = bxMinus + ADJ_BTN + ADJ_GAP;
            int tyVal   = ry + (HUD_ROW_H - this.font.lineHeight) / 2;

            // plus button after the value
            int bxPlus  = txVal + valW + ADJ_GAP;

            // hover checks
            boolean hoverMinus = (mouseX >= bxMinus && mouseX < bxMinus + ADJ_BTN && mouseY >= byBtn && mouseY < byBtn + ADJ_BTN);
            boolean hoverPlus  = (mouseX >= bxPlus  && mouseX < bxPlus  + ADJ_BTN && mouseY >= byBtn && mouseY < byBtn + ADJ_BTN);

            drawMiniButton(ctx, bxMinus, byBtn, ADJ_BTN, "-", hoverMinus);
            ctx.drawString(this.font, val, txVal, tyVal, 0xFFFFFFFF, false);
            drawMiniButton(ctx, bxPlus,  byBtn, ADJ_BTN, "+", hoverPlus);

            // store click rects
            hudMinusRects.put(k, new Rect(bxMinus, byBtn, ADJ_BTN, ADJ_BTN));
            hudPlusRects.put(k,  new Rect(bxPlus,  byBtn, ADJ_BTN, ADJ_BTN));


            // optional tooltip (counter name) on hover
            if (hover) {
                String display = getCounterDisplayName(stack, k);
                if (display == null || display.isBlank()) display = toTitle(k.replace('_', ' '));
                ctx.setTooltipForNextFrame(this.font, Component.literal(display), mouseX, mouseY);
            }
        }
    }

    // ------------------------------------------------------------
    // Input (1.21.10 signatures)
    // ------------------------------------------------------------

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean bl) {
        double mx = click.x();
        double my = click.y();
        int btn = click.button();

        // Info button toggle
        if (btn == 0 && isMouseOverInfo(mx, my)) {
            toggleInfoPanel();
            return true;
        }

        // Hide/Show
        if (btn == 0 && mx >= hideX && mx <= hideX + hideW && my >= hideY && my <= hideY + hideH) {
            toggleHidden();
            return true;
        }

        // +/- buttons (HUD + Counters tab list)
        if (btn == 0) {
            // HUD buttons always active
            for (var e : hudMinusRects.entrySet()) {
                if (e.getValue().contains(mx, my)) {
                    adjustCounterValue(e.getKey(), -1);
                    return true;
                }
            }
            for (var e : hudPlusRects.entrySet()) {
                if (e.getValue().contains(mx, my)) {
                    adjustCounterValue(e.getKey(), +1);
                    return true;
                }
            }

            // Counters tab list buttons (only meaningful when panel open)
            if (infoOpen && tab == Tab.COUNTERS) {
                for (var e : listMinusRects.entrySet()) {
                    if (e.getValue().contains(mx, my)) {
                        adjustCounterValue(e.getKey(), -1);
                        return true;
                    }
                }
                for (var e : listPlusRects.entrySet()) {
                    if (e.getValue().contains(mx, my)) {
                        adjustCounterValue(e.getKey(), +1);
                        return true;
                    }
                }
            }
        }

        // scrollbar drag start
        if (btn == 0 && infoOpen && isOverLegalityBar(mx, my)) {
            draggingLegalityBar = true;
            legalityBarDragOffset = (int) my - legBarY;
            return true;
        }

        // Left click = flip
        if (btn == 0 && isDoubleFaced() && isMouseOverFlip(mx, my)) {
            startFlip(faceIndex ^ 1);
            return true;
        }

        // Left click = rotate button
        if (btn == 0 && isMouseOverRotate(mx, my)) {
            rotateRight();
            return true;
        }

        // Any non-left click on card = rotate
        if (btn != 0 && isMouseOverCard(mx, my)) {
            rotateRight();
            return true;
        }

        // In mouseClicked(...)
        if (btn == 0 && infoOpen && tab == Tab.COUNTERS) {
            int panelX = 8;
            int panelY = 8 + INFO_SIZE + 6;
            int panelW = 190;
            int panelH = this.height - panelY - 8;

            int x = panelX + 10;
            int y = panelY + 10;
            y += 18 + 8; // space for tabs
            y += 16;     // "Counters" title height (matches drawCountersTab change)

            int listX = x;
            int listY = y;
            int listW = panelW - 20;
            int editorReserve = 58 /*textfield+buttons*/ + 44 /*icon grid*/ + 8;
            int listH = (panelY + panelH - 10) - listY - editorReserve;
            if (listH < CTR_ROW_H) listH = CTR_ROW_H;

            // If click is on the icon grid, let the icon picker handler below handle it
            if (counterEditorOpen
                    && mx >= iconAreaX && mx < iconAreaX + iconAreaW
                    && my >= iconAreaY && my < iconAreaY + iconAreaH) {
                // do nothing here (fall through)
            } else {
                if (mx >= listX && mx <= listX + listW && my >= listY && my <= listY + listH) {
                    CompoundTag counters = getOrCreateCounters(stack);
                    List<String> keys = new ArrayList<>(counters.keySet());
                    keys.sort(String::compareToIgnoreCase);
                    if (!keys.isEmpty()) {
                        int row = (int)((my - listY + countersScroll) / CTR_ROW_H);
                        row = clampInt(row, 0, keys.size() - 1);

                        String key = keys.get(row);
                        selectedCounterKey = key;
                        beginEditCounter(key);
                        return true;
                    }
                    return true;
                }
            }
        }

        // Icon picker click + scrollbar drag
        if (btn == 0 && infoOpen && tab == Tab.COUNTERS && counterEditorOpen) {
            // click inside icon grid
            if (mx >= iconAreaX && mx < iconAreaX + iconAreaW && my >= iconAreaY && my < iconAreaY + iconAreaH) {
                List<String> icons = new ArrayList<>();
                icons.add("none");
                icons.addAll(loadCounterIconKeys());

                int cell = ICON_CELL;
                int cols = ICON_COLS;

                int localX = (int) (mx - iconAreaX);
                int localY = (int) (my - iconAreaY) + iconScroll;

                int c = localX / cell;
                int r = localY / cell;

                if (c >= 0 && c < cols) {
                    int idx = r * cols + c;
                    if (idx >= 0 && idx < icons.size()) {
                        editingIconKey = icons.get(idx);

                        // If we're editing an existing counter, persist immediately
                        if (editingCounterKey != null) {
                            String display = getCounterDisplayName(stack, editingCounterKey);
                            sendCounterMetaUpdate(editingCounterKey, display, editingIconKey);
                        }

                        refreshIconButton();
                        return true;
                    }
                }
            }

            // scrollbar drag start
            if (mx >= iconBarX && mx < iconBarX + iconBarW && my >= iconBarY && my < iconBarY + iconBarH) {
                draggingIconBar = true;
                iconBarDragOffset = (int) my - iconBarY;
                return true;
            }
        }

        return super.mouseClicked(click, bl);
    }

    private void createCounterNow() {
        if (counterNameField == null) return;

        String name = counterNameField.getValue().trim();
        if (name.isBlank()) return;

        // always create a NEW counter from field
        String key = normalizeCounterKey(name);
        if (key.isBlank()) return;

        CompoundTag counters = getOrCreateCounters(stack);

        // ensure unique
        String base = key;
        int n = 2;
        while (counters.contains(key)) {
            key = base + "_" + n++;
        }

        setCounter(stack, key, 1);
        setCounterDisplayName(stack, key, name);
        setCounterIcon(stack, key, editingIconKey);

        sendCounterValueUpdate(key, 1);
        sendCounterMetaUpdate(key, name, editingIconKey);

        // select + switch editor into "editing existing" mode
        selectedCounterKey = key;
        beginEditCounter(key);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent click) {
        draggingLegalityBar = false;
        draggingIconBar = false;
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent click, double dx, double dy) {
        if (infoOpen && draggingLegalityBar) {
            // move thumb; convert thumb y -> scroll
            int thumbMinY = legAreaY;
            int thumbMaxY = legAreaY + legAreaH - legBarH;

            int desiredThumbY = (int) click.y() - legalityBarDragOffset;
            desiredThumbY = clampInt(desiredThumbY, thumbMinY, thumbMaxY);

            float track = Math.max(1, (thumbMaxY - thumbMinY));
            float t = (desiredThumbY - thumbMinY) / track;

            legalityScroll = t * getLegalityMaxScroll();
            legalityScroll = clampFloat(legalityScroll, 0f, getLegalityMaxScroll());
            return true;
        }
        if (infoOpen && tab == Tab.COUNTERS && draggingIconBar) {
            int thumbMinY = iconAreaY;
            int thumbMaxY = iconAreaY + iconAreaH - iconBarH;

            int desiredThumbY = (int) click.y() - iconBarDragOffset;
            desiredThumbY = clampInt(desiredThumbY, thumbMinY, thumbMaxY);

            // recompute maxScroll the same way as draw
            List<String> icons = new ArrayList<>();
            icons.add("none");
            icons.addAll(loadCounterIconKeys());

            int rows = (int) Math.ceil(icons.size() / (double) ICON_COLS);
            int contentH = rows * ICON_CELL;
            int maxScroll = Math.max(0, contentH - iconAreaH);

            float track = Math.max(1, (thumbMaxY - thumbMinY));
            float t = (desiredThumbY - thumbMinY) / track;

            iconScroll = clampInt((int) (t * maxScroll), 0, maxScroll);
            return true;
        }
        return super.mouseDragged(click, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {

        if (hudHoverCounterKey != null
                && mouseX >= hudX && mouseX <= hudX + hudW
                && mouseY >= hudY && mouseY <= hudY + hudH) {

            String k = hudHoverCounterKey;

            CompoundTag counters = getOrCreateCounters(stack);
            int cur = counters.getInt(k).orElse(0);

            int step = isShiftDown() ? 5 : 1;
            int dir = verticalAmount > 0 ? 1 : -1;

            int next = cur + dir * step;

            setCounter(stack, k, next);
            sendCounterValueUpdate(k, next);
            return true;
        }

        if (infoOpen && tab == Tab.COUNTERS) {
            // if scrolling over the counters list area, modify counter instead of scrolling legality
            // (weâ€™ll use the same panel geometry as drawInfoPanel)
            int panelX = 8;
            int panelY = 8 + INFO_SIZE + 6;
            int panelW = 190;
            int panelH = this.height - panelY - 8;

            int x = panelX + 10;
            int y = panelY + 10 + 18 + 8; // same offset used in drawInfoPanel

            int listX = x;
            int listY = y;
            int listW = panelW - 20;
            int listH = (panelY + panelH - 10) - listY;

            // If scrolling over icon picker, scroll icons (not counters)
            if (counterEditorOpen
                    && mouseX >= iconAreaX && mouseX < iconAreaX + iconAreaW
                    && mouseY >= iconAreaY && mouseY < iconAreaY + iconAreaH) {

                List<String> icons = new ArrayList<>();
                icons.add("none");
                icons.addAll(loadCounterIconKeys());

                int rows = (int) Math.ceil(icons.size() / (double) ICON_COLS);
                int contentH = rows * ICON_CELL;
                int maxScroll = Math.max(0, contentH - iconAreaH);

                iconScroll = clampInt(iconScroll + (int) (-verticalAmount * ICON_CELL * 2), 0, maxScroll);
                return true;
            }

            if (mouseX >= listX && mouseX <= listX + listW && mouseY >= listY && mouseY <= listY + listH) {
                // determine which row
                CompoundTag counters = getOrCreateCounters(stack);
                List<String> keys = new ArrayList<>(counters.keySet());
                keys.sort(String::compareToIgnoreCase);
                if (keys.isEmpty()) return true;

                int row = (int)((mouseY - listY + countersScroll) / CTR_ROW_H);
                row = clampInt(row, 0, keys.size() - 1);

                String k = keys.get(row);

                int step = isShiftDown() ? 5 : 1;
                int dir = verticalAmount > 0 ? 1 : -1; // up = +, down = -
                int cur = counters.getInt(k).orElse(0);

                setCounter(stack, k, cur + dir * step);
                ClientPlayNetworking.send(new SetCounterValuePayload(handSlot, k, cur + dir * step));
                return true;
            }

            // otherwise scroll the list itself
            int maxScroll = Math.max(0, (getOrCreateCounters(stack).size() * CTR_ROW_H) - listH);
            countersScroll = clampInt(countersScroll + (int)(-verticalAmount * CTR_ROW_H * 3), 0, maxScroll);
            return true;
        }

        // existing legality scroll logic
        if (infoOpen && isOverLegalityArea(mouseX, mouseY)) {
            float delta = (float) (-verticalAmount * (LEG_ROW_H * 3));
            legalityScroll = clampFloat(legalityScroll + delta, 0f, getLegalityMaxScroll());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, 0, verticalAmount);
    }

    private boolean isShiftDown() {
        var win = net.minecraft.client.Minecraft.getInstance().getWindow();
        long h = win.handle();
        return org.lwjgl.glfw.GLFW.glfwGetKey(h, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT) == org.lwjgl.glfw.GLFW.GLFW_PRESS
                || org.lwjgl.glfw.GLFW.glfwGetKey(h, org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
    }

    private boolean handleLegalityScroll(double mouseX, double mouseY, double verticalAmount) {
        if (infoOpen && isOverLegalityArea(mouseX, mouseY)) {
            float delta = (float) (-verticalAmount * (LEG_ROW_H * 3));
            float max = getLegalityMaxScroll(
                    // best effort: view height comes from cached area, rows from current list size if available
                    // if you want exact max here, store rows.size() into a field each draw
                    this.lastLegalityRowCount,
                    legAreaH
            );
            legalityScroll = clampFloat(legalityScroll + delta, 0f, max);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, 0, verticalAmount);
    }

    @Override
    public boolean charTyped(CharacterEvent input) {
        // If we're typing in the counter name field, swallow everything into the field.
        if (counterNameField != null && counterNameField.isFocused()) {
            if (counterNameField.charTyped(input)) return true;
            return true; // swallow so movement keys don't leak
        }

        // Otherwise let focused element handle it first
        if (this.getFocused() != null && this.getFocused().charTyped(input)) {
            return true;
        }

        return super.charTyped(input);
    }

    @Override
    public boolean keyPressed(KeyEvent key) {
        int kc = kiKeyCode(key);

        if (kc == GLFW.GLFW_KEY_ESCAPE) {
            this.onClose();
            return true;
        }

        // While typing, let the text field eat the key and STOP the rest of the screen/game hotkeys.
        if (counterNameField != null && counterNameField.isFocused()) {
            if (counterNameField.keyPressed(key)) return true;
            return true;
        }

        // Let focused widgets consume key first
        if (this.getFocused() != null && this.getFocused().keyPressed(key)) {
            return true;
        }

        // TAB = toggle info panel
        if (kc == GLFW.GLFW_KEY_TAB) {
            toggleInfoPanel();
            return true;
        }

        // R = flip
        if (isDoubleFaced() && kc == GLFW.GLFW_KEY_R) {
            startFlip(faceIndex ^ 1);
            return true;
        }

        // E = rotate right
        if (kc == GLFW.GLFW_KEY_E) {
            rotateRight();
            return true;
        }

        return super.keyPressed(key);
    }



    private boolean isMouseOverCard(double mx, double my) {
        return mx >= cardX && mx <= cardX + cardW
                && my >= cardY && my <= cardY + cardH;
    }

    private boolean isMouseOverFlip(double mx, double my) {
        return mx >= flipX && mx <= (flipX + flipW)
                && my >= flipY && my <= (flipY + flipH);
    }

    private boolean isMouseOverRotate(double mx, double my) {
        return mx >= rotX && mx <= (rotX + rotW)
                && my >= rotY && my <= (rotY + rotH);
    }

    private boolean isOverLegalityArea(double mx, double my) {
        return mx >= legAreaX && mx <= legAreaX + legAreaW
                && my >= legAreaY && my <= legAreaY + legAreaH;
    }

    private boolean isOverLegalityBar(double mx, double my) {
        return mx >= legBarX && mx <= legBarX + legBarW
                && my >= legBarY && my <= legBarY + legBarH;
    }

    // ------------------------------------------------------------
    // Face switching + animation kickoff
    // ------------------------------------------------------------

    private boolean isDoubleFaced() {
        return faceCount >= 2;
    }

    private void startFlip(int nextFace) {
        if (faceCount <= 1) return;
        if (flipping) return;

        int next = Math.max(0, Math.min(nextFace, faceCount - 1));
        if (next == faceIndex) return;

        CardArtManager.getOrRequestFace(stack, faceIndex);
        CardArtManager.getOrRequestFace(stack, next);

        flipping = true;
        flipProgress = 0f;
        flipSwapped = false;

        flipFromFace = faceIndex;
        flipToFace = next;
    }

    @Override
    public boolean mtgcard$flipHoveredCardFace(Minecraft client) {
        if (!isDoubleFaced()) return false;
        startFlip(faceIndex ^ 1);
        return true;
    }

    private void rotateRight() {
        if (flipping) return;
        rotSteps = (rotSteps + 1) & 3;
        writeRotation(stack, rotSteps);
    }

    // ------------------------------------------------------------
    // Render
    // ------------------------------------------------------------

    @Override
    public void render(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, this.width, this.height, 0xB0000000);

        int renderFace = getShownFaceIndex();

        Identifier texId;
        int texW;
        int texH;

        CardArtManager.TextureRef texRef = null;
        texRef = CardArtManager.getOrRequestFace(stack, renderFace);
        if (texRef == null || texRef.id() == null) {
            ctx.drawCenteredString(this.font, Component.literal("Loading card art..."),
                    this.width / 2, this.height / 2, 0xFFFFFF);
            super.render(ctx, mouseX, mouseY, delta);
            return;
        }

        if (texRef == null || texRef.id() == null) {
            ctx.drawCenteredString(
                    this.font,
                    Component.literal("Loading card art..."),
                    this.width / 2,
                    this.height / 2,
                    0xFFFFFF
            );
            super.render(ctx, mouseX, mouseY, delta);
            return;
        }

        // fit card
        float aspect = (float) texRef.texW() / (float) texRef.texH();
        int margin = 24;
        int maxW = this.width - margin * 2;
        int maxH = this.height - margin * 2;

        int drawW = maxW;
        int drawH = (int) Math.round(maxW / aspect);
        if (drawH > maxH) {
            drawH = maxH;
            drawW = (int) Math.round(maxH * aspect);
        }

        int x = (this.width - drawW) / 2;
        int y = (this.height - drawH) / 2;

        // flip transform values
        float sx = 1f;
        float sy = 1f;

        if (flipping) {
            float t = flipProgress;
            float tEase = t * t * (3f - 2f * t);

            if (tEase < 0.5f) {
                float a = tEase / 0.5f;
                sx = 1f - a;
            } else {
                float a = (tEase - 0.5f) / 0.5f;
                sx = a;
            }
            sx = Math.max(0.03f, sx);

            float edge = clamp01((0.35f - sx) / 0.35f);
            sy = 1f - edge * 0.05f;
        }

        // rotated bounds
        float cx = x + drawW / 2f;
        float cy = y + drawH / 2f;

        boolean swapWH = (rotSteps & 1) == 1;
        int rotCardW = swapWH ? drawH : drawW;
        int rotCardH = swapWH ? drawW : drawH;

        int rx = (int) Math.round(cx - rotCardW / 2f);
        int ry = (int) Math.round(cy - rotCardH / 2f);

        cardX = rx;
        cardY = ry;
        cardW = rotCardW;
        cardH = rotCardH;

        // shadow
        int shadowOffsetX = 6;
        int shadowOffsetY = 8;
        int shadowAlpha = 0x55;
        int shadowColor = (shadowAlpha << 24);
        ctx.fill(cardX + shadowOffsetX, cardY + shadowOffsetY,
                cardX + cardW + shadowOffsetX, cardY + cardH + shadowOffsetY,
                shadowColor);

        // apply transform
        var m = ctx.pose();
        m.pushMatrix();
        m.translate(cx, cy);

        float radians = (float) (rotSteps * (Math.PI / 2.0));
        m.rotate(radians);

        m.scale(sx, sy);
        m.translate(-cx, -cy);

        ctx.blit(
                RenderPipelines.GUI_TEXTURED,
                texRef.id(),
                x, y,
                0f, 0f,
                drawW, drawH,
                drawW, drawH
        );

        if (com.spider.mtgcard.client.render.CardFoilUtil.isFoil(stack)) {
            var sweep = com.spider.mtgcard.client.render.CardFoilUtil.computeSweep(System.currentTimeMillis(), drawW);
            if (sweep != null) {
                ctx.fill(
                        x + sweep.drawU(),
                        y,
                        x + sweep.drawU() + sweep.clipW(),
                        y + drawH,
                        com.spider.mtgcard.client.render.CardFoilUtil.guiShimmerColor(1f)
                );
            }
        }

        m.popMatrix();

        // screen-space UI
        drawInfoButton(ctx, mouseX, mouseY);

        // ALWAYS show mini counters HUD on right
        drawCountersHud(ctx, mouseX, mouseY);

        if (infoOpen) {
            currentScryfallId = readScryfallIdForFace(stack, renderFace);
            drawInfoPanel(ctx, mouseX, mouseY);
        }


        // NOW draw the info button (screen-space)
        drawInfoButton(ctx, mouseX, mouseY);

        // keep current id in sync when panel is open (so panel matches what you see)
        if (infoOpen) {
            currentScryfallId = readScryfallIdForFace(stack, renderFace);
        }

        // draw panel when open
        if (infoOpen) {
            drawInfoPanel(ctx, mouseX, mouseY);
        }

        // face indicator
        if (faceCount > 1) {
            String s = (renderFace + 1) + "/" + faceCount;
            ctx.drawString(this.font, s, cardX + 4, cardY + 4, 0xFFFFFF);
        }

        // buttons
        drawRotateButtonClean(ctx, mouseX, mouseY);
        drawHideButton(ctx, mouseX, mouseY);

        if (isDoubleFaced()) {
            drawFlipButtonClean(ctx, mouseX, mouseY);
        }

        super.render(ctx, mouseX, mouseY, delta);
    }

    private void toggleHidden() {
        hidden = !hidden;
        writeHidden(stack, hidden);
        sendHiddenUpdate(hidden);
    }

    private void sendHiddenUpdate(boolean hidden) {
        if (isDisplayMode()) {
            ClientPlayNetworking.send(new com.spider.mtgcard.net.payload.CardDisplayPayloads.DisplaySetHiddenC2S(displayEntityId, hidden));
        } else {
            ClientPlayNetworking.send(new com.spider.mtgcard.net.payload.SetHiddenPayload(handSlot, hidden));
        }
    }


    private int hideX, hideY, hideW, hideH;
    private static final int HIDE_W = 54;
    private static final int HIDE_H = 18;


    private void drawHideButton(GuiGraphics ctx, int mouseX, int mouseY) {
        int bx = this.width - 8 - HIDE_W;
        int by = 8;

        hideX = bx; hideY = by; hideW = HIDE_W; hideH = HIDE_H;

        boolean hover = mouseX >= hideX && mouseX <= hideX + hideW
                && mouseY >= hideY && mouseY <= hideY + hideH;

        int border = hover ? 0xFF70E0FF : 0xFF404040;
        int bg     = hover ? 0xCC1A1A1A : 0xAA101010;

        ctx.fill(hideX - 1, hideY - 1, hideX + hideW + 1, hideY + hideH + 1, border);
        ctx.fill(hideX, hideY, hideX + hideW, hideY + hideH, bg);

        String label = hidden ? "Facedown: ON" : "Facedown: OFF";
        int tw = this.font.width(label);
        int tx = hideX + (hideW - tw) / 2;
        int ty = hideY + (hideH - this.font.lineHeight) / 2;

        ctx.drawString(this.font, label, tx, ty, 0xFFFFFFFF, false);

        if (hover) {
            ctx.setTooltipForNextFrame(this.font,
                    Component.literal(hidden ? "Reveal card face" : "Hide as facedown"),
                    mouseX, mouseY);
        }
    }


    private void drawInfoButton(GuiGraphics ctx, int mouseX, int mouseY) {
        infoX = 8;
        infoY = 8;
        infoW = INFO_SIZE;
        infoH = INFO_SIZE;

        boolean infoHover = isMouseOverInfo(mouseX, mouseY);
        int border = infoHover ? 0xFF70E0FF : 0xFF404040;
        int bg = infoOpen ? 0xCC1A1A1A : 0xAA101010;

        ctx.fill(infoX - 1, infoY - 1, infoX + INFO_SIZE + 1, infoY + INFO_SIZE + 1, border);
        ctx.fill(infoX, infoY, infoX + INFO_SIZE, infoY + INFO_SIZE, bg);

        ctx.drawString(this.font, "i",
                infoX + (INFO_SIZE - this.font.width("i")) / 2,
                infoY + (INFO_SIZE - this.font.lineHeight) / 2,
                0xFFFFFFFF, false);

        if (infoHover) {
            ctx.setTooltipForNextFrame(this.font,
                    Component.literal(infoOpen ? "Hide info (Tab)" : "Show info (Tab)"),
                    mouseX, mouseY);
        }
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }

    private void drawFlipButtonClean(GuiGraphics ctx, int mouseX, int mouseY) {
        int bx = cardX - BTN_PAD - FLIP_SIZE;
        int by = cardY + (cardH - FLIP_SIZE) / 2;

        bx = Math.max(8, bx);
        by = Math.max(8, Math.min(by, this.height - FLIP_SIZE - 8));

        flipX = bx;
        flipY = by;
        flipW = FLIP_SIZE;
        flipH = FLIP_SIZE;

        boolean hover = isMouseOverFlip(mouseX, mouseY);

        int border = hover ? 0xFFFFE070 : 0xFF404040;
        int bg = hover ? 0xCC1A1A1A : 0xAA101010;

        float cxx = bx + FLIP_SIZE / 2f;
        float cyy = by + FLIP_SIZE / 2f;
        float radians = (float) (rotSteps * (Math.PI / 2.0));

        var m = ctx.pose();
        m.pushMatrix();
        m.translate(cxx, cyy);
        m.rotate(radians);
        m.translate(-cxx, -cyy);

        ctx.fill(bx - 1, by - 1, bx + FLIP_SIZE + 1, by + FLIP_SIZE + 1, border);
        ctx.fill(bx, by, bx + FLIP_SIZE, by + FLIP_SIZE, bg);

        String glyph = (faceIndex == 0) ? FLIP_GLYPH_FRONT : FLIP_GLYPH_BACK;
        float scale = 2.6f;
        int tw = this.font.width(glyph);
        int th = this.font.lineHeight;
        float drawX = cxx - (tw * scale) / 2f;
        float drawY = cyy - (th * scale) / 2f;

        m.pushMatrix();
        m.translate(drawX, drawY);
        m.scale(scale, scale);
        ctx.drawString(this.font, glyph, 0, 0, 0xFFFFE070, false);
        m.popMatrix();
        m.popMatrix();

        if (hover) {
            ctx.setTooltipForNextFrame(this.font, Component.literal("Flip card (R)"), mouseX, mouseY);
        }
    }

    private void drawRotateButtonClean(GuiGraphics ctx, int mouseX, int mouseY) {
        int bx = cardX - BTN_PAD - FLIP_SIZE;
        int by = cardY + (cardH - FLIP_SIZE) / 2 - (FLIP_SIZE + BTN_GAP);

        bx = Math.max(8, bx);
        by = Math.max(8, Math.min(by, this.height - FLIP_SIZE - 8));

        rotX = bx;
        rotY = by;
        rotW = FLIP_SIZE;
        rotH = FLIP_SIZE;

        boolean hover = isMouseOverRotate(mouseX, mouseY);

        int border = hover ? 0xFF70E0FF : 0xFF404040;
        int bg = hover ? 0xCC1A1A1A : 0xAA101010;

        ctx.fill(bx - 1, by - 1, bx + FLIP_SIZE + 1, by + FLIP_SIZE + 1, border);
        ctx.fill(bx, by, bx + FLIP_SIZE, by + FLIP_SIZE, bg);

        int tw = this.font.width(ROTATE_GLYPH);
        int th = this.font.lineHeight;
        float cxx = bx + FLIP_SIZE / 2f;
        float cyy = by + FLIP_SIZE / 2f;

        var m = ctx.pose();
        m.pushMatrix();
        m.translate(cxx, cyy);
        m.scale(2.4f, 2.4f);
        ctx.drawString(this.font, ROTATE_GLYPH, -tw / 2, -th / 2, 0xFF70E0FF, false);
        m.popMatrix();

        if (hover) {
            ctx.setTooltipForNextFrame(this.font, Component.literal("Rotate 90\u00B0 (E)"), mouseX, mouseY);
        }
    }

    private void drawFlipButton(GuiGraphics ctx, int mouseX, int mouseY) {
        int bx = cardX - BTN_PAD - FLIP_SIZE;
        int by = cardY + (cardH - FLIP_SIZE) / 2;

        bx = Math.max(8, bx);
        by = Math.max(8, Math.min(by, this.height - FLIP_SIZE - 8));

        flipX = bx; flipY = by; flipW = FLIP_SIZE; flipH = FLIP_SIZE;

        boolean hover = isMouseOverFlip(mouseX, mouseY);

        int border = hover ? 0xFFFFE070 : 0xFF404040;
        int bg     = hover ? 0xCC1A1A1A : 0xAA101010;

        float cxx = bx + FLIP_SIZE / 2f;
        float cyy = by + FLIP_SIZE / 2f;

        float radians = (float) (rotSteps * (Math.PI / 2.0));

        var m = ctx.pose();
        m.pushMatrix();
        m.translate(cxx, cyy);
        m.rotate(radians);
        m.translate(-cxx, -cyy);

        ctx.fill(bx - 1, by - 1, bx + FLIP_SIZE + 1, by + FLIP_SIZE + 1, border);
        ctx.fill(bx, by, bx + FLIP_SIZE, by + FLIP_SIZE, bg);

        String glyph = (faceIndex == 0) ? "â†»" : "â†º";
        float scale = 2.6f;

        int tw = this.font.width(glyph);
        int th = this.font.lineHeight;

        float drawX = cxx - (tw * scale) / 2f;
        float drawY = cyy - (th * scale) / 2f;

        m.pushMatrix();
        m.translate(drawX, drawY);
        m.scale(scale, scale);
        ctx.drawString(this.font, glyph, 0, 0, 0xFFFFE070, false);
        m.popMatrix();

        m.popMatrix();

        if (hover) {
            ctx.setTooltipForNextFrame(this.font, Component.literal("Flip card (R)"), mouseX, mouseY);
        }
    }

    private void drawRotateButton(GuiGraphics ctx, int mouseX, int mouseY) {
        int bx = cardX - BTN_PAD - FLIP_SIZE;
        int by = cardY + (cardH - FLIP_SIZE) / 2 - (FLIP_SIZE + BTN_GAP);

        bx = Math.max(8, bx);
        by = Math.max(8, Math.min(by, this.height - FLIP_SIZE - 8));

        rotX = bx; rotY = by; rotW = FLIP_SIZE; rotH = FLIP_SIZE;

        boolean hover = isMouseOverRotate(mouseX, mouseY);

        int border = hover ? 0xFF70E0FF : 0xFF404040;
        int bg     = hover ? 0xCC1A1A1A : 0xAA101010;

        ctx.fill(bx - 1, by - 1, bx + FLIP_SIZE + 1, by + FLIP_SIZE + 1, border);
        ctx.fill(bx, by, bx + FLIP_SIZE, by + FLIP_SIZE, bg);

        String glyph = "âŸ³";
        float scale = 2.4f;

        int tw = this.font.width(glyph);
        int th = this.font.lineHeight;

        float cxx = bx + FLIP_SIZE / 2f;
        float cyy = by + FLIP_SIZE / 2f;

        var m = ctx.pose();
        m.pushMatrix();
        m.translate(cxx, cyy);
        m.scale(scale, scale);
        ctx.drawString(this.font, glyph, -tw / 2, -th / 2, 0xFF70E0FF, false);
        m.popMatrix();

        if (hover) {
            ctx.setTooltipForNextFrame(this.font, Component.literal("Rotate 90Â° (E)"), mouseX, mouseY);
        }
    }

    // ---------------- INFO PANEL ----------------

    private void drawInfoPanel(GuiGraphics ctx, int mouseX, int mouseY) {
            int panelX = 8;
            int panelY = 8 + INFO_SIZE + 6;
            int panelW = 190;
            int panelH = this.height - panelY - 8;

            ctx.fill(panelX - 1, panelY - 1, panelX + panelW + 1, panelY + panelH + 1, 0xFF303030);
            ctx.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xCC101010);

            int x = panelX + 10;
            int y = panelY + 10;

            // --- Tab header line ---
            // (your actual tab buttons are widgets, but we need to avoid content overlapping them)
            y += 18 + 8; // space for tab buttons + padding

            switch (tab) {
                case INFO -> drawInfoTab(ctx, x, y, panelW, panelY, panelH, mouseX, mouseY);
                case COUNTERS -> drawCountersTab(ctx, x, y, panelW, panelY, panelH, mouseX, mouseY);
            }
    }

    private void drawInfoTab(GuiGraphics ctx, int x, int y, int panelW, int panelY, int panelH, int mouseX, int mouseY) {
        // Title
        ctx.drawString(this.font, Component.literal("Card Info"), x, y, 0xFFFFFFFF);
        y += 16;

        // NBT card header (name + set line)
        CompoundTag meta = getMeta(stack);

        String name = meta.getString("name").orElse("Unknown Card");
        String set = meta.getString("set").orElse("â€”").toUpperCase();
        String rarity = meta.getString("rarity").orElse("â€”");
        String typeLine = meta.getString("type_line").orElse("â€”");
        String collector = meta.getString("collector_number").orElse("â€”");

        // Name (trim if too wide)
        name = trimToWidth(name, panelW - 20);
        ctx.drawString(this.font, Component.literal(name), x, y, 0xFFFFFFFF);
        y += 12;

        String sub = set + " â€¢ " + rarity + " â€¢ #" + collector;
        sub = trimToWidth(sub, panelW - 20);
        ctx.drawString(this.font, Component.literal(sub), x, y, 0xFFAAAAAA);
        y += 12;

        // Type line
        typeLine = trimToWidth(typeLine, panelW - 20);
        ctx.drawString(this.font, Component.literal(typeLine), x, y, 0xFFAAAAAA);
        y += 14;

        ScryfallInfoManager.Entry localEntry = ScryfallInfoManager.Entry.fromLocalMeta(meta);
        ScryfallInfoManager.Entry remoteEntry = (shouldUseRemoteInfo() && currentScryfallId != null)
                ? ScryfallInfoManager.getCached(currentScryfallId)
                : null;
        ScryfallInfoManager.Entry e = remoteEntry == null ? localEntry : remoteEntry;

        PriceResult used = (e == null) ? new PriceResult("0", PriceTier.NORMAL) : resolvePrice(e);

        String basis = MtgcardConfig.get().Price_Basis;
        if (basis == null) basis = "USD";
        basis = basis.trim().toUpperCase();

        int usedKeyColor = switch (used.tier()) {
            case FOIL   -> PRICE_FOIL;   // Magenta
            case ETCHED -> PRICE_ETCHED; // Cyan
            default     -> 0xFFDDDDDD;   // Normal grey
        };

        // Current prices header + configured item + rounded value
        Component header = Component.literal("Current prices");
        ctx.drawString(this.font, header, x, y, 0xFF70E0FF);

        int headerW = this.font.width(header);
        int iconX = x + headerW + 6;
        int iconY = y - 5;

        if (e != null) {
            ItemStack priceStack = getConfiguredPriceItem();
            PriceResult result = resolvePrice(e);
            int amount = roundPriceToWhole(result.value());

            ctx.renderItem(priceStack, iconX, iconY);

            // Always keep the item amount white; only color the matching currency row(s)
            ctx.drawString(
                    this.font,
                    String.valueOf(amount),
                    iconX + 18,
                    y,
                    PRICE_NORMAL
            );
        }

        y += 14;

        if (e == null) {
            ctx.drawString(this.font, Component.literal("Loading..."), x, y, 0xFFAAAAAA);
            y += 12;
        } else {
            int baseKey = 0xFFDDDDDD;

            boolean basisUSD = basis.equals("USD");
            boolean basisEUR = basis.equals("EUR");
            boolean basisTIX = basis.equals("TIX");

// Color ONLY the line that was actually used
            int usdCol       = (basisUSD && used.tier() == PriceTier.NORMAL) ? usedKeyColor : baseKey;
            int usdFoilCol   = (basisUSD && used.tier() == PriceTier.FOIL)   ? usedKeyColor : baseKey;
            int usdEtchedCol = (basisUSD && used.tier() == PriceTier.ETCHED) ? usedKeyColor : baseKey;

            int eurCol     = (basisEUR && used.tier() == PriceTier.NORMAL) ? usedKeyColor : baseKey;
            int eurFoilCol = (basisEUR && used.tier() == PriceTier.FOIL)   ? usedKeyColor : baseKey;

            int tixCol = basisTIX ? usedKeyColor : baseKey; // TIX is the only row for that basis

            y = drawKv(ctx, x, y, "USD", "$" + e.usd, usdCol, 0xFFFFFFFF);
            y = drawKv(ctx, x, y, "USD Foil", "$" + e.usdFoil, usdFoilCol, 0xFFFFFFFF);
            y = drawKv(ctx, x, y, "USD Etched", "$" + e.usdEtched, usdEtchedCol, 0xFFFFFFFF);

            y = drawKv(ctx, x, y, "EUR", "â‚¬" + e.eur, eurCol, 0xFFFFFFFF);
            y = drawKv(ctx, x, y, "EUR Foil", "â‚¬" + e.eurFoil, eurFoilCol, 0xFFFFFFFF);

            y = drawKv(ctx, x, y, "TIX", e.tix, tixCol, 0xFFFFFFFF);

        }

        y += 8;

        ctx.drawString(this.font, Component.literal("Format legality"), x, y, 0xFF70E0FF);
        y += 14;

        if (e == null) {
            ctx.drawString(this.font, Component.literal("Loading..."), x, y, 0xFFAAAAAA);
            return;
        }

        // Build legality rows
        List<Map.Entry<String, String>> rows = new ArrayList<>(e.legalities.entrySet());
        rows.sort(Comparator
                .comparingInt((Map.Entry<String, String> it) -> legalityPriority(it.getKey()))
                .thenComparing(Map.Entry::getKey));

        lastLegalityRowCount = rows.size();
        if (rows.isEmpty()) {
            ctx.drawString(this.font, Component.literal("No legalities listed."), x, y, 0xFFAAAAAA);
            return;
        }

        // Scroll area rect (inside panel)
        int listX = x;
        int listY = y;
        int listW = panelW - 20;     // x already includes padding
        int listH = (panelY + panelH - 10) - listY;

        // reserve scrollbar space
        int contentW = listW - (SCROLLBAR_W + 4);

        legAreaX = listX;
        legAreaY = listY;
        legAreaW = listW;
        legAreaH = Math.max(0, listH);

        float maxScroll = getLegalityMaxScroll(rows.size(), legAreaH);
        legalityScroll = clampFloat(legalityScroll, 0f, maxScroll);

        // clip to the list area
        ctx.enableScissor(listX, listY, listX + contentW, listY + listH);

        int startY = listY - (int) legalityScroll;
        for (int i = 0; i < rows.size(); i++) {
            int rowY = startY + i * LEG_ROW_H;
            if (rowY + LEG_ROW_H < listY) continue;
            if (rowY > listY + listH) break;

            String key = rows.get(i).getKey();
            String status = rows.get(i).getValue();

            String label = prettyFormatName(key);
            label = trimToWidth(label, 84); // keep aligned with value column

            ctx.drawString(this.font, label + ":", listX, rowY, 0xFFDDDDDD, false);
            ctx.drawString(this.font, sanitizeUiText(prettyLegality(status)), listX + 88, rowY, legalityColor(status), false);
        }

        ctx.disableScissor();

        // scrollbar
        drawLegalityScrollbar(ctx, rows.size(), listX + contentW + 4, listY, SCROLLBAR_W, listH);
    }

    private void drawCountersTab(GuiGraphics ctx, int x, int y, int panelW, int panelY, int panelH, int mouseX, int mouseY) {
        // Title
        ctx.drawString(this.font, Component.literal("Counters"), x, y, 0xFF70E0FF);
        y += 16; // move below title

        // Load counters map from NBT
        CompoundTag counters = getOrCreateCounters(stack);

        // Build sorted list
        List<String> keys = new ArrayList<>(counters.keySet());
        keys.sort(String::compareToIgnoreCase);

        listMinusRects.clear();
        listPlusRects.clear();

        // ---- layout ----
        int listX = x;
        int listY = y;
        int listW = panelW - 20;

        // Leave space at bottom for icon grid + textfield + buttons
        int editorReserve = 58 /*textfield+buttons*/ + 44 /*icon grid*/ + 8;
        int listH = (panelY + panelH - 10) - listY - editorReserve;
        if (listH < CTR_ROW_H) listH = CTR_ROW_H;

        // Empty state (still draw editor below)
        if (keys.isEmpty()) {
            ctx.drawString(this.font, Component.literal("No counters yet."), listX, listY, 0xFFAAAAAA);
            ctx.drawString(this.font, Component.literal("Type a name, choose an icon, then Create."), listX, listY + 12, 0xFF777777);

            drawCountersEditorArea(ctx, panelY, panelH, x, mouseX, mouseY);
            ctx.drawString(this.font, Component.literal("Tip: Shift for +/-5"), x, panelY + panelH - 18, 0xFF777777);
            return;
        }

        // ---- list scissor (ONLY list) ----
        ctx.enableScissor(listX, listY, listX + listW, listY + listH);

        int maxScroll = Math.max(0, (keys.size() * CTR_ROW_H) - listH);
        countersScroll = clampInt(countersScroll, 0, maxScroll);

        int start = Math.max(0, countersScroll / CTR_ROW_H);
        int maxVisible = Math.max(1, listH / CTR_ROW_H + 2);

        for (int i = start; i < Math.min(keys.size(), start + maxVisible); i++) {
            int rowY = listY + (i * CTR_ROW_H) - countersScroll;

            String k = keys.get(i);
            int v = counters.getInt(k).orElse(0);
            String val = String.valueOf(v);

            boolean hover = mouseX >= listX && mouseX <= listX + listW
                    && mouseY >= rowY && mouseY <= rowY + CTR_ROW_H;

            boolean sel = (selectedCounterKey != null && selectedCounterKey.equals(k));

            // Highlight (behind content)
            if (sel) ctx.fill(listX, rowY, listX + listW, rowY + CTR_ROW_H, 0x663BE36A);
            else if (hover) ctx.fill(listX, rowY, listX + listW, rowY + CTR_ROW_H, 0x33202020);

            // --- icon ---
            String iconKey = getCounterIcon(stack, k);
            int iconSize = 12;
            int iconX = listX;
            int iconY = rowY + 1;

            if (iconKey == null || iconKey.equals("none")) {
                // dash placeholder
                ctx.drawString(this.font, "â€”", iconX + 2, rowY + 2, 0xFF777777, false);
            } else {
                Identifier tex = Identifier.fromNamespaceAndPath("mtgcard", "textures/gui/counters/" + iconKey + ".png");
                drawIconFit(ctx, tex, iconX, iconY, iconSize, iconSize);

            }

            // --- remember: name starts after icon ---
            int nameX = listX + iconSize + 4;

            String display = getCounterDisplayName(stack, k);
            if (display == null || display.isBlank()) display = toTitle(k.replace('_', ' '));

            // leave room for the value on the right
            int valueW = this.font.width(val);
            int reservedRight = (ADJ_BTN + ADJ_GAP) + valueW + (ADJ_GAP + ADJ_BTN); // [-] val [+]
            int maxNameW = Math.max(40, (listW - (iconSize + 4) - reservedRight - 4));

            display = trimToWidth(display, maxNameW);

            ctx.drawString(this.font, display, nameX, rowY + 2, 0xFFDDDDDD, false);
            int btnY = rowY + (CTR_ROW_H - ADJ_BTN) / 2;

            // right edge alignment
            int plusX  = listX + listW - ADJ_BTN;
            int valX   = plusX - ADJ_GAP - valueW;
            int minusX = valX - ADJ_GAP - ADJ_BTN;

            boolean hoverMinus = mouseX >= minusX && mouseX < minusX + ADJ_BTN && mouseY >= btnY && mouseY < btnY + ADJ_BTN;
            boolean hoverPlus  = mouseX >= plusX  && mouseX < plusX  + ADJ_BTN && mouseY >= btnY && mouseY < btnY + ADJ_BTN;

            drawMiniButton(ctx, minusX, btnY, ADJ_BTN, "-", hoverMinus);
            ctx.drawString(this.font, val, valX, rowY + 2, 0xFFFFFFFF, false);
            drawMiniButton(ctx, plusX,  btnY, ADJ_BTN, "+", hoverPlus);

            // store click rects for this key
            listMinusRects.put(k, new Rect(minusX, btnY, ADJ_BTN, ADJ_BTN));
            listPlusRects.put(k,  new Rect(plusX,  btnY, ADJ_BTN, ADJ_BTN));


            if (hover) {
                ctx.setTooltipForNextFrame(this.font,
                        Component.literal("Scroll: +/- 1  â€¢  Shift+Scroll: +/- 5  â€¢  Click: select"),
                        mouseX, mouseY);
            }
        }

        ctx.disableScissor();

        // ---- editor + icon grid (NOT scissored) ----
        drawCountersEditorArea(ctx, panelY, panelH, x, mouseX, mouseY);

        // Tip footer
        ctx.drawString(this.font, Component.literal("Tip: Shift for +/-5"), x, panelY + panelH - 18, 0xFF777777);
    }

    private static void editMeta(ItemStack st, java.util.function.Consumer<CompoundTag> mutator) {
        var comp = st.get(DataComponents.CUSTOM_DATA);
        CompoundTag root = (comp == null) ? new CompoundTag() : comp.copyTag();

        CompoundTag meta = root.getCompound("mtg_meta").orElseGet(CompoundTag::new);
        mutator.accept(meta);

        root.put("mtg_meta", meta);
        st.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
    }


    private void drawCountersEditorArea(GuiGraphics ctx, int panelY, int panelH, int x, int mouseX, int mouseY) {
        // editor layout (matches initCountersEditorWidgets)
        int panelX = 8;
        int panelW = 190;

        int editorTopY = panelY + panelH - 58; // where your TextField starts
        int iconTopY = editorTopY - 44;        // icon grid above text field

        // ----- Icon grid rect -----
        int areaX = panelX + 10;
        int areaY = iconTopY;
        int areaW = panelW - 20;
        int areaH = 40; // visible height for icons

        // background
        ctx.fill(areaX, areaY, areaX + areaW, areaY + areaH, 0x33101010);
        ctx.fill(areaX - 1, areaY - 1, areaX + areaW + 1, areaY + areaH + 1, 0x55303030);

        // get icon keys (include "none" as first entry)
        List<String> icons = new ArrayList<>();
        icons.add("none");
        icons.addAll(loadCounterIconKeys());

        // grid sizing
        int cell = ICON_CELL;
        int cols = ICON_COLS;
        int rows = (int) Math.ceil(icons.size() / (double) cols);

        int contentH = rows * cell;
        int maxScroll = Math.max(0, contentH - areaH);
        iconScroll = clampInt(iconScroll, 0, maxScroll);

        // reserve scrollbar space
        int contentW = areaW - (ICON_SCROLLBAR_W + 4);

        // save rects for mouse input
        iconAreaX = areaX;
        iconAreaY = areaY;
        iconAreaW = contentW;
        iconAreaH = areaH;

        // scissor icons
        ctx.enableScissor(areaX, areaY, areaX + contentW, areaY + areaH);

        int startY = areaY - iconScroll;
        for (int i = 0; i < icons.size(); i++) {
            int r = i / cols;
            int c = i % cols;

            int cx = areaX + c * cell;
            int cy = startY + r * cell;

            // skip if offscreen
            if (cy + cell < areaY) continue;
            if (cy > areaY + areaH) break;

            String key = icons.get(i);

            boolean selected = key.equals(editingIconKey);
            boolean hover = mouseX >= cx && mouseX < cx + cell && mouseY >= cy && mouseY < cy + cell;

            if (selected) ctx.fill(cx, cy, cx + cell, cy + cell, 0x553BE36A);
            else if (hover) ctx.fill(cx, cy, cx + cell, cy + cell, 0x33202020);

            // draw icon or "â€”" for none
            if (key.equals("none")) {
                String dash = "â€”";
                int tw = this.font.width(dash);
                ctx.drawString(this.font, dash,
                        cx + (cell - tw) / 2,
                        cy + (cell - this.font.lineHeight) / 2,
                        0xFFAAAAAA, false);
            } else {
                Identifier tex = Identifier.fromNamespaceAndPath("mtgcard", "textures/gui/counters/" + key + ".png");
                // Draw the full 16x16 (or whatever) texture into the cell with padding.
                int ix = cx + ICON_PAD;
                int iy = cy + ICON_PAD;
                int iw = cell - ICON_PAD * 2;
                int ih = cell - ICON_PAD * 2;

                drawIconFit(ctx, tex, ix, iy, iw, ih);
            }
        }

        ctx.disableScissor();

        // scrollbar (right side)
        int sbX = areaX + contentW + 4;
        int sbY = areaY;
        int sbW = ICON_SCROLLBAR_W;
        int sbH = areaH;

        drawIconScrollbar(ctx, sbX, sbY, sbW, sbH, contentH, areaH, maxScroll);
    }

    private void drawIconScrollbar(GuiGraphics ctx, int x, int y, int w, int h, int contentH, int viewH, int maxScroll) {
        // track
        ctx.fill(x, y, x + w, y + h, 0x55202020);

        iconBarX = x;
        iconBarW = w;

        if (maxScroll <= 0) {
            // no scrolling needed
            ctx.fill(x, y, x + w, y + h, 0x88404040);
            iconBarY = y;
            iconBarH = h;
            return;
        }

        float thumbHf = (viewH / (float) contentH) * viewH;
        int thumbH = clampInt((int) thumbHf, 10, h);
        int thumbMinY = y;
        int thumbMaxY = y + h - thumbH;

        float t = iconScroll / (float) maxScroll;
        int thumbY = thumbMinY + (int) ((thumbMaxY - thumbMinY) * t);

        int col = draggingIconBar ? 0xFF707070 : 0xFF505050;
        ctx.fill(x, thumbY, x + w, thumbY + thumbH, col);

        iconBarY = thumbY;
        iconBarH = thumbH;
    }

    private static CompoundTag getOrCreateCounters(ItemStack st) {
        var comp = st.get(DataComponents.CUSTOM_DATA);
        CompoundTag root = (comp == null) ? new CompoundTag() : comp.copyTag();
        CompoundTag meta = root.getCompound("mtg_meta").orElseGet(CompoundTag::new);

        CompoundTag counters = meta.getCompound("counters").orElseGet(CompoundTag::new);
        meta.put("counters", counters);
        root.put("mtg_meta", meta);

        // ensure it exists in stack
        st.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
        return counters;
    }

    private static void setCounter(ItemStack st, String key, int value) {
        var comp = st.get(DataComponents.CUSTOM_DATA);
        CompoundTag root = (comp == null) ? new CompoundTag() : comp.copyTag();
        CompoundTag meta = root.getCompound("mtg_meta").orElseGet(CompoundTag::new);
        CompoundTag counters = meta.getCompound("counters").orElseGet(CompoundTag::new);

        // Clamp at 0 instead of deleting
        counters.putInt(key, Math.max(0, value));

        meta.put("counters", counters);
        root.put("mtg_meta", meta);
        st.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
    }

    private static List<String> cachedCounterIconKeys;

    private static List<String> loadCounterIconKeys() {
        if (cachedCounterIconKeys != null) return cachedCounterIconKeys;

        var client = net.minecraft.client.Minecraft.getInstance();
        if (client == null) return cachedCounterIconKeys = List.of();

        var rm = client.getResourceManager();
        Set<String> keys = new HashSet<>();

        // add folders you actually use
        keys.addAll(listPngKeys(rm, "textures/gui/counters"));

        cachedCounterIconKeys = keys.stream().sorted(String::compareToIgnoreCase).toList();
        return cachedCounterIconKeys;
    }

    private void drawMiniButton(GuiGraphics ctx, int x, int y, int size, String glyph, boolean hover) {
        int border = hover ? 0xFF70E0FF : 0xFF404040;
        int bg     = hover ? 0xCC1A1A1A : 0xAA101010;

        ctx.fill(x - 1, y - 1, x + size + 1, y + size + 1, border);
        ctx.fill(x, y, x + size, y + size, bg);

        int tw = this.font.width(glyph);
        int tx = x + (size - tw) / 2;
        int ty = y + (size - this.font.lineHeight) / 2;
        ctx.drawString(this.font, glyph, tx, ty, 0xFFFFFFFF, false);
    }

    private void adjustCounterValue(String key, int deltaSteps) {
        if (key == null || key.isBlank()) return;

        CompoundTag counters = getOrCreateCounters(stack);
        int cur = counters.getInt(key).orElse(0);

        int step = isShiftDown() ? 5 : 1;
        int next = cur + deltaSteps * step;

        setCounter(stack, key, next); // clamps at 0
        sendCounterValueUpdate(key, next);
    }

    private static Set<String> listPngKeys(net.minecraft.server.packs.resources.ResourceManager rm, String folder) {
        Set<String> out = new HashSet<>();
        try {
            // Finds all resources under folder (namespace aware). Filter to your namespace.
            rm.listResources(folder, id -> id.getNamespace().equals("mtgcard") && id.getPath().endsWith(".png"))
                    .forEach((id, res) -> {
                        String path = id.getPath(); // e.g. textures/gui/counters/poison.png
                        String name = path.substring(path.lastIndexOf('/') + 1, path.length() - 4); // poison
                        out.add(normalizeIconKey(name));
                    });
        } catch (Throwable ignored) {}
        return out;
    }


    private void drawLegalityScrollbar(GuiGraphics ctx, int rows, int barX, int barY, int barW, int barH) {
        legBarX = barX;
        legBarY = barY;
        legBarW = barW;
        legBarH = barH;

        // background track
        ctx.fill(barX, barY, barX + barW, barY + barH, 0x55202020);

        float maxScroll = getLegalityMaxScroll(rows, barH);
        if (maxScroll <= 0f) {
            // no scrolling needed -> solid thumb
            ctx.fill(barX, barY, barX + barW, barY + barH, 0x88404040);
            // update thumb rect for dragging checks
            legBarY = barY;
            legBarH = barH;
            return;
        }

        float contentH = rows * LEG_ROW_H;
        float visibleH = barH;
        float thumbHf = (visibleH / contentH) * visibleH;
        int thumbH = clampInt((int) thumbHf, 12, Math.max(12, barH));

        int thumbMinY = barY;
        int thumbMaxY = barY + barH - thumbH;

        float t = legalityScroll / maxScroll;
        int thumbY = thumbMinY + (int) ((thumbMaxY - thumbMinY) * t);

        // thumb
        int thumbCol = draggingLegalityBar ? 0xFF707070 : 0xFF505050;
        ctx.fill(barX, thumbY, barX + barW, thumbY + thumbH, thumbCol);

        // update thumb rect for clicking/dragging
        legBarY = thumbY;
        legBarH = thumbH;
    }

    private int drawKv(GuiGraphics ctx, int x, int y, String k, String v, int keyColor, int valueColor) {
        k = sanitizeUiText(k);
        v = sanitizeUiText(v);
        if (v == null) v = "â€”";
        if (v.endsWith("â€”")) v = "â€”";
        ctx.drawString(this.font, k + ":", x, y, keyColor, false);
        ctx.drawString(this.font, v, x + 88, y, valueColor, false);
        return y + 12;
    }

    // Convenience overload (old behavior)
    private int drawKv(GuiGraphics ctx, int x, int y, String k, String v) {
        return drawKv(ctx, x, y, k, v, 0xFFDDDDDD, 0xFFFFFFFF);
    }

    private static ItemStack getConfiguredPriceItem() {
        String raw = MtgcardConfig.get().Price_Item;
        if (raw == null || raw.isBlank()) raw = "minecraft:diamond";

        Identifier id;
        try {
            id = Identifier.parse(raw.trim());
        } catch (Throwable ignored) {
            id = Identifier.parse("minecraft:diamond");
        }

        Item item = BuiltInRegistries.ITEM.getValue(id);
        if (item == null) item = Items.DIAMOND;

        return new ItemStack(item);
    }

    private static PriceResult pickBestPrice(
            boolean preferFoil,
            String normal,
            String foil,
            String etched
    ) {
        if (preferFoil) {
            if (isValidPrice(foil))   return new PriceResult(foil, PriceTier.FOIL);
            if (isValidPrice(etched)) return new PriceResult(etched, PriceTier.ETCHED);
            if (isValidPrice(normal)) return new PriceResult(normal, PriceTier.NORMAL);
        }

        if (isValidPrice(normal)) return new PriceResult(normal, PriceTier.NORMAL);
        if (isValidPrice(foil))   return new PriceResult(foil, PriceTier.FOIL);
        if (isValidPrice(etched)) return new PriceResult(etched, PriceTier.ETCHED);

        return new PriceResult("0", PriceTier.NORMAL);
    }

    private static boolean isValidPrice(String s) {
        if (s == null) return false;
        String t = s.trim();
        return !(t.isEmpty() || t.equals("-") || t.equals("â€”"));
    }

    private static int roundPriceToWhole(String price) {
        if (price == null) return 0;
        String s = price.trim();
        if (s.isEmpty() || s.equals("â€”")) return 0;

        try {
            double v = Double.parseDouble(s);
            if (Double.isNaN(v) || Double.isInfinite(v)) return 0;

            long r = Math.round(v); // nearest whole unit
            if (r < 0) r = 0;
            if (r > Integer.MAX_VALUE) r = Integer.MAX_VALUE;
            return (int) r;
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private PriceResult resolvePrice(ScryfallInfoManager.Entry e) {
        if (e == null) return new PriceResult("0", PriceTier.NORMAL);

        boolean preferFoil = stack.hasFoil();

        String basis = MtgcardConfig.get().Price_Basis;
        if (basis == null) basis = "USD";
        basis = basis.trim().toUpperCase();

        return switch (basis) {
            case "EUR" -> pickBestPrice(preferFoil, e.eur, e.eurFoil, null);
            case "TIX" -> new PriceResult(e.tix, PriceTier.NORMAL);
            case "USD" -> pickBestPrice(preferFoil, e.usd, e.usdFoil, e.usdEtched);
            default    -> pickBestPrice(preferFoil, e.usd, e.usdFoil, e.usdEtched);
        };
    }

    private static String prettyFormatName(String key) {
        if (key == null || key.isBlank()) return "â€”";
        // Scryfall keys are like "paupercommander" or "oldschool" or "standardbrawl" or "future"
        // Make them readable: split underscores if present; otherwise title-case.
        String s = key.trim().replace('_', ' ');
        return toTitle(s);
    }

    private static String toTitle(String s) {
        if (s == null) return "â€”";
        String[] parts = s.split("\\s+");
        StringBuilder out = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) continue;
            if (!out.isEmpty()) out.append(' ');
            out.append(Character.toUpperCase(p.charAt(0)));
            if (p.length() > 1) out.append(p.substring(1));
        }
        return out.isEmpty() ? "â€”" : out.toString();
    }

    // Keep common formats near the top, then everything else
    private static int legalityPriority(String key) {
        if (key == null) return 999;
        return switch (key) {
            case "commander" -> 0;
            case "modern" -> 1;
            case "pioneer" -> 2;
            case "standard" -> 3;
            case "historic" -> 4;
            case "legacy" -> 5;
            case "vintage" -> 6;
            case "pauper" -> 7;
            case "brawl" -> 8;
            case "standardbrawl" -> 9;
            case "alchemy" -> 10;
            case "timeless" -> 11;
            case "premodern" -> 12;
            case "oldschool" -> 13;
            case "duel" -> 14;
            case "oathbreaker" -> 15;
            case "paupercommander" -> 16;
            case "gladiator" -> 17;
            case "predh" -> 18;
            case "future" -> 19;
            case "explorer" -> 20;
            default -> 200;
        };
    }

    private float getLegalityMaxScroll() {
        return getLegalityMaxScroll(lastLegalityRowCount, legAreaH);
    }


    private float getLegalityMaxScroll(int rows, int viewH) {
        float contentH = rows * LEG_ROW_H;
        float max = contentH - viewH;
        return Math.max(0f, max);
    }

    private static float clampFloat(float v, float lo, float hi) {
        if (v < lo) return lo;
        if (v > hi) return hi;
        return v;
    }

    private static int clampInt(int v, int lo, int hi) {
        if (v < lo) return lo;
        if (v > hi) return hi;
        return v;
    }

    private String trimToWidth(String s, int maxPx) {
        s = sanitizeUiText(s);
        if (s == null) return "â€”";
        if (this.font.width(s) <= maxPx) return s;

        String ell = "...";
        int ellW = this.font.width(ell);
        String t = s;
        while (!t.isEmpty() && this.font.width(t) + ellW > maxPx) {
            t = t.substring(0, t.length() - 1);
        }
        return t.isEmpty() ? ell : (t + ell);
    }

    private static CompoundTag getMeta(ItemStack st) {
        var comp = st.get(DataComponents.CUSTOM_DATA);
        CompoundTag root = (comp == null) ? new CompoundTag() : comp.copyTag();
        return root.getCompound("mtg_meta").orElseGet(CompoundTag::new);
    }

    // ------------------------------------------------------------
    // NBT helpers
    // ------------------------------------------------------------

    private static int readFaceIndex(ItemStack st) {
        return TcgCardMeta.read(st).face();
    }

    private static int readFaceCount(ItemStack st) {
        return TcgCardMeta.faceCount(st);
    }

    private static void writeFaceIndex(ItemStack st, int idx) {
        var comp = st.get(DataComponents.CUSTOM_DATA);
        CompoundTag root = (comp == null) ? new CompoundTag() : comp.copyTag();
        TcgCardMeta.writeFace(root, idx);

        st.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
    }

    private static int readRotation(ItemStack st) {
        CompoundTag meta = getMeta(st);
        return meta.getInt("mtg_rot").orElse(0) & 3;
    }

    private static void writeRotation(ItemStack st, int rot) {
        var comp = st.get(DataComponents.CUSTOM_DATA);
        CompoundTag root = (comp == null) ? new CompoundTag() : comp.copyTag();
        CompoundTag meta = root.getCompound("mtg_meta").orElseGet(CompoundTag::new);

        meta.putInt("mtg_rot", rot & 3);
        root.put("mtg_meta", meta);

        st.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
    }

    private static int kiKeyCode(KeyEvent key) {
        try { return (int) key.getClass().getMethod("keyCode").invoke(key); } catch (Throwable ignored) {}
        try { return (int) key.getClass().getMethod("key").invoke(key); } catch (Throwable ignored) {}
        try { var f = key.getClass().getDeclaredField("keyCode"); f.setAccessible(true); return f.getInt(key); } catch (Throwable ignored) {}
        try { var f = key.getClass().getDeclaredField("key"); f.setAccessible(true); return f.getInt(key); } catch (Throwable ignored) {}
        return 0;
    }
    private static String normalizeCounterKey(String k) {
        if (k == null) return "";
        return k.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
    }

    private static String normalizeIconKey(String k) {
        if (k == null) return "none";
        String s = k.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
        return s.isBlank() ? "none" : s;
    }

    private boolean isDisplayMode() {
        return this.displayEntityId >= 0;
    }

    private void sendFaceUpdate(int faceIdx) {
        if (isDisplayMode()) {
            ClientPlayNetworking.send(new com.spider.mtgcard.net.payload.CardDisplayPayloads.DisplaySetFaceC2S(displayEntityId, faceIdx));
        } else {
            ClientPlayNetworking.send(new SetFacePayload(handSlot, faceIdx));
        }
    }

    private void sendCounterValueUpdate(String key, int value) {
        if (isDisplayMode()) {
            ClientPlayNetworking.send(new com.spider.mtgcard.net.payload.CardDisplayPayloads.DisplaySetCounterValueC2S(displayEntityId, key, value));
        } else {
            ClientPlayNetworking.send(new SetCounterValuePayload(handSlot, key, value));
        }
    }

    private void sendCounterMetaUpdate(String key, String displayName, String iconKey) {
        if (isDisplayMode()) {
            ClientPlayNetworking.send(new com.spider.mtgcard.net.payload.CardDisplayPayloads.DisplaySetCounterMetaC2S(displayEntityId, key, displayName, iconKey));
        } else {
            ClientPlayNetworking.send(new SetCounterMetaPayload(handSlot, key, displayName, iconKey));
        }
    }

}

