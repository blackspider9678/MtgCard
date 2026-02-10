package com.spider.mtgcard.deckcontrol;

import com.spider.mtgcard.screen.ModScreenHandlers;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ArrayPropertyDelegate;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerContext;
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.math.BlockPos;

import java.util.List;

public class DeckControlScreenHandler extends ScreenHandler {

    // synced indices
    public static final int P_LINKED  = 0; // 0/1
    public static final int P_LIBRARY = 2; // 0..99
    public static final int PROP_COUNT = 3;

    private final PlayerInventory playerInv;

    private final ScreenHandlerContext context;
    private final BlockPos pos;

    // store the delegate so we can read it
    private final PropertyDelegate props;

    public static final int GUI_W = 360;
    public static final int GUI_H = 340;

    // ✅ Client ctor (Extended screen): use a plain delegate that receives sync updates
    // ✅ Client ctor (Extended): Fabric already decoded BlockPos for us
    public DeckControlScreenHandler(int syncId, PlayerInventory inv, BlockPos pos) {
        super(ModScreenHandlers.DECKCONTROL, syncId);
        this.playerInv = inv;
        this.pos = pos;
        this.context = ScreenHandlerContext.EMPTY;

        this.props = new ArrayPropertyDelegate(PROP_COUNT);
        this.addProperties(this.props);

        addPlayerInventory(inv);
    }

    // ✅ Server ctor: provide computed delegate
    public DeckControlScreenHandler(int syncId, PlayerInventory inv, BlockPos pos, ScreenHandlerContext context) {
        super(ModScreenHandlers.DECKCONTROL, syncId);
        this.playerInv = inv;
        this.pos = pos;
        this.context = context;

        this.props = new PropertyDelegate() {
            @Override public int size() { return PROP_COUNT; }

            @Override
            public int get(int index) {
                final int[] out = new int[]{0};
                DeckControlScreenHandler.this.context.run((world, p) -> {
                    if (!(world.getBlockEntity(p) instanceof DeckControlBlockEntity dc)) return;
                    out[0] = switch (index) {
                        case P_LINKED  -> dc.hasLinkedDeckbox() ? 1 : 0;
                        case P_LIBRARY -> dc.getLibraryCount();
                        default -> 0;
                    };
                });
                return out[0];
            }

            @Override
            public void set(int index, int value) {
                // no client->server property writes
            }
        };

        this.addProperties(this.props);
        addPlayerInventory(inv);
    }

    private void addPlayerInventory(PlayerInventory inv) {
        final int SLOT = 18;

        // Center the 9-slot-wide inventory
        int invX = (GUI_W - 9 * SLOT) / 2;

        // Anchor inventory to bottom of GUI
        int invHeight = 3 * SLOT + SLOT + 4; // 3 rows + hotbar
        int topInv = GUI_H - 7 - invHeight;

        // Player inventory (3 rows)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(
                        inv,
                        col + row * 9 + 9,
                        invX + col * SLOT,
                        topInv + row * SLOT
                ));
            }
        }

        // Hotbar
        int hotbarY = topInv + 3 * SLOT + 4;
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(
                    inv,
                    col,
                    invX + col * SLOT,
                    hotbarY
            ));
        }
    }


    @Override
    public boolean canUse(PlayerEntity player) {
        // distance-only is fine for this UI (no internal slots)
        return player.squaredDistanceTo(
                pos.getX() + 0.5,
                pos.getY() + 0.5,
                pos.getZ() + 0.5
        ) <= 64.0;
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int slotIndex) {
        return ItemStack.EMPTY;
    }

    public boolean isCardItem(ItemStack st) {
        return st != null && !st.isEmpty()
                && st.isOf(com.spider.mtgcard.item.ModItems.CARD);
    }

    public BlockPos getPos() {
        return pos;
    }

    public boolean isLinked() {
        return this.props.get(P_LINKED) != 0;
    }

    public int getLibraryCount() {
        return this.props.get(P_LIBRARY);
    }

    public boolean isPlayerInventorySlot(Slot slot) {
        return slot.inventory == this.playerInv;
    }
}
