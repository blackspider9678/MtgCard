package com.spider.mtgcard.client.life;

import com.spider.mtgcard.client.compat.LegacyScreen;
import com.spider.mtgcard.api.LifeFormatRegistry;
import com.spider.mtgcard.life.LifeFormat;
import com.spider.mtgcard.life.LifePointPackets;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.core.BlockPos;
import org.joml.Vector2f;
import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;

import java.util.*;

/**
 * LifePointScreen
 * - Tabs: Life / Counters / Pods / Edit
 * - Life:
 *   - Big life square (editable) + +/- buttons
 *   - Scrollable counter icon grid under the life panel (scroll changes value on hover)
 *   - Other players list (select commander target)
 *   - Commander damage editor (updates life + commander damage)
 * - Counters:
 *   - Left scroll list of counters
 *   - Right editor + icon preview + scrollable icon picker grid
 * - Pods:
 *   - Left scroll list of pods (sticky +Create)
 *   - Middle Available + Right In Pod (buttons)
 * - Edit:
 *   - Preset list + options
 *   - Appearance scroll (palette/format/icon picker)
 *   - Preview
 *
 * Cleaned: removed unused modal/preset-id widgets, duplicate click handlers, unused fields/helpers.
 */
public final class LifePointScreen extends LegacyScreen {
    private enum Tab { LIFE, COUNTERS, GROUPS, EDIT }

    private final BlockPos pos;
    private Tab tab = Tab.LIFE;

    // top HUD
    private EditBox nameField;

    // shared editor field (life or counter value)
    private EditBox valueField;

    // Counters tab state
    private String counterKey = "poison";
    private EditBox addCounterField;

    // Commander damage
    private final Map<EditBox, BlockPos> cmdFieldToOther = new HashMap<>();
    private final Map<BlockPos, Integer> cmdCache = new HashMap<>();
    private boolean settingCmdProgrammatically = false;
    private BlockPos selectedCmdTarget = null;

    // Pods/groups
    private UUID selectedGroupId = null;
    private boolean groupsRequested = false;
    private boolean scanRequested = false;

    private String groupNameOriginal = "";
    private String groupNameCurrent = "";
    private boolean settingGroupNameProgrammatically = false;
    private EditBox groupNameField;

    private boolean groupDirty = false;

    private final Set<BlockPos> selectedMembers = new HashSet<>();
    private final List<BlockPos> orderedMembers = new ArrayList<>();
    private BlockPos lastScanOrigin = null;

    private static final int MAX_GROUP_MEMBERS = 6;

    // Create-group auto-select
    private boolean awaitingCreateSelect = false;
    private Set<UUID> createBeforeIds = Set.of();

    // ACTIVE PLAYER pulse
    private boolean lastTurnActive = false;
    private boolean lastStarted = false;
    private int activePulseTicks = 0;

    // HUD layout
    private static final int TOP_HUD_STRIP_H = 62;
    private static final int BOTTOM_HUD_STRIP_H = 34;
    private static final int HUD_STRIP_BG = 0xCC101010;
    private static final int HUD_STRIP_LINE = 0xFF2B2B2B;
    private static final String PLUS_MINUS = "\u00B1";
    private static final String TIMES = "\u00D7";
    private static final String UNCHECKED_PREFIX = "\u2610 ";
    private static final String CHECKED_PREFIX = "\u2611 ";
    private static final String TRIANGLE_UP = "\u25B2";
    private static final String TRIANGLE_DOWN = "\u25BC";
    private static final String SELECTED_PREFIX = "\u25B6 ";
    private static final String LOCKED_PREFIX = "[Locked] ";

    private static final int COL_BG = 0xAA141414;
    private static final int COL_BG_EDGE = 0xFF2B2B2B;

    private static final int LIFE_BOX_BG = 0xFF0C0C0C;
    private static final int LIFE_BOX_EDGE = 0xFF3A3A3A;

    // Panels
    private static final class Rect { int x,y,w,h; Rect(int x,int y,int w,int h){this.x=x;this.y=y;this.w=w;this.h=h;} }
    private Rect[] midCols = null;

    // LIFE tab panels
    private Rect lifeHealthPanel = null;
    private Rect lifeCounterViewport = null;
    private int lifeCounterScroll = 0;
    private int lifeCounterContentH = 0;
    private final Map<String, Rect> lifeCounterRects = new HashMap<>();

    private int lifeButtonsY = -1;
    private int lifeButtonsCenterX = -1;
    private Rect lifeFormatLabelRect = null;

    // LIFE tab: other players list
    private Rect otherPlayersViewport = null;
    private final Map<BlockPos, Rect> otherPlayerRects = new HashMap<>();
    private List<BlockPos> otherPlayersOrdered = new ArrayList<>();

    // COUNTERS tab: left list viewport + scroll
    private Rect countersListViewport = null;
    private int countersListScroll = 0;
    private int countersListContentH = 0;
    private List<String> countersListKeys = List.of();

    // COUNTERS tab: icon preview + picker viewport + scroll
    private Rect counterIconPreviewRect = null;
    private Rect counterIconPickerViewport = null;
    private int counterIconPickerScroll = 0;
    private int counterIconPickerContentH = 0;
    private final Map<String, Rect> counterPickerRects = new HashMap<>();

    // PODS tab: left pods list viewport + scroll + rects
    private Rect podsListViewport = null;
    private final Map<UUID, Rect> podRowRects = new HashMap<>();
    private Rect podCreateRect = null;
    private int podsListScroll = 0;
    private int podsListContentH = 0;
    private boolean podsAutoScrollPending = false;

    // PODS tab: locked tooltip tracking
    private static final class LockedInfo {
        final Rect rect;
        final String podName;
        LockedInfo(Rect rect, String podName) { this.rect = rect; this.podName = podName; }
    }
    private final Map<BlockPos, LockedInfo> lockedAvailRects = new HashMap<>();

    // EDIT tab (kept minimal but functional)
    private Rect editPresetListVp = null;
    private int editPresetListScroll = 0;
    private int editPresetListContentH = 0;

    private Rect editAppearanceVp = null;
    private int editAppearanceScroll = 0;
    private int editAppearanceContentH = 0;

    private Rect editCounterIncludeVp = null;
    private int editCounterIncludeScroll = 0;
    private int editCounterIncludeContentH = 0;

    private Rect editIconPickerVp = null;
    private int editIconPickerScroll = 0;
    private int editIconPickerContentH = 0;

    private Rect editPreviewRect = null;

    private final Map<String, Rect> editPresetRowRects = new HashMap<>();
    private Rect editIncludeCountersRect = null;
    private Rect editSaveToWorldRect = null;
    private final Map<String, Rect> editIncludeCounterRowRects = new HashMap<>();

    private final Map<String, Rect> editFormatRects = new HashMap<>();
    private final Map<String, Rect> editIconRects = new HashMap<>();
    private final List<Rect> editPaletteRectsPlayer = new ArrayList<>();
    private final List<Rect> editPaletteRectsLife = new ArrayList<>();
    private final List<Rect> editPaletteRectsIcon = new ArrayList<>();

    private EditBox editPresetNameField;
    private String selectedPresetKey = null;
    private boolean includeCustomCounters = false;
    private final Set<String> includedCustomCounterKeys = new HashSet<>();
    private boolean saveToWorld = false;

    // appearance values (cached)
    private int editPlayerColor = 0xE8E8E8;
    private int editLifeColor   = 0xFFFFFF;
    private String editIconKey  = "none";
    private String editFormatKey = "commander";
    private int editCmdLethal = 21;

    // EDIT tab: hex editors
    private EditBox editPlayerHexField;
    private EditBox editLifeHexField;
    private boolean settingHexProgrammatically = false;

    // appearance values (cached)
    private int editIconSwapColor = 0xFF0000; // default: original red

    // EDIT tab: icon swap hex editor
    private EditBox editIconHexField;
    private boolean iconHexWasFocused = false;

    // focus tracking for "normalize on blur"
    private boolean playerHexWasFocused = false;
    private boolean lifeHexWasFocused = false;

    // EDIT tab: color bar rects (used by drawEditAppearance)
    private Rect editPlayerBarRect = null;
    private Rect editLifeBarRect = null;

    private static final int KEY_RED = 0xFF0000;

    // -------------------------------------------------------------------------
    // UI SCALING (design-space -> screen-space)
    // -------------------------------------------------------------------------

    // Pick the size your UI was authored for (tune these once).
    // I like 960x540 (16:9) or 854x480. Choose what matches your â€œGUI Scale 5â€ look.
    private static final int DESIGN_W = 960;
    private static final int DESIGN_H = 540;
    private static final float MAX_UI_SCALE = 1.6f;

    private float uiScale = 1f;
    private int uiX = 0, uiY = 0;      // top-left of scaled design area in actual screen coords
    private int uiW = DESIGN_W, uiH = DESIGN_H;

    private void recomputeLayoutScale() {
        float sx = this.width  / (float) DESIGN_W;
        float sy = this.height / (float) DESIGN_H;
        uiScale = Math.min(MAX_UI_SCALE, Math.min(sx, sy));

        uiW = Math.round(DESIGN_W * uiScale);
        uiH = Math.round(DESIGN_H * uiScale);

        uiX = (this.width  - uiW) / 2;
        uiY = 0;
    }

    private int px(int designX) { return uiX + Math.round(designX * uiScale); }
    private int py(int designY) { return uiY + Math.round(designY * uiScale); }
    private int ps(int designS) { return Math.round(designS * uiScale); }

    private int topHudStripH() {
        return Math.min(this.height, ps(TOP_HUD_STRIP_H));
    }

    private int bottomHudStripH() {
        return Math.min(this.height, ps(BOTTOM_HUD_STRIP_H));
    }


    // cache recolored textures per (sourceTex + rgb)
    private final Map<String, Identifier> recolorTexCache = new HashMap<>();

    private Identifier recolored(Identifier src, int swapRgb) {
        swapRgb &= 0xFFFFFF;
        if (swapRgb == KEY_RED) return src; // no-op

        String k = src.toString() + "|swap:" + String.format("%06X", swapRgb);
        Identifier cached = recolorTexCache.get(k);
        if (cached != null) return cached;

        try {
            var rm = Minecraft.getInstance().getResourceManager();
            var resOpt = rm.getResource(src);
            if (resOpt.isEmpty()) return src;

            com.mojang.blaze3d.platform.NativeImage img;
            try (var in = resOpt.get().open()) {
                img = com.mojang.blaze3d.platform.NativeImage.read(in);
            }

            // recolor in-place
            int tgtR = (swapRgb >> 16) & 0xFF;
            int tgtG = (swapRgb >> 8) & 0xFF;
            int tgtB = (swapRgb) & 0xFF;

            int w = img.getWidth(), h = img.getHeight();
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int argb = img.getPixel(x, y);
                    int a = (argb >>> 24) & 0xFF;
                    if (a == 0) continue;

                    int r = (argb >>> 16) & 0xFF;
                    int g = (argb >>> 8) & 0xFF;
                    int b = (argb) & 0xFF;

                    if (isKeyRedish(r, g, b)) {
                        float f = r / 255f;

                        int nr = clamp((int)(tgtR * f), 0, 255);
                        int ng = clamp((int)(tgtG * f), 0, 255);
                        int nb = clamp((int)(tgtB * f), 0, 255);

                        int nargb = (a << 24) | (nr << 16) | (ng << 8) | nb;
                        img.setPixel(x, y, nargb);
                    }
                }
            }

            var tex = new net.minecraft.client.renderer.texture.DynamicTexture(
                    () -> "mtgcard_icon_" + Integer.toHexString(k.hashCode()),
                    img
            );
            // img is owned by texture now

            var tm = Minecraft.getInstance().getTextureManager();

            // path must be lowercase-ish and valid for Identifiers
            Identifier id = Identifier.fromNamespaceAndPath("mtgcard", "dynamic/icon/" + Integer.toHexString(k.hashCode()));

            // registers (and replaces if already registered)
            tm.register(id, tex);

            recolorTexCache.put(k, id);
            return id;

        } catch (Exception e) {
            return src;
        }
    }

    private static boolean isHexPartialOrEmpty(String str) {
        if (str == null) return true;
        String t = str.trim();
        if (t.isEmpty()) return true;

        if (t.startsWith("0x") || t.startsWith("0X")) t = t.substring(2);
        if (t.startsWith("#")) t = t.substring(1);

        if (t.length() > 6) return false;

        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            boolean ok = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
            if (!ok) return false;
        }
        return true;
    }

    private void drawIconSwapped(GuiGraphics ctx, Identifier tex, int x, int y, int w, int h) {
        Identifier use = recolored(tex, editIconSwapColor);
        drawIcon(ctx, use, x, y, w, h);
    }

    private boolean isKeyRedish(int r, int g, int b) {
        // tuned for your base key #FF0000
        // allows small compression artifacts / slight variations
        if (r < 140) return false;
        if (g > 80 || b > 80) return false;
        // ensure red is dominant
        return (r - Math.max(g, b)) > 90;
    }

    // -------------------------------------------------------------------------
    // DRAGGABLE SCROLLBARS
    // -------------------------------------------------------------------------

    // PODS: scrollbar should only cover the scroll area (excludes sticky "+ Create" footer)
    private Rect podsListScrollVp = null;

    private enum ScrollId {
        LIFE_COUNTERS,
        COUNTERS_LIST,
        COUNTER_ICON_PICKER,
        PODS_LIST,
        EDIT_PRESET_LIST,
        EDIT_INCLUDE_LIST,
        EDIT_ICON_PICKER,
        EDIT_APPEARANCE
    }

    private final EnumMap<ScrollId, Rect> scrollTrackRects = new EnumMap<>(ScrollId.class);
    private final EnumMap<ScrollId, Rect> scrollThumbRects = new EnumMap<>(ScrollId.class);

    private ScrollId draggingScroll = null;
    private int dragStartMouseY = 0;
    private int dragStartScroll = 0;
    private int dragMaxScroll = 0;
    private int dragTrackH = 0;
    private int dragThumbH = 0;


    // --- Preset workflow: "Your preset ID" display ---
    private String yourPresetId = "";

    public void setYourPresetId(String id) {
        this.yourPresetId = (id == null) ? "" : id;
    }

    public String getYourPresetId() {
        return yourPresetId;
    }

    private static final class Palette {
        final String label; final int rgb;
        Palette(String label, int rgb) { this.label = label; this.rgb = rgb & 0xFFFFFF; }
    }
    private static final List<Palette> MANA_PALETTE = List.of(
            new Palette("W", 0xF8F7F0),
            new Palette("U", 0x0E68AB),
            new Palette("B", 0x150B00),
            new Palette("R", 0xD3202A),
            new Palette("G", 0x00733E),
            new Palette("C", 0xB0B7C3),
            new Palette("Gr", 0x8A8A8A),
            new Palette("Bl", 0x0A0A0A)
    );

    private int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }

    private void drawScrollbar(GuiGraphics ctx, ScrollId id, Rect vp, int contentH, int scroll) {
        scrollTrackRects.remove(id);
        scrollThumbRects.remove(id);

        if (vp == null) return;
        if (contentH <= vp.h) return;

        int maxScroll = Math.max(0, contentH - vp.h);

        int barW = 6;
        int barX = vp.x + vp.w - barW - 2;
        int barY = vp.y + 2;
        int barH = vp.h - 4;

        // thumb height proportional to viewport/content
        int thumbH = Math.max(12, (int)(barH * (vp.h / (float)contentH)));
        int trackSpan = Math.max(1, barH - thumbH);

        float t = (maxScroll <= 0) ? 0f : (scroll / (float)maxScroll);
        int thumbY = barY + (int)(trackSpan * t);

        Rect track = new Rect(barX, barY, barW, barH);
        Rect thumb = new Rect(barX, thumbY, barW, thumbH);

        scrollTrackRects.put(id, track);
        scrollThumbRects.put(id, thumb);

        ctx.fill(track.x, track.y, track.x + track.w, track.y + track.h, 0x66222222);
        ctx.fill(thumb.x, thumb.y, thumb.x + thumb.w, thumb.y + thumb.h, 0xAA888888);
    }

    private void startScrollbarDrag(ScrollId id, int mouseY, int currentScroll, int maxScroll, int trackH, int thumbH) {
        draggingScroll = id;
        dragStartMouseY = mouseY;
        dragStartScroll = currentScroll;
        dragMaxScroll = maxScroll;
        dragTrackH = trackH;
        dragThumbH = thumbH;
    }

    // counter icons
    private static final Identifier ICON_POISON     = Identifier.fromNamespaceAndPath("mtgcard", "textures/gui/counters/poison.png");
    private static final Identifier ICON_ENERGY     = Identifier.fromNamespaceAndPath("mtgcard", "textures/gui/counters/energy.png");
    private static final Identifier ICON_EXPERIENCE = Identifier.fromNamespaceAndPath("mtgcard", "textures/gui/counters/experience.png");

    private final Map<String, Identifier> iconIdCache = new HashMap<>();
    private final Map<Identifier, int[]> texDims = new HashMap<>();

    private List<String> cachedCounterIcons = null;

    private LifePointScreen(BlockPos pos) {
        super(Component.translatable("screen.mtgcard.life_point"));
        this.pos = pos;
    }

    public static void open(BlockPos pos, CompoundTag state) {
        LifePointClientState.onSync(pos, state);

        var s = new LifePointScreen(pos);

        // pulse baseline
        s.lastTurnActive = LifePointClientState.get(pos).getBoolean("TurnActive").orElse(false);
        UUID gid = LifePointClientState.getGroupIdFor(pos);
        var gv = (gid != null) ? LifePointClientState.getGroup(gid) : null;
        s.lastStarted = (gv != null && gv.started);

        Minecraft.getInstance().setScreen(s);
    }

    // -------------------------------------------------------------------------
    // INIT / BUILD
    // -------------------------------------------------------------------------

    @Override
    protected void init() {
        if (applyFixedGuiScale(2)) return;

        clearWidgets();

        // NEW: compute design-space scaling and origin
        recomputeLayoutScale();

        cmdFieldToOther.clear();
        midCols = null;

        // clear per-tab rects
        lifeCounterRects.clear();
        otherPlayerRects.clear();
        counterPickerRects.clear();
        podRowRects.clear();
        lockedAvailRects.clear();

        lifeHealthPanel = null;
        lifeCounterViewport = null;
        lifeCounterContentH = 0;

        countersListViewport = null;
        countersListKeys = List.of();
        countersListContentH = 0;

        counterIconPreviewRect = null;
        counterIconPickerViewport = null;
        counterIconPickerContentH = 0;

        podsListViewport = null;
        podCreateRect = null;

        // EDIT rects
        editPresetRowRects.clear();
        editIncludeCounterRowRects.clear();
        editFormatRects.clear();
        editIconRects.clear();
        editPaletteRectsPlayer.clear();
        editPaletteRectsLife.clear();
        editPaletteRectsIcon.clear();
        editIncludeCountersRect = null;
        editSaveToWorldRect = null;
        editPlayerBarRect = null;
        editLifeBarRect = null;

        // keep scroll sane
        lifeCounterScroll = Math.max(0, lifeCounterScroll);
        countersListScroll = Math.max(0, countersListScroll);
        counterIconPickerScroll = Math.max(0, counterIconPickerScroll);
        podsListScroll = Math.max(0, podsListScroll);

        editPresetListScroll = Math.max(0, editPresetListScroll);
        editAppearanceScroll = Math.max(0, editAppearanceScroll);
        editCounterIncludeScroll = Math.max(0, editCounterIncludeScroll);
        editIconPickerScroll = Math.max(0, editIconPickerScroll);

        lifeButtonsY = -1;
        lifeButtonsCenterX = -1;
        lifeFormatLabelRect = null;

        // cache icon sizes once
        cacheDims(ICON_POISON);
        cacheDims(ICON_ENERGY);
        cacheDims(ICON_EXPERIENCE);

        var st = LifePointClientState.get(pos);

        editPlayerHexField = null;
        editLifeHexField = null;
        playerHexWasFocused = false;
        lifeHexWasFocused = false;
        settingHexProgrammatically = false;

        // cached appearance values from NBT
        editPlayerColor = st.getInt("PlayerColor").orElse(0xE8E8E8);
        editLifeColor   = st.getInt("LifeColor").orElse(0xFFFFFF);
        editIconKey     = st.getString("IconKey").orElse("none");
        editFormatKey   = LifeFormat.normalizeKey(st.getString("FormatKey").orElse(LifeFormat.DEFAULT.key()));
        editCmdLethal   = st.getInt("CmdLethal").orElse(21);
        editIconSwapColor = st.getInt("IconSwapColor").orElse(0xFF0000);
        editIconHexField = null;
        iconHexWasFocused = false;

        // preset default selection
        if (selectedPresetKey == null) {
            var keys = LifePointPresetStore.listKeys();
            if (!keys.isEmpty()) selectedPresetKey = keys.get(0);
        }

        // pulse detect started/turn changes
        UUID gid = LifePointClientState.getGroupIdFor(pos);
        var gv = (gid != null) ? LifePointClientState.getGroup(gid) : null;
        boolean startedNow = (gv != null && gv.started);
        boolean turnActiveNow = st.getBoolean("TurnActive").orElse(false);
        if (startedNow && turnActiveNow && (!lastStarted || !lastTurnActive)) activePulseTicks = 30;
        lastStarted = startedNow;
        lastTurnActive = turnActiveNow;

        // Build top HUD in DESIGN coords, converted via px/py/ps
        buildTopHudScaled(st);

        int ax0 = uiX + ps(12);
        int ay0 = topHudStripH() + ps(8);
        int aw0 = Math.max(ps(320), uiW - ps(24));
        int contentBottom = Math.max(ay0 + ps(120), this.height - bottomHudStripH() - ps(8));
        int ah0 = Math.max(ps(120), contentBottom - ay0);

        switch (tab) {
            case LIFE -> buildLifeTabWireframe(st, ax0, ay0, aw0, ah0);
            case COUNTERS -> buildCountersTabWireframe(st, ax0, ay0, aw0, ah0);
            case GROUPS -> buildPodsTabWireframe(st, ax0, ay0, aw0, ah0);
            case EDIT -> buildEditTabWireframe(st, ax0, ay0, aw0, ah0);
        }

        buildBottomHudScaled(st);
        sanitizeWidgetMessages();
    }

    private void buildTopHudScaled(CompoundTag st) {
        int margin = ps(12);
        int y = py(12);

        int rowH = ps(18);
        int gap = ps(8);

        int editW = ps(70);
        int editX = px(DESIGN_W - 12) - editW; // right edge of design area

        int labelW = ps(44);
        int nameX = px(12) + labelW + gap;
        int nameW = editX - nameX - gap;
        if (nameW < ps(80)) nameW = ps(80);

        var nameLabel = Button.builder(Component.literal("Name:"), b -> {})
                .bounds(px(12), y, labelW, rowH)
                .build();
        nameLabel.active = false;
        addRenderableWidget(nameLabel);

        nameField = new EditBox(font, nameX, y, nameW, rowH, Component.literal(""));
        nameField.setValue(st.getString("DisplayName").orElse(""));
        nameField.setResponder(s -> ClientPlayNetworking.send(new LifePointPackets.SetNameC2S(pos, s)));
        addRenderableWidget(nameField);

        // tabs
        y += ps(24);
        int tabH = ps(18);
        int tabW = ps(90);
        int tabX = px(12);

        addRenderableWidget(Button.builder(Component.literal("Life"), b -> { tab = Tab.LIFE; init(); })
                .bounds(tabX, y, tabW, tabH).build());
        tabX += tabW + gap;

        addRenderableWidget(Button.builder(Component.literal("Counters"), b -> { tab = Tab.COUNTERS; init(); })
                .bounds(tabX, y, tabW, tabH).build());
        tabX += tabW + gap;

        addRenderableWidget(Button.builder(Component.literal("Pods"), b -> {
            tab = Tab.GROUPS;
            scanRequested = false;
            podsAutoScrollPending = true;
            init();
        }).bounds(tabX, y, tabW, tabH).build());
        tabX += tabW + gap;

        addRenderableWidget(Button.builder(Component.literal("Edit"), b -> { tab = Tab.EDIT; init(); })
                .bounds(tabX, y, tabW, tabH).build());
    }

    private void buildBottomHudScaled(CompoundTag st) {
        int btnY = this.height - bottomHudStripH() + ps(8);
        int btnH = ps(20);
        int gap = ps(10);

        int btnW = ps(120);
        int total = btnW * 4 + gap * 3;
        int startX = uiX + (uiW - total) / 2;
        // simpler + safer:
        startX = uiX + (uiW - total) / 2;

        boolean hasGroup = st.contains("GroupId");
        UUID gid = LifePointClientState.getGroupIdFor(pos);
        var gv = (gid != null) ? LifePointClientState.getGroup(gid) : null;

        boolean started = (gv != null && gv.started);
        boolean turnActive = st.getBoolean("TurnActive").orElse(false);

        boolean isDead = false;
        if (gv != null && gv.dead != null) isDead = gv.dead.contains(pos);

        var startBtn = Button.builder(Component.literal("Start"), b -> {
            b.active = false;
            ClientPlayNetworking.send(new LifePointPackets.StartGameC2S(pos));
            Minecraft.getInstance().setScreen(null);
        }).bounds(startX + (btnW + gap) * 0, btnY, btnW, btnH).build();
        startBtn.active = hasGroup && !started;
        addRenderableWidget(startBtn);

        var passBtn = Button.builder(Component.literal("Pass"), b ->
                ClientPlayNetworking.send(new LifePointPackets.PassTurnC2S(pos))
        ).bounds(startX + (btnW + gap) * 1, btnY, btnW, btnH).build();
        passBtn.active = hasGroup && started && turnActive && !isDead;
        addRenderableWidget(passBtn);

        var resetBtn = Button.builder(Component.literal("Reset"), b -> {
            b.active = false;
            ClientPlayNetworking.send(new LifePointPackets.ResetGameC2S(pos));
        }).bounds(startX + (btnW + gap) * 2, btnY, btnW, btnH).build();
        resetBtn.active = hasGroup && started;
        addRenderableWidget(resetBtn);

        final boolean deadNow = isDead;
        var aliveBtn = Button.builder(Component.literal(deadNow ? "Dead" : "Alive"), b ->
                ClientPlayNetworking.send(new LifePointPackets.SetDeadC2S(pos, !deadNow))
        ).bounds(startX + (btnW + gap) * 3, btnY, btnW, btnH).build();
        aliveBtn.active = hasGroup;
        addRenderableWidget(aliveBtn);
    }

    private void buildTopHud(CompoundTag st) {
        int margin = 12;
        int y = 12;

        int rowH = 18;
        int gap = 8;

        int editW = 70;
        int editX = width - margin - editW;

        int labelW = 44;
        int nameX = margin + labelW + gap;
        int nameW = editX - nameX - gap;
        if (nameW < 80) nameW = 80;

        var nameLabel = Button.builder(Component.literal("Name:"), b -> {})
                .bounds(margin, y, labelW, rowH)
                .build();
        nameLabel.active = false;
        addRenderableWidget(nameLabel);

        nameField = new EditBox(font, nameX, y, nameW, rowH, Component.literal(""));
        nameField.setValue(st.getString("DisplayName").orElse(""));
        nameField.setResponder(s -> ClientPlayNetworking.send(new LifePointPackets.SetNameC2S(pos, s)));
        addRenderableWidget(nameField);

        // tabs
        y += 24;
        int tabH = 18;
        int tabW = 90;
        int tabX = margin;

        addRenderableWidget(Button.builder(Component.literal("Life"), b -> { tab = Tab.LIFE; init(); })
                .bounds(tabX, y, tabW, tabH).build());
        tabX += tabW + gap;

        addRenderableWidget(Button.builder(Component.literal("Counters"), b -> { tab = Tab.COUNTERS; init(); })
                .bounds(tabX, y, tabW, tabH).build());
        tabX += tabW + gap;

        addRenderableWidget(Button.builder(Component.literal("Pods"), b -> {
            tab = Tab.GROUPS;
            scanRequested = false;
            podsAutoScrollPending = true;
            init();
        }).bounds(tabX, y, tabW, tabH).build());
        tabX += tabW + gap;

        addRenderableWidget(Button.builder(Component.literal("Edit"), b -> { tab = Tab.EDIT; init(); })
                .bounds(tabX, y, tabW, tabH).build());
    }

    private void buildBottomHud(CompoundTag st) {
        int btnY = height - 26;
        int btnH = ps(20);
        int gap = ps(10);

        int btnW = ps(120);
        int total = btnW * 4 + gap * 3;
        int startX = (width - total) / 2;

        boolean hasGroup = st.contains("GroupId");
        UUID gid = LifePointClientState.getGroupIdFor(pos);
        var gv = (gid != null) ? LifePointClientState.getGroup(gid) : null;

        boolean started = (gv != null && gv.started);
        boolean turnActive = st.getBoolean("TurnActive").orElse(false);

        boolean isDead = false;
        if (gv != null && gv.dead != null) isDead = gv.dead.contains(pos);

        var startBtn = Button.builder(Component.literal("Start"), b -> {
            b.active = false;
            ClientPlayNetworking.send(new LifePointPackets.StartGameC2S(pos));
            Minecraft.getInstance().setScreen(null);
        }).bounds(startX + (btnW + gap) * 0, btnY, btnW, btnH).build();
        startBtn.active = hasGroup && !started;
        addRenderableWidget(startBtn);

        var passBtn = Button.builder(Component.literal("Pass"), b ->
                ClientPlayNetworking.send(new LifePointPackets.PassTurnC2S(pos))
        ).bounds(startX + (btnW + gap) * 1, btnY, btnW, btnH).build();
        passBtn.active = hasGroup && started && turnActive && !isDead;
        addRenderableWidget(passBtn);

        var resetBtn = Button.builder(Component.literal("Reset"), b -> {
            b.active = false;
            ClientPlayNetworking.send(new LifePointPackets.ResetGameC2S(pos));
        }).bounds(startX + (btnW + gap) * 2, btnY, btnW, btnH).build();
        resetBtn.active = hasGroup && started;
        addRenderableWidget(resetBtn);

        final boolean deadNow = isDead;
        var aliveBtn = Button.builder(Component.literal(deadNow ? "Dead" : "Alive"), b ->
                ClientPlayNetworking.send(new LifePointPackets.SetDeadC2S(pos, !deadNow))
        ).bounds(startX + (btnW + gap) * 3, btnY, btnW, btnH).build();
        aliveBtn.active = hasGroup;
        addRenderableWidget(aliveBtn);
    }

    private void addHeader(int x, int y, int w, String txt) {
        var b = Button.builder(Component.literal(sanitizeUiText(txt)), bb -> {})
                .bounds(x, y, w, 18).build();
        b.active = false;
        addRenderableWidget(b);
    }

    private void addHintLabel(int x, int y, int w, String txt) {
        int ww = Math.max(40, w); // safety
        var b = Button.builder(Component.literal(sanitizeUiText(txt)), bb -> {})
                .bounds(x, y, ww, 18)
                .build();
        b.active = false;
        addRenderableWidget(b);
    }

    // Back-compat overload: old calls keep working.
    // Uses a sane default width based on the current column/panel.
    private void addHintLabel(int x, int y, String txt) {
        int w = ps(220); // fallback

        // If we have the column layout, clamp to the column width
        if (midCols != null && midCols.length > 0) {
            Rect col = null;
            for (Rect r : midCols) {
                if (r != null && x >= r.x && x < r.x + r.w) { col = r; break; }
            }
            if (col != null) {
                w = Math.max(ps(60), col.w - ps(20)); // "fit the section"
            }
        }

        addHintLabel(x, y, w, txt);
    }

    private static String sanitizeUiText(String s) {
        if (s == null) return "";

        return s
                .replace("Ã‚Â±", PLUS_MINUS)
                .replace("Â±", PLUS_MINUS)
                .replace("Ãƒâ€”", TIMES)
                .replace("Ã—", TIMES)
                .replace("â˜", UNCHECKED_PREFIX.trim())
                .replace("â˜‘", CHECKED_PREFIX.trim())
                .replace("â–²", TRIANGLE_UP)
                .replace("â–¼", TRIANGLE_DOWN)
                .replace("â–¶", SELECTED_PREFIX.trim())
                .replace("Ã°Å¸â€â€™", LOCKED_PREFIX.trim());
    }

    private void sanitizeWidgetMessages() {
        for (var child : this.children()) {
            if (child instanceof Button button) {
                String cleaned = sanitizeUiText(button.getMessage().getString());
                button.setMessage(Component.literal(cleaned));
            }
        }
    }

    // -------------------------------------------------------------------------
    // LIFE TAB
    // -------------------------------------------------------------------------

    private void buildLifeTabWireframe(CompoundTag st, int x0, int y0, int w0, int h0) {
        UUID gid = LifePointClientState.getGroupIdFor(pos);
        var gv = (gid != null) ? LifePointClientState.getGroup(gid) : null;

        int gap = ps(10);
        int colW = (w0 - gap * 2) / 3;

        int leftX = x0;
        int midX  = x0 + colW + gap;
        int rightX= x0 + (colW + gap) * 2;

        midCols = new Rect[] {
                new Rect(leftX,  y0, colW, h0),
                new Rect(midX,   y0, colW, h0),
                new Rect(rightX, y0, colW, h0),
        };

        LifeFormat format = LifeFormat.fromKey(st.getString("FormatKey").orElse(LifeFormat.DEFAULT.key()));

        addHeader(leftX, y0, colW, "Your Player Info");
        addHeader(midX,  y0, colW, "Other Players");
        addHeader(rightX,y0, colW, "Commander Damage");

        // LEFT: Big Life Box
        int life = st.getInt("Life").orElse(40);

        int box = ps(72);
        int boxX = leftX + ps(10);
        int boxY = y0 + 26;
        int hintY = boxY + box + ps(10);
        int hintRowGap = ps(16);
        int hintRowH = 18;
        int countersStartY = hintY + hintRowGap + hintRowH + ps(8);

        // Health sub-panel (behind life square + buttons)
        int healthPanelX = leftX + 6;
        int healthPanelY = y0 + 22;
        int healthPanelW = colW - 12;

        int healthPanelBottom = countersStartY - ps(8);

        lifeHealthPanel = new Rect(
                healthPanelX,
                healthPanelY,
                healthPanelW,
                Math.max(30, healthPanelBottom - healthPanelY)
        );

        // Counters viewport under health panel
        int vpX = leftX + 6;
        int vpY = countersStartY;
        int vpW = colW - 12;
        int vpBottom = y0 + h0 - 10;
        int vpH = Math.max(40, vpBottom - vpY);
        lifeCounterViewport = new Rect(vpX, vpY, vpW, vpH);

        valueField = new EditBox(font, boxX, boxY, box, box, Component.literal(""));
        valueField.setValue(Integer.toString(life));
        valueField.moveCursorToEnd(false);
        valueField.setMaxLength(5);
        valueField.setResponder(s -> {
            try {
                int v = Integer.parseInt(s.trim());
                ClientPlayNetworking.send(new LifePointPackets.SetLifeC2S(pos, v));
            } catch (Exception ignored) {}
        });
        addRenderableWidget(valueField);

        // hide widget visuals (we render box + big number ourselves)
        valueField.setBordered(false);
        valueField.setTextColor(0x00000000);
        valueField.setTextColorUneditable(0x00000000);

        // Buttons to the right of square
        int btnW = ps(22);
        int btnH = ps(18);
        int btnGap = 6;

        int bx = boxX + box + btnGap;
        int by = boxY + (box / 2) - (btnH / 2);

        int startBx = bx;
        int stripW = (btnW + 10) + 4 + btnW + 4 + btnW + 4 + (btnW + 10);
        lifeButtonsY = by;
        lifeButtonsCenterX = startBx + (stripW / 2);
        lifeFormatLabelRect = new Rect(startBx, by + btnH + ps(6), stripW, ps(12));

        addRenderableWidget(Button.builder(Component.literal("--"), b ->
                ClientPlayNetworking.send(new LifePointPackets.AddLifeC2S(pos, -10))
        ).bounds(bx, by, btnW + 10, btnH).build());
        bx += (btnW + 10) + 4;

        addRenderableWidget(Button.builder(Component.literal("-"), b ->
                ClientPlayNetworking.send(new LifePointPackets.AddLifeC2S(pos, -1))
        ).bounds(bx, by, btnW, btnH).build());
        bx += btnW + 4;

        addRenderableWidget(Button.builder(Component.literal("+"), b ->
                ClientPlayNetworking.send(new LifePointPackets.AddLifeC2S(pos, +1))
        ).bounds(bx, by, btnW, btnH).build());
        bx += btnW + 4;

        addRenderableWidget(Button.builder(Component.literal("++"), b ->
                ClientPlayNetworking.send(new LifePointPackets.AddLifeC2S(pos, +10))
        ).bounds(bx, by, btnW + 10, btnH).build());

        int hintW = (lifeHealthPanel != null) ? (lifeHealthPanel.w - ps(20)) : (colW - ps(20));
        addHintLabel(leftX + ps(10), hintY, hintW, "Scroll Â±1");
        addHintLabel(leftX + ps(10), hintY + ps(16), hintW, "Shift Ã—10");

        // MIDDLE: Other players flat list
        int listY = y0 + 26;
        int opVpX = midX + 6;
        int opVpY = listY;
        int opVpW = colW - 12;
        int opVpH = Math.max(40, (y0 + h0 - 10) - opVpY);
        otherPlayersViewport = new Rect(opVpX, opVpY, opVpW, opVpH);

        otherPlayersOrdered = new ArrayList<>();
        if (gv != null && gv.order != null && !gv.order.isEmpty()) {
            for (BlockPos other : gv.order) {
                if (!other.equals(pos)) otherPlayersOrdered.add(other);
            }
        }

        // RIGHT: commander damage editor for selected target
        if (format.hasCommanderDamage()) {
            buildCommanderDamageWireframe(st, rightX, y0 + 26, colW, h0 - 26);
        } else {
            addHintLabel(rightX + ps(10), y0 + 26, colW - ps(20), "Off for " + format.displayName());
        }
    }

    private void buildCommanderDamageWireframe(CompoundTag st, int x, int y, int w, int h) {
        if (!LifeFormat.fromKey(st.getString("FormatKey").orElse(LifeFormat.DEFAULT.key())).hasCommanderDamage()) {
            addHintLabel(x + 10, y, w - 20, "Commander only");
            return;
        }

        UUID gid = LifePointClientState.getGroupIdFor(pos);
        if (gid == null) { addHintLabel(x + 10, y, "No Pod linked"); return; }

        var gv = LifePointClientState.getGroup(gid);
        if (gv == null || gv.order == null || gv.order.isEmpty()) { addHintLabel(x + 10, y, "No players"); return; }

        if (selectedCmdTarget == null || selectedCmdTarget.equals(pos)) {
            for (BlockPos p : gv.order) {
                if (!p.equals(pos)) { selectedCmdTarget = p; break; }
            }
        }
        if (selectedCmdTarget == null) { addHintLabel(x + 10, y, "No target"); return; }

        String nm = LifePointClientState.groupMemberName(gid, selectedCmdTarget);
        if (nm == null || nm.isBlank()) nm = LifePointClientState.get(selectedCmdTarget).getString("DisplayName").orElse("");
        if (nm == null || nm.isBlank()) nm = shortPos(selectedCmdTarget);

        long lk = selectedCmdTarget.asLong();
        int v = 0;

        // NEW FORMAT: CmdLKeys + CmdL_<long>
        String cmdLKeys = st.getString("CmdLKeys").orElse("");
        if (!cmdLKeys.isEmpty()) {
            // if present, just read the one key we care about
            v = st.getInt("CmdL_" + lk).orElse(0);
        } else {
            // LEGACY fallback (if you still have old worlds)
            var cd = st.getCompound("CommanderDamage").orElse(new CompoundTag());
            String key = shortPos(selectedCmdTarget);
            v = cd.contains(key) ? cd.getInt(key).orElse(0) : 0;
        }

        v = Math.max(0, v);
        cmdCache.put(selectedCmdTarget, v);

        addHeader(x, y, w, nm);

        int fy = y + 26;
        int fieldW = 60;
        int btnW = ps(20);
        int gap = 6;

        int fx = x + 10;

        var field = new EditBox(font, fx, fy, fieldW, 18, Component.literal(""));
        settingCmdProgrammatically = true;
        field.setValue(Integer.toString(v));
        settingCmdProgrammatically = false;

        applyCommanderLethalStyle(field, v);

        field.setResponder(s -> {
            if (settingCmdProgrammatically) return;

            int oldV = cmdCache.getOrDefault(selectedCmdTarget, 0);
            int newV;
            try { newV = Integer.parseInt(s.trim()); }
            catch (Exception ignored) { return; }

            newV = Math.max(0, Math.min(9999, newV));
            if (newV == oldV) return;

            cmdCache.put(selectedCmdTarget, newV);
            applyCommanderLethalStyle(field, newV);

            int delta = newV - oldV;
            ClientPlayNetworking.send(new LifePointPackets.AddCommanderDamageC2S(pos, selectedCmdTarget, delta));
        });

        addRenderableWidget(field);
        cmdFieldToOther.put(field, selectedCmdTarget);

        int minusX = fx + fieldW + gap;
        int plusX  = minusX + btnW + 2;

        addRenderableWidget(Button.builder(Component.literal("-"), b -> {
            int oldV = cmdCache.getOrDefault(selectedCmdTarget, 0);
            if (oldV <= 0) return;
            int newV = oldV - 1;
            cmdCache.put(selectedCmdTarget, newV);

            settingCmdProgrammatically = true;
            field.setValue(Integer.toString(newV));
            settingCmdProgrammatically = false;

            applyCommanderLethalStyle(field, newV);

            ClientPlayNetworking.send(new LifePointPackets.AddCommanderDamageC2S(pos, selectedCmdTarget, -1));
        }).bounds(minusX, fy, btnW, 18).build());

        addRenderableWidget(Button.builder(Component.literal("+"), b -> {
            int oldV = cmdCache.getOrDefault(selectedCmdTarget, 0);
            int newV = Math.min(9999, oldV + 1);
            cmdCache.put(selectedCmdTarget, newV);

            settingCmdProgrammatically = true;
            field.setValue(Integer.toString(newV));
            settingCmdProgrammatically = false;

            applyCommanderLethalStyle(field, newV);

            ClientPlayNetworking.send(new LifePointPackets.AddCommanderDamageC2S(pos, selectedCmdTarget, +1));
        }).bounds(plusX, fy, btnW, 18).build());
    }

    private void applyCommanderLethalStyle(EditBox field, int value) {
        int lethal = Math.max(1, editCmdLethal);
        if (value >= lethal) field.setTextColor(0xFFFF5555);
        else field.setTextColor(0xFFE0E0E0);
    }

    // -------------------------------------------------------------------------
    // COUNTERS TAB
    // -------------------------------------------------------------------------

    private void buildCountersTabWireframe(CompoundTag st, int x0, int y0, int w0, int h0) {
        var counters = st.getCompound("Counters").orElse(new CompoundTag());

        int gap = ps(10);
        int leftW = Math.min(260, (w0 - gap) / 2);
        int rightW = w0 - leftW - gap;

        int leftX = x0;
        int rightX = x0 + leftW + gap;

        midCols = new Rect[]{
                new Rect(leftX, y0, leftW, h0),
                new Rect(rightX, y0, rightW, h0),
        };

        addHeader(leftX, y0, leftW, "Counters");
        addHeader(rightX, y0, rightW, "Counter Editor");

        int listY = y0 + 26;
        int rowH = 20;

        // build ordered key list
        List<String> keys = new ArrayList<>(counters.keySet());
        keys.replaceAll(LifePointScreen::normalizeCounterKey);
        keys.removeIf(String::isBlank);

        if (!keys.contains("poison")) keys.add("poison");
        if (!keys.contains("energy")) keys.add("energy");
        if (!keys.contains("experience")) keys.add("experience");

        keys.remove("poison"); keys.add(0, "poison");
        keys.remove("energy"); keys.add(Math.min(1, keys.size()), "energy");
        keys.remove("experience"); keys.add(Math.min(2, keys.size()), "experience");

        if (keys.size() > 3) {
            List<String> rest = new ArrayList<>(keys.subList(3, keys.size()));
            rest.sort(String::compareToIgnoreCase);
            keys = new ArrayList<>(keys.subList(0, 3));
            keys.addAll(rest);
        }

        countersListKeys = keys;

        // Left list viewport (manual render)
        int leftPad = 6;
        int vpX = leftX + leftPad;
        int vpY = listY;
        int vpW = leftW - leftPad * 2;

        int addBlockH = 18 + 22 + 18;
        int vpH = Math.max(60, (y0 + h0 - 10) - vpY - addBlockH - 8);
        countersListViewport = new Rect(vpX, vpY, vpW, vpH);

        countersListContentH = countersListKeys.size() * rowH;
        int maxScroll = Math.max(0, countersListContentH - countersListViewport.h);
        countersListScroll = Math.max(0, Math.min(countersListScroll, maxScroll));

        // Add counter block under list
        int addY = countersListViewport.y + countersListViewport.h + 8;

        addCounterField = new EditBox(font, leftX + 6, addY, leftW - 12, 18, Component.literal(""));
        addCounterField.setHint(Component.literal("+ Add Counter"));
        addRenderableWidget(addCounterField);

        addRenderableWidget(Button.builder(Component.literal("Add"), b -> {
            String k = addCounterField.getValue();
            if (k == null) return;
            k = k.trim().toLowerCase(Locale.ROOT);
            if (k.isEmpty()) return;

            ClientPlayNetworking.send(new LifePointPackets.SetCounterC2S(pos, k, 0));
            ClientPlayNetworking.send(new LifePointPackets.SetCounterIconC2S(pos, k, "none"));

            counterKey = k;
            init();
        }).bounds(leftX + 6, addY + 22, 60, 18).build());

        // Right editor
        int cur = counters.contains(counterKey) ? counters.getInt(counterKey).orElse(0) : 0;

        var title = Button.builder(Component.literal(counterKey), bb -> {})
                .bounds(rightX + 6, listY, rightW - 12, 18).build();
        title.active = false;
        addRenderableWidget(title);

        valueField = new EditBox(font, rightX + (rightW / 2) - 40, listY + 28, 80, 18, Component.literal(""));
        valueField.setValue(Integer.toString(cur));
        valueField.setResponder(s -> {
            try {
                int v = Integer.parseInt(s.trim());
                v = Math.max(0, v); // âœ… only minimum clamp
                ClientPlayNetworking.send(
                        new LifePointPackets.SetCounterC2S(pos, counterKey, v)
                );

                // snap field back if user typed a negative
                if (!Integer.toString(v).equals(s.trim())) {
                    valueField.setValue(Integer.toString(v));
                    valueField.moveCursorToEnd(false);
                }
            } catch (Exception ignored) {}
        });


        addRenderableWidget(valueField);

        addHintLabel(rightX + 10, listY + 52, "Scroll = Â±1");
        addHintLabel(rightX + 10, listY + 68, "Shift + Scroll = Â±10");

        int resetX = rightX + 10;
        int resetY = listY + 92;

        addRenderableWidget(Button.builder(Component.literal("Reset"), b -> {
            ClientPlayNetworking.send(new LifePointPackets.SetCounterC2S(pos, counterKey, 0));
            init();
        }).bounds(resetX, resetY, 80, 18).build());

        // icon preview + picker
        int prevY = resetY + 26;
        int prevH = 52;
        counterIconPreviewRect = new Rect(rightX + 10, prevY, rightW - 20, prevH);

        int pickerY = prevY + prevH + 8;
        int pickerH = Math.max(60, (y0 + h0 - 10) - pickerY);
        counterIconPickerViewport = new Rect(rightX + 10, pickerY, rightW - 20, pickerH);
        counterIconPickerScroll = Math.max(0, counterIconPickerScroll);
    }

    // -------------------------------------------------------------------------
    // PODS TAB
    // -------------------------------------------------------------------------

    private void buildPodsTabWireframe(CompoundTag st, int x0, int y0, int w0, int h0) {
        requestGroupsIfNeeded();
        requestNearbyScanIfNeeded();

        if (selectedGroupId == null) {
            UUID gid = LifePointClientState.getGroupIdFor(pos);
            if (gid != null) selectGroup(gid);
        }

        if (awaitingCreateSelect) {
            Set<UUID> now = LifePointClientState.GROUP_NAMES.keySet();
            for (UUID id : now) {
                if (!createBeforeIds.contains(id)) {
                    selectGroup(id);
                    awaitingCreateSelect = false;
                    break;
                }
            }
        }

        int gap = ps(10);
        int colW = (w0 - gap * 2) / 3;

        int podsX = x0;
        int availX = x0 + colW + gap;
        int inpodX = x0 + (colW + gap) * 2;

        midCols = new Rect[] {
                new Rect(podsX,  y0, colW, h0),
                new Rect(availX, y0, colW, h0),
                new Rect(inpodX, y0, colW, h0),
        };

        addHeader(podsX, y0, colW, "Pods");
        addHeader(availX, y0, colW, "Available Blocks");
        addHeader(inpodX, y0, colW, "In Pod");

        int listY = y0 + 26;
        int rowH = 20;

        // Left pods list viewport (manual draw)
        int vpX = podsX + 6;
        int vpY = listY;
        int vpW = colW - 12;
        int vpH = Math.max(60, (y0 + h0 - 10) - vpY);
        podsListViewport = new Rect(vpX, vpY, vpW, vpH);

        // "+ Create" footer is sticky
        int footerH = rowH;
        int listH = Math.max(0, podsListViewport.h - footerH);

        var groups = LifePointClientState.groupsList();
        podsListContentH = groups.size() * rowH;

        int maxScroll = Math.max(0, podsListContentH - listH);
        podsListScroll = Math.max(0, Math.min(podsListScroll, maxScroll));

        if (podsAutoScrollPending && selectedGroupId != null) {
            int selIndex = -1;
            for (int i = 0; i < groups.size(); i++) {
                if (Objects.equals(groups.get(i).id(), selectedGroupId)) { selIndex = i; break; }
            }
            if (selIndex >= 0) {
                int targetY = selIndex * rowH;
                int centered = targetY - (listH / 2) + (rowH / 2);
                podsListScroll = Math.max(0, Math.min(maxScroll, centered));
            }
            podsAutoScrollPending = false;
        }

        // If no selected pod yet, stop here (still render left list)
        if (selectedGroupId == null) {
            addHintLabel(availX + 10, listY, "Select a Pod");
            addHintLabel(inpodX + 10, listY, "Select a Pod");
            return;
        }

        // Gather candidates from scan + current selection/order
        List<BlockPos> found = (lastScanOrigin != null)
                ? LifePointClientState.NEARBY_RESULTS.getOrDefault(lastScanOrigin, List.of())
                : List.of();

        // Which pod a block is already in
        Map<BlockPos, String> inPodName = new HashMap<>();
        for (var g : LifePointClientState.GROUPS.values()) {
            if (g == null || g.order == null) continue;
            if (g.id.equals(selectedGroupId)) continue;

            String nm = (g.name == null || g.name.isBlank())
                    ? LifePointClientState.groupName(g.id)
                    : g.name;
            if (nm == null || nm.isBlank()) nm = "Unknown";

            for (BlockPos bp : g.order) inPodName.put(bp, nm);
        }

        LinkedHashSet<BlockPos> candidates = new LinkedHashSet<>(found);
        candidates.addAll(orderedMembers);
        candidates.addAll(selectedMembers);

        // Available list = candidates not already selected
        lockedAvailRects.clear();
        List<BlockPos> available = new ArrayList<>();
        for (BlockPos p : candidates) {
            if (selectedMembers.contains(p)) continue;
            available.add(p);
        }

        available.sort(Comparator
                .comparing((BlockPos p) -> {
                    String n = LifePointClientState.nearbyName(lastScanOrigin, p);
                    return (n == null ? "" : n.toLowerCase(Locale.ROOT));
                })
                .thenComparingInt(BlockPos::getX)
                .thenComparingInt(BlockPos::getY)
                .thenComparingInt(BlockPos::getZ)
        );

        // Middle/right lists are button-based (kept as-is)
        int maxRows = 8;

        if (available.isEmpty()) {
            addHintLabel(availX + 10, listY, "No nearby blocks found");
            addHintLabel(availX + 10, listY + 16, "Press Rescan (or reopen the screen)");
        }

        for (int i = 0; i < Math.min(available.size(), maxRows); i++) {
            BlockPos p = available.get(i);

            String name = LifePointClientState.nearbyName(lastScanOrigin, p);
            if (name == null || name.isBlank()) name = shortPos(p);

            boolean full = selectedMembers.size() >= MAX_GROUP_MEMBERS;

            String otherPod = inPodName.get(p);
            boolean locked = (otherPod != null);

            String label = locked
                    ? "ðŸ”’ " + name + " (In pod " + otherPod + ")"
                    : "â˜ " + name;

            label = sanitizeUiText(label);
            int bx = availX + 6;
            int by = listY + i * rowH;
            int bw = colW - 12;

            var b = Button.builder(Component.literal(label), bb -> {
                if (locked) return;
                toggleMember(p);
                init();
            }).bounds(bx, by, bw, 18).build();

            b.active = !locked && !full;
            addRenderableWidget(b);

            if (locked) {
                Rect rr = new Rect(bx, by, bw, 18);
                lockedAvailRects.put(p, new LockedInfo(rr, otherPod));
            }
        }

        // Right: selected in pod, with reorder buttons
        List<BlockPos> selectedOrdered = new ArrayList<>();
        for (BlockPos p : orderedMembers) if (selectedMembers.contains(p) && !selectedOrdered.contains(p)) selectedOrdered.add(p);
        for (BlockPos p : selectedMembers) if (!selectedOrdered.contains(p)) selectedOrdered.add(p);

        for (int i = 0; i < Math.min(selectedOrdered.size(), maxRows); i++) {
            BlockPos p = selectedOrdered.get(i);

            String name = LifePointClientState.nearbyName(lastScanOrigin, p);
            if (name == null || name.isBlank()) name = shortPos(p);

            int rowY = listY + i * rowH;
            int baseX = inpodX + 6;
            int totalW = colW - 12;

            int btnW = ps(18);
            int gapBtns = 2;

            int mainW = totalW - (btnW * 2 + gapBtns * 2);

            addRenderableWidget(Button.builder(Component.literal("â˜‘ " + name), bb -> {
                toggleMember(p);
                init();
            }).bounds(baseX, rowY, mainW, 18).build());

            addRenderableWidget(Button.builder(Component.literal("â–²"), bb -> {
                moveMemberInOrder(p, -1);
                init();
            }).bounds(baseX + mainW + gapBtns, rowY, btnW, 18).build());

            addRenderableWidget(Button.builder(Component.literal("â–¼"), bb -> {
                moveMemberInOrder(p, +1);
                init();
            }).bounds(baseX + mainW + gapBtns + btnW + gapBtns, rowY, btnW, 18).build());
        }

        // Bottom controls in right column
        int controlsY = y0 + h0 - 72;

        groupNameField = new EditBox(font, inpodX + 6, controlsY, colW - 12, 18, Component.literal(""));
        settingGroupNameProgrammatically = true;
        groupNameField.setValue(groupNameCurrent == null ? "" : groupNameCurrent);
        settingGroupNameProgrammatically = false;
        groupNameField.setResponder(s -> {
            if (settingGroupNameProgrammatically) return;
            groupNameCurrent = (s == null ? "" : s);
            groupDirty = true;
        });
        addRenderableWidget(groupNameField);

        addRenderableWidget(Button.builder(Component.literal("Save Pod"), b -> {
            var finalOrder = orderedMembers.stream().filter(selectedMembers::contains).distinct().toList();
            String nm = (groupNameCurrent == null ? "" : groupNameCurrent);
            ClientPlayNetworking.send(new LifePointPackets.SaveGroupC2S(selectedGroupId, nm, finalOrder));
            groupNameOriginal = groupNameCurrent;
            groupDirty = false;
            init();
        }).bounds(inpodX + 6, controlsY + 22, (colW - 12) / 2 - 4, 18).build());

        addRenderableWidget(Button.builder(Component.literal("Delete"), b ->
                Minecraft.getInstance().setScreen(new ConfirmDeleteGroupScreen(this, selectedGroupId))
        ).bounds(inpodX + 6 + (colW - 12) / 2 + 4, controlsY + 22, (colW - 12) / 2 - 4, 18).build());
    }

    private void moveMemberInOrder(BlockPos p, int dir) {
        int i = orderedMembers.indexOf(p);
        if (i < 0) return;

        int j = i + (dir < 0 ? -1 : 1);
        while (j >= 0 && j < orderedMembers.size()) {
            BlockPos q = orderedMembers.get(j);
            if (selectedMembers.contains(q)) break;
            j += (dir < 0 ? -1 : 1);
        }
        if (j < 0 || j >= orderedMembers.size()) return;

        Collections.swap(orderedMembers, i, j);
        groupDirty = true;
    }

    // -------------------------------------------------------------------------
    // EDIT TAB (kept; minimal cleanup)
    // -------------------------------------------------------------------------

    private void buildEditTabWireframe(CompoundTag st, int x0, int y0, int w0, int h0) {
        int gap = ps(10);
        int colW = (w0 - gap * 2) / 3;

        int leftX  = x0;
        int midX   = x0 + colW + gap;
        int rightX = x0 + (colW + gap) * 2;

        midCols = new Rect[] {
                new Rect(leftX,  y0, colW, h0),
                new Rect(midX,   y0, colW, h0),
                new Rect(rightX, y0, colW, h0),
        };

        addHeader(leftX,  y0, colW, "Presets");
        addHeader(midX,   y0, colW, "Appearance");
        addHeader(rightX, y0, colW, "Preview");

        final int pad = 6;

        // content starts below header
        int topY = y0 + 26;

        // footer buttons area (Apply/Save/Delete) (sticky)
        int footerBtnH = 18;
        int footerGapTop = 8;
        int footerY = y0 + h0 - footerBtnH - 8;

        // --- Preset name field (FIX: put it BELOW header, not above it) ---
        int presetFieldY = topY; // directly under the header
        editPresetNameField = new EditBox(
                font,
                leftX + pad,
                presetFieldY,
                colW - pad * 2,
                18,
                Component.literal("")
        );
        editPresetNameField.setHint(Component.literal("Preset name"));
        editPresetNameField.setMaxLength(32);
        editPresetNameField.setValue(selectedPresetKey == null ? "" : selectedPresetKey);
        editPresetNameField.setResponder(s -> {
            if (s == null) return;
            selectedPresetKey = s.trim();
        });
        addRenderableWidget(editPresetNameField);

        // --- Preset list viewport (below name field) ---
        int listY = presetFieldY + 18 + 8;
        int optsTopY = listY + 120 + 10; // where the checkboxes start visually (used in drawEditPresets)
        int listH = Math.max(60, optsTopY - listY - 2);
        editPresetListVp = new Rect(leftX + pad, listY, colW - pad * 2, listH);

        // --- Include-counters viewport: must END above footer buttons ---
        // This viewport is used only when includeCustomCounters=true
        int includeListY = optsTopY + 58; // include checkbox + save-to-world + spacing
        int includeListBottom = footerY - footerGapTop;
        int includeListH = Math.max(40, includeListBottom - includeListY);
        editCounterIncludeVp = new Rect(leftX + pad, includeListY, colW - pad * 2, includeListH);

        // --- Footer buttons (sticky) ---
        int btnW = Math.max(24, (colW - pad * 2 - 8 * 2) / 3);
        int bx = leftX + pad;

        addRenderableWidget(Button.builder(Component.literal("Apply"), b -> {
            if (selectedPresetKey == null || selectedPresetKey.isBlank()) return;
            var p = LifePointPresetStore.get(selectedPresetKey);
            if (p == null) return;

            editPlayerColor = p.playerColor & 0xFFFFFF;
            editLifeColor   = p.lifeColor & 0xFFFFFF;
            editIconKey     = (p.iconKey == null || p.iconKey.isBlank()) ? "none" : p.iconKey;
            editIconSwapColor = p.iconSwapColor & 0xFFFFFF;
            boolean applyFormat = canEditFormat();
            if (applyFormat) {
                editFormatKey = LifeFormat.normalizeKey(p.formatKey);
            }

            ClientPlayNetworking.send(new LifePointPackets.SetPlayerColorC2S(pos, editPlayerColor));
            ClientPlayNetworking.send(new LifePointPackets.SetColorC2S(pos, editLifeColor));
            ClientPlayNetworking.send(new LifePointPackets.SetIconKeyC2S(pos, editIconKey));
            ClientPlayNetworking.send(new LifePointPackets.SetIconSwapColorC2S(pos, editIconSwapColor));
            if (applyFormat) {
                ClientPlayNetworking.send(new LifePointPackets.SetFormatKeyC2S(pos, editFormatKey));
            }

            if (includeCustomCounters && p.customCounters != null) {
                for (var e : p.customCounters.entrySet()) {
                    ClientPlayNetworking.send(new LifePointPackets.SetCounterC2S(pos, e.getKey(), e.getValue()));
                }
            }

            init();
        }).bounds(bx, footerY, btnW, footerBtnH).build());
        bx += btnW + 8;

        addRenderableWidget(Button.builder(Component.literal("Save"), b -> {
            if (selectedPresetKey == null || selectedPresetKey.isBlank()) selectedPresetKey = "Preset";

            Map<String, Integer> custom = null;
            if (includeCustomCounters) {
                custom = new LinkedHashMap<>();
                var counters = st.getCompound("Counters").orElse(new CompoundTag());
                for (String k : includedCustomCounterKeys) {
                    if (!counters.contains(k)) continue;
                    custom.put(k, counters.getInt(k).orElse(0));
                }
            }

            LifePointPresetStore.upsert(new LifePointPresetStore.Preset(
                    selectedPresetKey,
                    editPlayerColor,
                    editLifeColor,
                    (editIconKey == null ? "none" : editIconKey),
                    (editFormatKey == null ? "commander" : editFormatKey),
                    editCmdLethal,
                    editIconSwapColor,
                    custom
            ));

            // OPTIONAL server save (you implement)
            if (saveToWorld) {
                // ClientPlayNetworking.send(new LifePointPackets.SavePresetToWorldC2S(pos, selectedPresetKey, ...));
            }

            init();
        }).bounds(bx, footerY, btnW, footerBtnH).build());
        bx += btnW + 8;

        addRenderableWidget(Button.builder(Component.literal("Delete"), b -> {
            if (selectedPresetKey == null || selectedPresetKey.isBlank()) return;
            LifePointPresetStore.delete(selectedPresetKey);
            selectedPresetKey = null;
            init();
        }).bounds(bx, footerY, btnW, footerBtnH).build());

        // Middle appearance viewport
        editAppearanceVp = new Rect(midX + pad, topY, colW - pad * 2, h0 - 26 - 10);

        // --- Hex editors (editable) ---
// Layout inside appearance panel (non-scrolled top section)
        int ax = editAppearanceVp.x + 8;
        int aw = editAppearanceVp.w - 16;
        int y = editAppearanceVp.y + 8;

        int labelGap = 12;
        int paletteH = 18 * 2 + 6; // tile rows (2) + gap (1)
        int hexH = 18;
        int hexGap = 6;
        int barH = 14;
        int sectionGap = 14;

// Player section
        y += labelGap;                 // space after "Player Color" label (drawn)
        y += paletteH + hexGap;        // palette area + gap to hex

        editPlayerHexField = new EditBox(font, ax, y, aw, hexH, Component.literal(""));
        editPlayerHexField.setMaxLength(7);
        editPlayerHexField.setHint(Component.literal("#RRGGBB"));
        editPlayerHexField.setValue(String.format("#%06X", editPlayerColor & 0xFFFFFF));
        editPlayerHexField.setResponder(s -> onHexFieldChanged(editPlayerHexField, true, s));
        addRenderableWidget(editPlayerHexField);

        y += hexH + 6;
        editPlayerBarRect = new Rect(ax, y, aw, barH);
        y += barH + sectionGap;

        // Life section
        y += labelGap;                 // space after "Life Color" label (drawn)
        y += paletteH + hexGap;

        editLifeHexField = new EditBox(font, ax, y, aw, hexH, Component.literal(""));
        editLifeHexField.setMaxLength(7);
        editLifeHexField.setHint(Component.literal("#RRGGBB"));
        editLifeHexField.setValue(String.format("#%06X", editLifeColor & 0xFFFFFF));
        editLifeHexField.setResponder(s -> onHexFieldChanged(editLifeHexField, false, s));
        addRenderableWidget(editLifeHexField);

        editIconHexField = new EditBox(font, 0, 0, 0, 18, Component.literal(""));
        editIconHexField.setMaxLength(7);
        editIconHexField.setHint(Component.literal("#RRGGBB"));
        editIconHexField.setValue(String.format("#%06X", editIconSwapColor & 0xFFFFFF));
        editIconHexField.setResponder(s -> onIconHexFieldChanged(editIconHexField, s));
        addRenderableWidget(editIconHexField);

        y += hexH + 6;
        editLifeBarRect = new Rect(ax, y, aw, barH);
        // Right preview rect
        editPreviewRect = new Rect(rightX + pad, topY, colW - pad * 2, h0 - 26 - 10);
    }


    // -------------------------------------------------------------------------
    // DRAW HELPERS
    // -------------------------------------------------------------------------

    private boolean ptIn(Rect r, double mx, double my) {
        return r != null && mx >= r.x && mx < r.x + r.w && my >= r.y && my < r.y + r.h;
    }

    private void onIconHexFieldChanged(EditBox field, String raw) {
        if (settingHexProgrammatically) return;
        if (field == null) return;

        String s = (raw == null) ? "" : raw.trim();
        if (s.isEmpty()) return;

        if (s.startsWith("0x") || s.startsWith("0X")) s = s.substring(2);

        if (!s.startsWith("#") && s.length() <= 6) {
            boolean ok = true;
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                boolean hex = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
                if (!hex) { ok = false; break; }
            }
            if (ok) {
                settingHexProgrammatically = true;
                field.setValue("#" + s);
                field.moveCursorToEnd(false);
                settingHexProgrammatically = false;
                return;
            }
        }

        int v = parseHexColorOrNeg(s);
        if (v < 0) return;

        if ((editIconSwapColor & 0xFFFFFF) == v) return;
        editIconSwapColor = v;
        ClientPlayNetworking.send(new LifePointPackets.SetIconSwapColorC2S(pos, editIconSwapColor));
    }

    private void drawHudStrips(GuiGraphics ctx) {
        int topY1 = topHudStripH();
        ctx.fill(0, 0, width, topY1, HUD_STRIP_BG);
        ctx.fill(0, topY1 - 1, width, topY1, HUD_STRIP_LINE);

        int botY0 = Math.max(0, height - bottomHudStripH());
        ctx.fill(0, botY0, width, height, HUD_STRIP_BG);
        ctx.fill(0, botY0, width, botY0 + 1, HUD_STRIP_LINE);
    }

    private void drawMiddleColumnPanels(GuiGraphics ctx) {
        if (midCols == null) return;
        for (Rect r : midCols) {
            if (r == null) continue;
            ctx.fill(r.x, r.y, r.x + r.w, r.y + r.h, COL_BG);
            ctx.fill(r.x, r.y, r.x + r.w, r.y + 1, COL_BG_EDGE);
        }
    }

    private void drawLifeHealthPanel(GuiGraphics ctx) {
        if (tab != Tab.LIFE || lifeHealthPanel == null) return;

        int x = lifeHealthPanel.x;
        int y = lifeHealthPanel.y;
        int w = lifeHealthPanel.w;
        int h = lifeHealthPanel.h;

        int bg   = 0xFF0F0F0F;
        int edge = 0xFF3A3A3A;

        ctx.fill(x, y, x + w, y + h, bg);
        ctx.fill(x, y, x + w, y + 1, edge);
        ctx.fill(x, y + h - 1, x + w, y + h, edge);
        ctx.fill(x, y, x + 1, y + h, edge);
        ctx.fill(x + w - 1, y, x + w, y + h, edge);

        if (lifeFormatLabelRect != null) {
            String label = "Format: " + LifeFormat.displayName(LifePointClientState.getFormatKey(pos));
            ctx.drawCenteredString(
                    font,
                    Component.literal(label),
                    lifeFormatLabelRect.x + lifeFormatLabelRect.w / 2,
                    lifeFormatLabelRect.y,
                    0xFFB0B0B0
            );
        }
    }

    private void drawBigLifeBox(GuiGraphics ctx) {
        if (tab != Tab.LIFE || valueField == null) return;

        int x = valueField.getX();
        int y = valueField.getY();
        int w = valueField.getWidth();
        int h = valueField.getHeight();

        ctx.fill(x, y, x + w, y + h, LIFE_BOX_BG);
        ctx.fill(x, y, x + w, y + 1, LIFE_BOX_EDGE);
        ctx.fill(x, y + h - 1, x + w, y + h, LIFE_BOX_EDGE);
        ctx.fill(x, y, x + 1, y + h, LIFE_BOX_EDGE);
        ctx.fill(x + w - 1, y, x + w, y + h, LIFE_BOX_EDGE);

        String s = valueField.getValue();
        if (s == null || s.isBlank()) s = "0";

        float scale = 2.0f;
        int tw = font.width(s);
        int th = font.lineHeight;

        var m = ctx.pose();
        m.pushMatrix();
        m.translate(new Vector2f(x + (w / 2f), y + (h / 2f)));
        m.scale(new Vector2f(scale, scale));
        ctx.drawString(font, Component.literal(s), (int)(-tw / 2f), (int)(-th / 2f), 0xFFFFFFFF);
        m.popMatrix();
    }

    private void drawFlatRow(GuiGraphics ctx, Rect r, boolean selected, boolean hovered) {
        int bg = selected ? 0xFF1B1B1B : (hovered ? 0xFF161616 : 0xFF141414);
        ctx.fill(r.x, r.y, r.x + r.w, r.y + r.h, bg);
        ctx.fill(r.x, r.y, r.x + r.w, r.y + 1, 0xFF2C2C2C);
    }

    private void drawFormatRow(GuiGraphics ctx, Rect r, boolean selected, boolean hovered, boolean enabled) {
        int bg;
        int edge;
        if (!enabled) {
            bg = selected ? 0xFF24211A : 0xFF111111;
            edge = selected ? 0xFF7A6422 : 0xFF252525;
        } else if (selected) {
            bg = 0xFF1E3A2F;
            edge = 0xFF55C782;
        } else {
            bg = hovered ? 0xFF18221E : 0xFF141414;
            edge = 0xFF2C2C2C;
        }

        ctx.fill(r.x, r.y, r.x + r.w, r.y + r.h, bg);
        ctx.fill(r.x, r.y, r.x + r.w, r.y + 1, edge);
        if (selected) ctx.fill(r.x, r.y, r.x + 2, r.y + r.h, edge);
    }

    private String fitText(String text, int maxWidth) {
        String s = sanitizeUiText(text);
        if (font.width(s) <= maxWidth) return s;

        String dots = "...";
        int dotsW = font.width(dots);
        return font.plainSubstrByWidth(s, Math.max(1, maxWidth - dotsW)) + dots;
    }

    private boolean canEditFormat() {
        UUID gid = LifePointClientState.getGroupIdFor(pos);
        var gv = (gid != null) ? LifePointClientState.getGroup(gid) : null;
        return gv == null || !gv.started;
    }

    // texture dims
    private void cacheDims(Identifier id) {
        if (texDims.containsKey(id)) return;
        try {
            var rm = Minecraft.getInstance().getResourceManager();
            var resOpt = rm.getResource(id);
            if (resOpt.isEmpty()) {
                texDims.put(id, new int[]{16,16});
                return;
            }
            try (var in = resOpt.get().open()) {
                var img = com.mojang.blaze3d.platform.NativeImage.read(in);
                texDims.put(id, new int[]{img.getWidth(), img.getHeight()});
                img.close();
            }
        } catch (Exception e) {
            texDims.put(id, new int[]{16,16});
        }
    }
    private int texW(Identifier id) { return texDims.getOrDefault(id, new int[]{16,16})[0]; }
    private int texH(Identifier id) { return texDims.getOrDefault(id, new int[]{16,16})[1]; }

    private void drawIcon(GuiGraphics ctx, Identifier tex, int x, int y, int w, int h) {
        cacheDims(tex);
        int tw = texW(tex);
        int th = texH(tex);
        if (tw <= 0 || th <= 0) return;

        float scale = Math.min(w / (float) tw, h / (float) th);
        int dw = Math.round(tw * scale);
        int dh = Math.round(th * scale);

        int dx = x + (w - dw) / 2;
        int dy = y + (h - dh) / 2;

        ctx.blit(
                RenderPipelines.GUI_TEXTURED,
                tex,
                dx, dy,
                0f, 0f,
                dw, dh,
                tw, th,
                tw, th
        );
    }

    // icon resolve (counters/ + fallback to set_icons)
    private Identifier counterIconId(String key) {
        if (key == null || key.isBlank()) key = "none";
        key = key.trim().toLowerCase(Locale.ROOT);

        if (key.equals("poison")) return ICON_POISON;
        if (key.equals("energy")) return ICON_ENERGY;
        if (key.equals("experience") || key.equals("xp")) return ICON_EXPERIENCE;

        Identifier cached = iconIdCache.get(key);
        if (cached != null) return cached;

        var rm = Minecraft.getInstance().getResourceManager();

        Identifier a = Identifier.fromNamespaceAndPath("mtgcard", "textures/gui/counters/" + key + ".png");
        if (rm.getResource(a).isPresent()) { iconIdCache.put(key, a); return a; }

        Identifier b = Identifier.fromNamespaceAndPath("mtgcard", "textures/gui/set_icons/" + key + ".png");
        if (rm.getResource(b).isPresent()) { iconIdCache.put(key, b); return b; }

        Identifier none = Identifier.fromNamespaceAndPath("mtgcard", "textures/gui/counters/none.png");
        iconIdCache.put(key, none);
        return none;
    }

    private List<String> loadCounterIconKeys() {
        if (cachedCounterIcons != null) return cachedCounterIcons;

        var rm = Minecraft.getInstance().getResourceManager();
        var foundCounters = rm.listResources("textures/gui/counters", id -> id.getPath().endsWith(".png"));
        var foundSets     = rm.listResources("textures/gui/set_icons", id -> id.getPath().endsWith(".png"));

        LinkedHashSet<String> keys = new LinkedHashSet<>();

        for (Identifier id : foundCounters.keySet()) {
            String path = id.getPath();
            String name = path.substring(path.lastIndexOf('/') + 1);
            if (!name.endsWith(".png")) continue;
            keys.add(name.substring(0, name.length() - 4).toLowerCase(Locale.ROOT));
        }
        for (Identifier id : foundSets.keySet()) {
            String path = id.getPath();
            String name = path.substring(path.lastIndexOf('/') + 1);
            if (!name.endsWith(".png")) continue;
            keys.add(name.substring(0, name.length() - 4).toLowerCase(Locale.ROOT));
        }

        keys.remove("none");
        List<String> list = new ArrayList<>(keys);
        list.sort(String::compareToIgnoreCase);
        list.add(0, "none");

        cachedCounterIcons = list;
        return cachedCounterIcons;
    }

    private static String normalizeCounterKey(String k) {
        if (k == null) return "";
        return k.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeIconKey(String k) {
        if (k == null) return "none";
        k = k.trim().toLowerCase(Locale.ROOT);
        return k.isEmpty() ? "none" : k;
    }

    // -------------------------------------------------------------------------
    // LIFE tab: counters HUD grid
    // -------------------------------------------------------------------------

    private void drawLifeCounterBox(GuiGraphics ctx, int x, int y, int size, Identifier icon, int value, boolean hovered) {
        int bg = hovered ? 0xFF1A1A1A : 0xFF101010;
        int edge = 0xFF3A3A3A;

        ctx.fill(x, y, x + size, y + size, bg);
        ctx.fill(x, y, x + size, y + 1, edge);
        ctx.fill(x, y + size - 1, x + size, y + size, edge);
        ctx.fill(x, y, x + 1, y + size, edge);
        ctx.fill(x + size - 1, y, x + size, y + size, edge);

        int iconPad = 6;
        int iconAreaH = size - 18;
        int iconSize = Math.max(10, Math.min(size - iconPad * 2, iconAreaH - 8));
        int ix = x + (size - iconSize) / 2;
        int iy = y + 6;

        drawIcon(ctx, icon, ix, iy, iconSize, iconSize);

        String s = Integer.toString(Math.max(0, value));
        int tw = font.width(s);
        int tx = x + (size - tw) / 2;
        int ty = y + size - 14;
        ctx.drawString(font, Component.literal(s), tx, ty, 0xFFFFFFFF);
    }

    private void drawLifeCountersHud(GuiGraphics ctx, int mouseX, int mouseY) {
        if (tab != Tab.LIFE || lifeCounterViewport == null) return;

        var st = LifePointClientState.get(pos);
        var counters = st.getCompound("Counters").orElse(new CompoundTag());
        var icons = st.getCompound("CounterIcons").orElse(new CompoundTag());

        List<String> keys = new ArrayList<>(counters.keySet());
        keys.replaceAll(LifePointScreen::normalizeCounterKey);
        keys.removeIf(String::isBlank);

        if (!keys.contains("poison")) keys.add("poison");
        if (!keys.contains("energy")) keys.add("energy");
        if (!keys.contains("experience")) keys.add("experience");

        keys.remove("poison"); keys.add(0, "poison");
        keys.remove("energy"); keys.add(Math.min(1, keys.size()), "energy");
        keys.remove("experience"); keys.add(Math.min(2, keys.size()), "experience");

        if (keys.size() > 3) {
            List<String> rest = new ArrayList<>(keys.subList(3, keys.size()));
            rest.sort(String::compareToIgnoreCase);
            keys = new ArrayList<>(keys.subList(0, 3));
            keys.addAll(rest);
        }

        Rect vp = lifeCounterViewport;

        int boxSize = 48;
        int gap = 8;

        int cols = Math.max(1, (vp.w + gap) / (boxSize + gap));
        int xPad = 6;
        int yPad = 6;

        int startX = vp.x + xPad;
        int startY = vp.y + yPad;

        int rows = (int)Math.ceil(keys.size() / (double)cols);
        lifeCounterContentH = yPad + rows * (boxSize + gap) - gap + yPad;

        int maxScroll = Math.max(0, lifeCounterContentH - vp.h);
        lifeCounterScroll = Math.max(0, Math.min(lifeCounterScroll, maxScroll));

        lifeCounterRects.clear();

        ctx.enableScissor(vp.x, vp.y, vp.x + vp.w, vp.y + vp.h);

        for (int i = 0; i < keys.size(); i++) {
            String k = keys.get(i);
            int col = i % cols;
            int row = i / cols;

            int x = startX + col * (boxSize + gap);
            int y = startY + row * (boxSize + gap) - lifeCounterScroll;

            if (y + boxSize < vp.y || y > vp.y + vp.h) continue;

            int v = counters.contains(k) ? counters.getInt(k).orElse(0) : 0;

            String iconKey;
            if (k.equals("poison")) iconKey = "poison";
            else if (k.equals("energy")) iconKey = "energy";
            else if (k.equals("experience")) iconKey = "experience";
            else iconKey = normalizeIconKey(icons.contains(k) ? icons.getString(k).orElse("none") : "none");

            Identifier tex = counterIconId(iconKey);

            Rect r = new Rect(x, y, boxSize, boxSize);
            lifeCounterRects.put(k, r);

            boolean hovered = mouseX >= r.x && mouseX < r.x + r.w && mouseY >= r.y && mouseY < r.y + r.h;
            drawLifeCounterBox(ctx, r.x, r.y, boxSize, tex, v, hovered);
        }

        ctx.disableScissor();

        drawScrollbar(ctx, ScrollId.LIFE_COUNTERS, vp, lifeCounterContentH, lifeCounterScroll);
    }

    // -------------------------------------------------------------------------
    // COUNTERS tab draw
    // -------------------------------------------------------------------------

    private void drawCountersList(GuiGraphics ctx, int mouseX, int mouseY) {
        if (countersListViewport == null) return;

        Rect vp = countersListViewport;

        ctx.fill(vp.x, vp.y, vp.x + vp.w, vp.y + vp.h, 0xFF0F0F0F);
        ctx.fill(vp.x, vp.y, vp.x + vp.w, vp.y + 1, 0xFF3A3A3A);
        ctx.fill(vp.x, vp.y + vp.h - 1, vp.x + vp.w, vp.y + vp.h, 0xFF3A3A3A);

        ctx.enableScissor(vp.x, vp.y, vp.x + vp.w, vp.y + vp.h);

        int rowH = 20;

        var st = LifePointClientState.get(pos);
        var counters = st.getCompound("Counters").orElse(new CompoundTag());
        var icons = st.getCompound("CounterIcons").orElse(new CompoundTag());

        for (int i = 0; i < countersListKeys.size(); i++) {
            String k = countersListKeys.get(i);
            int v = counters.contains(k) ? counters.getInt(k).orElse(0) : 0;

            int y = vp.y + i * rowH - countersListScroll;
            if (y + 18 < vp.y || y > vp.y + vp.h) continue;

            boolean sel = Objects.equals(counterKey, k);

            int rx = vp.x + 2;
            int rw = vp.w - 4;

            int bg = sel ? 0xFF1B1B1B : 0xFF141414;
            ctx.fill(rx, y, rx + rw, y + 18, bg);
            ctx.fill(rx, y, rx + rw, y + 1, 0xFF2C2C2C);

            String iconKey;
            if (k.equals("poison")) iconKey = "poison";
            else if (k.equals("energy")) iconKey = "energy";
            else if (k.equals("experience")) iconKey = "experience";
            else iconKey = icons.contains(k) ? icons.getString(k).orElse("none") : "none";

            iconKey = normalizeIconKey(iconKey);
            Identifier tex = counterIconId(iconKey);

            int iconSz = 12;
            int ix = rx + 6;
            int iy = y + 3;
            drawIcon(ctx, tex, ix, iy, iconSz, iconSz);

            String label = (sel ? "â–¶ " : "  ") + k + "   " + v;
            label = sanitizeUiText(label);
            ctx.drawString(font, Component.literal(label), rx + 6 + iconSz + 6, y + 5, 0xFFFFFFFF);
        }

        ctx.disableScissor();

        if (countersListContentH > vp.h) {
            int maxScroll = Math.max(0, countersListContentH - vp.h);
            int barW = 4;
            int barX = vp.x + vp.w - barW - 2;
            int barY = vp.y + 2;
            int barH = vp.h - 4;

            float t = (maxScroll <= 0) ? 0f : (countersListScroll / (float)maxScroll);
            int thumbH = Math.max(10, (int)(barH * (vp.h / (float)countersListContentH)));
            int thumbY = barY + (int)((barH - thumbH) * t);

            ctx.fill(barX, barY, barX + barW, barY + barH, 0x66222222);
            ctx.fill(barX, thumbY, barX + barW, thumbY + thumbH, 0xAA888888);
        }
        drawScrollbar(ctx, ScrollId.COUNTERS_LIST, vp, countersListContentH, countersListScroll);
    }

    private void drawCounterIconPreviewAndPicker(GuiGraphics ctx, int mouseX, int mouseY) {
        if (counterIconPreviewRect == null || counterIconPickerViewport == null) return;

        // Preview
        Rect p = counterIconPreviewRect;
        ctx.fill(p.x, p.y, p.x + p.w, p.y + p.h, 0xFF0F0F0F);
        ctx.fill(p.x, p.y, p.x + p.w, p.y + 1, 0xFF3A3A3A);
        ctx.fill(p.x, p.y + p.h - 1, p.x + p.w, p.y + p.h, 0xFF3A3A3A);

        var st = LifePointClientState.get(pos);
        var icons = st.getCompound("CounterIcons").orElse(new CompoundTag());

        String ck = normalizeCounterKey(counterKey);
        String iconKey;
        if (ck.equals("poison")) iconKey = "poison";
        else if (ck.equals("energy")) iconKey = "energy";
        else if (ck.equals("experience")) iconKey = "experience";
        else iconKey = icons.contains(ck) ? icons.getString(ck).orElse("none") : "none";

        iconKey = normalizeIconKey(iconKey);
        Identifier tex = counterIconId(iconKey);

        int size = Math.min(p.h - 10, 40);
        int ix = p.x + 10;
        int iy = p.y + (p.h - size) / 2;
        drawIcon(ctx, tex, ix, iy, size, size);

        ctx.drawString(font, Component.literal("Selected: " + iconKey), ix + size + 10, p.y + (p.h / 2) - 4, 0xFFB0B0B0);

        // Picker grid
        Rect vp = counterIconPickerViewport;

        ctx.fill(vp.x, vp.y, vp.x + vp.w, vp.y + vp.h, 0xFF0F0F0F);
        ctx.fill(vp.x, vp.y, vp.x + vp.w, vp.y + 1, 0xFF3A3A3A);
        ctx.fill(vp.x, vp.y + vp.h - 1, vp.x + vp.w, vp.y + vp.h, 0xFF3A3A3A);

        counterPickerRects.clear();

        List<String> keys = loadCounterIconKeys();

        int box = 22;
        int gap = 6;
        int topPad = 6;
        int bottomPad = 6;

        int cols = Math.max(1, (vp.w - 10 + gap) / (box + gap));
        int x0 = vp.x + 6;
        int y0 = vp.y + topPad;

        int rows = (int) Math.ceil(keys.size() / (double) cols);
        counterIconPickerContentH = topPad + rows * (box + gap) - gap + bottomPad;

        int maxScroll = Math.max(0, counterIconPickerContentH - vp.h);
        counterIconPickerScroll = Math.max(0, Math.min(counterIconPickerScroll, maxScroll));

        ctx.enableScissor(vp.x, vp.y, vp.x + vp.w, vp.y + vp.h);

        for (int i = 0; i < keys.size(); i++) {
            String key = keys.get(i);

            int c = i % cols;
            int r = i / cols;

            int x = x0 + c * (box + gap);
            int y = y0 + r * (box + gap) - counterIconPickerScroll;

            if (y + box < vp.y || y > vp.y + vp.h) continue;

            Rect rr = new Rect(x, y, box, box);
            counterPickerRects.put(key, rr);

            boolean hovered = mouseX >= rr.x && mouseX < rr.x + rr.w && mouseY >= rr.y && mouseY < rr.y + rr.h;
            int bg = hovered ? 0xFF1A1A1A : 0xFF141414;

            ctx.fill(rr.x, rr.y, rr.x + rr.w, rr.y + rr.h, bg);
            ctx.fill(rr.x, rr.y, rr.x + rr.w, rr.y + 1, 0xFF2C2C2C);

            drawIcon(ctx, counterIconId(key), rr.x + 3, rr.y + 3, rr.w - 6, rr.h - 6);
        }

        ctx.disableScissor();

        if (counterIconPickerContentH > vp.h) {
            int barW = 4;
            int barX = vp.x + vp.w - barW - 2;
            int barY = vp.y + 2;
            int barH = vp.h - 4;

            float t = (maxScroll <= 0) ? 0f : (counterIconPickerScroll / (float)maxScroll);
            int thumbH = Math.max(10, (int)(barH * (vp.h / (float)counterIconPickerContentH)));
            int thumbY = barY + (int)((barH - thumbH) * t);

            ctx.fill(barX, barY, barX + barW, barY + barH, 0x66222222);
            ctx.fill(barX, thumbY, barX + barW, thumbY + thumbH, 0xAA888888);
        }
        drawScrollbar(ctx, ScrollId.COUNTER_ICON_PICKER, vp, counterIconPickerContentH, counterIconPickerScroll);
    }

    // -------------------------------------------------------------------------
    // LIFE tab: other players list
    // -------------------------------------------------------------------------

    private void drawOtherPlayersFlat(GuiGraphics ctx, int mouseX, int mouseY) {
        if (otherPlayersViewport == null) return;

        Rect vp = otherPlayersViewport;
        otherPlayerRects.clear();

        ctx.fill(vp.x, vp.y, vp.x + vp.w, vp.y + vp.h, 0xFF0F0F0F);
        ctx.fill(vp.x, vp.y, vp.x + vp.w, vp.y + 1, 0xFF3A3A3A);

        int rowH = 20;
        ctx.enableScissor(vp.x, vp.y, vp.x + vp.w, vp.y + vp.h);

        UUID gid = LifePointClientState.getGroupIdFor(pos);
        List<BlockPos> teammates = LifePointClientState.sharedTeamMembers(pos);

        for (int i = 0; i < otherPlayersOrdered.size(); i++) {
            BlockPos other = otherPlayersOrdered.get(i);
            int y = vp.y + i * rowH;
            if (y + 18 < vp.y || y > vp.y + vp.h) continue;

            Rect r = new Rect(vp.x + 2, y, vp.w - 4, 18);
            otherPlayerRects.put(other, r);

            boolean sel = (selectedCmdTarget != null && selectedCmdTarget.equals(other));
            boolean hov = mouseX >= r.x && mouseX < r.x + r.w && mouseY >= r.y && mouseY < r.y + r.h;
            drawFlatRow(ctx, r, sel, hov);
            boolean teammateRow = teammates.contains(other);
            if (teammateRow) {
                ctx.fill(r.x, r.y, r.x + r.w, r.y + 1, 0xFFFFD54F);
                ctx.fill(r.x, r.y + r.h - 1, r.x + r.w, r.y + r.h, 0xFFFFD54F);
                ctx.fill(r.x, r.y, r.x + 2, r.y + r.h, 0xFFFFD54F);
            }

            String nm = (gid != null) ? LifePointClientState.groupMemberName(gid, other) : "";
            if (nm == null || nm.isBlank()) nm = LifePointClientState.get(other).getString("DisplayName").orElse("");
            if (nm == null || nm.isBlank()) nm = shortPos(other);

            String rowLabel = sanitizeUiText((sel ? "â–¶ " : "  ") + nm);
            int textColor = teammateRow ? 0xFFFFD54F : 0xFFFFFFFF;
            ctx.drawString(font, Component.literal(rowLabel), r.x + 6, r.y + 5, textColor);
        }

        ctx.disableScissor();
    }

    // -------------------------------------------------------------------------
    // PODS tab: left pods list draw + tooltip
    // -------------------------------------------------------------------------

    private void drawPodsFlat(GuiGraphics ctx, int mouseX, int mouseY) {
        if (podsListViewport == null) return;
        podsListScrollVp = null;

        Rect vp = podsListViewport;
        podRowRects.clear();
        podCreateRect = null;

        int rowH = 20;
        var groups = LifePointClientState.groupsList();

        int footerH = rowH;
        int listH = Math.max(0, vp.h - footerH);
        int listTop = vp.y;
        int listBottom = vp.y + listH;

        podsListScrollVp = new Rect(vp.x, vp.y, vp.w, listH);

        podsListContentH = groups.size() * rowH;

        int maxScroll = Math.max(0, podsListContentH - listH);
        podsListScroll = Math.max(0, Math.min(podsListScroll, maxScroll));

        ctx.fill(vp.x, vp.y, vp.x + vp.w, vp.y + vp.h, 0xFF0F0F0F);
        ctx.fill(vp.x, vp.y, vp.x + vp.w, vp.y + 1, 0xFF3A3A3A);

        ctx.fill(vp.x, listBottom, vp.x + vp.w, listBottom + 1, 0xFF2C2C2C);

        ctx.enableScissor(vp.x, listTop, vp.x + vp.w, listBottom);

        int yBase = listTop - podsListScroll;

        for (int i = 0; i < groups.size(); i++) {
            var g = groups.get(i);
            UUID id = g.id();

            int iy = yBase + i * rowH;
            if (iy + 18 < listTop || iy > listBottom) continue;

            Rect r = new Rect(vp.x + 2, iy, vp.w - 4, 18);
            podRowRects.put(id, r);

            boolean sel = Objects.equals(selectedGroupId, id);
            boolean hov = mouseX >= r.x && mouseX < r.x + r.w && mouseY >= r.y && mouseY < r.y + r.h;
            drawFlatRow(ctx, r, sel, hov);

            int size = 0;
            var gv = LifePointClientState.getGroup(id);
            if (gv != null && gv.order != null) size = gv.order.size();

            String name = LifePointClientState.groupName(id);
            if (name == null || name.isBlank()) name = "Pod";

            String label = (sel ? "â–¶ " : "  ") + name + "  (" + size + "/" + MAX_GROUP_MEMBERS + ")";
            label = sanitizeUiText(label);
            ctx.drawString(font, Component.literal(label), r.x + 6, r.y + 5, 0xFFFFFFFF);
        }

        ctx.disableScissor();

        int createY = vp.y + vp.h - rowH;
        Rect cr = new Rect(vp.x + 2, createY, vp.w - 4, 18);
        podCreateRect = cr;

        boolean hov = mouseX >= cr.x && mouseX < cr.x + cr.w && mouseY >= cr.y && mouseY < cr.y + cr.h;
        drawFlatRow(ctx, cr, false, hov);
        ctx.drawString(font, Component.literal("+ Create"), cr.x + 6, cr.y + 5, 0xFFFFFFFF);

        drawScrollbar(ctx, ScrollId.PODS_LIST, podsListScrollVp, podsListContentH, podsListScroll);
    }

    private void drawPodsTooltips(GuiGraphics ctx, int mouseX, int mouseY) {
        if (lockedAvailRects.isEmpty()) return;

        for (var e : lockedAvailRects.values()) {
            Rect r = e.rect;
            if (r == null) continue;

            boolean hov = mouseX >= r.x && mouseX < r.x + r.w && mouseY >= r.y && mouseY < r.y + r.h;
            if (!hov) continue;

            ctx.setComponentTooltipForNextFrame(
                    font,
                    List.of(
                            Component.literal("This block is already in pod: " + e.podName),
                            Component.literal("Remove it from that pod to add it here.")
                    ),
                    mouseX, mouseY
            );
            return;
        }
    }

    // -------------------------------------------------------------------------
    // EDIT tab drawing
    // -------------------------------------------------------------------------

    private void drawCheckbox(GuiGraphics ctx, Rect box, boolean checked, String label, int labelX, int labelY) {
        int bg = 0xFF101010;
        int edge = 0xFF3A3A3A;
        ctx.fill(box.x, box.y, box.x + box.w, box.y + box.h, bg);
        ctx.fill(box.x, box.y, box.x + box.w, box.y + 1, edge);
        ctx.fill(box.x, box.y + box.h - 1, box.x + box.w, box.y + box.h, edge);
        ctx.fill(box.x, box.y, box.x + 1, box.y + box.h, edge);
        ctx.fill(box.x + box.w - 1, box.y, box.x + box.w, box.y + box.h, edge);

        if (checked) {
            ctx.fill(box.x + 3, box.y + 3, box.x + box.w - 3, box.y + box.h - 3, 0xFF2E8B57);
        }
        if (!label.isEmpty()) {
            ctx.drawString(font, Component.literal(label), labelX, labelY, 0xFFFFFFFF);
        }
    }

    private void drawColorSwatch(GuiGraphics ctx, Rect r, int rgb, String label) {
        int bg = 0xFF101010;
        int edge = 0xFF3A3A8A; // subtle tint
        ctx.fill(r.x, r.y, r.x + r.w, r.y + r.h, bg);
        ctx.fill(r.x, r.y, r.x + r.w, r.y + 1, 0xFF3A3A3A);
        ctx.fill(r.x, r.y + r.h - 1, r.x + r.w, r.y + r.h, 0xFF3A3A3A);
        ctx.fill(r.x, r.y, r.x + 1, r.y + r.h, 0xFF3A3A3A);
        ctx.fill(r.x + r.w - 1, r.y, r.x + r.w, r.y + r.h, 0xFF3A3A3A);

        int c = 0xFF000000 | (rgb & 0xFFFFFF);
        ctx.fill(r.x + 6, r.y + 6, r.x + r.w - 6, r.y + r.h - 6, c);

        // draw label + hex INSIDE the swatch (prevents scissor clipping)
        ctx.drawString(font, Component.literal(label), r.x + 6, r.y + 4, 0xFFB0B0B0);
        ctx.drawString(font, Component.literal(toHex(rgb)), r.x + 6, r.y + r.h - 12, 0xFFB0B0B0);
    }

    private int drawPaletteTiles(GuiGraphics ctx, int x, int y, int w, boolean forPlayer) {
        return drawPaletteTiles(ctx, x, y, w, forPlayer ? editPaletteRectsPlayer : editPaletteRectsLife);
    }

    private int drawPaletteTiles(GuiGraphics ctx, int x, int y, int w, List<Rect> out) {
        int tile = 18;
        int gap = 6;

        int cols = 4;
        int rows = 2;

        int totalW = cols * tile + (cols - 1) * gap;
        int startX = x + Math.max(0, (w - totalW) / 2);

        int idx = 0;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (idx >= MANA_PALETTE.size()) break;
                Palette p = MANA_PALETTE.get(idx++);

                int bx = startX + c * (tile + gap);
                int by = y + r * (tile + gap);

                Rect rr = new Rect(bx, by, tile, tile);
                out.add(rr);

                int col = 0xFF000000 | (p.rgb & 0xFFFFFF);
                ctx.fill(rr.x, rr.y, rr.x + rr.w, rr.y + rr.h, 0xFF101010);
                ctx.fill(rr.x + 2, rr.y + 2, rr.x + rr.w - 2, rr.y + rr.h - 2, col);
                ctx.drawString(font, Component.literal(p.label), rr.x + 5, rr.y + 5, 0xFFFFFFFF);
            }
        }
        return y + rows * tile + (rows - 1) * gap;
    }

    private void drawEditPresets(GuiGraphics ctx, int mouseX, int mouseY) {
        if (editPresetListVp == null) return;

        Rect vp = editPresetListVp;
        editPresetRowRects.clear();

        // Preset list background
        ctx.fill(vp.x, vp.y, vp.x + vp.w, vp.y + vp.h, 0xFF0F0F0F);
        ctx.fill(vp.x, vp.y, vp.x + vp.w, vp.y + 1, 0xFF3A3A3A);

        List<String> keys = LifePointPresetStore.listKeys();
        int rowH = 18;

        editPresetListContentH = keys.size() * rowH;
        int maxScroll = Math.max(0, editPresetListContentH - vp.h);
        editPresetListScroll = Math.max(0, Math.min(editPresetListScroll, maxScroll));

        ctx.enableScissor(vp.x, vp.y, vp.x + vp.w, vp.y + vp.h);

        for (int i = 0; i < keys.size(); i++) {
            String k = keys.get(i);
            int ry = vp.y + i * rowH - editPresetListScroll;
            if (ry + rowH < vp.y || ry > vp.y + vp.h) continue;

            Rect rr = new Rect(vp.x + 2, ry, vp.w - 4, rowH);
            editPresetRowRects.put(k, rr);

            boolean sel = Objects.equals(selectedPresetKey, k);
            boolean hov = mouseX >= rr.x && mouseX < rr.x + rr.w && mouseY >= rr.y && mouseY < rr.y + rr.h;
            drawFlatRow(ctx, rr, sel, hov);

            String rowLabel = sanitizeUiText((sel ? "â–¶ " : "  ") + k);
            ctx.drawString(font, Component.literal(rowLabel), rr.x + 6, rr.y + 5, 0xFFFFFFFF);
        }

        ctx.disableScissor();
        drawScrollbar(ctx, ScrollId.EDIT_PRESET_LIST, vp, editPresetListContentH, editPresetListScroll);

        // --- Options area (under preset list) ---
        int x = vp.x;
        int y = vp.y + vp.h + 8;

        // Include custom counters
        editIncludeCountersRect = new Rect(x, y, 12, 12);
        drawCheckbox(ctx, editIncludeCountersRect, includeCustomCounters, "Include custom counters", x + 18, y - 1);

        // Save-to-world (ALWAYS visible, directly under include option)
        int ySave = y + 20;
        editSaveToWorldRect = new Rect(x, ySave, 12, 12);
        drawCheckbox(ctx, editSaveToWorldRect, saveToWorld, "Save to world?", x + 18, ySave - 1);

        // Include list below the two options (scrollable, clipped, never overlaps footer)
        if (includeCustomCounters && editCounterIncludeVp != null) {
            Rect cvp = editCounterIncludeVp;
            editIncludeCounterRowRects.clear();

            ctx.fill(cvp.x, cvp.y, cvp.x + cvp.w, cvp.y + cvp.h, 0xFF0F0F0F);
            ctx.fill(cvp.x, cvp.y, cvp.x + cvp.w, cvp.y + 1, 0xFF3A3A3A);

            var st = LifePointClientState.get(pos);
            var counters = st.getCompound("Counters").orElse(new CompoundTag());

            List<String> ckeys = new ArrayList<>(counters.keySet());
            ckeys.replaceAll(LifePointScreen::normalizeCounterKey);
            ckeys.removeIf(String::isBlank);
            ckeys.sort(String::compareToIgnoreCase);

            ckeys.remove("poison");
            ckeys.remove("energy");
            ckeys.remove("experience");

            int rH = 18;
            editCounterIncludeContentH = ckeys.size() * rH;
            int max = Math.max(0, editCounterIncludeContentH - cvp.h);
            editCounterIncludeScroll = Math.max(0, Math.min(editCounterIncludeScroll, max));

            ctx.enableScissor(cvp.x, cvp.y, cvp.x + cvp.w, cvp.y + cvp.h);

            for (int i = 0; i < ckeys.size(); i++) {
                String k = ckeys.get(i);
                int ry = cvp.y + i * rH - editCounterIncludeScroll;
                if (ry + rH < cvp.y || ry > cvp.y + cvp.h) continue;

                Rect rr = new Rect(cvp.x + 2, ry, cvp.w - 4, rH);
                editIncludeCounterRowRects.put(k, rr);

                boolean checked = includedCustomCounterKeys.contains(k);
                boolean hov = mouseX >= rr.x && mouseX < rr.x + rr.w && mouseY >= rr.y && mouseY < rr.y + rr.h;

                int bg = hov ? 0xFF161616 : 0xFF141414;
                ctx.fill(rr.x, rr.y, rr.x + rr.w, rr.y + rr.h, bg);
                ctx.fill(rr.x, rr.y, rr.x + rr.w, rr.y + 1, 0xFF2C2C2C);

                Rect small = new Rect(rr.x + 4, rr.y + 3, 12, 12);
                drawCheckbox(ctx, small, checked, "", 0, 0);

                ctx.drawString(font, Component.literal(k), rr.x + 22, rr.y + 5, 0xFFFFFFFF);
            }

            ctx.disableScissor();
            drawScrollbar(ctx, ScrollId.EDIT_INCLUDE_LIST, cvp, editCounterIncludeContentH, editCounterIncludeScroll);
        }
    }


    private void drawEditAppearance(GuiGraphics ctx, int mouseX, int mouseY) {
        if (editAppearanceVp == null) return;

        Rect vp = editAppearanceVp;

        // panel bg
        ctx.fill(vp.x, vp.y, vp.x + vp.w, vp.y + vp.h, 0xFF0F0F0F);
        ctx.fill(vp.x, vp.y, vp.x + vp.w, vp.y + 1, 0xFF3A3A3A);

        // ---- scrolling content ----
        editPaletteRectsPlayer.clear();
        editPaletteRectsLife.clear();
        editPaletteRectsIcon.clear();
        editFormatRects.clear();
        editIconRects.clear();

        final int padX = 8;
        final int innerX = vp.x + padX;
        final int innerW = vp.w - padX * 2;

        // IMPORTANT: apply scroll here
        int y = vp.y + 8 - editAppearanceScroll;

        // clip everything inside appearance panel
        ctx.enableScissor(vp.x, vp.y, vp.x + vp.w, vp.y + vp.h);

        // ---- Player Color ----
        ctx.drawString(font, Component.literal("Player Color"), innerX, y, 0xFFB0B0B0);
        y += 14;

        y = drawPaletteTiles(ctx, innerX, y, innerW, true);
        y += 6;

        ctx.drawString(font, Component.literal("Hex"), innerX, y, 0xFF707070);
        y += 14;

        // Move the player hex field to match scrolled layout
        if (editPlayerHexField != null) {
            editPlayerHexField.setX(innerX);
            editPlayerHexField.setY(y);
            editPlayerHexField.setWidth(innerW);

            boolean inView = (y + 18) > vp.y && y < (vp.y + vp.h);
            editPlayerHexField.visible = inView;

            // optional: drop focus if scrolled away
            if (!inView && this.getFocused() == editPlayerHexField) this.setFocused(null);
        }
        y += 18 + 6;

        // player color bar
        Rect playerBar = new Rect(innerX, y, innerW, 14);
        editPlayerBarRect = playerBar;
        ctx.fill(playerBar.x, playerBar.y, playerBar.x + playerBar.w, playerBar.y + playerBar.h, 0xFF101010);
        ctx.fill(playerBar.x, playerBar.y, playerBar.x + playerBar.w, playerBar.y + 1, 0xFF2C2C2C);
        ctx.fill(playerBar.x, playerBar.y + playerBar.h - 1, playerBar.x + playerBar.w, playerBar.y + playerBar.h, 0xFF2C2C2C);
        int pc = 0xFF000000 | (editPlayerColor & 0xFFFFFF);
        ctx.fill(playerBar.x + 2, playerBar.y + 2, playerBar.x + playerBar.w - 2, playerBar.y + playerBar.h - 2, pc);

        y += 14 + 12; // bar + spacing

        // ---- Life Color ----
        ctx.drawString(font, Component.literal("Life Color"), innerX, y, 0xFFB0B0B0);
        y += 14;

        y = drawPaletteTiles(ctx, innerX, y, innerW, false);
        y += 6;

        ctx.drawString(font, Component.literal("Hex"), innerX, y, 0xFF707070);
        y += 14;

        // Move the life hex field to match scrolled layout
        if (editLifeHexField != null) {
            editLifeHexField.setX(innerX);
            editLifeHexField.setY(y);
            editLifeHexField.setWidth(innerW);

            boolean inView = (y + 18) > vp.y && y < (vp.y + vp.h);
            editLifeHexField.visible = inView;

            if (!inView && this.getFocused() == editLifeHexField) this.setFocused(null);
        }
        y += 18 + 6;

        // life color bar
        Rect lifeBar = new Rect(innerX, y, innerW, 14);
        editLifeBarRect = lifeBar;
        ctx.fill(lifeBar.x, lifeBar.y, lifeBar.x + lifeBar.w, lifeBar.y + lifeBar.h, 0xFF101010);
        ctx.fill(lifeBar.x, lifeBar.y, lifeBar.x + lifeBar.w, lifeBar.y + 1, 0xFF2C2C2C);
        ctx.fill(lifeBar.x, lifeBar.y + lifeBar.h - 1, lifeBar.x + lifeBar.w, lifeBar.y + lifeBar.h, 0xFF2C2C2C);
        int lc = 0xFF000000 | (editLifeColor & 0xFFFFFF);
        ctx.fill(lifeBar.x + 2, lifeBar.y + 2, lifeBar.x + lifeBar.w - 2, lifeBar.y + lifeBar.h - 2, lc);

        y += 14 + 10;

        // ---- Format ----
        ctx.drawString(font, Component.literal("Format"), innerX, y, 0xFFB0B0B0);
        y += 14;

        int gridCols = 2;
        int cellH = 18;
        int cellGap = 6;
        int cellW = (innerW - cellGap) / 2;

        boolean formatEditable = canEditFormat();
        List<LifeFormat> formats = LifeFormatRegistry.entries();
        for (int i = 0; i < formats.size(); i++) {
            LifeFormat format = formats.get(i);
            String fk = format.key();
            int cx = innerX + (i % gridCols) * (cellW + cellGap);
            int cy = y + (i / gridCols) * (cellH + cellGap);

            Rect r = new Rect(cx, cy, cellW, cellH);
            editFormatRects.put(fk, r);

            boolean sel = Objects.equals(editFormatKey, fk);
            boolean hov = formatEditable && mouseX >= r.x && mouseX < r.x + r.w && mouseY >= r.y && mouseY < r.y + r.h;

            drawFormatRow(ctx, r, sel, hov, formatEditable);
            String label = fitText(format.displayName(), r.w - 10);
            int textColor = formatEditable ? 0xFFFFFFFF : 0xFF777777;
            ctx.drawString(font, Component.literal(label), r.x + 6, r.y + 5, textColor);
        }
        y += (int) Math.ceil(formats.size() / 2.0) * (cellH + cellGap);
        y += 10;

        // ---- Icon Key ----
        // ---- Icon Key ----
        ctx.drawString(font, Component.literal("Icon Key"), innerX, y, 0xFFB0B0B0);
        y += 14;

// Preview row
        int prevH = 28;
        Rect prev = new Rect(innerX, y, innerW, prevH);
        ctx.fill(prev.x, prev.y, prev.x + prev.w, prev.y + prev.h, 0xFF101010);
        ctx.fill(prev.x, prev.y, prev.x + prev.w, prev.y + 1, 0xFF2C2C2C);

        Identifier tex = counterIconId(editIconKey);
        drawIconSwapped(ctx, tex, prev.x + 6, prev.y + 4, 20, 20);
        ctx.drawString(font, Component.literal("Selected: " + editIconKey),
                prev.x + 30, prev.y + 10, 0xFFB0B0B0);

        // âœ… move y BELOW the preview block before drawing hex stuff
        y += prevH + 8;

        ctx.drawString(font, Component.literal("Icon Color"), innerX, y, 0xFF707070);
        y += 14;

        y = drawPaletteTiles(ctx, innerX, y, innerW, editPaletteRectsIcon);
        y += 6;

        // Hex label + field below preview
        ctx.drawString(font, Component.literal("Hex"), innerX, y, 0xFF707070);
        y += 14;

        if (editIconHexField != null) {
            editIconHexField.setX(innerX);
            editIconHexField.setY(y);
            editIconHexField.setWidth(innerW);

            boolean inView = (y + 18) > vp.y && y < (vp.y + vp.h);
            editIconHexField.visible = inView;
            if (!inView && this.getFocused() == editIconHexField) this.setFocused(null);
        }
        y += 18 + 10;

        // Icon grid viewport (scrolls INSIDE itself; keep it independent)
        int vpH = 160;
        Rect ivp = new Rect(innerX, y, innerW, vpH);
        editIconPickerVp = ivp;

        ctx.fill(ivp.x, ivp.y, ivp.x + ivp.w, ivp.y + ivp.h, 0xFF0F0F0F);
        ctx.fill(ivp.x, ivp.y, ivp.x + ivp.w, ivp.y + 1, 0xFF3A3A3A);

        List<String> iconKeys = loadCounterIconKeys();
        int box = 22;
        int gap = 6;
        int cols = Math.max(1, (ivp.w - 10 + gap) / (box + gap));

        int topPad = 6;
        int bottomPad = 6;
        int x0 = ivp.x + 6;
        int y0 = ivp.y + topPad;

        int rows = (int) Math.ceil(iconKeys.size() / (double) cols);
        editIconPickerContentH = topPad + rows * (box + gap) - gap + bottomPad;
        int maxScroll = Math.max(0, editIconPickerContentH - ivp.h);
        editIconPickerScroll = Math.max(0, Math.min(editIconPickerScroll, maxScroll));

        ctx.enableScissor(ivp.x, ivp.y, ivp.x + ivp.w, ivp.y + ivp.h);

        for (int i = 0; i < iconKeys.size(); i++) {
            String k = iconKeys.get(i);
            int c = i % cols;
            int r = i / cols;

            int bx = x0 + c * (box + gap);
            int by = y0 + r * (box + gap) - editIconPickerScroll;

            if (by + box < ivp.y || by > ivp.y + ivp.h) continue;

            Rect rr = new Rect(bx, by, box, box);
            editIconRects.put(k, rr);

            boolean hov = mouseX >= rr.x && mouseX < rr.x + rr.w && mouseY >= rr.y && mouseY < rr.y + rr.h;
            boolean sel = Objects.equals(editIconKey, k);

            int bg = sel ? 0xFF1B1B1B : (hov ? 0xFF161616 : 0xFF141414);
            ctx.fill(rr.x, rr.y, rr.x + rr.w, rr.y + rr.h, bg);
            ctx.fill(rr.x, rr.y, rr.x + rr.w, rr.y + 1, 0xFF2C2C2C);

            drawIconSwapped(ctx, counterIconId(k), rr.x + 3, rr.y + 3, rr.w - 6, rr.h - 6);
        }

        ctx.disableScissor();

        // icon picker scrollbar stays as-is
        drawScrollbar(ctx, ScrollId.EDIT_ICON_PICKER, ivp, editIconPickerContentH, editIconPickerScroll);

        // DONE drawing inside appearance panel
        ctx.disableScissor();

        // ---- compute appearance content height (for appearance scrollbar) ----
        // ivp.y already includes "- editAppearanceScroll", so add it back to get real (unscrolled) bottom
        int contentBottom = (ivp.y + editAppearanceScroll) + ivp.h + 10;
        editAppearanceContentH = Math.max(0, contentBottom - vp.y);

        int max = Math.max(0, editAppearanceContentH - vp.h);
        editAppearanceScroll = Math.max(0, Math.min(editAppearanceScroll, max));

        drawScrollbar(ctx, ScrollId.EDIT_APPEARANCE, vp, editAppearanceContentH, editAppearanceScroll);
    }

    private void drawEditPreview(GuiGraphics ctx) {
        if (editPreviewRect == null) return;

        // Shared layout solve (NO HEADER)
        var L = com.spider.mtgcard.client.ui.PreviewLayout.computeNoHeader(
                editPreviewRect.x, editPreviewRect.y, editPreviewRect.w, editPreviewRect.h,
                10, 16
        );

        var panel   = L.panel();
        var iconBox = L.iconBox();

        // Outer panel
        ctx.fill(panel.x(), panel.y(), panel.x2(), panel.y2(), 0xFF0F0F0F);
        ctx.fill(panel.x(), panel.y(), panel.x2(), panel.y() + 1, 0xFF3A3A3A);
        ctx.fill(panel.x(), panel.y2() - 1, panel.x2(), panel.y2(), 0xFF3A3A3A);
        ctx.fill(panel.x(), panel.y(), panel.x() + 1, panel.y2(), 0xFF3A3A3A);
        ctx.fill(panel.x2() - 1, panel.y(), panel.x2(), panel.y2(), 0xFF3A3A3A);

        // Read state
        var st = LifePointClientState.get(pos);
        String nm = st.getString("DisplayName").orElse("");
        if (nm == null || nm.isBlank()) nm = "Player";
        int life = st.getInt("Life").orElse(40);

        Identifier icon = counterIconId(editIconKey);

        // Icon (background) + dark overlay to make text readable
        drawIconSwapped(ctx, icon, iconBox.x(), iconBox.y(), iconBox.w(), iconBox.h());
        ctx.fill(iconBox.x(), iconBox.y(), iconBox.x2(), iconBox.y2(), 0x66000000);

        // Life text (centered on icon box)
        String s = Integer.toString(life);
        float scale = L.lifeScale();

        int tw = font.width(s);
        int th = font.lineHeight;

        var m = ctx.pose();
        m.pushMatrix();
        m.translate(new org.joml.Vector2f(L.lifeCenterX(), L.lifeCenterY()));
        m.scale(new org.joml.Vector2f(scale, scale));
        int lifeCol = 0xFF000000 | (editLifeColor & 0xFFFFFF);
        ctx.drawString(font, Component.literal(s), (int)(-tw / 2f), (int)(-th / 2f), lifeCol);
        m.popMatrix();

        // Name pinned near bottom (use name baseline from layout)
        int nameCol = 0xFF000000 | (editPlayerColor & 0xFFFFFF);
        ctx.drawCenteredString(font, Component.literal(nm), panel.cx(), L.nameBaselineY(), nameCol);
    }


    // -------------------------------------------------------------------------
    // INPUT
    // -------------------------------------------------------------------------

    private boolean isShiftDownNow() {
        var win = Minecraft.getInstance().getWindow();
        return InputConstants.isKeyDown(win, GLFW.GLFW_KEY_LEFT_SHIFT)
                || InputConstants.isKeyDown(win, GLFW.GLFW_KEY_RIGHT_SHIFT);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubleClick) {
        final double mouseX = click.x();
        final double mouseY = click.y();
        final int button = click.button();

        // Let widgets (tabs/buttons/text fields) go first
        if (super.mouseClicked(click, doubleClick)) return true;

        final int mx = (int) mouseX;
        final int my = (int) mouseY;

        // Scrollbar thumb drag / track jump
        if (button == 0) {

            // 1) click thumb -> start drag
            for (var e : scrollThumbRects.entrySet()) {
                ScrollId id = e.getKey();
                Rect thumb = e.getValue();
                Rect track = scrollTrackRects.get(id);
                if (thumb == null || track == null) continue;

                if (ptIn(thumb, mx, my)) {
                    Rect vp = getViewportFor(id);
                    if (vp == null) break;

                    int current = getScrollFor(id);
                    int content = getContentHFor(id);
                    int maxScroll = Math.max(0, content - vp.h);

                    startScrollbarDrag(id, my, current, maxScroll, track.h, thumb.h);
                    return true;
                }
            }

            // 2) click track -> jump
            for (var e : scrollTrackRects.entrySet()) {
                ScrollId id = e.getKey();
                Rect track = e.getValue();
                Rect thumb = scrollThumbRects.get(id);
                if (track == null || thumb == null) continue;

                if (!ptIn(track, mx, my)) continue;

                Rect vp = getViewportFor(id);
                if (vp == null) continue;

                int content = getContentHFor(id);
                int maxScroll = Math.max(0, content - vp.h);

                int barY = track.y;
                int barH = track.h;

                int thumbH = Math.max(12, (int) (barH * (vp.h / (float) Math.max(content, 1))));
                int trackSpan = Math.max(1, barH - thumbH);

                int targetThumbTop = clamp(my - (thumbH / 2), barY, barY + trackSpan);
                float t = (targetThumbTop - barY) / (float) trackSpan;

                setScrollFor(id, (int) (t * maxScroll));
                return true;
            }
        }

        // Your own hitboxes
        return mouseClickedAfterScrollbars(click, doubleClick);
    }


    @Override
    public boolean mouseDragged(MouseButtonEvent click, double deltaX, double deltaY) {
        if (draggingScroll != null && click.button() == 0) {
            Rect track = scrollTrackRects.get(draggingScroll);
            Rect vp = getViewportFor(draggingScroll);
            if (track == null || vp == null) return true;

            int trackSpan = Math.max(1, dragTrackH - dragThumbH);
            int dy = (int) click.y() - dragStartMouseY;

            // Convert mouse delta to scroll delta proportionally
            int newScroll = dragStartScroll + (int) (dy * (dragMaxScroll / (float) trackSpan));
            newScroll = clamp(newScroll, 0, dragMaxScroll);

            setScrollFor(draggingScroll, newScroll);
            return true;
        }
        return super.mouseDragged(click, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent click) {
        if (click.button() == 0) draggingScroll = null;
        return super.mouseReleased(click);
    }

    private Rect getViewportFor(ScrollId id) {
        return switch (id) {
            case LIFE_COUNTERS -> lifeCounterViewport;
            case COUNTERS_LIST -> countersListViewport;
            case COUNTER_ICON_PICKER -> counterIconPickerViewport;
            case PODS_LIST -> (podsListScrollVp != null ? podsListScrollVp : podsListViewport);
            case EDIT_PRESET_LIST -> editPresetListVp;
            case EDIT_INCLUDE_LIST -> editCounterIncludeVp;
            case EDIT_ICON_PICKER -> editIconPickerVp;
            case EDIT_APPEARANCE -> editAppearanceVp;
        };
    }


    private int getContentHFor(ScrollId id) {
        return switch (id) {
            case LIFE_COUNTERS -> lifeCounterContentH;
            case COUNTERS_LIST -> countersListContentH;
            case COUNTER_ICON_PICKER -> counterIconPickerContentH;
            case PODS_LIST -> podsListContentH;
            case EDIT_PRESET_LIST -> editPresetListContentH;
            case EDIT_INCLUDE_LIST -> editCounterIncludeContentH;
            case EDIT_ICON_PICKER -> editIconPickerContentH;
            case EDIT_APPEARANCE -> editAppearanceContentH;
        };
    }

    private int getScrollFor(ScrollId id) {
        return switch (id) {
            case LIFE_COUNTERS -> lifeCounterScroll;
            case COUNTERS_LIST -> countersListScroll;
            case COUNTER_ICON_PICKER -> counterIconPickerScroll;
            case PODS_LIST -> podsListScroll;
            case EDIT_PRESET_LIST -> editPresetListScroll;
            case EDIT_INCLUDE_LIST -> editCounterIncludeScroll;
            case EDIT_ICON_PICKER -> editIconPickerScroll;
            case EDIT_APPEARANCE -> editAppearanceScroll;
        };
    }

    private void setScrollFor(ScrollId id, int v) {
        switch (id) {
            case LIFE_COUNTERS -> lifeCounterScroll = v;
            case COUNTERS_LIST -> countersListScroll = v;
            case COUNTER_ICON_PICKER -> counterIconPickerScroll = v;
            case PODS_LIST -> podsListScroll = v;
            case EDIT_PRESET_LIST -> editPresetListScroll = v;
            case EDIT_INCLUDE_LIST -> editCounterIncludeScroll = v;
            case EDIT_ICON_PICKER -> editIconPickerScroll = v;
            case EDIT_APPEARANCE -> editAppearanceScroll = v;
        }
    }


    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int d = verticalAmount > 0 ? 1 : -1;
        if (isShiftDownNow()) d *= 10;

        // commander fields scroll
        if (tab == Tab.LIFE) {
            for (var e : cmdFieldToOther.entrySet()) {
                EditBox w = e.getKey();
                if (w != null && w.isMouseOver(mouseX, mouseY)) {
                    BlockPos other = e.getValue();
                    int oldV = cmdCache.getOrDefault(other, 0);
                    int newV = Math.max(0, Math.min(9999, oldV + d));
                    int delta = newV - oldV;
                    if (delta != 0) {
                        cmdCache.put(other, newV);

                        settingCmdProgrammatically = true;
                        w.setValue(Integer.toString(newV));
                        settingCmdProgrammatically = false;

                        applyCommanderLethalStyle(w, newV);

                        ClientPlayNetworking.send(new LifePointPackets.AddCommanderDamageC2S(pos, other, delta));
                    }
                    return true;
                }
            }
        }

        // COUNTERS: scroll left list
        if (tab == Tab.COUNTERS && countersListViewport != null) {
            Rect vp = countersListViewport;
            boolean hoverVp = mouseX >= vp.x && mouseX < vp.x + vp.w && mouseY >= vp.y && mouseY < vp.y + vp.h;
            if (hoverVp && countersListContentH > vp.h) {
                int step = isShiftDownNow() ? 24 : 12;
                int dir = verticalAmount > 0 ? -1 : 1;
                int maxScroll = Math.max(0, countersListContentH - vp.h);
                countersListScroll = Math.max(0, Math.min(maxScroll, countersListScroll + dir * step));
                return true;
            }
        }

        // PODS: scroll pods list (only scroll area; footer excluded)
        if (tab == Tab.GROUPS && podsListViewport != null) {
            Rect vp = podsListViewport;

            int rowH = 20;
            int listH = Math.max(0, vp.h - rowH);

            boolean hoverVp = mouseX >= vp.x && mouseX < vp.x + vp.w && mouseY >= vp.y && mouseY < vp.y + vp.h;
            if (hoverVp && podsListContentH > listH) {
                int step = isShiftDownNow() ? 24 : 12;
                int dir = verticalAmount > 0 ? -1 : 1;

                int maxScroll = Math.max(0, podsListContentH - listH);
                podsListScroll = Math.max(0, Math.min(maxScroll, podsListScroll + dir * step));
                return true;
            }
        }

        // COUNTERS: scroll icon picker
        if (tab == Tab.COUNTERS && counterIconPickerViewport != null) {
            Rect vp = counterIconPickerViewport;
            boolean hoverVp = mouseX >= vp.x && mouseX < vp.x + vp.w && mouseY >= vp.y && mouseY < vp.y + vp.h;
            if (hoverVp && counterIconPickerContentH > vp.h) {
                int step = isShiftDownNow() ? 24 : 12;
                int dir = verticalAmount > 0 ? -1 : 1;

                int maxScroll = Math.max(0, counterIconPickerContentH - vp.h);
                counterIconPickerScroll = Math.max(0, Math.min(maxScroll, counterIconPickerScroll + dir * step));
                return true;
            }
        }

        // LIFE: hover counter box -> change that counter
        if (tab == Tab.LIFE && !lifeCounterRects.isEmpty()) {
            for (var e : lifeCounterRects.entrySet()) {
                String k = e.getKey();
                Rect r = e.getValue();
                if (r == null) continue;

                boolean hover = mouseX >= r.x && mouseX < r.x + r.w && mouseY >= r.y && mouseY < r.y + r.h;
                if (hover) {
                    var st = LifePointClientState.get(pos);
                    var counters = st.getCompound("Counters").orElse(new CompoundTag());

                    int oldV = counters.contains(k) ? counters.getInt(k).orElse(0) : 0;
                    int newV = Math.max(0, oldV + d);

                    if (newV != oldV) {
                        ClientPlayNetworking.send(
                                new LifePointPackets.SetCounterC2S(pos, k, newV)
                        );
                    }
                    return true;
                }
            }
        }

        // LIFE: scroll counters viewport
        if (tab == Tab.LIFE && lifeCounterViewport != null) {
            Rect vp = lifeCounterViewport;
            boolean hoverVp = mouseX >= vp.x && mouseX < vp.x + vp.w && mouseY >= vp.y && mouseY < vp.y + vp.h;
            if (hoverVp && lifeCounterContentH > vp.h) {
                int step = isShiftDownNow() ? 24 : 12;
                int dir = verticalAmount > 0 ? -1 : 1;

                int maxScroll = Math.max(0, lifeCounterContentH - vp.h);
                lifeCounterScroll = Math.max(0, Math.min(maxScroll, lifeCounterScroll + dir * step));
                return true;
            }
        }

        // fallback: scroll on valueField
        if (valueField != null && valueField.isMouseOver(mouseX, mouseY)) {
            if (tab == Tab.LIFE) ClientPlayNetworking.send(new LifePointPackets.AddLifeC2S(pos, d));
            else if (tab == Tab.COUNTERS) {
                var st = LifePointClientState.get(pos);
                var counters = st.getCompound("Counters").orElse(new CompoundTag());

                int oldV = counters.contains(counterKey)
                        ? counters.getInt(counterKey).orElse(0)
                        : 0;

                int newV = Math.max(0, oldV + d); // âœ… no max cap

                if (newV != oldV) {
                    ClientPlayNetworking.send(
                            new LifePointPackets.SetCounterC2S(pos, counterKey, newV)
                    );
                    if (valueField != null) valueField.setValue(Integer.toString(newV));
                }
                return true;
            }
            return true;
        }

        // EDIT: scroll icon picker
        if (tab == Tab.EDIT && editIconPickerVp != null) {
            Rect vp = editIconPickerVp;
            boolean hover = mouseX >= vp.x && mouseX < vp.x + vp.w && mouseY >= vp.y && mouseY < vp.y + vp.h;
            if (hover && editIconPickerContentH > vp.h) {
                int step = isShiftDownNow() ? 24 : 12;
                int dir = verticalAmount > 0 ? -1 : 1;
                int max = Math.max(0, editIconPickerContentH - vp.h);
                editIconPickerScroll = Math.max(0, Math.min(max, editIconPickerScroll + dir * step));
                return true;
            }
        }

        // EDIT: scroll appearance
        if (tab == Tab.EDIT) {
            Rect vp = editAppearanceVp;
            boolean hover = mouseX >= vp.x && mouseX < vp.x + vp.w && mouseY >= vp.y && mouseY < vp.y + vp.h;
            if (hover && editAppearanceContentH > vp.h) {
                int step = isShiftDownNow() ? 24 : 12;
                int dir = verticalAmount > 0 ? -1 : 1;

                int max = Math.max(0, editAppearanceContentH - vp.h);
                editAppearanceScroll = Math.max(0, Math.min(max, editAppearanceScroll + dir * step));
                return true;
            }
        }

        // EDIT: scroll preset list
        if (tab == Tab.EDIT && editPresetListVp != null) {
            Rect vp = editPresetListVp;
            boolean hover = mouseX >= vp.x && mouseX < vp.x + vp.w && mouseY >= vp.y && mouseY < vp.y + vp.h;
            if (hover && editPresetListContentH > vp.h) {
                int step = isShiftDownNow() ? 24 : 12;
                int dir = verticalAmount > 0 ? -1 : 1;
                int max = Math.max(0, editPresetListContentH - vp.h);
                editPresetListScroll = Math.max(0, Math.min(max, editPresetListScroll + dir * step));
                return true;
            }
        }

        // EDIT: scroll include-counter list
        if (tab == Tab.EDIT && includeCustomCounters && editCounterIncludeVp != null) {
            Rect vp = editCounterIncludeVp;
            boolean hover = mouseX >= vp.x && mouseX < vp.x + vp.w && mouseY >= vp.y && mouseY < vp.y + vp.h;
            if (hover && editCounterIncludeContentH > vp.h) {
                int step = isShiftDownNow() ? 24 : 12;
                int dir = verticalAmount > 0 ? -1 : 1;
                int max = Math.max(0, editCounterIncludeContentH - vp.h);
                editCounterIncludeScroll = Math.max(0, Math.min(max, editCounterIncludeScroll + dir * step));
                return true;
            }
        }

        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override public boolean isPauseScreen() { return false; }

    // -------------------------------------------------------------------------
    // RENDER
    // -------------------------------------------------------------------------

    @Override
    public void render(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        // background
        ctx.fill(0, 0, width, height, 0xAA000000);

        // panels / custom UI (everything that can cover widgets)
        drawHudStrips(ctx);
        drawMiddleColumnPanels(ctx);
        if (tab == Tab.LIFE) drawLifeHealthPanel(ctx);

        switch (tab) {
            case LIFE -> {
                drawOtherPlayersFlat(ctx, mouseX, mouseY);
                drawBigLifeBox(ctx);
                drawLifeCountersHud(ctx, mouseX, mouseY);
            }
            case COUNTERS -> {
                drawCountersList(ctx, mouseX, mouseY);
                drawCounterIconPreviewAndPicker(ctx, mouseX, mouseY);
            }
            case GROUPS -> {
                drawPodsFlat(ctx, mouseX, mouseY);
                drawPodsTooltips(ctx, mouseX, mouseY);
            }
            case EDIT -> {
                drawEditPresets(ctx, mouseX, mouseY);
                drawEditAppearance(ctx, mouseX, mouseY);
                drawEditPreview(ctx);
            }
        }

        // titles / overlays that should be behind widgets (optional)
        ctx.drawCenteredString(font, Component.literal("Life Point Block"), uiX + uiW / 2, py(6), 0xFFFFFF);

        // ACTIVE PLAYER pulse (still behind widgets)
        UUID gid = LifePointClientState.getGroupIdFor(pos);
        var gv = (gid != null) ? LifePointClientState.getGroup(gid) : null;
        boolean started = (gv != null && gv.started);
        boolean turnActive = LifePointClientState.get(pos).getBoolean("TurnActive").orElse(false);

        if (tab == Tab.LIFE && started) {
            if (activePulseTicks > 0) activePulseTicks--;

            if (turnActive) {
                float t;
                if (activePulseTicks > 0) {
                    float p = 1.0f - (activePulseTicks / 30.0f);
                    t = (float) (0.5f + 0.5f * Math.sin(p * Math.PI));
                } else t = 0.0f;

                int base = 0xFFFFD54F;
                int r0 = (base >> 16) & 0xFF, g0 = (base >> 8) & 0xFF, b0 = base & 0xFF;
                int r = (int) (r0 + (255 - r0) * (t * 0.85f));
                int g = (int) (g0 + (255 - g0) * (t * 0.85f));
                int b = (int) (b0 + (255 - b0) * (t * 0.85f));
                int col = (0xFF << 24) | (r << 16) | (g << 8) | b;

                if (lifeButtonsCenterX > 0 && lifeButtonsY > 0) {
                    ctx.drawCenteredString(
                            font,
                            Component.literal("ACTIVE PLAYER"),
                            lifeButtonsCenterX,
                            lifeButtonsY - 12,
                            col
                    );
                }
            }
        }

        // Pod-full hint (behind widgets)
        if (tab == Tab.GROUPS && selectedGroupId != null && selectedMembers.size() >= MAX_GROUP_MEMBERS) {
            int leftW = (width - 24 - 10 * 2) / 3;
            int availX = 12 + leftW + 10;
            int contentTop = 12 + 24 + 18 + 10;
            ctx.drawString(font,
                    Component.literal("Pod full (" + MAX_GROUP_MEMBERS + "/" + MAX_GROUP_MEMBERS + ")"),
                    availX + 10, contentTop + 110, 0xFFB0B0B0);
        }

        if (tab != Tab.EDIT) {
            if (editPlayerHexField != null) editPlayerHexField.visible = false;
            if (editLifeHexField != null) editLifeHexField.visible = false;
            if (editIconHexField != null) editIconHexField.visible = false;
        }

        // IMPORTANT: widgets (buttons) drawn LAST so theyâ€™re on top
        super.render(ctx, mouseX, mouseY, delta);
    }

    // -------------------------------------------------------------------------
    // PODS backing logic
    // -------------------------------------------------------------------------

    private void requestGroupsIfNeeded() {
        if (!groupsRequested) {
            groupsRequested = true;
            ClientPlayNetworking.send(new LifePointPackets.RequestGroupsC2S());
        }
    }

    private void requestNearbyScanIfNeeded() {
        if (!scanRequested) {
            scanRequested = true;
            lastScanOrigin = pos;
            ClientPlayNetworking.send(new LifePointPackets.ScanNearbyC2S(pos, 50));
        }
    }

    private void selectGroup(UUID id) {
        if (Objects.equals(selectedGroupId, id)) return;

        selectedGroupId = id;

        selectedMembers.clear();
        orderedMembers.clear();
        groupDirty = false;

        var gv = LifePointClientState.getGroup(id);
        if (gv != null) {
            if (gv.order != null) {
                selectedMembers.addAll(gv.order);
                orderedMembers.addAll(gv.order);
            }
            groupNameOriginal = (gv.name == null ? "" : gv.name);
            groupNameCurrent  = groupNameOriginal;
        } else {
            String nm = LifePointClientState.groupName(id);
            groupNameOriginal = (nm == null ? "" : nm);
            groupNameCurrent  = groupNameOriginal;
        }

        if (groupNameField != null) {
            settingGroupNameProgrammatically = true;
            groupNameField.setValue(groupNameCurrent == null ? "" : groupNameCurrent);
            settingGroupNameProgrammatically = false;
        }
        podsAutoScrollPending = true;
    }

    private void toggleMember(BlockPos p) {
        if (selectedMembers.contains(p)) {
            selectedMembers.remove(p);
            orderedMembers.remove(p);
            groupDirty = true;
            return;
        }

        selectedMembers.add(p);
        if (!orderedMembers.contains(p)) orderedMembers.add(p);
        groupDirty = true;
    }

    public void onScanArrived(BlockPos origin) {
        if (!origin.equals(this.pos)) return;
        if (this.tab != Tab.GROUPS) return;
        this.init();
    }

    private static String shortPos(BlockPos p) {
        return p.getX() + "," + p.getY() + "," + p.getZ();
    }

    private static String toHex(int rgb) {
        rgb &= 0xFFFFFF;
        return String.format("#%06X", rgb);
    }

    private void onHexFieldChanged(EditBox field, boolean forPlayer, String raw) {
        if (settingHexProgrammatically) return;
        if (field == null) return;

        String s = (raw == null) ? "" : raw.trim();
        if (s.isEmpty()) return;

        // Allow "RRGGBB" -> "#RRGGBB"
        // Also allow "0xRRGGBB" -> "#RRGGBB"
        if (s.startsWith("0x") || s.startsWith("0X")) s = s.substring(2);

        // If they typed/pasted 6 hex chars without '#', auto-add it
        if (!s.startsWith("#") && s.length() <= 6) {
            // only if all chars are valid hex so far
            boolean ok = true;
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                boolean hex = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
                if (!hex) { ok = false; break; }
            }
            if (ok) {
                settingHexProgrammatically = true;
                field.setValue("#" + s);
                field.moveCursorToEnd(false);
                settingHexProgrammatically = false;
                return; // wait for next change event
            }
        }

        // Only send when we have a full valid 6-digit color
        int v = parseHexColorOrNeg(s);
        if (v < 0) return;

        if (forPlayer) {
            if ((editPlayerColor & 0xFFFFFF) == v) return;
            editPlayerColor = v;
            ClientPlayNetworking.send(new LifePointPackets.SetPlayerColorC2S(pos, editPlayerColor));
        } else {
            if ((editLifeColor & 0xFFFFFF) == v) return;
            editLifeColor = v;
            ClientPlayNetworking.send(new LifePointPackets.SetColorC2S(pos, editLifeColor));
        }
    }

    private static int parseHexColorOrNeg(String s) {
        if (s == null) return -1;
        s = s.trim();
        if (s.startsWith("#")) s = s.substring(1);
        if (s.length() != 6) return -1;
        for (int i = 0; i < 6; i++) {
            char c = s.charAt(i);
            boolean ok = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
            if (!ok) return -1;
        }
        try {
            return Integer.parseInt(s, 16) & 0xFFFFFF;
        } catch (Exception e) {
            return -1;
        }
    }

    private static String normalizeHexText(String s, int fallbackRgb) {
        int v = parseHexColorOrNeg(s);
        if (v < 0) v = fallbackRgb & 0xFFFFFF;
        return String.format("#%06X", v);
    }

    // -------------------------------------------------------------------------
    // Confirm Delete Screen
    // -------------------------------------------------------------------------

    private static final class ConfirmDeleteGroupScreen extends LegacyScreen {
        private final LifePointScreen parent;
        private final UUID groupId;

        protected ConfirmDeleteGroupScreen(LifePointScreen parent, UUID groupId) {
            super(Component.literal("Delete Pod?"));
            this.parent = parent;
            this.groupId = groupId;
        }

        @Override
        protected void init() {
            if (applyFixedGuiScale(2)) return;

            int cx = width / 2;
            int y = height / 2 - 10;

            addRenderableWidget(Button.builder(Component.literal("Confirm"), b -> {
                ClientPlayNetworking.send(new LifePointPackets.DeleteGroupC2S(groupId));
                Minecraft.getInstance().setScreen(parent);

                parent.selectedGroupId = null;
                parent.groupDirty = false;
                parent.selectedMembers.clear();
                parent.orderedMembers.clear();
                parent.scanRequested = false;
                parent.refresh();
            }).bounds(cx - 90, y, 80, 20).build());

            addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> {
                Minecraft.getInstance().setScreen(parent);
                parent.refresh();
            }).bounds(cx + 10, y, 80, 20).build());
        }

        @Override public boolean isPauseScreen() { return false; }

        @Override
        public void render(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
            ctx.fill(0, 0, width, height, 0xCC000000);
            super.render(ctx, mouseX, mouseY, delta);

            int cx = width / 2;
            ctx.drawCenteredString(font, Component.literal("Delete this pod?"), cx, height / 2 - 40, 0xFFFFFFFF);
        }
    }

    private boolean mouseClickedAfterScrollbars(MouseButtonEvent click, boolean doubleClick) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();

        // --- COUNTERS: click list rows ---
        if (tab == Tab.COUNTERS && countersListViewport != null && button == 0) {
            Rect vp = countersListViewport;
            if (ptIn(vp, mouseX, mouseY)) {
                int rowH = 20;
                int localY = (int)(mouseY - vp.y) + countersListScroll;
                int idx = localY / rowH;
                if (idx >= 0 && idx < countersListKeys.size()) {
                    counterKey = countersListKeys.get(idx);
                    init();
                    return true;
                }
            }
        }

        // --- COUNTERS: click icon grid ---
        if (tab == Tab.COUNTERS && counterIconPickerViewport != null && button == 0) {
            Rect vp = counterIconPickerViewport;
            if (ptIn(vp, mouseX, mouseY)) {
                for (var e : counterPickerRects.entrySet()) {
                    Rect r = e.getValue();
                    if (r == null) continue;
                    if (!ptIn(r, mouseX, mouseY)) continue;

                    String ck = normalizeCounterKey(counterKey);
                    String picked = normalizeIconKey(e.getKey());

                    if (ck.equals("poison")) picked = "poison";
                    if (ck.equals("energy")) picked = "energy";
                    if (ck.equals("experience")) picked = "experience";

                    ClientPlayNetworking.send(new LifePointPackets.SetCounterIconC2S(pos, ck, picked));
                    init();
                    return true;
                }
            }
        }

        // --- LIFE: select commander target ---
        if (tab == Tab.LIFE && button == 0 && otherPlayersViewport != null && !otherPlayerRects.isEmpty()) {
            if (ptIn(otherPlayersViewport, mouseX, mouseY)) {
                for (var e : otherPlayerRects.entrySet()) {
                    if (ptIn(e.getValue(), mouseX, mouseY)) {
                        selectedCmdTarget = e.getKey();
                        init();
                        return true;
                    }
                }
            }
        }

        // --- PODS: click pods list / create ---
        if (tab == Tab.GROUPS && button == 0) {
            // Only allow pod row clicks in the scroll area (footer excluded)
            if (podsListScrollVp != null && ptIn(podsListScrollVp, mouseX, mouseY)) {
                for (var e : podRowRects.entrySet()) {
                    Rect r = e.getValue();
                    if (r == null) continue;
                    if (ptIn(r, mouseX, mouseY)) {
                        selectGroup(e.getKey());
                        scanRequested = false;
                        requestNearbyScanIfNeeded();
                        init();
                        return true;
                    }
                }
            }

            // "+ Create" footer click
            if (podCreateRect != null && ptIn(podCreateRect, mouseX, mouseY)) {
                createBeforeIds = new HashSet<>(LifePointClientState.GROUP_NAMES.keySet());
                awaitingCreateSelect = true;
                ClientPlayNetworking.send(new LifePointPackets.CreateEmptyGroupC2S());

                groupDirty = false;
                scanRequested = false;
                init();
                return true;
            }
        }

        // --- EDIT: click interactions ---
        if (tab == Tab.EDIT && button == 0) {
            // Preset list clicks ONLY inside list viewport
            if (editPresetListVp != null && ptIn(editPresetListVp, mouseX, mouseY)) {
                for (var e : editPresetRowRects.entrySet()) {
                    if (ptIn(e.getValue(), mouseX, mouseY)) {
                        selectedPresetKey = e.getKey();
                        if (editPresetNameField != null) editPresetNameField.setValue(selectedPresetKey);
                        return true;
                    }
                }
            }

            // Include checkbox
            if (editIncludeCountersRect != null && ptIn(editIncludeCountersRect, mouseX, mouseY)) {
                includeCustomCounters = !includeCustomCounters;
                if (!includeCustomCounters) includedCustomCounterKeys.clear();
                return true;
            }

            // Save-to-world checkbox
            if (editSaveToWorldRect != null && ptIn(editSaveToWorldRect, mouseX, mouseY)) {
                saveToWorld = !saveToWorld;
                return true;
            }

            // Include list clicks ONLY inside include viewport
            if (includeCustomCounters && editCounterIncludeVp != null && ptIn(editCounterIncludeVp, mouseX, mouseY)) {
                for (var e : editIncludeCounterRowRects.entrySet()) {
                    if (ptIn(e.getValue(), mouseX, mouseY)) {
                        String k = e.getKey();
                        if (includedCustomCounterKeys.contains(k)) includedCustomCounterKeys.remove(k);
                        else includedCustomCounterKeys.add(k);
                        return true;
                    }
                }
            }

            // Format clicks
            for (var e : editFormatRects.entrySet()) {
                if (ptIn(e.getValue(), mouseX, mouseY)) {
                    if (!canEditFormat()) return true;
                    editFormatKey = e.getKey();
                    ClientPlayNetworking.send(new LifePointPackets.SetFormatKeyC2S(pos, editFormatKey));
                    return true;
                }
            }

            // Player palette
            for (int i = 0; i < editPaletteRectsPlayer.size() && i < MANA_PALETTE.size(); i++) {
                if (ptIn(editPaletteRectsPlayer.get(i), mouseX, mouseY)) {
                    editPlayerColor = MANA_PALETTE.get(i).rgb;
                    ClientPlayNetworking.send(new LifePointPackets.SetPlayerColorC2S(pos, editPlayerColor));
                    if (editPlayerHexField != null) {
                        settingHexProgrammatically = true;
                        editPlayerHexField.setValue(String.format("#%06X", editPlayerColor & 0xFFFFFF));
                        editPlayerHexField.moveCursorToEnd(false);
                        settingHexProgrammatically = false;
                    }
                    return true;
                }
            }

            // Life palette
            for (int i = 0; i < editPaletteRectsLife.size() && i < MANA_PALETTE.size(); i++) {
                if (ptIn(editPaletteRectsLife.get(i), mouseX, mouseY)) {
                    editLifeColor = MANA_PALETTE.get(i).rgb;
                    ClientPlayNetworking.send(new LifePointPackets.SetColorC2S(pos, editLifeColor));
                    if (editLifeHexField != null) {
                        settingHexProgrammatically = true;
                        editLifeHexField.setValue(String.format("#%06X", editLifeColor & 0xFFFFFF));
                        editLifeHexField.moveCursorToEnd(false);
                        settingHexProgrammatically = false;
                    }
                    return true;
                }
            }

            // Icon color palette
            for (int i = 0; i < editPaletteRectsIcon.size() && i < MANA_PALETTE.size(); i++) {
                if (ptIn(editPaletteRectsIcon.get(i), mouseX, mouseY)) {
                    editIconSwapColor = MANA_PALETTE.get(i).rgb;
                    ClientPlayNetworking.send(new LifePointPackets.SetIconSwapColorC2S(pos, editIconSwapColor));
                    if (editIconHexField != null) {
                        settingHexProgrammatically = true;
                        editIconHexField.setValue(String.format("#%06X", editIconSwapColor & 0xFFFFFF));
                        editIconHexField.moveCursorToEnd(false);
                        settingHexProgrammatically = false;
                    }
                    return true;
                }
            }

            // Icon grid clicks ONLY inside icon viewport
            if (editIconPickerVp != null && ptIn(editIconPickerVp, mouseX, mouseY)) {
                for (var e : editIconRects.entrySet()) {
                    if (ptIn(e.getValue(), mouseX, mouseY)) {
                        editIconKey = e.getKey();
                        ClientPlayNetworking.send(new LifePointPackets.SetIconKeyC2S(pos, editIconKey));
                        return true;
                    }
                }
            }
        }

        return false;
    }

    @Override
    public void tick() {
        super.tick();

        // Player hex blur normalize
        if (editPlayerHexField != null) {
            boolean focused = (this.getFocused() == editPlayerHexField);
            if (playerHexWasFocused && !focused) {
                // lost focus: normalize to "#RRGGBB" based on current color (or parsed value if valid)
                String t = editPlayerHexField.getValue();
                int v = parseHexColorOrNeg(t);
                if (v < 0) v = editPlayerColor & 0xFFFFFF;

                settingHexProgrammatically = true;
                editPlayerHexField.setValue(String.format("#%06X", v));
                editPlayerHexField.moveCursorToEnd(false);
                settingHexProgrammatically = false;

                // ensure state + packet (optional but keeps it consistent)
                if ((editPlayerColor & 0xFFFFFF) != v) {
                    editPlayerColor = v;
                    ClientPlayNetworking.send(new LifePointPackets.SetPlayerColorC2S(pos, editPlayerColor));
                }
            }
            playerHexWasFocused = focused;
        }

        // Life hex blur normalize
        if (editLifeHexField != null) {
            boolean focused = (this.getFocused() == editLifeHexField);
            if (lifeHexWasFocused && !focused) {
                String t = editLifeHexField.getValue();
                int v = parseHexColorOrNeg(t);
                if (v < 0) v = editLifeColor & 0xFFFFFF;

                settingHexProgrammatically = true;
                editLifeHexField.setValue(String.format("#%06X", v));
                editLifeHexField.moveCursorToEnd(false);
                settingHexProgrammatically = false;

                if ((editLifeColor & 0xFFFFFF) != v) {
                    editLifeColor = v;
                    ClientPlayNetworking.send(new LifePointPackets.SetColorC2S(pos, editLifeColor));
                }
            }
            lifeHexWasFocused = focused;
        }

        if (editIconHexField != null) {
            boolean focused = (this.getFocused() == editIconHexField);
            if (iconHexWasFocused && !focused) {
                String t = editIconHexField.getValue();
                int v = parseHexColorOrNeg(t);
                if (v < 0) v = editIconSwapColor & 0xFFFFFF;

                settingHexProgrammatically = true;
                editIconHexField.setValue(String.format("#%06X", v));
                editIconHexField.moveCursorToEnd(false);
                settingHexProgrammatically = false;

                if ((editIconSwapColor & 0xFFFFFF) != v) {
                    editIconSwapColor = v;
                    ClientPlayNetworking.send(new LifePointPackets.SetIconSwapColorC2S(pos, editIconSwapColor));
                }
            }
            iconHexWasFocused = focused;
        }
    }


    public void refresh(){
        if (this.getFocused() instanceof EditBox) {
            return;
        }
        this.init();
    }
    public BlockPos getPos() { return pos; }
}

