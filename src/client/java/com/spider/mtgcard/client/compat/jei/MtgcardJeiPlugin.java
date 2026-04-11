package com.spider.mtgcard.client.compat.jei;

import com.spider.mtgcard.client.gui.CardDatabaseScreen;
import com.spider.mtgcard.client.gui.CardStoreScreen;
import com.spider.mtgcard.client.gui.DeckControlScreen;
import com.spider.mtgcard.client.gui.DeckboxScreen;
import com.spider.mtgcard.client.gui.GraveyardScreen;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.Identifier;

@JeiPlugin
public final class MtgcardJeiPlugin implements IModPlugin {
    private static final Identifier PLUGIN_ID = Identifier.fromNamespaceAndPath("mtgcard", "jei_gui_hiding");

    @Override
    public Identifier getPluginUid() {
        return PLUGIN_ID;
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
        registration.addGuiScreenHandler(screenClass, screen -> null);
    }
}
