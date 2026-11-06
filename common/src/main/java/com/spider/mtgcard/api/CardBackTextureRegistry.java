package com.spider.mtgcard.api;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
public final class CardBackTextureRegistry {
 public static ResourceLocation originalTextureForStackOrDefault(ItemStack s) { return ResourceLocation.fromNamespaceAndPath("mtgcard", "textures/item/card.png"); }
 public static ResourceLocation textureForStackOrDefault(ItemStack s) { return CardSleeves.get(s).map(CardSleeve::backTexture).orElseGet(()->originalTextureForStackOrDefault(s)); }
}
