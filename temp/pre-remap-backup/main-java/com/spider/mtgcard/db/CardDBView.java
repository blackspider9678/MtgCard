package com.spider.mtgcard.db;

import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;

import static java.awt.SystemColor.window;

public interface CardDBView {
    int getIntakeCount();
    int getWindowOffset();
    int getMaxWindowOffset();
    void setWindowOffset(int off);
    void shiftWindow(int delta);
    boolean removeFromIntakeByUid(String uid);

    /** A 54-slot inventory bound to the 6×9 window. */
    Container getWindowInventory();

    /** Append a CARD stack to the unbounded intake list (server only). */
    void appendToIntake(ItemStack stack);

    /** True if the window is showing a transient projection (search). */
    boolean isProjectingSearch();

    /** Copy the entire backing list (server). */
    java.util.List<ItemStack> copyIntakeAll();

    /** Reset any transient projection and refresh visible window. */
    void clearSearchProjection();

    default void projectSearchResults(java.util.List<ItemStack> results) {}
}
