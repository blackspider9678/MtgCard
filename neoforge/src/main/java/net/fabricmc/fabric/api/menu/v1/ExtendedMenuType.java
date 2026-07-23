package net.fabricmc.fabric.api.menu.v1;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.network.IContainerFactory;

public class ExtendedMenuType<T extends AbstractContainerMenu, D> extends MenuType<T> {
    @FunctionalInterface
    public interface Factory<T extends AbstractContainerMenu, D> {
        T create(int syncId, Inventory inventory, D data);
    }

    private final StreamCodec<? super RegistryFriendlyByteBuf, D> codec;

    public ExtendedMenuType(Factory<T, D> factory, StreamCodec<? super RegistryFriendlyByteBuf, D> codec) {
        super((IContainerFactory<T>) (syncId, inventory, data) ->
                factory.create(syncId, inventory, data == null ? null : codec.decode(data)), FeatureFlags.VANILLA_SET);
        this.codec = codec;
    }

    @SuppressWarnings("unchecked")
    void encodeOpeningData(RegistryFriendlyByteBuf buffer, Object data) {
        ((StreamCodec<RegistryFriendlyByteBuf, Object>) this.codec).encode(buffer, data);
    }
}
