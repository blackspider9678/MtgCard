package net.fabricmc.fabric.api.menu.v1;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.neoforged.neoforge.common.extensions.IMenuProviderExtension;

public interface ExtendedMenuProvider<T> extends MenuProvider, IMenuProviderExtension {
    T getScreenOpeningData(ServerPlayer player);

    default T getScreenOpeningData(AbstractContainerMenu menu) {
        return getScreenOpeningData((ServerPlayer) null);
    }

    default boolean shouldCloseCurrentScreen() {
        return true;
    }

    @Override
    default boolean shouldTriggerClientSideContainerClosingOnOpen() {
        return shouldCloseCurrentScreen();
    }

    @Override
    default void writeClientSideData(AbstractContainerMenu menu, RegistryFriendlyByteBuf buffer) {
        if (menu.getType() instanceof ExtendedMenuType<?, ?> extendedMenuType) {
            extendedMenuType.encodeOpeningData(buffer, getScreenOpeningData(menu));
        }
    }
}
