package com.spider.mtgcard.client;

import com.spider.mtgcard.client.compat.LegacyScreen;
import com.spider.mtgcard.client.input.GuiCardFaceFlipHandler;
import com.spider.mtgcard.client.input.GuiCardFaceFlipper;
import com.spider.mtgcard.client.java.CardArtManager;
import com.spider.mtgcard.net.payload.CardDisplayPayloads;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Environment(EnvType.CLIENT)
public class AttachedCardsScreen extends LegacyScreen implements GuiCardFaceFlipHandler {
    private static final int PAD = 10;
    private static final int LIST_W = 220;
    private static final int ROW_H = 28;
    private static final int BTN_H = 20;
    private static final int MINI = 16;
    private static final int SCROLLBAR_W = 6;
    private static final Identifier CARD_BACK_TEX =
            Identifier.fromNamespaceAndPath("mtgcard", "textures/gui/card.png");

    private final int entityId;
    private final UUID hostId;
    private final long version;
    private UUID selectedCardId;
    private final ItemStack hostStack;
    private final ArrayList<Entry> attachments = new ArrayList<>();

    private Button doneButton;
    private Button detachButton;
    private Button upButton;
    private Button downButton;

    private int scroll;
    private boolean dirty;
    private boolean saving;
    private int listX, listY, listH;
    private int previewX, previewY, previewW, previewH;
    private final Map<UUID, Rect> rowRects = new HashMap<>();
    private final Map<UUID, Rect> upRects = new HashMap<>();
    private final Map<UUID, Rect> downRects = new HashMap<>();

    private record Entry(UUID id, ItemStack stack, int rotStep) {
        Entry {
            id = id == null ? UUID.randomUUID() : id;
            stack = stack == null ? ItemStack.EMPTY : stack.copy();
            rotStep &= 1;
        }
    }

    private record Rect(int x, int y, int w, int h) {
        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    public AttachedCardsScreen(int entityId, UUID hostId, long version, UUID selectedCardId,
                               ItemStack hostStack, List<CardDisplayPayloads.AttachmentSyncEntry> attachmentEntries) {
        super(Component.literal("Attached Cards"));
        this.entityId = entityId;
        this.hostId = hostId == null ? new UUID(0L, 0L) : hostId;
        this.version = Math.max(0L, version);
        this.selectedCardId = selectedCardId == null ? this.hostId : selectedCardId;
        this.hostStack = hostStack == null ? ItemStack.EMPTY : hostStack.copy();

        if (attachmentEntries != null) {
            for (CardDisplayPayloads.AttachmentSyncEntry entry : attachmentEntries) {
                this.attachments.add(new Entry(entry.id(), entry.stack(), entry.rotStep()));
            }
        }
        if (!containsSelected(this.selectedCardId)) {
            this.selectedCardId = this.hostId;
        }
    }

    @Override
    protected void init() {
        super.init();
        clearWidgets();

        int right = this.width - PAD;
        int bottom = this.height - PAD;

        doneButton = addRenderableWidget(Button.builder(Component.literal("Done"), b -> saveAndReturn())
                .bounds(right - 74, bottom - BTN_H, 74, BTN_H)
                .build());
        detachButton = addRenderableWidget(Button.builder(Component.literal("Detach"), b -> detachSelected())
                .bounds(PAD, bottom - BTN_H, 74, BTN_H)
                .build());
        upButton = addRenderableWidget(Button.builder(Component.literal("Up"), b -> moveSelected(-1))
                .bounds(PAD + 80, bottom - BTN_H, 48, BTN_H)
                .build());
        downButton = addRenderableWidget(Button.builder(Component.literal("Down"), b -> moveSelected(1))
                .bounds(PAD + 132, bottom - BTN_H, 56, BTN_H)
                .build());
        updateButtonStates();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, width, height, 0xB0000000);

        layout();
        drawList(ctx, mouseX, mouseY);
        drawPreview(ctx, mouseX, mouseY);

        if (saving) {
            ctx.drawCenteredString(font, Component.literal("Saving..."), width / 2, height - 28, 0xFFFFFFFF);
        }

        updateButtonStates();
        super.render(ctx, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean bl) {
        double mx = click.x();
        double my = click.y();
        if (click.button() != 0 || saving) {
            return super.mouseClicked(click, bl);
        }

        for (Map.Entry<UUID, Rect> e : upRects.entrySet()) {
            if (e.getValue().contains(mx, my)) {
                select(e.getKey());
                moveAttachment(e.getKey(), -1);
                return true;
            }
        }
        for (Map.Entry<UUID, Rect> e : downRects.entrySet()) {
            if (e.getValue().contains(mx, my)) {
                select(e.getKey());
                moveAttachment(e.getKey(), 1);
                return true;
            }
        }
        for (Map.Entry<UUID, Rect> e : rowRects.entrySet()) {
            if (e.getValue().contains(mx, my)) {
                select(e.getKey());
                return true;
            }
        }
        return super.mouseClicked(click, bl);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (mouseX >= listX && mouseX <= listX + LIST_W && mouseY >= listY && mouseY <= listY + listH) {
            int maxScroll = maxScroll();
            scroll = clamp(scroll - (int) Math.signum(verticalAmount) * ROW_H, 0, maxScroll);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(KeyEvent key) {
        int kc = keyCode(key);
        if (kc == GLFW.GLFW_KEY_ESCAPE) {
            saveAndReturn();
            return true;
        }
        if (kc == GLFW.GLFW_KEY_UP) {
            moveSelected(-1);
            return true;
        }
        if (kc == GLFW.GLFW_KEY_DOWN) {
            moveSelected(1);
            return true;
        }
        if (kc == GLFW.GLFW_KEY_R && mtgcard$flipHoveredCardFace(Minecraft.getInstance())) {
            return true;
        }
        return super.keyPressed(key);
    }

    @Override
    public void onClose() {
        saveAndReturn();
    }

    @Override
    public boolean mtgcard$flipHoveredCardFace(Minecraft client) {
        if (saving) return false;
        ItemStack selected = selectedStack();
        if (!GuiCardFaceFlipper.isDoubleFaced(selected)) return false;

        int faceCount = GuiCardFaceFlipper.getFaceCount(selected);
        int next = (GuiCardFaceFlipper.readFaceIndex(selected) + 1) % faceCount;
        GuiCardFaceFlipper.writeFaceIndex(selected, next);
        ClientPlayNetworking.send(new CardDisplayPayloads.DisplaySetFaceC2S(entityId, hostId, selectedCardId, next));
        return true;
    }

    private void layout() {
        listX = PAD;
        listY = PAD + 18;
        listH = Math.max(ROW_H, this.height - listY - BTN_H - PAD * 2 - 8);
        previewX = listX + LIST_W + PAD * 2;
        previewY = PAD + 18;
        previewW = Math.max(80, this.width - previewX - PAD);
        previewH = Math.max(80, this.height - previewY - BTN_H - PAD * 2 - 8);
    }

    private void drawList(GuiGraphics ctx, int mouseX, int mouseY) {
        rowRects.clear();
        upRects.clear();
        downRects.clear();

        ctx.drawString(font, Component.literal("Host"), listX, PAD, 0xFFFFFFFF, false);
        drawHostRow(ctx, mouseX, mouseY);

        int attachHeaderY = listY + ROW_H + 8;
        ctx.drawString(font, Component.literal("Attached Cards (" + attachments.size() + ")"), listX, attachHeaderY, 0xFFFFFFFF, false);

        int attachListY = attachHeaderY + 14;
        int attachListH = Math.max(ROW_H, listH - ROW_H - 22);
        int contentH = attachments.size() * ROW_H;
        scroll = clamp(scroll, 0, Math.max(0, contentH - attachListH));

        ctx.fill(listX - 1, attachListY - 1, listX + LIST_W + 1, attachListY + attachListH + 1, 0xFF303030);
        ctx.fill(listX, attachListY, listX + LIST_W, attachListY + attachListH, 0xCC101010);

        int contentW = LIST_W - SCROLLBAR_W - 4;
        ctx.enableScissor(listX, attachListY, listX + contentW, attachListY + attachListH);
        for (int i = 0; i < attachments.size(); i++) {
            int y = attachListY + i * ROW_H - scroll;
            if (y + ROW_H < attachListY) continue;
            if (y > attachListY + attachListH) break;
            drawAttachmentRow(ctx, attachments.get(i), i, listX, y, contentW, mouseX, mouseY);
        }
        ctx.disableScissor();

        drawScrollbar(ctx, listX + contentW + 4, attachListY, SCROLLBAR_W, attachListH, contentH);
    }

    private void drawHostRow(GuiGraphics ctx, int mouseX, int mouseY) {
        boolean selected = hostId.equals(selectedCardId);
        boolean hover = mouseX >= listX && mouseX < listX + LIST_W && mouseY >= listY && mouseY < listY + ROW_H;
        if (selected) ctx.fill(listX, listY, listX + LIST_W, listY + ROW_H, 0x663BE36A);
        else if (hover) ctx.fill(listX, listY, listX + LIST_W, listY + ROW_H, 0x33202020);

        ctx.renderItem(hostStack, listX + 4, listY + 6);
        ctx.drawString(font, trim(hostStack.getHoverName().getString(), LIST_W - 54), listX + 26, listY + 5, 0xFFFFFFFF, false);
        ctx.drawString(font, Component.literal("Host"), listX + 26, listY + 16, 0xFF70E0FF, false);
        rowRects.put(hostId, new Rect(listX, listY, LIST_W, ROW_H));
    }

    private void drawAttachmentRow(GuiGraphics ctx, Entry entry, int index, int x, int y, int w, int mouseX, int mouseY) {
        boolean selected = entry.id().equals(selectedCardId);
        boolean hover = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + ROW_H;
        if (selected) ctx.fill(x, y, x + w, y + ROW_H, 0x663BE36A);
        else if (hover) ctx.fill(x, y, x + w, y + ROW_H, 0x33202020);

        ctx.renderItem(entry.stack(), x + 4, y + 6);
        ctx.drawString(font, trim(entry.stack().getHoverName().getString(), w - 82), x + 26, y + 5, 0xFFFFFFFF, false);
        ctx.drawString(font, Component.literal("#" + (index + 1)), x + 26, y + 16, 0xFFAAAAAA, false);

        int upX = x + w - MINI * 2 - 8;
        int downX = x + w - MINI - 4;
        int by = y + (ROW_H - MINI) / 2;
        boolean upHover = mouseX >= upX && mouseX < upX + MINI && mouseY >= by && mouseY < by + MINI;
        boolean downHover = mouseX >= downX && mouseX < downX + MINI && mouseY >= by && mouseY < by + MINI;
        drawMini(ctx, upX, by, "^", index > 0, upHover);
        drawMini(ctx, downX, by, "v", index < attachments.size() - 1, downHover);

        rowRects.put(entry.id(), new Rect(x, y, w, ROW_H));
        upRects.put(entry.id(), new Rect(upX, by, MINI, MINI));
        downRects.put(entry.id(), new Rect(downX, by, MINI, MINI));
    }

    private void drawPreview(GuiGraphics ctx, int mouseX, int mouseY) {
        ItemStack selected = selectedStack();
        ctx.drawString(font, selected.getHoverName(), previewX, PAD, 0xFFFFFFFF, false);

        boolean hidden = readHidden(selected);
        CardArtManager.TextureRef ref = hidden ? null : CardArtManager.getOrRequestFace(selected, readFace(selected));

        int maxW = previewW;
        int maxH = previewH;
        float aspect = hidden || ref == null ? (488f / 680f) : ref.texW() / (float) ref.texH();
        int drawW = maxW;
        int drawH = Math.round(maxW / aspect);
        if (drawH > maxH) {
            drawH = maxH;
            drawW = Math.round(maxH * aspect);
        }
        int x = previewX + (previewW - drawW) / 2;
        int y = previewY + (previewH - drawH) / 2;

        if (!hidden && (ref == null || ref.id() == null)) {
            ctx.drawCenteredString(font, Component.literal("Loading card art..."),
                    previewX + previewW / 2, previewY + previewH / 2, 0xFFFFFFFF);
            return;
        }

        Identifier tex = hidden ? CARD_BACK_TEX : ref.id();
        ctx.blit(RenderPipelines.GUI_TEXTURED, tex, x, y, 0f, 0f, drawW, drawH, drawW, drawH);

        if (com.spider.mtgcard.client.render.CardFoilUtil.isFoil(selected)) {
            var sweep = com.spider.mtgcard.client.render.CardFoilUtil.computeSweep(System.currentTimeMillis(), drawW);
            if (sweep != null) {
                ctx.fill(x + sweep.drawU(), y, x + sweep.drawU() + sweep.clipW(), y + drawH,
                        com.spider.mtgcard.client.render.CardFoilUtil.guiShimmerColor(1f));
            }
        }

        int faceCount = GuiCardFaceFlipper.getFaceCount(selected);
        if (faceCount > 1) {
            String text = (readFace(selected) + 1) + "/" + faceCount + "  R/F8 flips";
            ctx.drawString(font, Component.literal(text), x + 4, y + 4, 0xFFFFFFFF, false);
        }
    }

    private void drawScrollbar(GuiGraphics ctx, int x, int y, int w, int h, int contentH) {
        ctx.fill(x, y, x + w, y + h, 0x55202020);
        int maxScroll = Math.max(0, contentH - h);
        if (maxScroll <= 0) {
            ctx.fill(x, y, x + w, y + h, 0x88404040);
            return;
        }
        int thumbH = clamp(Math.round((h / (float) contentH) * h), 10, h);
        int thumbY = y + Math.round((h - thumbH) * (scroll / (float) maxScroll));
        ctx.fill(x, thumbY, x + w, thumbY + thumbH, 0xFF505050);
    }

    private void drawMini(GuiGraphics ctx, int x, int y, String label, boolean enabled, boolean hover) {
        int border = !enabled ? 0xFF303030 : (hover ? 0xFF70E0FF : 0xFF404040);
        int bg = !enabled ? 0x66202020 : (hover ? 0xCC1A1A1A : 0xAA101010);
        int color = enabled ? 0xFFFFFFFF : 0xFF777777;
        ctx.fill(x - 1, y - 1, x + MINI + 1, y + MINI + 1, border);
        ctx.fill(x, y, x + MINI, y + MINI, bg);
        ctx.drawString(font, label, x + (MINI - font.width(label)) / 2, y + (MINI - font.lineHeight) / 2, color, false);
    }

    private void select(UUID id) {
        if (containsSelected(id)) {
            selectedCardId = id;
            updateButtonStates();
        }
    }

    private boolean containsSelected(UUID id) {
        if (id == null) return false;
        if (id.equals(hostId)) return true;
        return indexOfAttachment(id) >= 0;
    }

    private void moveSelected(int delta) {
        if (selectedCardId.equals(hostId)) return;
        moveAttachment(selectedCardId, delta);
    }

    private void moveAttachment(UUID id, int delta) {
        int index = indexOfAttachment(id);
        if (index < 0) return;
        int next = index + delta;
        if (next < 0 || next >= attachments.size()) return;
        Entry moved = attachments.remove(index);
        attachments.add(next, moved);
        dirty = true;
        selectedCardId = id;
        updateButtonStates();
    }

    private void detachSelected() {
        if (saving || selectedCardId.equals(hostId)) return;
        saving = true;
        updateButtonStates();
        ClientPlayNetworking.send(new CardDisplayPayloads.AttachmentDetachC2S(
                entityId,
                hostId,
                version,
                selectedCardId,
                currentOrder()
        ));
    }

    private void saveAndReturn() {
        if (saving) return;
        if (!dirty) {
            Minecraft.getInstance().setScreen(new CardLargeViewScreen(
                    selectedStack(),
                    -1,
                    entityId,
                    hostId,
                    version,
                    selectedCardId,
                    attachments.size()
            ));
            return;
        }

        saving = true;
        updateButtonStates();
        ClientPlayNetworking.send(new CardDisplayPayloads.AttachmentReorderC2S(
                entityId,
                hostId,
                version,
                currentOrder(),
                selectedCardId,
                true
        ));
    }

    private void updateButtonStates() {
        boolean attachmentSelected = !hostId.equals(selectedCardId) && indexOfAttachment(selectedCardId) >= 0;
        int index = indexOfAttachment(selectedCardId);
        if (doneButton != null) doneButton.active = !saving;
        if (detachButton != null) detachButton.active = !saving && attachmentSelected;
        if (upButton != null) upButton.active = !saving && attachmentSelected && index > 0;
        if (downButton != null) downButton.active = !saving && attachmentSelected && index >= 0 && index < attachments.size() - 1;
    }

    private ItemStack selectedStack() {
        if (selectedCardId.equals(hostId)) {
            return hostStack;
        }
        int index = indexOfAttachment(selectedCardId);
        if (index >= 0) {
            return attachments.get(index).stack();
        }
        return hostStack;
    }

    private int indexOfAttachment(UUID id) {
        if (id == null) return -1;
        for (int i = 0; i < attachments.size(); i++) {
            if (attachments.get(i).id().equals(id)) return i;
        }
        return -1;
    }

    private List<UUID> currentOrder() {
        ArrayList<UUID> ids = new ArrayList<>(attachments.size());
        for (Entry entry : attachments) {
            ids.add(entry.id());
        }
        return List.copyOf(ids);
    }

    private int maxScroll() {
        int attachListH = Math.max(ROW_H, listH - ROW_H - 22);
        return Math.max(0, attachments.size() * ROW_H - attachListH);
    }

    private static boolean readHidden(ItemStack st) {
        CompoundTag meta = getMeta(st);
        return meta.getBoolean("mtg_hidden").orElse(false);
    }

    private static int readFace(ItemStack st) {
        return com.spider.mtgcard.util.TcgCardMeta.read(st).face();
    }

    private static CompoundTag getMeta(ItemStack st) {
        var comp = st.get(DataComponents.CUSTOM_DATA);
        CompoundTag root = (comp == null) ? new CompoundTag() : comp.copyTag();
        return root.getCompound("mtg_meta").orElseGet(CompoundTag::new);
    }

    private String trim(String text, int maxWidth) {
        if (text == null) return "";
        if (font.width(text) <= maxWidth) return text;
        String dots = "...";
        int dotsW = font.width(dots);
        String out = text;
        while (!out.isEmpty() && font.width(out) + dotsW > maxWidth) {
            out = out.substring(0, out.length() - 1);
        }
        return out.isEmpty() ? dots : out + dots;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int keyCode(KeyEvent key) {
        try { return (int) key.getClass().getMethod("keyCode").invoke(key); } catch (Throwable ignored) {}
        try { return (int) key.getClass().getMethod("key").invoke(key); } catch (Throwable ignored) {}
        try { var f = key.getClass().getDeclaredField("keyCode"); f.setAccessible(true); return f.getInt(key); } catch (Throwable ignored) {}
        try { var f = key.getClass().getDeclaredField("key"); f.setAccessible(true); return f.getInt(key); } catch (Throwable ignored) {}
        return 0;
    }
}
