package com.spider.mtgcard.client.compat.jei;

import com.spider.mtgcard.client.gui.CardDatabaseScreen;
import com.spider.mtgcard.client.gui.CardStoreScreen;
import com.spider.mtgcard.client.gui.DeckControlScreen;
import com.spider.mtgcard.client.gui.DeckboxScreen;
import com.spider.mtgcard.client.gui.GraveyardScreen;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.handlers.IGuiProperties;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Items;

import java.util.List;

@JeiPlugin
public final class MtgcardJeiPlugin implements IModPlugin {
    private static final Identifier PLUGIN_ID = Identifier.fromNamespaceAndPath("mtgcard", "jei_gui_hiding");

    @Override
    public Identifier getPluginUid() {
        return PLUGIN_ID;
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(new DiceCustomizerJeiCategory(registration.getJeiHelpers().getGuiHelper()));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        registration.addRecipes(DiceCustomizerJeiCategory.TYPE, List.of(DiceCustomizerJeiCategory.Recipe.create()));
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addCraftingStation(DiceCustomizerJeiCategory.TYPE, Items.LOOM);
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        disableJei(registration, CardDatabaseScreen.class);
        disableJei(registration, CardStoreScreen.class);
        disableJei(registration, DeckboxScreen.class);
        disableJei(registration, DeckControlScreen.class);
        disableJei(registration, GraveyardScreen.class);
    }

    private static <T extends Screen> void disableJei(IGuiHandlerRegistration registration, Class<T> screenClass) {
        registration.addGuiScreenHandler(screenClass, screen -> new FullScreenGuiProperties(
                screenClass,
                screen.width,
                screen.height
        ));
    }

    /**
     * Claims the whole screen as GUI space so JEI has no room to place either
     * its ingredient list or bookmark overlay. Returning {@code null} no longer
     * suppresses container overlays because newer JEI versions continue on to
     * their generic AbstractContainerScreen handler.
     */
    private record FullScreenGuiProperties(
            Class<? extends Screen> screenClass,
            int screenWidth,
            int screenHeight
    ) implements IGuiProperties {
        @Override
        public int guiLeft() {
            return 0;
        }

        @Override
        public int guiTop() {
            return 0;
        }

        @Override
        public int guiXSize() {
            return screenWidth;
        }

        @Override
        public int guiYSize() {
            return screenHeight;
        }
    }
}
