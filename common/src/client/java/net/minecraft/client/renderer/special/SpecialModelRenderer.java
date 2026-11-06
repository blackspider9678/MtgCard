package net.minecraft.client.renderer.special;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.MapCodec;
import com.spider.mtgcard.client.compat.render.SubmitNodeCollector;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3fc;

import java.util.function.Consumer;

public interface SpecialModelRenderer<T> {
    @Nullable
    T extractArgument(ItemStack stack);

    void submit(@Nullable T data, PoseStack matrices, SubmitNodeCollector queue, int light, int overlay, boolean glint, int seed);

    void getExtents(Consumer<Vector3fc> vertices);

    interface Unbaked {
        SpecialModelRenderer<?> bake(BakingContext context);

        MapCodec<? extends Unbaked> type();
    }

    interface BakingContext {
    }
}
