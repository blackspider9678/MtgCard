package com.spider.mtgcard.client.displayblock;

import com.spider.mtgcard.client.life.LifePointClientState;
import com.spider.mtgcard.displayblock.DisplayBlock;
import com.spider.mtgcard.displayblock.DisplayBlockEntity;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.SubmitNodeCollector;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import com.mojang.math.Axis;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import java.util.*;

public class DisplayBlockEntityRenderer implements BlockEntityRenderer<DisplayBlockEntity, DisplayBlockEntityRenderer.State> {

    private static final Identifier WHITE = Identifier.fromNamespaceAndPath("mtgcard", "textures/misc/white.png");

    private static final Identifier ICON_POISON     = Identifier.fromNamespaceAndPath("mtgcard", "textures/gui/counters/poison.png");
    private static final Identifier ICON_ENERGY     = Identifier.fromNamespaceAndPath("mtgcard", "textures/gui/counters/energy.png");
    private static final Identifier ICON_EXPERIENCE = Identifier.fromNamespaceAndPath("mtgcard", "textures/gui/counters/experience.png");

    private static final Identifier ICON_NONE       = Identifier.fromNamespaceAndPath("mtgcard", "textures/gui/counters/none.png");

    private final java.util.Map<String, Identifier> iconIdCache = new java.util.HashMap<>();

    private static final int FULL_BRIGHT = 0x00F000F0; // 15728880

    public DisplayBlockEntityRenderer(BlockEntityRendererProvider.Context ctx) {}

    // Z layers (screen local space). Bigger = closer to camera.
    private static final float Z_BG_FILL      = 0.0000f;
    private static final float Z_BG_BORDER    = 0.0002f;

    private static final float Z_PIP          = 0.0006f;

    // was 0.0010 / 0.0012
    private static final float Z_ICON         = 0.0011f;

    private static final float Z_CMD_CIRCLES  = 0.0016f;
    private static final float Z_CMD_TEXT     = 0.0020f;

    private static final float Z_LIFE_TEXT    = 0.0026f;
    private static final float Z_NAME_TEXT    = 0.0029f;

    private final Map<String, Identifier> recolorIdCache = new HashMap<>();
    private final Map<String, net.minecraft.client.renderer.texture.AbstractTexture> recolorTexCache = new HashMap<>();


    public static final class State extends BlockEntityRenderState {
        /** The blockstate-facing stored on each display tile (what isDisplayTile checks). */
        Direction facing = Direction.NORTH;      // tileFacing

        /** The face we actually render on (visible front of the screen). */
        Direction screenFace = Direction.NORTH;

        int wBlocks = 1, hBlocks = 1;
        int minX, minY, minZ, maxX, maxY, maxZ;

        boolean isController = false;
        BlockPos controllerPos = BlockPos.ZERO;

        boolean hasLife = false;
        int life = 0;
        String name = "";

        int lifeColor = 0xFFFFFF;
        int playerColor = 0xE8E8E8;
        int iconColor = 0xFFFFFF;

        boolean turnActive = false;
        String iconKey = "none";

        public Vec3 camPos = Vec3.ZERO;

        // Life animation
        float lifePulse = 0f;     // 0..1 (1 right after change)
        float lifeScale = 1f;     // multiplier for life number scale
        int lifeGlowAlpha = 0;    // 0..255

        // Dead state
        boolean isDead = false;

        // Commander damage (render list)
        boolean showCommander = false;
        List<CmdDmgEntry> cmd = new ArrayList<>();

        BlockPos linkedLifePos = null;
    }

    private static final class CmdDmgEntry {
        final String name;
        final int dmg;
        final int color; // RGB or ARGB allowed

        CmdDmgEntry(String name, int dmg, int color) {
            this.name = name;
            this.dmg = dmg;
            this.color = color;
        }
    }


    private static int chanR(int rgb) { return (rgb >> 16) & 0xFF; }
    private static int chanG(int rgb) { return (rgb >> 8) & 0xFF; }
    private static int chanB(int rgb) { return (rgb) & 0xFF; }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(
            DisplayBlockEntity be,
            State s,
            float tickProgress,
            Vec3 cameraPos,
            @Nullable net.minecraft.client.renderer.feature.ModelFeatureRenderer.CrumblingOverlay crumblingOverlay
    ) {
        BlockEntityRenderer.super.extractRenderState(be, s, tickProgress, cameraPos, crumblingOverlay);

        // --- Consistent facing model ---
        // The blockstate-facing is the visible front of the display.
        s.facing = be.getBlockState().getValue(DisplayBlock.FACING);
        s.screenFace = s.facing;
        s.camPos = cameraPos;

        s.linkedLifePos = be.getLinkedLifePos().orElse(null);
        Identifier linkedDim = be.getLinkedDimId().orElse(null);
        BlockPos linkedPos = s.linkedLifePos;

        Level w = be.getLevel();

        if (w != null && w.isClientSide() && linkedDim != null && s.linkedLifePos != null) {
            BestRect best = computeBestFilledRect(w, be.getBlockPos(), s.facing, s.screenFace, linkedDim, s.linkedLifePos);

            BlockPos myPos = be.getBlockPos();

            if (best.tiles.contains(myPos)) {
                // I'm inside the big rectangle
                s.controllerPos = best.controller;
                s.isController = myPos.equals(best.controller);
                s.wBlocks = best.w;
                s.hBlocks = best.h;
            } else {
                // I'm outside the big rectangle -> render as a mini 1x1
                s.controllerPos = myPos;
                s.isController = true;
                s.wBlocks = 1;
                s.hBlocks = 1;
            }
        } else {
            s.controllerPos = be.getBlockPos();
            s.isController = true;
            s.wBlocks = 1;
            s.hBlocks = 1;
        }

        // life preview from client cache
        s.hasLife = false;
        s.life = 0;
        s.name = "";
        s.lifeColor = 0xFFFFFF;
        s.playerColor = 0xE8E8E8;
        s.turnActive = false;
        s.iconKey = "none";

        // ---- Commander damage parsing + threshold ----
        s.cmd.clear();
        int area = s.wBlocks * s.hBlocks;
        s.showCommander = false;

        DisplayLinkedLifeClient.resolve(be).ifPresent(st -> {
            s.hasLife = true;
            s.life = st.getInt("Life").orElse(0);
            s.name = st.getString("DisplayName").orElse("");
            s.lifeColor = st.getInt("LifeColor").orElse(0xFFFFFF);
            s.playerColor = st.getInt("PlayerColor").orElse(0xE8E8E8);
            s.iconColor = st.getInt("IconSwapColor").orElse(s.lifeColor); // fallback to lifeColor if missing

            s.turnActive = st.getBoolean("TurnActive").orElse(false);
            s.iconKey = st.getString("IconKey").orElse("none");
            // dead is NOT based on life value anymore
            s.isDead = isDeadFromGroup(s.linkedLifePos);

            // ---- Dead state (FLAG-BASED, life can go negative) ----
            s.isDead = isDeadFromGroup(s.linkedLifePos);

            // ---- Commander damage threshold ----
            s.showCommander = (area >= MIN_BLOCKS_FOR_COMMANDER) && !s.isDead;


            if (s.showCommander) {
                st.getList("CommanderDamageList").ifPresent(list -> {
                    UUID gid = (s.linkedLifePos != null)
                            ? LifePointClientState.getGroupIdFor(s.linkedLifePos)
                            : null;

                    for (int i = 0; i < list.size(); i++) {
                        if (!(list.get(i) instanceof CompoundTag c)) continue;

                        long ap = c.getLong("AttackerPos").orElse(0L);
                        int dmg = c.getInt("Damage").orElse(0);
                        if (dmg <= 0) continue;

                        BlockPos attacker = BlockPos.of(ap);
                        String nm = displayNameForPos(gid, attacker);
                        int col = playerColorForPos(attacker);

                        s.cmd.add(new CmdDmgEntry(nm, dmg, col));
                    }
                });
            }
        });

// ---- Life-change animation (keyed by controller so multiblock behaves as one screen) ----
        long now = Util.getMillis();
        BlockPos keyPos = s.controllerPos;

        Integer prev = lastLifeByController.get(keyPos);
        if (s.hasLife) {
            if (prev == null || prev != s.life) {
                lastLifeByController.put(keyPos, s.life);
                lastLifeChangeMsByController.put(keyPos, now);
            }
        }

        long last = lastLifeChangeMsByController.getOrDefault(keyPos, 0L);
        float pulse = 0f;
        if (last != 0L) {
            float age = (now - last) / (float) LIFE_PULSE_MS;
            pulse = 1.0f - Math.min(1.0f, Math.max(0.0f, age));
        }
        s.lifePulse = pulse;
        s.lifeScale = 1.0f + pulse * 0.35f;          // pop size (tweak)
        s.lifeGlowAlpha = (int)(pulse * 160.0f);     // shadow glow alpha (tweak)

    }

    private static final class BestRect {
        final BlockPos controller; // top-left in world
        final int w, h;
        final Set<BlockPos> tiles; // world positions included in rect

        BestRect(BlockPos controller, int w, int h, Set<BlockPos> tiles) {
            this.controller = controller;
            this.w = w;
            this.h = h;
            this.tiles = tiles;
        }
    }

    /**
     * Finds the largest axis-aligned filled rectangle inside the connected component.
     * Rectangle axes are screen-right and screen-down.
     */
    private static BestRect computeBestFilledRect(Level world, BlockPos anyTile,
                                                  Direction tileFacing, Direction screenFace,
                                                  @Nullable Identifier dimId, @Nullable BlockPos lifePos) {

        Set<BlockPos> comp = collectConnectedScreen(world, anyTile, tileFacing, screenFace, dimId, lifePos);
        if (comp.isEmpty()) {
            return new BestRect(anyTile, 1, 1, Set.of(anyTile));
        }

        Direction right = screenRight(screenFace);

        // Use the component's top Y as v=0 (top row)
        int maxY = Integer.MIN_VALUE;
        for (BlockPos p : comp) maxY = Math.max(maxY, p.getY());

        // Compute u along 'right'
        int minU = Integer.MAX_VALUE, maxU = Integer.MIN_VALUE;
        int minV = Integer.MAX_VALUE, maxV = Integer.MIN_VALUE;

        // Pick any anchor for u math
        BlockPos anchor = comp.iterator().next();

        for (BlockPos p : comp) {
            int u = screenU(anchor, p, right);        // signed steps relative to anchor
            int v = maxY - p.getY();                  // 0 at top, increases downward
            minU = Math.min(minU, u);
            maxU = Math.max(maxU, u);
            minV = Math.min(minV, v);
            maxV = Math.max(maxV, v);
        }

        int gridW = (maxU - minU) + 1;
        int gridH = (maxV - minV) + 1;

        boolean[][] filled = new boolean[gridH][gridW];

        for (BlockPos p : comp) {
            int u = screenU(anchor, p, right) - minU;
            int v = (maxY - p.getY()) - minV;
            if (v >= 0 && v < gridH && u >= 0 && u < gridW) {
                filled[v][u] = true;
            }
        }

        // Max rectangle in binary matrix using histogram per row
        int[] heights = new int[gridW];

        int bestArea = 0;
        int bestW = 1, bestH = 1;
        int bestLeft = 0;
        int bestTop = 0;

        for (int row = 0; row < gridH; row++) {
            for (int col = 0; col < gridW; col++) {
                heights[col] = filled[row][col] ? heights[col] + 1 : 0;
            }

            // Largest rectangle in histogram (heights)
            ArrayDeque<Integer> st = new ArrayDeque<>();
            for (int col = 0; col <= gridW; col++) {
                int h = (col == gridW) ? 0 : heights[col];

                while (!st.isEmpty() && h < heights[st.peekLast()]) {
                    int height = heights[st.removeLast()];
                    int rightCol = col - 1;
                    int leftCol = st.isEmpty() ? 0 : st.peekLast() + 1;
                    int width = rightCol - leftCol + 1;

                    int area = width * height;
                    int top = row - height + 1;

                    // Tie-break: bigger area, then topmost, then leftmost
                    if (area > bestArea ||
                            (area == bestArea && top < bestTop) ||
                            (area == bestArea && top == bestTop && leftCol < bestLeft)) {
                        bestArea = area;
                        bestW = width;
                        bestH = height;
                        bestLeft = leftCol;
                        bestTop = top;
                    }
                }
                st.addLast(col);
            }
        }

        // Convert best rect grid coords back to world positions
        // Base controller of the whole grid at (minU, minV) in world:
        BlockPos gridTopLeft = anchor.relative(right, minU).atY(maxY - minV);

        BlockPos rectController = gridTopLeft.relative(right, bestLeft).below(bestTop);

        HashSet<BlockPos> rectTiles = new HashSet<>();
        for (int dv = 0; dv < bestH; dv++) {
            BlockPos rowStart = rectController.below(dv);
            for (int du = 0; du < bestW; du++) {
                rectTiles.add(rowStart.relative(right, du));
            }
        }

        return new BestRect(rectController, bestW, bestH, rectTiles);
    }

    private static BlockPos parseLegacyPosKey(String k) {
        if (k == null) return null;
        k = k.trim();
        try {
            String[] p = k.split(",");
            if (p.length == 3) {
                int x = Integer.parseInt(p[0].trim());
                int y = Integer.parseInt(p[1].trim());
                int z = Integer.parseInt(p[2].trim());
                return new BlockPos(x, y, z);
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static final class RectInfo {
        final boolean isRect;
        final BlockPos controller;
        final int w, h;
        RectInfo(boolean isRect, BlockPos controller, int w, int h) {
            this.isRect = isRect;
            this.controller = controller;
            this.w = w;
            this.h = h;
        }
    }

    private static Set<BlockPos> collectConnectedScreen(Level world, BlockPos start,
                                                        Direction tileFacing, Direction screenFace,
                                                        @Nullable Identifier dimId, @Nullable BlockPos lifePos) {
        // no link = treat standalone
        if (dimId == null || lifePos == null) return Set.of(start);

        ArrayDeque<BlockPos> q = new ArrayDeque<>();
        HashSet<BlockPos> out = new HashSet<>();

        q.add(start);
        out.add(start);

        // Only expand along the screen plane (right/left/up/down on the screen), not â€œthroughâ€ the wall.
        Direction right = screenRight(screenFace);
        Direction left  = right.getOpposite();
        Direction up    = Direction.UP;
        Direction down  = Direction.DOWN;

        while (!q.isEmpty()) {
            BlockPos p = q.removeFirst();

            // 4-neighborhood on the screen plane
            for (Direction d : new Direction[]{ right, left, up, down }) {
                BlockPos n = p.relative(d);
                if (out.contains(n)) continue;
                if (!isDisplayTileSameLink(world, n, tileFacing, dimId, lifePos)) continue;

                out.add(n);
                q.addLast(n);
            }
        }

        return out;
    }

    private static RectInfo computeRectInfo(Level world, BlockPos anyTile,
                                            Direction tileFacing, Direction screenFace,
                                            @Nullable Identifier dimId, @Nullable BlockPos lifePos) {

        Set<BlockPos> tiles = collectConnectedScreen(world, anyTile, tileFacing, screenFace, dimId, lifePos);
        if (tiles.isEmpty()) return new RectInfo(true, anyTile, 1, 1);

        Direction right = screenRight(screenFace);

        // Choose an anchor for coordinate math (any tile in the set)
        BlockPos anchor = tiles.iterator().next();

        int minU = Integer.MAX_VALUE, maxU = Integer.MIN_VALUE;
        int minV = Integer.MAX_VALUE, maxV = Integer.MIN_VALUE;

        for (BlockPos p : tiles) {
            int u = screenU(anchor, p, right);      // horizontal (right)
            int v = anchor.getY() - p.getY();       // vertical (down) since y decreases

            minU = Math.min(minU, u);
            maxU = Math.max(maxU, u);
            minV = Math.min(minV, v);
            maxV = Math.max(maxV, v);
        }

        int w = (maxU - minU) + 1;
        int h = (maxV - minV) + 1;

        // Controller is top-left: (minU, minV)
        BlockPos controller = anchor.relative(right, minU).below(minV);

        boolean isRect = (tiles.size() == w * h);

        // Optional: extra safety â€” ensure every cell exists (catches weird cases)
        if (isRect) {
            for (int dv = 0; dv < h; dv++) {
                BlockPos rowStart = controller.below(dv);
                for (int du = 0; du < w; du++) {
                    BlockPos cell = rowStart.relative(right, du);
                    if (!tiles.contains(cell)) { isRect = false; break; }
                }
                if (!isRect) break;
            }
        }

        return new RectInfo(isRect, controller, w, h);
    }

    /** Compute screen "u" (right steps) from anchor -> p. */
    private static int screenU(BlockPos anchor, BlockPos p, Direction right) {
        // right is always horizontal in world (E/W or N/S).
        // For E/W use X delta; for N/S use Z delta.
        return switch (right) {
            case EAST  -> p.getX() - anchor.getX();
            case WEST  -> anchor.getX() - p.getX();
            case SOUTH -> p.getZ() - anchor.getZ();
            case NORTH -> anchor.getZ() - p.getZ();
            default    -> 0;
        };
    }


    private static String displayNameForPos(@Nullable UUID groupId, BlockPos pos) {
        if (groupId != null) {
            String nm = LifePointClientState.groupMemberName(groupId, pos);
            if (nm != null && !nm.isBlank()) return nm;
        }
        CompoundTag st = LifePointClientState.get(pos);
        if (st != null) {
            String n = st.getString("DisplayName").orElse("");
            if (n != null && !n.isBlank()) return n;
        }
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    private static int playerColorForPos(BlockPos pos) {
        CompoundTag st = LifePointClientState.get(pos);
        if (st == null) return 0xFFE8E8E8;
        int rgb = st.getInt("PlayerColor").orElse(0xE8E8E8);
        return 0xFF000000 | (rgb & 0x00FFFFFF);
    }

    /**
     * Return the world-direction that is "screen right" when you are looking at the screenFace.
     * NOTE: This expects a FACE normal (screenFace), not the tileFacing.
     */
    private static Direction screenRight(Direction screenFace) {
        return switch (screenFace) {
            case NORTH -> Direction.WEST;
            case SOUTH -> Direction.EAST;
            case EAST  -> Direction.NORTH;
            case WEST  -> Direction.SOUTH;
            default    -> Direction.EAST;
        };
    }

    private static boolean isDisplayTileSameLink(Level world, BlockPos pos, Direction tileFacing,
                                                 @Nullable Identifier dimId, @Nullable BlockPos lifePos) {
        var st = world.getBlockState(pos);
        if (!(st.getBlock() instanceof DisplayBlock)) return false;
        if (st.getValue(DisplayBlock.FACING) != tileFacing) return false;

        BlockEntity be = world.getBlockEntity(pos);
        if (!(be instanceof DisplayBlockEntity dbe)) return false;

        // If our screen has no link, treat it as standalone (donâ€™t merge with linked ones)
        if (dimId == null || lifePos == null) return false;

        return dbe.getLinkedDimId().map(dimId::equals).orElse(false)
                && dbe.getLinkedLifePos().map(lifePos::equals).orElse(false);
    }

    /**
     * Returns int[]{w,h} for the largest solid rectangle starting at controllerPos (top-left),
     * expanding to the right (screen-right) and downward.
     *
     * tileFacing is for "is this tile part of the screen" checks,
     * screenFace is for "which direction is right".
     */
    private static int[] computeSolidRect(Level world, BlockPos controllerPos,
                                          Direction tileFacing, Direction screenFace,
                                          @Nullable Identifier dimId, @Nullable BlockPos lifePos) {
        Direction right = screenRight(screenFace);

        int maxH = 64;
        int maxW = 64;

        int rectW = Integer.MAX_VALUE;
        int rectH = 0;

        for (int y = 0; y < maxH; y++) {
            BlockPos rowStart = controllerPos.below(y);
            if (!isDisplayTileSameLink(world, rowStart, tileFacing, dimId, lifePos)) break;

            int rowW = 0;
            for (int x = 0; x < maxW; x++) {
                BlockPos p = rowStart.relative(right, x);
                if (!isDisplayTileSameLink(world, p, tileFacing, dimId, lifePos)) break;
                rowW++;
            }

            if (rowW == 0) break;
            rectW = Math.min(rectW, rowW);
            rectH++;
        }

        if (rectW == Integer.MAX_VALUE) rectW = 1;
        if (rectH <= 0) rectH = 1;

        return new int[]{rectW, rectH};
    }

    @Override
    public void submit(State s, PoseStack matrices, SubmitNodeCollector queue, CameraRenderState cameraState) {
        if (!s.isController) return;

        matrices.pushPose();

        // center of controller block
        matrices.translate(0.5f, 0.5f, 0.5f);

        // rotate so our quad faces outward ON THE VISIBLE FACE
        orientQuadToFace(matrices, s.screenFace);

        // move to the face plane (avoid z-fight)
        matrices.translate(0f, 0f, 0.501f);

        // ---- TOP-LEFT ANCHORED PANEL (extends +X right and -Y down in this oriented space) ----
        final float bezel = 0.06f;

        final float fx0 = -0.5f + bezel;
        final float fx1 = -0.5f + s.wBlocks - bezel;

        final float fyTop    =  0.5f - bezel;
        final float fyBottom =  0.5f - s.hBlocks + bezel;

        final float fInset = 0.02f;

        // Solid black background + dark border, fully opaque
        final int bgR = 0, bgG = 0, bgB = 0;
        final int br = 18, bG = 18, bb = 18;

        // Fill (OPAQUE)
        queue.submitCustomGeometry(
                matrices,
                RenderTypes.entityCutout(WHITE),
                (entry, vc) -> {
                    Matrix4f mat = entry.pose();
                    drawPanelQuad(mat, vc,
                            fx0, fyBottom, Z_BG_FILL,
                            fx1, fyTop,    Z_BG_FILL,
                            0f, 0f, 1f, 1f,
                            FULL_BRIGHT,
                            OverlayTexture.NO_OVERLAY,
                            bgR, bgG, bgB, 255
                    );
                }
        );

        // Border (also OPAQUE)
        queue.submitCustomGeometry(
                matrices,
                RenderTypes.entityCutout(WHITE),
                (entry, vc) -> {
                    Matrix4f mat = entry.pose();
                    drawPanelQuad(mat, vc,
                            fx0 + fInset, fyBottom + fInset, Z_BG_BORDER,
                            fx1 - fInset, fyTop - fInset,    Z_BG_BORDER,
                            0f, 0f, 1f, 1f,
                            FULL_BRIGHT,
                            OverlayTexture.NO_OVERLAY,
                            br, bG, bb, 255
                    );
                }
        );

        // Optional turn pip (opaque)
        if (s.turnActive) {
            float pip = 0.10f;
            float px0 = fx0 + 0.10f;
            float px1 = px0 + pip;
            float py1 = fyTop - 0.10f;
            float py0 = py1 - pip;

            int pr = chanR(s.lifeColor), pg = chanG(s.lifeColor), pb = chanB(s.lifeColor);

            queue.submitCustomGeometry(
                    matrices,
                    RenderTypes.entityCutout(WHITE),
                    (entry, vc) -> {
                        Matrix4f mat = entry.pose();
                        drawPanelQuad(mat, vc,
                                px0, py0, Z_PIP,
                                px1, py1, Z_PIP,
                                0f, 0f, 1f, 1f,
                                FULL_BRIGHT,
                                OverlayTexture.NO_OVERLAY,
                                pr, pg, pb, 255
                        );
                    }
            );
        }

        // If dead: draw skull + name; otherwise normal
        if (s.isDead) {
            drawDeadSkull(s, matrices, queue, fx0, fx1, fyBottom, fyTop);
            drawLifeName(s, matrices, queue, fx0, fx1, fyBottom, fyTop);
        } else {
            drawLifeIcon(s, matrices, queue, fx0, fx1, fyBottom, fyTop);
            drawLifeNumber(s, matrices, queue, fx0, fx1, fyBottom, fyTop);
            drawLifeName(s, matrices, queue, fx0, fx1, fyBottom, fyTop);
        }

        // Commander damage overlay (only when big enough)
        if (s.showCommander && !s.cmd.isEmpty()) {
            drawCommanderDamage(s, matrices, queue, fx0, fx1, fyBottom, fyTop);
        }

        // --- BACKPLATE: hides the front UI when looking from behind ---
        queue.submitCustomGeometry(
                matrices,
                RenderTypes.entityCutout(WHITE),
                (entry, vc) -> {
                    Matrix4f mat = entry.pose();
                    // push slightly behind the front plane (negative z in our oriented screen space)
                    drawBackQuad(mat, vc,
                            fx0, fyBottom, -0.0020f,
                            fx1, fyTop,    -0.0020f,
                            0f, 0f, 1f, 1f,
                            FULL_BRIGHT,
                            OverlayTexture.NO_OVERLAY,
                            0, 0, 0, 255
                    );
                }
        );
        matrices.popPose();
    }

    private Identifier resolveIconIdRecolored(String key, int rgb) {
        if (key == null || key.isBlank()) key = "none";
        key = key.trim().toLowerCase(Locale.ROOT);

        // If "none", bail early
        if (key.equals("none")) return ICON_NONE;

        // Find the *base* icon (same logic you already have)
        Identifier base = null;
        var rm = Minecraft.getInstance().getResourceManager();

        Identifier a = Identifier.fromNamespaceAndPath("mtgcard", "textures/gui/counters/" + key + ".png");
        if (rm.getResource(a).isPresent()) base = a;

        if (base == null) {
            Identifier b = Identifier.fromNamespaceAndPath("mtgcard", "textures/gui/set_icons/" + key + ".png");
            if (rm.getResource(b).isPresent()) base = b;
        }

        if (base == null) base = ICON_NONE;

        // If white, just use original (optional)
        rgb &= 0x00FFFFFF;
        if (rgb == 0x00FFFFFF) return base;

        // Cache key = base + color
        String cacheKey = base.toString() + "|" + String.format("%06X", rgb);

        Identifier cached = recolorIdCache.get(cacheKey);
        if (cached != null) return cached;

        try (var res = rm.getResource(base).orElseThrow().open()) {
            com.mojang.blaze3d.platform.NativeImage img = com.mojang.blaze3d.platform.NativeImage.read(res);

            final int SRC = 0x00FF0000;          // #FF0000 (RGB)
            final int TOL = 12;                  // tolerance for anti-alias edges (tweak 0..25)

            for (int y = 0; y < img.getHeight(); y++) {
                for (int x = 0; x < img.getWidth(); x++) {
                    int argb = img.getPixel(x, y);
                    int aCh = (argb >>> 24) & 0xFF;
                    if (aCh == 0) continue;

                    int px = argb & 0x00FFFFFF;

                    // Only swap "red" pixels (keeps white icons, etc.)
                    if (isNear(px, SRC, TOL)) {
                        img.setPixel(x, y, (aCh << 24) | (rgb & 0x00FFFFFF));
                    }
                }
            }

            var tex = new net.minecraft.client.renderer.texture.DynamicTexture(
                    () -> "mtgcard_dyn_icon",
                    img
            );

            // Make a deterministic id path (must be lowercase + safe chars)
            String safe = Integer.toHexString(cacheKey.hashCode());
            Identifier dynId = Identifier.fromNamespaceAndPath("mtgcard", "dyn/icon_" + safe);

            Minecraft.getInstance().getTextureManager().register(dynId, tex);

            recolorIdCache.put(cacheKey, dynId);
            recolorTexCache.put(cacheKey, tex);
            return dynId;

        } catch (Exception e) {
            // Fallback to base if anything fails
            return base;
        }
    }

    private static boolean isNear(int rgb, int target, int tol) {
        int r = (rgb >>> 16) & 0xFF, g = (rgb >>> 8) & 0xFF, b = rgb & 0xFF;
        int tr = (target >>> 16) & 0xFF, tg = (target >>> 8) & 0xFF, tb = target & 0xFF;
        return Math.abs(r - tr) <= tol && Math.abs(g - tg) <= tol && Math.abs(b - tb) <= tol;
    }

    private static void orientQuadToFace(PoseStack matrices, Direction facing) {
        // Quad starts facing +Z (SOUTH) in local space
        switch (facing) {
            case SOUTH -> { /* no rotation */ }
            case NORTH -> matrices.mulPose(Axis.YP.rotationDegrees(180f));
            case EAST  -> matrices.mulPose(Axis.YP.rotationDegrees(90f));
            case WEST  -> matrices.mulPose(Axis.YP.rotationDegrees(-90f));
            case UP    -> matrices.mulPose(Axis.XP.rotationDegrees(-90f));
            case DOWN  -> matrices.mulPose(Axis.XP.rotationDegrees(90f));
        }
    }

    private static boolean isDeadFromGroup(@Nullable BlockPos lifePos) {
        if (lifePos == null) return false;
        UUID gid = LifePointClientState.getGroupIdFor(lifePos);
        if (gid == null) return false;
        return LifePointClientState.groupMemberDead(gid, lifePos);
    }

    private static final boolean DEBUG_TEXT = false;

    private void drawLifeNumber(State s, PoseStack matrices, SubmitNodeCollector queue,
                                float x0, float x1, float yBottom, float yTop) {

        Font tr = Minecraft.getInstance().font;

        float cx = (x0 + x1) * 0.5f;
        float cy = (yBottom + yTop) * 0.5f;

        matrices.pushPose();
        matrices.translate(cx, cy, Z_LIFE_TEXT);

        // Base scale tuned for 1x1
        float base = 0.04f;

        float dim = Math.min(s.wBlocks, s.hBlocks);
        float mult = 1.0f + (dim - 1.0f) * 0.85f;
        mult = Math.min(mult, 4.0f);

        float sc = base * mult * s.lifeScale; // <-- add animation multiplier
        matrices.scale(sc, -sc, sc);

        String txt = s.hasLife ? String.valueOf(s.life) : "--";
        int w = tr.width(txt);

        int lifeARGB = 0xFF000000 | (s.lifeColor & 0x00FFFFFF);

// Optional â€œglow shadowâ€ (actually just a dark shadow to make color readable)
        if (s.lifeGlowAlpha > 0) {
            int shadow = ((s.lifeGlowAlpha & 0xFF) << 24) | 0x000000; // alpha black
            queue.submitText(
                    matrices,
                    (-w / 2f) + 1f,
                    (-tr.lineHeight / 2f) + 1f,
                    Component.literal(txt).getVisualOrderText(),
                    false,
                    Font.DisplayMode.NORMAL,
                    FULL_BRIGHT,
                    shadow,
                    0,
                    0
            );
        }

        queue.submitText(
                matrices,
                -w / 2f,
                -tr.lineHeight / 2f,
                Component.literal(txt).getVisualOrderText(),
                false,
                Font.DisplayMode.NORMAL,
                FULL_BRIGHT,
                lifeARGB,
                0,
                0
        );



        matrices.popPose();
    }

    // --- Animation cache (client only) ---
    private static final long LIFE_PULSE_MS = 350L;
    private final Map<BlockPos, Integer> lastLifeByController = new HashMap<>();
    private final Map<BlockPos, Long> lastLifeChangeMsByController = new HashMap<>();

    // --- Commander damage threshold ---
    private static final int MIN_BLOCKS_FOR_COMMANDER = 6; // "larger than X blocks" -> tweak anytime

    // Dead skull (vanilla texture so you don't need an asset yet)
    private static final Identifier SKULL_TEX =
            Identifier.fromNamespaceAndPath("mtgcard", "textures/gui/dead.png"); // example


    private void drawLifeName(State s, PoseStack matrices, SubmitNodeCollector queue,
                              float x0, float x1, float yBottom, float yTop) {

        Font tr = Minecraft.getInstance().font;

        // If no link/name, either skip or show placeholder
        String name = (s.hasLife && s.name != null) ? s.name.trim() : "";
        if (name.isBlank()) return; // or: name = "â€”";

        // Position: bottom-center, with a little padding above the bezel
        float cx = (x0 + x1) * 0.5f;
        float padding = 0.14f; // tweak if you want it closer/further from the edge
        float cy = yBottom + padding;

        matrices.pushPose();
        matrices.translate(cx, cy, Z_NAME_TEXT);

        // Smaller than the life number and scales gently with size
        float base = 0.018f; // baseline for 1x1
        float dim = Math.min(s.wBlocks, s.hBlocks);
        float mult = 1.0f + (dim - 1.0f) * 0.35f; // gentle growth
        mult = Math.min(mult, 2.25f);             // cap
        float sc = base * mult;

        // Keep text upright in screen space
        matrices.scale(sc, -sc, sc);

        // Clamp to available width (in "text pixels" after scaling)
        float availableWorldW = (x1 - x0) - 0.18f; // leave some side padding
        int maxPx = Math.max(10, (int)(availableWorldW / sc));

        String shown = tr.plainSubstrByWidth(name, maxPx);

        // If it got trimmed, add ellipsis (optional)
        if (!shown.equals(name) && shown.length() >= 2) {
            // try to make space for "..."
            String dots = "...";
            int dotsW = tr.width(dots);
            String cut = tr.plainSubstrByWidth(name, Math.max(2, maxPx - dotsW));
            shown = cut + dots;
        }

        int w = tr.width(shown);

        int nameARGB = 0xFF000000 | (s.playerColor & 0x00FFFFFF);

        queue.submitText(
                matrices,
                -w / 2f,
                -tr.lineHeight / 2f,
                Component.literal(shown).getVisualOrderText(),
                false,
                Font.DisplayMode.NORMAL,
                FULL_BRIGHT,
                nameARGB,
                0,
                0
        );

        matrices.popPose();
    }

    private Identifier resolveIconId(String key) {
        if (key == null || key.isBlank()) key = "none";
        key = key.trim().toLowerCase(java.util.Locale.ROOT);

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

    private static final float BEZEL = 0.06f;

    private void drawLifeIcon(State s, PoseStack matrices, SubmitNodeCollector queue, float x0, float x1, float yBottom, float yTop) {

        if (!s.hasLife) return;

        String key = (s.iconKey == null) ? "none" : s.iconKey;
        if (key.isBlank() || key.equalsIgnoreCase("none")) return;

        Identifier tex = resolveIconIdRecolored(key, s.iconColor);

        // center of screen panel
        float cx = (x0 + x1) * 0.5f;
        float cy = (yBottom + yTop) * 0.5f;

        // panel size (world units)
        float panelW = (x1 - x0);
        float panelH = (yTop - yBottom);
        float panelMin = Math.min(panelW, panelH);

        // how much free space actually exists inside the bezel on the *small* axis
        float insetMin = panelMin - (BEZEL * 2.0f);          // use the SAME bezel as render()
        insetMin = Math.max(insetMin, panelMin * 0.50f);     // safety

        float dim = Math.min(s.wBlocks, s.hBlocks);

        // Let it get BIG. 1x1 ~95% of inset, grows a bit, then cap.
        float frac = 0.95f + (dim - 1.0f) * 0.03f;           // 1x1=0.95, 2=0.98, 3=1.01...
        frac = Math.min(frac, 1.08f);                        // allow slight oversize if you want

        float iconSize = insetMin * frac;

        // HARD cap so it never exceeds the inner panel area too much
        float maxSize = insetMin * 1.02f;                    // allow a tiny bleed if desired
        iconSize = Math.min(iconSize, maxSize);

        float ix0 = cx - iconSize * 0.5f;
        float ix1 = cx + iconSize * 0.5f;
        float iy0 = cy - iconSize * 0.5f;
        float iy1 = cy + iconSize * 0.5f;

        matrices.pushPose();
        // tiny push toward camera in screen-local space
        matrices.translate(0f, 0f, 0.0010f);

        // Draw the icon quad
        queue.submitCustomGeometry(
                matrices,
                RenderTypes.entityCutout(tex),
                (entry, vc) -> {
                    Matrix4f mat = entry.pose();
                    drawPanelQuad(mat, vc,
                            ix0, iy0, Z_ICON,
                            ix1, iy1, Z_ICON,
                            0f, 0f, 1f, 1f,
                            FULL_BRIGHT,
                            OverlayTexture.NO_OVERLAY,
                            255, 255, 255, 255
                    );
                }
        );

        matrices.popPose();

    }

    private void drawCommanderDamage(State s, PoseStack matrices, SubmitNodeCollector queue,
                                     float x0, float x1, float yBottom, float yTop) {

        if (s.cmd == null || s.cmd.isEmpty()) return;

        Font tr = Minecraft.getInstance().font;

        // Layout: top-left, with padding.
        float pad = 0.10f;
        float startX = x0 + pad;
        float startY = yTop - pad - 0.22f; // leave room near the top edge / turn pip

        // Size scales gently with screen size, but stays small.
        float dim = Math.min(s.wBlocks, s.hBlocks);
        float baseR = 0.10f;                       // radius in "world units"
        float r = baseR * (1.0f + (dim - 1f) * 0.06f);
        r = Math.min(r, 0.14f);// cap

        final float fr = r;

        float gap = r * 0.55f;                     // spacing between circles
        float stepY = (r * 2f) + gap;

        // Cap how many we show so it doesn't clutter
        int max = Math.min(8, s.cmd.size());

        // Optional: if you ever want 2 columns when too many
        int wrapAt = 6;                            // 6 down, then start a new column
        float colStepX = (r * 2f) + (r * 0.8f);

        for (int i = 0; i < max; i++) {
            CmdDmgEntry e = s.cmd.get(i);

            // Position with optional wrap
            int col = (i >= wrapAt) ? 1 : 0;
            int row = (i >= wrapAt) ? (i - wrapAt) : i;

            float cx = startX + col * colStepX + r;
            float cy = startY - row * stepY - r;

            int fill = (e.color & 0xFF000000) == 0
                    ? (0xFF000000 | (e.color & 0x00FFFFFF))
                    : e.color;

            // Outline + fill circles
            final float outline = fr * 1.18f;
            final int outlineCol = 0xCC000000;

            queue.submitCustomGeometry(
                    matrices,
                    RenderTypes.entityCutout(WHITE),
                    (entry, vc) -> {
                        Matrix4f mat = entry.pose();
                        // Outline
                        drawFilledCircle(mat, vc, cx, cy, Z_CMD_CIRCLES, outline, outlineCol);
                        // Fill
                        drawFilledCircle(mat, vc, cx, cy, Z_CMD_CIRCLES + 0.0001f, fr, fill);
                    }
            );

            // Damage number centered on top
            String txt = Integer.toString(e.dmg);

            matrices.pushPose();
            matrices.translate(cx, cy, Z_CMD_TEXT);

            // Small text scale tuned to circle size
            float sc = (r / 0.10f) * 0.012f; // 0.012 for r=0.10
            sc = Math.min(sc, 0.018f);
            matrices.scale(sc, -sc, sc);

            int w = tr.width(txt);
            int color = 0xFFFFFFFF;

            // Shadow for readability
            queue.submitText(
                    matrices,
                    (-w / 2f) + 1f,
                    (-tr.lineHeight / 2f) + 1f,
                    Component.literal(txt).getVisualOrderText(),
                    false,
                    Font.DisplayMode.NORMAL,
                    FULL_BRIGHT,
                    0xA0000000,
                    0,
                    0
            );

            queue.submitText(
                    matrices,
                    -w / 2f,
                    -tr.lineHeight / 2f,
                    Component.literal(txt).getVisualOrderText(),
                    false,
                    Font.DisplayMode.NORMAL,
                    FULL_BRIGHT,
                    color,
                    0,
                    0
            );

            matrices.popPose();
        }
    }

    private void drawDeadSkull(State s, PoseStack matrices, SubmitNodeCollector queue,
                               float x0, float x1, float yBottom, float yTop) {
        float cx = (x0 + x1) * 0.5f;
        float cy = (yBottom + yTop) * 0.5f;

        float panelW = (x1 - x0);
        float panelH = (yTop - yBottom);
        float panelMin = Math.min(panelW, panelH);

        float size = panelMin * 0.70f; // big but not edge-to-edge
        float ix0 = cx - size * 0.5f;
        float ix1 = cx + size * 0.5f;
        float iy0 = cy - size * 0.5f;
        float iy1 = cy + size * 0.5f;

        matrices.pushPose();
        // tiny push toward camera in screen-local space
        matrices.translate(0f, 0f, 0.0010f);

        // Draw the icon quad
        queue.submitCustomGeometry(
                matrices,
                RenderTypes.entityCutout(SKULL_TEX),
                (entry, vc) -> {
                    Matrix4f mat = entry.pose();
                    drawPanelQuad(mat, vc,
                            ix0, iy0, Z_ICON,
                            ix1, iy1, Z_ICON,
                            0f, 0f, 1f, 1f,
                            FULL_BRIGHT,
                            OverlayTexture.NO_OVERLAY,
                            255, 255, 255, 255
                    );
                }
        );

        matrices.popPose();

    }

    private static void drawFilledCircle(Matrix4f mat, VertexConsumer vc,
                                         float cx, float cy, float z, float r, int argb) {

        int a = (argb >>> 24) & 0xFF;
        int rr = (argb >>> 16) & 0xFF;
        int g  = (argb >>> 8) & 0xFF;
        int b  = (argb) & 0xFF;

        // Triangle fan
        final int segments = 18; // smooth enough, still cheap

        // Center
        vc.addVertex(mat, cx, cy, z)
                .setColor(rr, g, b, a)
                .setUv(0.5f, 0.5f)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(FULL_BRIGHT)
                .setNormal(0f, 0f, 1f);

        for (int i = 0; i <= segments; i++) {
            double ang = (Math.PI * 2.0) * (i / (double) segments);
            float x = cx + (float) (Math.cos(ang) * r);
            float y = cy + (float) (Math.sin(ang) * r);

            // UVs don't matter much because WHITE is solid, but keep valid range
            float u = 0.5f + (x - cx) / (r * 2f);
            float v = 0.5f + (y - cy) / (r * 2f);

            vc.addVertex(mat, x, y, z)
                    .setColor(rr, g, b, a)
                    .setUv(u, v)
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(FULL_BRIGHT)
                    .setNormal(0f, 0f, 1f);
        }
    }

    private static void drawPanelQuad(
            Matrix4f mat,
            VertexConsumer vc,
            float x0, float y0, float z0,
            float x1, float y1, float z1,
            float u0, float v0,
            float u1, float v1,
            int light,
            int overlay,
            int r, int g, int b, int a
    ) {
        // Front-facing winding for a quad that starts facing +Z in local space.
        put(vc, mat, x0, y0, z0, u0, v1, light, overlay, r, g, b, a);
        put(vc, mat, x1, y0, z1, u1, v1, light, overlay, r, g, b, a);
        put(vc, mat, x1, y1, z1, u1, v0, light, overlay, r, g, b, a);
        put(vc, mat, x0, y1, z0, u0, v0, light, overlay, r, g, b, a);
    }

    private static void drawBackQuad(
            Matrix4f mat,
            VertexConsumer vc,
            float x0, float y0, float z0,
            float x1, float y1, float z1,
            float u0, float v0,
            float u1, float v1,
            int light,
            int overlay,
            int r, int g, int b, int a
    ) {
        // Back-facing winding for the black backplate so the rear stays opaque.
        put(vc, mat, x0, y0, z0, u0, v1, light, overlay, r, g, b, a);
        put(vc, mat, x0, y1, z0, u0, v0, light, overlay, r, g, b, a);
        put(vc, mat, x1, y1, z1, u1, v0, light, overlay, r, g, b, a);
        put(vc, mat, x1, y0, z1, u1, v1, light, overlay, r, g, b, a);
    }

    private static void put(
            VertexConsumer vc,
            Matrix4f mat,
            float x, float y, float z,
            float u, float v,
            int light,
            int overlay,
            int r, int g, int b, int a
    ) {
        vc.addVertex(mat, x, y, z)
                .setColor(r, g, b, a)
                .setUv(u, v)
                .setOverlay(overlay)
                .setLight(light)
                .setNormal(0f, 0f, 1f);
    }
}
