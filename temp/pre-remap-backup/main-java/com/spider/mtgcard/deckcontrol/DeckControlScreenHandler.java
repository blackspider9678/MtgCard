package com.spider.mtgcard.deckcontrol;

import com.spider.mtgcard.screen.ModScreenHandlers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.core.BlockPos;

import java.util.List;

public class DeckControlScreenHandler extends AbstractContainerMenu {

    public static final int P_LINKED  = 0;
    public static final int P_LIBRARY = 2;
    public static final int PROP_COUNT = 3;

    private final Inventory playerInv;
    private final ContainerLevelAccess context;
    private final BlockPos pos;
    private final ContainerData props;

    public static final int GUI_W = 360;
    public static final int GUI_H = 340;

    public DeckControlScreenHandler(int syncId, Inventory inv) {
        this(syncId, inv, BlockPos.ZERO);
    }

    public DeckControlScreenHandler(int syncId, Inventory inv, BlockPos pos) {
        super(ModScreenHandlers.DECKCONTROL, syncId);
        this.playerInv = inv;
        this.pos = pos;
        this.context = ContainerLevelAccess.NULL;

        this.props = new SimpleContainerData(PROP_COUNT);
        this.addDataSlots(this.props);

        addPlayerInventory(inv);
    }

    public DeckControlScreenHandler(int syncId, Inventory inv, BlockPos pos, ContainerLevelAccess context) {
        super(ModScreenHandlers.DECKCONTROL, syncId);
        this.playerInv = inv;
        this.pos = pos;
        this.context = context;

        this.props = new ContainerData() {
            @Override public int getCount() { return PROP_COUNT; }

            @Override
            public int get(int index) {
                final int[] out = new int[]{0};
                DeckControlScreenHandler.this.context.execute((world, p) -> {
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
            }
        };

        this.addDataSlots(this.props);
        addPlayerInventory(inv);
    }

    private void addPlayerInventory(Inventory inv) {
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
    public boolean stillValid(Player player) {
        // distance-only is fine for this UI (no internal slots)
        return player.distanceToSqr(
                pos.getX() + 0.5,
                pos.getY() + 0.5,
                pos.getZ() + 0.5
        ) <= 64.0;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        return ItemStack.EMPTY;
    }

    public boolean isCardItem(ItemStack st) {
        return st != null && !st.isEmpty()
                && st.is(com.spider.mtgcard.item.ModItems.CARD);
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
        return slot.container == this.playerInv;
    }
}
