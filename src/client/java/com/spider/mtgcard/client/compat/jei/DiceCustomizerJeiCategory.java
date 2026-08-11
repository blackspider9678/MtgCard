package com.spider.mtgcard.client.compat.jei;

import com.spider.mtgcard.dice.DiceCustomizerIngredients;
import com.spider.mtgcard.item.ModItems;
import com.spider.mtgcard.registry.ModRegistry;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.types.IRecipeType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public final class DiceCustomizerJeiCategory implements IRecipeCategory<DiceCustomizerJeiCategory.Recipe> {
    public static final IRecipeType<Recipe> TYPE = IRecipeType.create(
            ModRegistry.id("dice_customizer"),
            Recipe.class
    );

    private final IDrawable icon;
    private final IDrawable arrow;

    public DiceCustomizerJeiCategory(IGuiHelper guiHelper) {
        this.icon = guiHelper.createDrawableItemStack(new ItemStack(ModItems.D20_DICE));
        this.arrow = guiHelper.getRecipeArrow();
    }

    @Override
    public IRecipeType<Recipe> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("screen.mtgcard.dice_customizer");
    }

    @Override
    public int getWidth() {
        return 116;
    }

    @Override
    public int getHeight() {
        return 38;
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, Recipe recipe, IFocusGroup focuses) {
        builder.addInputSlot(8, 11)
                .setStandardSlotBackground()
                .add(DiceCustomizerIngredients.MATERIAL_ITEM);

        builder.addOutputSlot(88, 11)
                .setOutputSlotBackground()
                .addItemStacks(recipe.outputs());
    }

    @Override
    public void draw(Recipe recipe, IRecipeSlotsView recipeSlotsView, GuiGraphics guiGraphics, double mouseX, double mouseY) {
        arrow.draw(guiGraphics, 42, 11);
    }

    public record Recipe(List<ItemStack> outputs) {
        public static Recipe create() {
            return new Recipe(List.of(
                    new ItemStack(ModItems.D4_DICE),
                    new ItemStack(ModItems.D6_DICE),
                    new ItemStack(ModItems.D8_DICE),
                    new ItemStack(ModItems.D10_DICE),
                    new ItemStack(ModItems.D12_DICE),
                    new ItemStack(ModItems.D20_DICE),
                    new ItemStack(ModItems.D100_DICE)
            ));
        }
    }
}
